package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.cli.code.CodeIndex;
import io.github.ilyaslabs.evl.cli.code.CodeResolver;
import io.github.ilyaslabs.evl.cli.scan.JavaSourceScanner;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import io.github.ilyaslabs.evl.core.parse.IndexFile;
import io.github.ilyaslabs.evl.core.parse.Library;
import io.github.ilyaslabs.evl.core.parse.Manifest;
import io.github.ilyaslabs.evl.core.parse.ManifestParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads an EVL application the way the CLI and build plugins do: manifest and documents, processor
 * output (or a JavaParser scan of sources), libraries on the classpath, and a code resolver.
 */
public final class ProjectLoader {

    /** Well-known locations of processor output relative to the project directory. */
    public static final List<String> INDEX_LOCATIONS = List.of(
        "build/classes/java/main/META-INF/evl/index.json",
        "build/generated/ksp/main/resources/META-INF/evl/index.json",
        "build/tmp/kapt3/classes/main/META-INF/evl/index.json",
        "build/resources/main/META-INF/evl/index.json",
        "target/classes/META-INF/evl/index.json");

    private Path dir = Path.of(".");
    private final List<Path> classpath = new ArrayList<>();
    private final List<Path> indexes = new ArrayList<>();
    private boolean scan = true;
    private boolean verify = true;
    private String note;

    public ProjectLoader dir(Path d) {
        this.dir = d;
        return this;
    }

    public ProjectLoader classpath(List<Path> entries) {
        classpath.addAll(entries);
        return this;
    }

    public ProjectLoader index(List<Path> files) {
        indexes.addAll(files);
        return this;
    }

    public ProjectLoader scan(boolean s) {
        this.scan = s;
        return this;
    }

    public ProjectLoader verify(boolean v) {
        this.verify = v;
        return this;
    }

    /** What was used for annotations (index files, source scan, or nothing). */
    public String note() {
        return note;
    }

    public static List<Path> splitClasspath(String cp) {
        List<Path> out = new ArrayList<>();
        if (cp == null || cp.isBlank()) {
            return out;
        }
        for (String s : cp.split(File.pathSeparator)) {
            if (!s.isBlank()) {
                out.add(Path.of(s));
            }
        }
        return out;
    }

    public EvlResult load() throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        EvlWorkspace.Builder b = EvlWorkspace.builder().root(root).discover(root);
        Manifest manifest = manifest(root);
        List<Path> sourceRoots = new ArrayList<>();
        for (String s : manifest == null || manifest.sources().isEmpty() ? List.of("src/main/java", "src/main/kotlin")
            : manifest.sources()) {
            sourceRoots.add(root.resolve(s));
        }
        List<Path> found = new ArrayList<>(indexes);
        if (found.isEmpty()) {
            for (String loc : INDEX_LOCATIONS) {
                Path p = root.resolve(loc);
                if (Files.isRegularFile(p)) {
                    found.add(p);
                }
            }
        }
        Set<String> annotatedKeys = new LinkedHashSet<>();
        if (!found.isEmpty()) {
            for (Path p : found) {
                b.addIndex(p);
                IndexFile.read(p.toString(), Files.readString(p)).records().forEach(r -> collectKey(r, annotatedKeys));
            }
            note = "annotations from " + found.size() + " index file(s)";
        } else if (scan) {
            Map<String, List<AnnotationRecord>> scanned = new JavaSourceScanner(root).scan(sourceRoots);
            scanned.forEach(b::addAnnotations);
            scanned.values().forEach(l -> l.forEach(r -> collectKey(r, annotatedKeys)));
            note = "annotations scanned from Java sources (" + scanned.size() + " file(s))";
        } else {
            note = "no annotations";
        }
        CodeIndex code = new CodeIndex();
        for (Path entry : classpath) {
            if (Library.isLibrary(entry)) {
                b.addLibrary(entry);
            }
        }
        if (verify) {
            boolean compiled = false;
            for (Path entry : classpath) {
                code.addClasses(entry);
            }
            for (String out : List.of("build/classes/java/main", "build/classes/kotlin/main", "target/classes")) {
                Path p = root.resolve(out);
                if (Files.isDirectory(p)) {
                    code.addClasses(p);
                    compiled = true;
                }
            }
            if (!compiled) {
                code.addSources(root, sourceRoots);
            } else {
                code.addSources(root, List.of()); // keep Kotlin detection off when classes exist
            }
            List<Path> resources = List.of(root.resolve("src/main/resources"), root.resolve("config"), root);
            Set<String> keys = CodeResolver.configKeys(resources);
            keys.addAll(annotatedKeys);
            b.sourceResolver(new CodeResolver(code, keys));
        }
        return b.build().load();
    }

    private static void collectKey(AnnotationRecord r, Set<String> keys) {
        if (r.annotation().equals("ConfigProperty") && r.string("key") != null) {
            keys.add(r.string("key"));
        }
    }

    private static Manifest manifest(Path root) throws IOException {
        Path m = root.resolve("evl.yaml");
        if (!Files.isRegularFile(m)) {
            return null;
        }
        return ManifestParser.parse("evl.yaml", Files.readString(m)).manifest().orElse(null);
    }
}
