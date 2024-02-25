package io.github.legacymoddingmc.legacymappings;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import io.github.legacymoddingmc.legacymappings.task.GenerateMappingsTask;
import org.gradle.api.Task;
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

        srgMergedMinecraftJar = project.getLayout().getBuildDirectory().dir("legacy-mappings").get().file("srg_merged_minecraft.jar").getAsFile().toPath();
        srgMergedMinecraftSourcesJar = project.getLayout().getBuildDirectory().dir("legacy-mappings").get().file("srg_merged_minecraft-sources.jar").getAsFile().toPath();

        TaskProvider<GenerateMappingsTask> taskGenerateMappings = project.getTasks().register("generateMappings", GenerateMappingsTask.class);

        // hack to grab rfg temp files
        if(!Files.isRegularFile(srgMergedMinecraftJar) || !Files.isRegularFile(srgMergedMinecraftSourcesJar)) {
            taskGenerateMappings.configure(task -> {
                task.dependsOn("decompileSrgJar", "cleanupDecompSrgJar");
            });

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
}
