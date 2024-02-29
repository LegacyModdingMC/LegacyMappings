package io.github.legacymoddingmc.legacymappings.task;

import io.github.legacymoddingmc.legacymappings.LegacyMappingsPlugin;
import io.github.legacymoddingmc.legacymappings.util.JarInfo;
import io.github.legacymoddingmc.legacymappings.util.MappingUtil;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.srg.SrgFileReader;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class PrepareEnigmaTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputSrgMergedMinecraftJar();

    @InputDirectory
    public abstract DirectoryProperty getInputMappingDir();

    @OutputFile
    public abstract RegularFileProperty getOutputEnigmaJar();

    @OutputFile
    public abstract RegularFileProperty getOutputEnigmaMapping();

    private Path userdevPath;

    @TaskAction
    private void prepareEnigma() throws IOException {
        userdevPath = LegacyMappingsPlugin.getUserdev(getProject());

        Path outJar = getOutputEnigmaJar().getAsFile().get().toPath();
        Files.createDirectories(outJar.getParent());

        cleanUpJar(getInputSrgMergedMinecraftJar().get().getAsFile().toPath(), outJar);
        MemoryMappingTree mappings = MappingUtil.readSplitMappings(getInputMappingDir().get().getAsFile().toPath());
        cleanUpMappings(mappings);
        mappings.accept(MappingWriter.create(getOutputEnigmaMapping().getAsFile().get().toPath(), MappingFormat.TINY_2_FILE));
    }

    private void cleanUpMappings(MemoryMappingTree mappings) throws IOException {
        Path srg = userdevPath.resolve("conf/packaged.srg");

        JarInfo srgJar = new JarInfo(getInputSrgMergedMinecraftJar().get().getAsFile().toPath(), null);

        MemoryMappingTree notchSrg = new MemoryMappingTree();
        SrgFileReader.read(Files.newBufferedReader(srg), "official", "srg", notchSrg);

        MemoryMappingTree srgNotch = new MemoryMappingTree();
        notchSrg.accept(new MappingSourceNsSwitch(srgNotch, "srg"));

        Collection<String> dupes = MappingUtil.removeDuplicateSrgs(srgNotch, srgJar, true)
                .stream().map(m -> m.getOwner().getSrcName() + " " + m.getSrcName() + " " + m.getSrcDesc()).collect(Collectors.toList());

        Map<String, MethodMapping> srgToMethod = new HashMap<>();
        for(ClassMapping cls : mappings.getClasses()) {
            for(MethodMapping method : cls.getMethods()) {
                if(method.getSrcName().startsWith("func_")) {
                    if(method.getDstName(0).equals(method.getSrcName())) {
                        method.setDstName(null, 0);
                    } else {
                        srgToMethod.put(method.getSrcName(), method);
                    }
                    for(MethodArgMapping arg : method.getArgs()) {
                        if(arg.getSrcName().equals(arg.getDstName(0))) {
                            arg.setDstName(null, 0);
                        }
                    }
                }
            }
            for(FieldMapping field : cls.getFields()) {
                if(field.getSrcName().startsWith("field_")) {
                    if(field.getDstName(0).equals(field.getSrcName())) {
                        field.setDstName(null, 0);
                    }
                }
            }
        }

        copyDupes(mappings, dupes, srgToMethod);

        for(ClassMapping cls : mappings.getClasses()) {
            for(MethodMapping method : cls.getMethods()) {
                if(method.getSrcName().startsWith("func_") && method.getDstName(0) == null) {
                    MethodMapping canonical = srgToMethod.get(method.getSrcName());
                    if(canonical != null) {
                        method.setDstName(canonical.getDstName(0), 0);
                        method.setDstName(Objects.toString(canonical.getDstName(1), "") + " # WARNING: needs to be kept in sync with " + canonical.getOwner().getSrcName() + "/" + canonical.getDstName(0), 1);
                        for(MethodArgMapping arg : method.getArgs()) {
                            arg.setDstName(canonical.getArg(-1, arg.getLvIndex(), null).getDstName(0), 0);
                        }
                    }
                }
            }
        }

        // enigma's parser parses null mappings as "", we don't want that so let's drop them
        for(ClassMapping cls : mappings.getClasses()) {
            cls.getMethods().removeIf(m -> m.getDstName(0) == null);
            cls.getFields().removeIf(f -> f.getDstName(0) == null);
        }
    }

    private static void copyDupes(MemoryMappingTree srgMcp, Collection<String> dupes, Map<String, MethodMapping> srgToMethod) throws IOException {
        do {
            if (srgMcp.visitHeader()) {
                srgMcp.visitNamespaces(srgMcp.getSrcNamespace(), Collections.singletonList(srgMcp.getDstNamespaces().get(0)));
            }

            if (srgMcp.visitContent()) {
                for(String dupe : dupes) {
                    String[] parts = dupe.split(" ");
                    String dupeOwner = parts[0];
                    String dupeMember = parts[1];
                    String dupeDesc = parts[2];

                    MethodMapping canonical = srgToMethod.get(dupeMember);

                    if (srgMcp.visitClass(dupeOwner)) {
                        if (srgMcp.visitElementContent(MappedElementKind.CLASS)) {
                            if (srgMcp.visitMethod(dupeMember, dupeDesc)) {
                                if (srgMcp.visitElementContent(MappedElementKind.METHOD)) {
                                    /*srgMcp.visitDstName(
                                            MappedElementKind.METHOD,
                                            0,
                                            canonical.getDstName(0));*/
                                    for (MethodArgMapping canonicalArg : canonical.getArgs()) {
                                        if (srgMcp.visitMethodArg(canonicalArg.getArgPosition(), canonicalArg.getLvIndex(), canonicalArg.getSrcName())) {
                                            //srgMcp.visitDstName(MappedElementKind.METHOD_ARG, 0, canonicalArg.getDstName(0));
                                        }
                                        srgMcp.visitElementContent(MappedElementKind.METHOD_ARG);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } while(!srgMcp.visitEnd());
    }

    private static void cleanUpJar(Path in, Path out) throws IOException {
        Map<String, String> env = Collections.singletonMap("create", "true");

        if(Files.exists(out)) {
            Files.delete(out);
        }

        try (FileSystem zipfs = FileSystems.newFileSystem(URI.create(in.toUri().toString().replace("file://", "jar:file:")), env)) {
            try (FileSystem outZipfs = FileSystems.newFileSystem(URI.create(out.toUri().toString().replace("file://", "jar:file:")), env)) {
                Path outRoot = outZipfs.getPath("/");
                Iterator<Path> stream = Files.walk(zipfs.getPath("/")).iterator();
                while(stream.hasNext()) {
                    Path entry = stream.next();

                    if(Files.isRegularFile(entry)) {
                        byte[] newBytes = transformFile(entry, Files.readAllBytes(entry));
                        if(newBytes != null) {
                            Path parent = entry.getParent();
                            if(parent != null) {
                                Files.createDirectories(outRoot.resolve(parent.toString()));
                            }
                            Path newEntry = Files.createFile(outRoot.resolve(entry.toString()));
                            Files.write(newEntry, newBytes);
                        }
                    }
                }
            }
        }
    }

    private static byte[] transformFile(Path entry, byte[] bytes) {
        if(entry.toString().startsWith("/cpw/") || !entry.toString().endsWith(".class")) {
            return null;
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);

        classNode.fields.removeIf(f -> f.name.equals("__OBFID"));

        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }
}
