package io.github.legacymoddingmc.legacymappings.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import io.github.legacymoddingmc.legacymappings.LegacyMappingsExtension;
import io.github.legacymoddingmc.legacymappings.LegacyMappingsPlugin;
import io.github.legacymoddingmc.legacymappings.util.DependencyUtil;
import io.github.legacymoddingmc.legacymappings.util.JarInfo;
import io.github.legacymoddingmc.legacymappings.util.MappingUtil;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.srg.SrgFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.ElementMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GenerateMappingsTask extends DefaultTask {

    private LegacyMappingsExtension ext;

    private Path tmpDir;

    private Path userdevPath;
    private final List<Path> mcpPaths = new ArrayList<>();
    private Path yarnPath;
    private Path featherPath;

    @Input
    public abstract Property<Boolean> getIsDebug();

    private boolean writeToFile;

    public GenerateMappingsTask() {
        getIsDebug().convention(false);
    }

    @TaskAction
    public void run() throws IOException {
        ext = (LegacyMappingsExtension) getProject().getExtensions().getByName("legacyMappings");

        tmpDir = getTemporaryDir().toPath();

        userdevPath = extractDep(ext.getUserdev());

        for(Dependency mcpDep : ext.getMcpLayers()) {
            mcpPaths.add(extractDep(mcpDep));
        }

        yarnPath = extractDep(ext.getYarn());
        featherPath = extractDep(ext.getFeather());

        writeToFile = getIsDebug().get();

        generateMappings();
    }

    private Path extractDep(Dependency dep) {
        return DependencyUtil.extractDep(getProject(), dep, tmpDir);
    }

    public void generateMappings() throws IOException {
        Path srg = userdevPath.resolve("conf/packaged.srg");

        MemoryMappingTree notchCalamusFeather = new MemoryMappingTree();
        MappingReader.read(featherPath.resolve("mappings/mappings.tiny"), MappingFormat.TINY_2_FILE, notchCalamusFeather);
        // Work around ornithe bug:
        // For some reason some names lack a notch->calamus mapping and the calamus name is put as the notch name.
        removeIfSourceIsIntermediary(notchCalamusFeather);

        MemoryMappingTree notchIntermediaryYarn = new MemoryMappingTree();
        MappingReader.read(yarnPath.resolve("mappings/mappings.tiny"), MappingFormat.TINY_2_FILE, notchIntermediaryYarn);
        // Work around fabric bug:
        // For some reason some names lack a notch->calamus mapping and the calamus name is put as the notch name.
        removeIfSourceIsIntermediary(notchIntermediaryYarn);

        JarInfo srgJar = new JarInfo(LegacyMappingsPlugin.srgMergedMinecraftJar, LegacyMappingsPlugin.srgMergedMinecraftSourcesJar);

        MemoryMappingTree notchSrg = new MemoryMappingTree();
        SrgFileReader.read(Files.newBufferedReader(srg), "official", "srg", notchSrg);

        // srg -> feather

        MemoryMappingTree notchFeatherSrg = new MemoryMappingTree();
        notchFeatherSrg.setSrcNamespace("official");
        notchCalamusFeather.accept(notchFeatherSrg);
        dropDuplicateDstNames(notchFeatherSrg);
        notchFeatherSrg.setDstNamespaces(Collections.singletonList("named"));
        notchSrg.accept(notchFeatherSrg);

        MemoryMappingTree srgFeather = new MemoryMappingTree();
        srgFeather.setSrcNamespace("srg");
        notchFeatherSrg.accept(new MappingSourceNsSwitch(srgFeather, "srg"));
        srgFeather.setDstNamespaces(Collections.singletonList("named"));

        // srg -> yarn

        MemoryMappingTree notchYarnSrg = new MemoryMappingTree();
        notchYarnSrg.setSrcNamespace("official");
        notchIntermediaryYarn.accept(notchYarnSrg);
        dropDuplicateDstNames(notchYarnSrg);
        notchYarnSrg.setDstNamespaces(Collections.singletonList("named"));
        notchSrg.accept(notchYarnSrg);

        MemoryMappingTree srgYarn = new MemoryMappingTree();
        srgYarn.setSrcNamespace("srg");
        notchYarnSrg.accept(new MappingSourceNsSwitch(srgYarn, "srg"));
        srgYarn.setDstNamespaces(Collections.singletonList("named"));

        MemoryMappingTree srgMcpUserdev = readMcp(userdevPath.resolve("conf"), notchSrg, srgJar);

        List<MemoryMappingTree> srgMcps = new ArrayList<>();
        for(Path mcpPath : mcpPaths) {
            srgMcps.add(readMcp(mcpPath, notchSrg, srgJar));
        }

        // can't be bothered to softcode the order here. it's only used for javadoc.
        List<Pair<String, VisitableMappingTree>> mappingsToJoin = new ArrayList<>();
        mappingsToJoin.add(Pair.of("yarn", srgYarn));
        mappingsToJoin.add(Pair.of("feather", srgFeather));
        for(int i = srgMcps.size() - 1; i >= 0; i--) {
            mappingsToJoin.add(Pair.of("mcp-" + i, srgMcps.get(i)));
        }
        mappingsToJoin.add(Pair.of("mcpUserdev", srgMcpUserdev));

        MemoryMappingTree joined = joinMappings(mappingsToJoin);
        // yarn adds some bogus source names
        dropBogusNames(joined, srgMcpUserdev, false);

        List<String> mcpLayerNames = new ArrayList<>();
        mcpLayerNames.add("mcpUserdev");
        for(int i = 0; i < srgMcps.size(); i++) {
            mcpLayerNames.add("mcp-" + i);
        }

        MemoryMappingTree srgMcpPreferOlder = layer(joined, "mcp",
                Lists.reverse(mcpLayerNames),
                null);

        MemoryMappingTree srgMcpPreferNewer = layer(joined, "mcp",
                mcpLayerNames,
                null);

        List<Pair<String, VisitableMappingTree>> finalMappingsToJoin = new ArrayList<>();
        finalMappingsToJoin.add(Pair.of("mcpPreferNewer", srgMcpPreferNewer));
        finalMappingsToJoin.add(Pair.of("mcpPreferOlder", srgMcpPreferOlder));
        finalMappingsToJoin.addAll(mappingsToJoin);

        MemoryMappingTree joinedFinal = joinMappings(finalMappingsToJoin);
        // yarn adds some bogus source names
        dropBogusNames(joinedFinal, srgMcpUserdev, false);

        List<String> layerOrder = ext.getLayerOrder();
        List<String> commentOrder = ext.getCommentOrder();

        if(getIsDebug().get()) {
            boolean wroteMcp = false;
            List<String> expandedCommentOrder = new ArrayList<>();
            for(String s : commentOrder) {
                if(!s.startsWith("mcp")) {
                    expandedCommentOrder.add(s);
                } else if(!wroteMcp) { // once is enough
                    expandedCommentOrder.addAll(mcpLayerNames);
                    wroteMcp = true;
                }
            }
            commentOrder = expandedCommentOrder;
        }

        MemoryMappingTree merged = layer(joinedFinal, "named",
                layerOrder,
                commentOrder);
        MappingUtil.removeDuplicateSrgs(merged, srgJar, false);
        removeHardToMap(merged);

        MemoryMappingTree mergedCompleted = new MemoryMappingTree();
        merged.accept(new MappingNsCompleter(mergedCompleted, Collections.singletonMap("named", "srg")));

        if(!writeToFile) {
            CharArrayWriter writer = new CharArrayWriter();
            mergedCompleted.accept(new Tiny2FileWriter(writer, false));
            MappingUtil.writeTiny2Dir(writer.toString(), getProject().getLayout().getProjectDirectory().dir("mappings").getAsFile().toPath(), null);
        } else {
            System.out.println("Writing debug mappings with comment order: " + commentOrder);
            mergedCompleted.accept(MappingWriter.create(getProject().getLayout().getBuildDirectory().file("mappings/mappings-debug.tiny").get().getAsFile().toPath(), MappingFormat.TINY_2_FILE), VisitOrder.createByName());
        }
    }

    /** Removes the destination names of all hard-to-map names (e.g. enums), since mapping these requires manual consideration. */
    private void removeHardToMap(MemoryMappingTree mappings) {
        for(ClassMapping cls : mappings.getClasses()) {
            for(MethodMapping m : cls.getMethods()) {
                if(!m.getSrcName().startsWith("func_")) {
                    m.setDstName(null, 0);
                    for(MethodArgMapping arg : m.getArgs()) {
                        if(!arg.getSrcName().startsWith("p_")) {
                            arg.setDstName(null, 0);
                        }
                    }
                }
            }
            for(FieldMapping m : cls.getFields()) {
                if(!m.getSrcName().startsWith("field_")) {
                    m.setDstName(null, 0);
                }
            }
        }
    }

    private static MemoryMappingTree layer(MemoryMappingTree joined, String dstName, List<String> layerOrder, List<String> commentOrder) throws IOException {
        MemoryMappingTree layered = new MemoryMappingTree();
        for(String layerName : layerOrder) {
            MemoryMappingTree layer = new MemoryMappingTree();
            joined.accept(layer);
            layer.setDstNamespaces(Collections.singletonList(layerName));
            MemoryMappingTree layerRenamed = new MemoryMappingTree();
            layer.accept(new MappingNsRenamer(layerRenamed, Collections.singletonMap(layer.getDstNamespaces().get(0), dstName)));
            layerRenamed.accept(layered);
        }

        if(commentOrder != null) {
            StringBuilder altNames = new StringBuilder();
            int[] commentIndexOrder = commentOrder.stream().mapToInt(ns -> joined.getNamespaceId(ns)).toArray();

            do {
                if (layered.visitHeader()) {
                    layered.visitNamespaces("srg", Collections.singletonList("comment"));
                }

                if (layered.visitContent()) {
                    for(ClassMapping joinedClass : joined.getClasses()) {
                        if(layered.visitClass(joinedClass.getSrcName())) {
                            if(layered.visitElementContent(MappedElementKind.CLASS)) {
                                for(FieldMapping joinedField : joinedClass.getFields()) {
                                    if(layered.visitField(joinedField.getSrcName(), joinedField.getSrcDesc())) {
                                        collectAltNames(joined, joinedField, altNames, commentIndexOrder);
                                        layered.visitDstName(MappedElementKind.FIELD, 0, "#" + altNames.toString());
                                        layered.visitElementContent(MappedElementKind.FIELD);
                                    }
                                }
                                for(MethodMapping joinedMethod : joinedClass.getMethods()) {
                                    if(layered.visitMethod(joinedMethod.getSrcName(), joinedMethod.getSrcDesc())) {
                                        collectAltNames(joined, joinedMethod, altNames, commentIndexOrder);
                                        layered.visitDstName(MappedElementKind.METHOD, 0, "#" + altNames.toString());
                                        if(layered.visitElementContent(MappedElementKind.METHOD)) {
                                            for(MethodArgMapping arg : joinedMethod.getArgs()) {
                                                if (layered.visitMethodArg(-1, arg.getLvIndex(), arg.getSrcName())) {
                                                    collectAltNames(joined, arg, altNames, commentIndexOrder);
                                                    layered.visitDstName(MappedElementKind.METHOD_ARG, 0, "#" + altNames.toString());
                                                    layered.visitElementContent(MappedElementKind.METHOD_ARG);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } while(!layered.visitEnd());
        }

        return layered;
    }

    private static void collectAltNames(MappingTree joined, ElementMapping joinedField, StringBuilder altNames, int[] commentIndexOrder) {
        altNames.setLength(0);
        String lastName = null;
        for(int i = 0; i < commentIndexOrder.length; i++) {
            if(i > 0) {
                altNames.append(", ");
            }
            String name = joinedField.getDstName(commentIndexOrder[i]);
            if(name != null && name.equals(lastName)) {
                name = ":";
            } else {
                if(name == null) {
                    name = "~";
                }
                lastName = name;
            }
            altNames.append(name);
        }
    }

    private static void dropBogusNames(MemoryMappingTree tree, MemoryMappingTree correct, boolean verbose) {
        int yarnId = tree.getNamespaceId("yarn");
        tree.getClasses().removeIf(cls -> {
            ClassMapping correctCls = correct.getClass(cls.getSrcName());
            if(correctCls != null) {
                cls.getMethods().removeIf(m -> {
                    MethodMapping correctMethod = correctCls.getMethod(m.getSrcName(), m.getSrcDesc());
                    if(correctMethod == null) {
                        if(verbose) {
                            System.out.println("dropping bogus method: " + m.getOwner() + "." + m);
                            System.out.println("  yarn name: " + m.getOwner().getDstName(yarnId) + "." + ObjectUtils.firstNonNull(m.getDstName(yarnId), m.getSrcName()) + m.getDstDesc(yarnId));
                        }
                        return true;
                    } else {
                        m.getArgs().removeIf(a -> {
                            MethodArgMapping correctArg = correctMethod.getArg(-1, a.getLvIndex(), null);
                            if(correctArg == null) {
                                if(verbose) {
                                    System.out.println("dropping bogus arg: " + m.getOwner() + "." + m + " " + a);
                                    System.out.println("  yarn name: " + m.getOwner().getDstName(yarnId) + "." + ObjectUtils.firstNonNull(m.getDstName(yarnId), m.getSrcName()) + m.getDstDesc(yarnId));
                                }
                                return true;
                            }
                            return false;
                        });
                    }
                    return false;
                });
                cls.getFields().removeIf(f -> {
                    FieldMapping correctField = correctCls.getField(f.getSrcName(), f.getSrcDesc());
                    if(correctField == null) {
                        if(verbose) {
                            System.out.println("dropping bogus field: " + f.getOwner() + "." + f);
                            System.out.println("  yarn name: " + f.getOwner().getDstName(yarnId) + "." + f.getDstName(yarnId) + f.getDstDesc(yarnId));
                        }
                        return true;
                    }
                    return false;
                });
            } else {
                if(verbose) {
                    System.out.println("dropping bogus class: " + cls);
                    System.out.println("  yarn name: " + cls.getDstName(yarnId));
                }
                return true;
            }
            return false;
        });
    }

    /** Converts A->B, A->C, A->D to A->(B, C, D)
     * @throws IOException */
    private MemoryMappingTree joinMappings(List<Pair<String, VisitableMappingTree>> args) throws IOException {
        MemoryMappingTree joined = new MemoryMappingTree();

        for(Pair<String, VisitableMappingTree> pair : args) {
            String name = pair.getLeft();
            VisitableMappingTree mapping = pair.getRight();

            if(joined.getSrcNamespace() == null) {
                joined.setSrcNamespace(mapping.getSrcNamespace());
            } else if(!joined.getSrcNamespace().equals(mapping.getSrcNamespace())){
                throw new IllegalArgumentException("All mappings be of the same source namespace");
            }

            Preconditions.checkArgument(mapping.getDstNamespaces().size() == 1, "All mappings must have exactly one destination namespace");

            VisitableMappingTree renamedMapping = new MemoryMappingTree();
            mapping.accept(new MappingNsRenamer(renamedMapping, Collections.singletonMap(mapping.getDstNamespaces().get(0), name)));

            renamedMapping.accept(joined);
        }

        return joined;
    }

    /** Removes all methods that start with m_ or method_, and all fields that start with f_ or field_. */
    public static void removeIfSourceIsIntermediary(MemoryMappingTree tree) {
        for(MappingTree.ClassMapping cls : tree.getClasses()) {
            cls.getMethods().removeIf(m -> m.getSrcName().startsWith("m_") || m.getSrcName().startsWith("method_"));
            cls.getFields().removeIf(f -> f.getSrcDesc().startsWith("f_") || f.getSrcDesc().startsWith("field_"));
        }
    }

    /** Converts `A -> (B, C)` to `A -> (B, null)` if B == C */
    public static void dropDuplicateDstNames(MemoryMappingTree mappings) {
        Preconditions.checkArgument(mappings.getDstNamespaces().size() == 2, "Mapping must have two destination namespaces");

        for(MappingTree.ClassMapping klass : mappings.getClasses()) {
            for(MappingTree.MethodMapping method : klass.getMethods()) {
                if(method.getDstName(0).equals(method.getDstName(1))) {
                    method.setDstName(null, 1);
                }
            }
            for(MappingTree.FieldMapping field : klass.getFields()) {
                if(field.getDstName(0).equals(field.getDstName(1))) {
                    field.setDstName(null, 1);
                }
            }
        }
    }

    public static MemoryMappingTree readMcp(Path dir, MemoryMappingTree notchSrg, JarInfo srgJar) throws IOException {
        Path fields = dir.resolve("fields.csv");
        Path methods = dir.resolve("methods.csv");
        Path params = dir.resolve("params.csv");
        MemoryMappingTree srgMcp = new MemoryMappingTree();
        return readSrgMcpMappings(srgMcp, "mcp", notchSrg, readDocumentedCsv(fields), readDocumentedCsv(methods), readCsv(params), srgJar);
    }

    private static Map<String, String> readCsv(Path csv) throws IOException {
        Map<String, String> names = new HashMap<>(5000);
        try (CSVReader csvReader = createCsvReader(csv)) {
            for (String[] line : csvReader) {
                names.put(line[0], line[1]);
            }
        }
        return names;
    }

    public static MemoryMappingTree readSrgMcpMappings(MemoryMappingTree srgMcp, String namespace, MemoryMappingTree notchSrg, Map<String, DocumentedName> fields, Map<String, DocumentedName> methods, Map<String, String> params, JarInfo srgJar) throws IOException {
        Map<String, Collection<String>> paramsByMethod = new HashMap<>();
        for(String k : params.keySet()) {
            String id = k.split("_")[1];
            paramsByMethod.computeIfAbsent(id, x -> new ArrayList<>()).add(k);
        }

        while(true) {
            if (srgMcp.visitHeader()) {
                srgMcp.visitNamespaces("srg", Collections.singletonList(namespace));
            }

            if (srgMcp.visitContent()) {
                for(MappingTree.ClassMapping notchSrgClass : notchSrg.getClasses()) {
                    if(srgMcp.visitClass(notchSrgClass.getDstName(0))) {
                        srgMcp.visitDstName(MappedElementKind.CLASS, 0, notchSrgClass.getDstName(0));
                        if(srgMcp.visitElementContent(MappedElementKind.CLASS)) {
                            for(MappingTree.FieldMapping notchSrgField : notchSrgClass.getFields()) {
                                if(srgMcp.visitField(notchSrgField.getDstName(0), notchSrgField.getDstDesc(0))) {
                                    DocumentedName fieldName = fields.get(notchSrgField.getDstName(0));
                                    srgMcp.visitDstName(MappedElementKind.FIELD, 0, DocumentedName.getName(fieldName));
                                    if(srgMcp.visitElementContent(MappedElementKind.FIELD)) {
                                        String javadoc = DocumentedName.getJavadoc(fieldName);
                                        if(javadoc != null && !javadoc.isEmpty()) {
                                            srgMcp.visitComment(MappedElementKind.FIELD, DocumentedName.getJavadoc(fieldName));
                                        }
                                    }
                                }
                            }
                            for(JarInfo.ClassInfo.MethodInfo method : srgJar.getData().get(notchSrgClass.getDstName(0)).getMethods().values()) {
                                String srgName = method.getName();
                                String srgDesc = method.getDesc();

                                if(srgMcp.visitMethod(srgName, srgDesc)) {
                                    DocumentedName methodName = methods.get(srgName);
                                    srgMcp.visitDstName(MappedElementKind.METHOD, 0, DocumentedName.getName(methodName));

                                    if(srgMcp.visitElementContent(MappedElementKind.METHOD)) {
                                        String javadoc = DocumentedName.getJavadoc(methodName);
                                        if(javadoc != null && !javadoc.isEmpty()) {
                                            srgMcp.visitComment(MappedElementKind.METHOD, javadoc);
                                        }

                                        for(String paramSrc : srgJar.lookupMethodParameters(method)) {
                                            int parameterIndex = Integer.parseInt(paramSrc.split("_")[2]);
                                            String dstName = params.get(paramSrc);
                                            if (srgMcp.visitMethodArg(-1, parameterIndex, paramSrc)) {
                                                if(dstName != null) {
                                                    srgMcp.visitDstName(MappedElementKind.METHOD_ARG, 0, dstName);
                                                }
                                                srgMcp.visitElementContent(MappedElementKind.METHOD_ARG);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if(srgMcp.visitEnd()) {
                break;
            }
        }

        return srgMcp;
    }

    public static Map<String, DocumentedName> readDocumentedCsv(Path csv) throws IOException {
        Map<String, DocumentedName> names = new HashMap<>(5000);
        try (CSVReader csvReader = createCsvReader(csv)) {
            for (String[] line : csvReader) {
                names.put(line[0], new DocumentedName(line[1], line[3].replaceAll("\\\\n", "\n")));
            }
        }
        return names;
    }

    public static CSVReader createCsvReader(Path file) throws IOException {
        final CSVParser csvParser = new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER)
                .withStrictQuotes(false).build();
        return new CSVReaderBuilder(Files.newBufferedReader(file, StandardCharsets.UTF_8)).withSkipLines(1)
                .withCSVParser(csvParser).build();
    }

    public static class DocumentedName {
        private final String name;
        private final String javadoc;

        public DocumentedName(String name, String javadoc) {
            this.name = name;
            this.javadoc = javadoc;
        }

        public static String getName(DocumentedName name) {
            return name == null ? null : name.name;
        }
        public static String getJavadoc(DocumentedName name) {
            return name == null ? null : name.javadoc;
        }
    }

}
