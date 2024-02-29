package io.github.legacymoddingmc.legacymappings.task;

import io.github.legacymoddingmc.legacymappings.util.MappingUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class SaveEnigmaTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputEnigmaFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputMappingDir();

    @TaskAction
    private void saveEnigma() throws IOException {
        MemoryMappingTree enigmaMappings = new MemoryMappingTree();
        MappingReader.read(getInputEnigmaFile().get().getAsFile().toPath(), enigmaMappings);

        MemoryMappingTree realMappings = MappingUtil.readSplitMappings(getOutputMappingDir().get().getAsFile().toPath());

        Set<String> dirtyClasses = new HashSet<>();

        for(ClassMapping dstCls : realMappings.getClasses()) {
            boolean classIsDirty = false;

            for(MethodMapping dstMethod : dstCls.getMethods()) {
                MethodMapping srcMethod = enigmaMappings.getMethod(dstMethod.getOwner().getSrcName(), dstMethod.getSrcName(), dstMethod.getSrcDesc());
                if(srcMethod != null) {
                    if(!Objects.equals(srcMethod.getDstName(0), dstMethod.getDstName(0))) {
                        dstMethod.setDstName(srcMethod.getDstName(0), 0);
                        classIsDirty = true;
                    }
                    for(MethodArgMapping dstArg : dstMethod.getArgs()) {
                        MethodArgMapping srcArg = srcMethod.getArg(-1, dstArg.getLvIndex(), null);
                        if(srcArg != null) {
                            if(!Objects.equals(srcArg.getDstName(0), dstArg.getDstName(0))) {
                                dstArg.setDstName(srcArg.getDstName(0), 0);
                                classIsDirty = true;
                            }
                        }
                    }
                }
            }

            for(FieldMapping dstField : dstCls.getFields()) {
                FieldMapping srcField = enigmaMappings.getField(dstField.getOwner().getSrcName(), dstField.getSrcName(), dstField.getSrcDesc());
                if(srcField != null) {
                    if(!Objects.equals(srcField.getDstName(0), dstField.getDstName(0))) {
                        dstField.setDstName(srcField.getDstName(0), 0);
                        classIsDirty = true;
                    }
                }
            }

            if(classIsDirty) {
                dirtyClasses.add(dstCls.getSrcName());
            }
        }

        CharArrayWriter writer = new CharArrayWriter();
        realMappings.accept(new Tiny2FileWriter(writer, false));
        MappingUtil.writeTiny2Dir(writer.toString(), getOutputMappingDir().get().getAsFile().toPath(), dirtyClasses);
    }
}
