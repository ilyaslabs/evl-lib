package io.github.ilyaslabs.evl.gradle;

import io.github.ilyaslabs.evl.cli.ProjectLoader;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.GraphExport;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Writes the resolved index for AI agents ({@code evlIndex}, build/evl/evl-index.json). */
@CacheableTask
public abstract class EvlIndexTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void write() {
        try {
            List<Path> cp = new ArrayList<>();
            for (File f : getClasspath().getFiles()) {
                cp.add(f.toPath());
            }
            EvlResult r = new ProjectLoader().dir(getProjectDirectory().get().getAsFile().toPath()).classpath(cp).load();
            Path out = getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(out.getParent());
            Files.writeString(out, GraphExport.json(r));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
