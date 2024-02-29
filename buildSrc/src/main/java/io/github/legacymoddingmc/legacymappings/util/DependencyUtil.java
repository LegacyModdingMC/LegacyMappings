package io.github.legacymoddingmc.legacymappings.util;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

public class DependencyUtil {
    public static Path extractDep(Project project, Dependency dep, Path tmpDir) {
        Path depPath = resolveDep(project, dep);
        Path outPath = tmpDir.resolve("extracted/" + FilenameUtils.removeExtension(depPath.getFileName().toString()));
        extract(project, depPath, outPath);
        return outPath;
    }

    public static void extract(Project project, Path zip, Path out) {
        project.copy(c -> {
            c.from(project.zipTree(zip));
            c.into(out);
        });
    }

    public static Path resolveDep(Project project, Dependency dep) {
        Set<File> files = project.getConfigurations().detachedConfiguration(dep).resolve();

        if(files.size() != 1) {
            throw new IllegalStateException("Expected one file resolving " + dep + " but found these: " + files);
        }
        return files.iterator().next().toPath();
    }
}
