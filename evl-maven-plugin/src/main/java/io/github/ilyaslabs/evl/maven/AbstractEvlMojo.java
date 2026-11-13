package io.github.ilyaslabs.evl.maven;

import io.github.ilyaslabs.evl.cli.ProjectLoader;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;

/** Shared parameters. Fields are injected by Maven as declared in META-INF/maven/plugin.xml. */
abstract class AbstractEvlMojo extends AbstractMojo {

    /** Project base directory. */
    File basedir;
    /** Build output directory (target/classes). */
    File outputDirectory;
    /** Build directory (target). */
    File buildDirectory;
    /** Compile classpath, including target/classes. */
    List<String> classpathElements = new ArrayList<>();
    /** Skip all EVL goals. */
    boolean skip;

    EvlResult load() throws IOException {
        List<Path> cp = new ArrayList<>();
        if (classpathElements != null) {
            for (String e : classpathElements) {
                cp.add(Path.of(e));
            }
        }
        if (outputDirectory != null && !cp.contains(outputDirectory.toPath())) {
            cp.add(outputDirectory.toPath());
        }
        return new ProjectLoader().dir(basedir.toPath()).classpath(cp).load();
    }
}
