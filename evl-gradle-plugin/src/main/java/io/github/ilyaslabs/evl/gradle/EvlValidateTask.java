package io.github.ilyaslabs.evl.gradle;

import io.github.ilyaslabs.evl.cli.ProjectLoader;
import io.github.ilyaslabs.evl.cli.out.Printer;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Level;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Validates the application against the compiled classpath ({@code evlValidate}). */
@CacheableTask
public abstract class EvlValidateTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    /** Compiled classes, processor output and dependency jars. */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<Boolean> getFailOnWarning();

    @Input
    public abstract Property<Boolean> getFailOnError();

    @Input
    public abstract Property<String> getFormat();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void validate() {
        try {
            List<Path> cp = new ArrayList<>();
            List<Path> indexes = new ArrayList<>();
            for (File f : getClasspath().getFiles()) {
                cp.add(f.toPath());
                Path idx = f.toPath().resolve("META-INF/evl/index.json");
                if (f.isDirectory() && Files.isRegularFile(idx) && !Files.isRegularFile(f.toPath().resolve("META-INF/evl/manifest.json"))) {
                    indexes.add(idx);
                }
            }
            Path dir = getProjectDirectory().get().getAsFile().toPath();
            EvlResult r = new ProjectLoader().dir(dir).classpath(cp).index(indexes).load();
            String format = getFormat().get().toLowerCase(Locale.ROOT);
            Printer.Format f = format.equals("sarif") ? Printer.Format.SARIF : Printer.Format.TEXT;
            StringWriter report = new StringWriter();
            Printer.diagnostics(r, f, false, "gradle", new PrintWriter(report));
            Path out = getReport().get().getAsFile().toPath();
            Files.createDirectories(out.getParent());
            Files.writeString(out, report.toString());
            for (Diagnostic d : r.diagnostics()) {
                String line = d.toString();
                if (d.level() == Level.ERROR) {
                    getLogger().error(line);
                } else if (d.level() == Level.WARNING || d.level() == Level.WEAK_WARNING) {
                    getLogger().warn(line);
                } else {
                    getLogger().info(line);
                }
            }
            long errors = r.count(Level.ERROR);
            long warnings = r.count(Level.WARNING);
            if (getFailOnError().get() && errors > 0) {
                throw new GradleException("EVL validation failed with " + errors + " error(s). See " + out);
            }
            if (getFailOnWarning().get() && warnings > 0) {
                throw new GradleException("EVL validation found " + warnings + " warning(s) and failOnWarning is set. See " + out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
