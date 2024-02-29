package io.github.legacymoddingmc.legacymappings;

import io.github.legacymoddingmc.legacymappings.task.ExportMappingsTask;
import io.github.legacymoddingmc.legacymappings.task.GenerateMappingsTask;
import io.github.legacymoddingmc.legacymappings.task.PrepareEnigmaTask;
import io.github.legacymoddingmc.legacymappings.task.SaveEnigmaTask;
import io.github.legacymoddingmc.legacymappings.util.DependencyUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class LegacyMappingsPlugin implements Plugin<Project> {

    public static Path srgMergedMinecraftJar;
    public static Path srgMergedMinecraftSourcesJar;

    @Override
    public void apply(Project project) {
        project.getExtensions().create("legacyMappings", LegacyMappingsExtension.class, project);

        project.getRepositories().maven(repo -> {
            repo.setName("Legacy Fabric");
            repo.setUrl(URI.create("https://repo.legacyfabric.net/repository/legacyfabric"));
        });
        project.getRepositories().maven(repo -> {
            repo.setName("OrnitheMC");
            repo.setUrl(URI.create("https://maven.ornithemc.net/releases"));
        });

        RegularFile srgMergedMinecraftJarRegular = project.getLayout().getBuildDirectory().dir("legacy-mappings").get().file("srg_merged_minecraft.jar");

        srgMergedMinecraftJar = project.getLayout().getBuildDirectory().dir("legacy-mappings").get().file("srg_merged_minecraft.jar").getAsFile().toPath();
        srgMergedMinecraftSourcesJar = project.getLayout().getBuildDirectory().dir("legacy-mappings").get().file("srg_merged_minecraft-sources.jar").getAsFile().toPath();

        TaskProvider<GenerateMappingsTask> taskGenerateMappings = project.getTasks().register("generateMappings", GenerateMappingsTask.class, task -> {
            task.setGroup("mapping");
            task.setDescription("Generates a new set of mappings, layering the mappings specified in the legacyMappings extension.");
        });

        TaskProvider<GenerateMappingsTask> taskGenerateDebugMappings = project.getTasks().register("generateDebugMappings", GenerateMappingsTask.class, task -> {
            task.setGroup("mapping");
            task.setDescription("Generates a new set of mappings, layering the mappings specified in the legacyMappings extension. The MCP layers are not flattened in the comments.");
            task.getIsDebug().set(true);
        });

        TaskProvider<ExportMappingsTask> taskExportMappings = project.getTasks().register("exportMappings", ExportMappingsTask.class, task -> {
            task.setGroup("mapping build");
            task.setDescription("Converts the mappings from source format into a consumable format.");
            task.getInputTinyDir().set(project.getLayout().getProjectDirectory().dir("mappings"));
            task.getOutputTinyFile().set(project.getLayout().getBuildDirectory().file("mappings/mappings.tiny"));
            task.getOutputCsvDir().set(project.getLayout().getBuildDirectory().dir("mappings/csv"));
        });

        TaskProvider<PrepareEnigmaTask> taskPrepareEnigma = project.getTasks().register("prepareEnigma", PrepareEnigmaTask.class, task -> {
            task.setGroup("internal mapping");
            task.setDescription("Converts the mappings and the Minecraft jar into a format Enigma can work with.");
            task.getInputSrgMergedMinecraftJar().set(srgMergedMinecraftJarRegular);
            task.getInputMappingDir().set(project.getLayout().getProjectDirectory().dir("mappings"));
            task.getOutputEnigmaJar().set(project.getLayout().getBuildDirectory().file("legacy-mappings/enigma-workspace/minecraft.jar"));
            task.getOutputEnigmaMapping().set(project.getLayout().getBuildDirectory().file("legacy-mappings/enigma-workspace/mappings.tiny"));
        });

        TaskProvider<SaveEnigmaTask> taskSaveEnigma = project.getTasks().register("saveEnigma", SaveEnigmaTask.class, task -> {
            task.setGroup("internal mapping");
            task.setDescription("Propagates the changes made to Enigma's copy of the mappings to the real version.");
            task.getInputEnigmaFile().set(taskPrepareEnigma.flatMap(PrepareEnigmaTask::getOutputEnigmaMapping));
            task.getOutputMappingDir().set(project.getLayout().getProjectDirectory().dir("mappings"));
        });

        setUpJarHack(project, taskGenerateMappings, taskPrepareEnigma);
    }

    private void setUpJarHack(Project project, TaskProvider<? extends Task>... needers) {
        // hack to grab rfg temp files for tasks that need it
        if(!Files.isRegularFile(srgMergedMinecraftJar) || !Files.isRegularFile(srgMergedMinecraftSourcesJar)) {
            for(TaskProvider<? extends Task> t : needers) {
                t.configure(task -> {
                    task.dependsOn("decompileSrgJar", "cleanupDecompSrgJar");
                });
            }

            project.afterEvaluate(a -> {
                try {
                    Files.deleteIfExists(getIJarOutputTaskOutput(project.getTasks().getByName("decompileSrgJar")).get().getAsFile().toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            project.getTasks().named("deobfuscateMergedJarToSrg").configure(t -> {
                t.doLast(a -> {
                    try {
                        if(!Files.exists(srgMergedMinecraftJar)) {
                            Files.createDirectories(srgMergedMinecraftJar.getParent());
                            Files.copy(getIJarOutputTaskOutput(t).get().getAsFile().toPath(), srgMergedMinecraftJar);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
            project.getTasks().named("cleanupDecompSrgJar").configure(t -> {
                t.doLast(a -> {
                    try {
                        if(!Files.exists(srgMergedMinecraftSourcesJar)) {
                            Files.createDirectories(srgMergedMinecraftSourcesJar.getParent());
                            Files.copy(getIJarOutputTaskOutput(t).get().getAsFile().toPath(), srgMergedMinecraftSourcesJar);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }
    }

    // I don't know how to add RFG to the buildSrc classpath without conflicting with the plugin in the regular project, lul
    private static RegularFileProperty getIJarOutputTaskOutput(Task task) {
        try {
            return (RegularFileProperty)task.getClass().getMethod("getOutputJar").invoke(task);
        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public static Path getUserdev(Project project) {
        LegacyMappingsExtension ext = (LegacyMappingsExtension) project.getExtensions().getByName("legacyMappings");
        return DependencyUtil.extractDep(project, ext.getUserdev(),
                project.getLayout().getBuildDirectory().dir("legacy-mappings/userdev").get().getAsFile().toPath());
    }
}
