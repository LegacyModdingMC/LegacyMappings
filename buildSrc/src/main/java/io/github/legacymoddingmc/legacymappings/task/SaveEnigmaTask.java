package io.github.legacymoddingmc.legacymappings.task;

import io.github.legacymoddingmc.legacymappings.util.MappingUtil;
import org.gradle.api.DefaultTask;

import java.io.BufferedWriter;
import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.tools.ant.types.CharSet;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

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
import net.fabricmc.mappingio.tree.MappingTree.MemberMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitOrder;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

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
