package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Options shared by commands that load a project. */
public class ProjectOptions {

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Project directory (contains evl.yaml).")
    Path dir;

    @Option(names = {"--classpath", "-cp"}, description = "Compiled classes and dependency jars (libraries with META-INF/evl are loaded).")
    String classpath;

    @Option(names = "--index", description = "Processor index.json files (default: found in build output).")
    List<Path> indexes = new ArrayList<>();

    @Option(names = "--no-scan", negatable = true, defaultValue = "true", fallbackValue = "true",
        description = "Scan Java sources for annotations when no index.json exists (default: on).")
    boolean scan = true;

    @Option(names = "--no-verify", negatable = true, defaultValue = "true", fallbackValue = "true",
        description = "Verify source references against classes or sources (default: on).")
    boolean verify = true;

    public EvlResult load() throws IOException {
        return new ProjectLoader().dir(dir).classpath(ProjectLoader.splitClasspath(classpath)).index(indexes)
            .scan(scan).verify(verify).load();
    }
}
