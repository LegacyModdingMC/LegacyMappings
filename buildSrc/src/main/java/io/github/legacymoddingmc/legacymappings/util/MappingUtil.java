package io.github.legacymoddingmc.legacymappings.util;

import io.github.legacymoddingmc.legacymappings.util.JarInfo.ClassInfo;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.MemberMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MappingUtil {
    public static Collection<MethodMapping> removeDuplicateSrgs(MemoryMappingTree tree, JarInfo srgJar, boolean dry) throws IOException {
        Set<MethodMapping> removed = new HashSet<>();

        Map<String, Set<NameOriginCandidate>> originMap = new HashMap<>();
        Map<String, NameOriginCandidate> finalOriginMap = new HashMap<>();
        for(ClassMapping cls : tree.getClasses()) {
            for(MethodMapping m : cls.getMethods()) {
                String name = m.getSrcName();
                if(name.startsWith("func_")) {
                    NameOriginCandidate origin = findOrigin(cls.getSrcName(), name, false, tree, srgJar);
                    originMap.computeIfAbsent(name, k -> new HashSet<>()).add(origin);
                }
            }
        }
        for(String name : originMap.keySet()) {
            List<NameOriginCandidate> candidates = new ArrayList<>();
            candidates.addAll(originMap.get(name));
            NameOriginCandidate chosen = null;
            if(candidates.size() == 1) {
                chosen = candidates.get(0);
            } else {
                candidates.sort((l, r) -> {
                    // prefer concrete class, otherwise prefer shorter name
                    return l.itf && !r.itf ? 1 : !l.itf && r.itf ? -1 : l.getSimpleName().length() - r.getSimpleName().length();
                });

                chosen = candidates.get(0);

                if(!dry) {
                    System.out.println("Origin conflict: " + candidates + " # " + tree.getMethod(chosen.owner, chosen.member, chosen.desc).getDstName(0));
                    System.out.println("Choosing " + chosen + "\n");
                }

                for(int i = 1; i < candidates.size(); i++) {
                    NameOriginCandidate candidate = candidates.get(i);
                    removed.add(tree.getMethod(candidate.owner, candidate.member, candidate.desc));
                }
            }
            finalOriginMap.put(name, chosen);
        }

        if(!dry) {
            for(ClassMapping cls : tree.getClasses()) {
                cls.getMethods().removeIf(m -> m.getSrcName().startsWith("func_") && !finalOriginMap.get(m.getSrcName()).matches(cls, m));
            }
        }

        return removed;
    }

    private static NameOriginCandidate findOrigin(String className, String name, boolean isField, MemoryMappingTree tree, JarInfo srgJar) {
        List<NameOriginCandidate> candidates = new ArrayList<>();
        findOrigin(className, name, isField, tree, srgJar, candidates, 0);

        if(candidates.size() == 1) {
            return candidates.get(0);
        } else {
            Collections.sort(candidates);
            List<NameOriginCandidate> candidatesOfSameType = candidates.stream().distinct().filter(c -> c.itf == candidates.get(0).itf).collect(Collectors.toList());
            if(candidatesOfSameType.size() == 1) {
                return candidatesOfSameType.get(0);
            } else if(candidatesOfSameType.get(1).distance == candidatesOfSameType.get(0).distance) {
                System.out.println("multiple candidates for " + name + ": " + candidates);
                return null;
            } else {
                return candidatesOfSameType.get(0);
            }
        }
    }

    private static void findOrigin(String className, String name, boolean isField, MemoryMappingTree tree, JarInfo srgJar, List<NameOriginCandidate> candidates, int level) {
        ClassMapping cls = tree.getClass(className);
        for(MemberMapping method : (isField ? cls.getFields() : cls.getMethods())) {
            if(method.getSrcName().equals(name)) {
                candidates.add(new NameOriginCandidate(cls.getSrcName(), method.getSrcName(), method.getSrcDesc(), level, srgJar.getData().get(className).isInterface()));
            }
        }
        for(ClassInfo superior : srgJar.getSuperiors(className)) {
            findOrigin(superior.getClassNode().name, name, isField, tree, srgJar, candidates, level + 1);
        }
    }

    public static class NameOriginCandidate implements Comparable<NameOriginCandidate> {
        private final String owner;
        private final String member;
        private final String desc;
        private final int distance;
        private final boolean itf;

        public NameOriginCandidate(String owner, String member, String desc, int distance, boolean itf) {
            this.owner = owner;
            this.member = member;
            this.desc = desc;
            this.distance = distance;
            this.itf = itf;
        }

        @Override
        public int compareTo(NameOriginCandidate o) {
            // prefer interface, otherwise prefer farther ancestor
            return itf && !o.itf ? -1 : o.itf && !itf ? 1 : -Integer.compare(distance, o.distance);
        }

        public boolean matches(ClassMapping cls, MethodMapping m) {
            return (owner.equals(cls.getSrcName()) && member.equals(m.getSrcName()) && desc.equals(m.getSrcDesc()));
        }

        public String getSimpleName() {
            int lastSlash = owner.lastIndexOf('/');
            if(lastSlash != -1) {
                return owner.substring(lastSlash + 1);
            } else {
                return owner;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof NameOriginCandidate) {
                NameOriginCandidate oc = (NameOriginCandidate)obj;
                return owner.equals(oc.owner) && member.equals(oc.member) && desc.equals(oc.desc);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return owner + "." + member + desc;
        }
    }

    public static MemoryMappingTree readSplitMappings(Path srcDir) throws IOException {
        MemoryMappingTree mappings = new MemoryMappingTree();
        try(StringReader reader = new StringReader(String.join("\n", readSplitMappingLines(srcDir)))) {
            MappingReader.read(reader, mappings);
        }
        return mappings;
    }

    public static List<String> readSplitMappingLines(Path srcDir) throws IOException {
        List<String> lines = new ArrayList<>();
        boolean wroteHeader = false;
        try(Stream<Path> files = Files.walk(srcDir)) {
            for(Iterator<Path> it = files.iterator(); it.hasNext();) {
                Path p = it.next();
                if(p.toString().endsWith(".tiny") && Files.isRegularFile(p)) {
                    for(String line : Files.readAllLines(p)) {
                        if(line.startsWith("tiny")) {
                            if(!wroteHeader) {
                                lines.add(line);
                                wroteHeader = true;
                            }
                        } else {
                            lines.add(line);
                        }
                    }
                }
            }
        }
        return lines;
    }

    public static void writeTiny2Dir(String string, Path outDir, Set<String> classesToWrite) throws IOException {
        String header = string.split("\\n")[0];
        List<String> classes = Arrays.asList(string.split("\\nc\\t")).stream().filter(c -> !c.startsWith("tiny")).map(c -> c = "c\t" + c).collect(Collectors.toList());
        for(String classText : classes) {
            int firstTab = classText.indexOf('\t');
            int secondTab = classText.indexOf('\t', firstTab + 1);
            String className = classText.substring(firstTab + 1, secondTab);

            if(classesToWrite == null || classesToWrite.contains(className)) {
                Path outPath = outDir.resolve(className + ".tiny");
                Files.createDirectories(outPath.getParent());
                Files.write(outPath, Arrays.asList((header + "\n" + classText).split("\\n")));
            }
        }
    }
}
