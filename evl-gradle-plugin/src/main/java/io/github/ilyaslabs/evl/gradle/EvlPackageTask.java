package io.github.ilyaslabs.evl.gradle;

import io.github.ilyaslabs.evl.cli.LibraryPackager;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Writes META-INF/evl/manifest.json and documents for the jar (EVL-DOC-030). */
@CacheableTask
public abstract class EvlPackageTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    /** evl.yaml and documents, for up-to-date checks. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Input
    @Optional
    public abstract Property<String> getArtifact();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void write() {
        try {
            java.nio.file.Path out = getOutputDirectory().get().getAsFile().toPath();
            if (java.nio.file.Files.exists(out)) {
                try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(out)) {
                    for (java.nio.file.Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        java.nio.file.Files.delete(p);
                    }
                }
            }
            LibraryPackager.write(getProjectDirectory().get().getAsFile().toPath(),
                getOutputDirectory().get().getAsFile().toPath(), getArtifact().getOrNull());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
