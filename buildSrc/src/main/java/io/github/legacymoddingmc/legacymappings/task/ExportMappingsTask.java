package io.github.legacymoddingmc.legacymappings.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodArgMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class ExportMappingsTask extends DefaultTask {

    @InputDirectory
    public abstract DirectoryProperty getInputTinyDir();

    @OutputFile
    public abstract RegularFileProperty getOutputTinyFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputCsvDir();

    @TaskAction
    public void run() throws IOException {
        build(getInputTinyDir().get().getAsFile().toPath(), getOutputTinyFile().get().getAsFile().toPath());
        exportCsv(getInputTinyDir().get().getAsFile().toPath(), getOutputCsvDir().get().getAsFile().toPath());
    }

    private static void exportCsv(Path srcDir, Path outDir) throws IOException {
        MemoryMappingTree mappings = new MemoryMappingTree();
        try(StringReader reader = new StringReader(String.join("\n", readSplitMappings(srcDir)))) {
            MappingReader.read(reader, mappings);
        }

        writeCsv(mappings, outDir);
    }

    private static void build(Path srcDir, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.write(out, readSplitMappings(srcDir), StandardOpenOption.CREATE);
    }

    private static List<String> readSplitMappings(Path srcDir) throws IOException {
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

    private static void writeCsv(MemoryMappingTree mappingTree, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        try(ICSVWriter methodWriter = createCsvWriter(outDir.resolve("methods.csv").toFile());
            ICSVWriter fieldWriter = createCsvWriter(outDir.resolve("fields.csv").toFile());
            ICSVWriter paramsWriter = createCsvWriter(outDir.resolve("params.csv").toFile())) {
            methodWriter.writeNext(new String[] {"searge", "name", "side", "desc"}, false);
            fieldWriter.writeNext(new String[] {"searge", "name", "side", "desc"}, false);
            paramsWriter.writeNext(new String[] {"param", "name", "side"}, false);

            for(ClassMapping cls : mappingTree.getClasses()) {
                for(MethodMapping m : cls.getMethods()) {
                    if(m.getSrcName().startsWith("func_") && !m.getSrcName().equals(m.getDstName(0))) {
                        String comment = ObjectUtils.firstNonNull(m.getComment(), "").replaceAll("\\n", "\\\\n");
                        methodWriter.writeNext(new String[] {m.getSrcName(), m.getDstName(0), "0", comment}, false);
                    }

                    for(MethodArgMapping arg : m.getArgs()) {
                        if((arg.getSrcName().startsWith("p_") && Character.isDigit(arg.getSrcName().charAt(2)))
                                || (arg.getSrcName().startsWith("p_i") && Character.isDigit(arg.getSrcName().charAt(3)))) {
                            if(!arg.getSrcName().equals(arg.getDstName(0))) {
                                paramsWriter.writeNext(new String[] {arg.getSrcName(), arg.getDstName(0), "0"}, false);
                            }
                        }
                    }
                }
                for(FieldMapping f : cls.getFields()) {
                    if(f.getSrcName().startsWith("field_") && !f.getSrcName().equals(f.getDstName(0))) {
                        String comment = ObjectUtils.firstNonNull(f.getComment(), "").replaceAll("\\n", "\\\\n");
                        fieldWriter.writeNext(new String[] {f.getSrcName(), f.getDstName(0), "0", comment}, false);
                    }
                }
            }
        }
    }

    public static ICSVWriter createCsvWriter(File file) throws IOException {
        final CSVParser csvParser = new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER)
                .withStrictQuotes(false).build();
        return new CSVWriterBuilder(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)).withParser(csvParser).build();
    }

}