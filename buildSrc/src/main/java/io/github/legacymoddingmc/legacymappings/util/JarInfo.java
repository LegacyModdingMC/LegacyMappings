package io.github.legacymoddingmc.legacymappings.util;

import io.github.legacymoddingmc.legacymappings.util.JarInfo.ClassInfo.MethodInfo;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JarInfo {

    private final Map<String, ClassInfo> data = new HashMap<>();

    private final Map<String, List<String>> funcParams = new HashMap<>();

    private static final Pattern PARAM_FINDER = Pattern
            .compile("p_\\d+_\\d+_");

    public JarInfo(Path srgJar, Path srgSourcesJar) throws IOException {
        extractClassInfo(srgJar);
        if(srgSourcesJar != null) {
            extractParameters(srgSourcesJar);
        }
    }

    public Map<String, ClassInfo> getData() {
        return data;
    }

    private void extractClassInfo(Path srgJar) throws IOException {
        try(JarFile jf = new JarFile(srgJar.toFile())) {
            for(JarEntry je : Collections.list(jf.entries())) {
                if(je.getName().endsWith(".class")) {
                    byte[] bytes = IOUtils.toByteArray(jf.getInputStream(je));
                    readClass(bytes);
                }
            }
        }
    }

    private void extractParameters(Path path) throws IOException {
        final Matcher mParam = PARAM_FINDER.matcher("");

        try (FileSystem zipfs = FileSystems.newFileSystem(URI.create(path.toUri().toString().replace("file://", "jar:file:")), Collections.singletonMap("create", "true"))) {
            for(Path root : zipfs.getRootDirectories()) {
                Iterator<Path> stream = Files.walk(root).iterator();
                while(stream.hasNext()) {
                    Path entry = stream.next();

                    if(entry.toString().endsWith(".java")) {
                        for(String line : Files.readAllLines(entry)) {
                            mParam.reset(line);

                            while (mParam.find()) {
                                final String found = mParam.group(0);

                                String id = found.split("_")[1];
                                funcParams.computeIfAbsent(id, x -> new ArrayList<>()).add(found);
                            }
                        }
                    }
                }
            }
        }

        for(String k : funcParams.keySet()) {
            List<String> l = funcParams.get(k);
            l = l.stream().distinct().sorted().collect(Collectors.toList());
            funcParams.put(k, l);
        }
    }

    /** Returns the list of parameters for a method that has a SRG id. This includes interfaces, unlike the ClassInfos. */
    public List<String> getParameters(String id) {
        return funcParams.get(id);
    }

    public List<ClassInfo> getSuperiors(String className) {
        ClassInfo ci = data.get(className);
        if(ci == null) {
            return Collections.emptyList();
        } else {
            List<ClassInfo> superiors = new ArrayList<>();
            ClassInfo parent = data.get(ci.classNode.superName);
            if(parent != null) {
                superiors.add(parent);
            }
            for(String itfName : ci.classNode.interfaces) {
                ClassInfo itf = data.get(itfName);
                if(itf != null) {
                    superiors.add(itf);
                }
            }
            return superiors;
        }
    }

    private void readClass(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

        ClassInfo ci = new ClassInfo(classNode);

        data.put(classNode.name, ci);
    }

    public static class ClassInfo {
        private final ClassNode classNode;

        private final Map<Pair<String, String>, MethodInfo> methods = new HashMap<>();

        public ClassInfo(ClassNode cn) {
            this.classNode = cn;
            for(MethodNode method : cn.methods) {
                ClassInfo.MethodInfo methodInfo = new ClassInfo.MethodInfo(method);
                methods.put(Pair.of(method.name, method.desc), methodInfo);
            }
        }

        @Override
        public String toString() {
            return classNode.name;
        }

        public boolean isInterface() {
            return (classNode.access & Opcodes.ACC_INTERFACE) != 0;
        }

        public ClassNode getClassNode() {
            return classNode;
        }

        public Map<Pair<String, String>, MethodInfo> getMethods() {
            return methods;
        }

        public static class MethodInfo {
            private final String name;
            private final String desc;
            private final List<String> parameters = new ArrayList<>();

            public MethodInfo(MethodNode method) {
                if(method.localVariables != null) {
                    for(LocalVariableNode lv : method.localVariables) {
                        if(lv.name.startsWith("p_") && lv.name.endsWith("_")) {
                            parameters.add(lv.name);
                        }
                    }
                }
                this.name = method.name;
                this.desc = method.desc;
            }

            public String getName() {
                return name;
            }

            public String getDesc() {
                return desc;
            }

            public List<String> getParameters() {
                return parameters;
            }
        }
    }

    public Collection<String> lookupMethodParameters(MethodInfo method) {
        if(method.name.startsWith("func_")) {
            String id = method.name.split("_")[1];
            Collection<String> idParams = funcParams.get(id);
            if(idParams != null) {
                return idParams;
            }
        }
        return method.getParameters();
    }

}
