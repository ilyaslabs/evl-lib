package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Ids;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.parse.AnnotationMapper;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import io.github.ilyaslabs.evl.core.parse.DocumentParser;
import io.github.ilyaslabs.evl.core.parse.IndexFile;
import io.github.ilyaslabs.evl.core.parse.Library;
import io.github.ilyaslabs.evl.core.parse.Manifest;
import io.github.ilyaslabs.evl.core.parse.ManifestParser;
import io.github.ilyaslabs.evl.core.parse.MappedAnnotations;
import io.github.ilyaslabs.evl.core.parse.ParsedDocument;
import io.github.ilyaslabs.evl.core.parse.RawDecl;
import io.github.ilyaslabs.evl.core.parse.Versions;
import io.github.ilyaslabs.evl.core.resolve.GraphBuilder;
import io.github.ilyaslabs.evl.core.spec.Rule;
import io.github.ilyaslabs.evl.core.validate.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Entry point of {@code evl-core}: holds the inputs of one EVL application and produces immutable
 * {@link EvlResult}s. {@link #load()} and {@link #update(ChangeSet)} never throw for invalid input
 * (EVL-CORE-001); every problem becomes a diagnostic.
 *
 * <pre>{@code
 * EvlWorkspace ws = EvlWorkspace.builder().discover(Path.of(".")).build();
 * EvlResult result = ws.load();
 * }</pre>
 */
public final class EvlWorkspace {

    /** Directories never searched for documents. */
    public static final Set<String> IGNORED_DIRS = Set.of(".git", ".gradle", ".idea", "build", "target", "out",
        "node_modules", ".evl");

    private final String manifestFile;
    private String manifestContent;
    private final String derivedAppId;
    private final Map<String, String> documentSources = new LinkedHashMap<>();
    private final Map<String, ParsedDocument> documents = new LinkedHashMap<>();
    private final Map<String, List<AnnotationRecord>> annotations = new LinkedHashMap<>();
    private final List<Diagnostic> inputDiagnostics = new ArrayList<>();
    private final List<Library> libraries;
    private final SourceResolver resolver;
    private final CancellationToken cancel;
    private List<GraphBuilder.AppInput> parsedLibraries;
    private List<Diagnostic> libraryDiagnostics;
    private EvlResult last;

    private EvlWorkspace(Builder b) {
        this.manifestFile = b.manifestFile;
        this.manifestContent = b.manifestContent;
        this.derivedAppId = b.derivedAppId;
        this.documentSources.putAll(b.documents);
        this.annotations.putAll(b.annotations);
        this.inputDiagnostics.addAll(b.diagnostics);
        this.libraries = List.copyOf(b.libraries);
        this.resolver = b.resolver;
        this.cancel = b.cancel;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Loads everything and returns a new snapshot. */
    public synchronized EvlResult load() {
        documents.clear();
        last = null;
        return compute();
    }

    /** Applies changes, re-parsing only the changed documents, and returns a new snapshot. */
    public synchronized EvlResult update(ChangeSet changes) {
        changes.documents().forEach((file, content) -> {
            documents.remove(file);
            if (content == null) {
                documentSources.remove(file);
            } else {
                documentSources.put(file, content);
            }
        });
        changes.annotations().forEach((unit, records) -> {
            if (records == null) {
                annotations.remove(unit);
            } else {
                annotations.put(unit, records);
            }
        });
        changes.manifest().ifPresent(m -> manifestContent = m.orElse(null));
        return compute();
    }

    /** The most recent result, or {@code null} before the first load. */
    public synchronized EvlResult last() {
        return last;
    }

    private EvlResult compute() {
        List<Diagnostic> diags = new ArrayList<>(inputDiagnostics);
        try {
            return compute(diags);
        } catch (java.util.concurrent.CancellationException e) {
            throw e;
        } catch (RuntimeException e) {
            // EVL-CORE-001: never throw for input; an internal failure is still reported, not thrown.
            diags.add(Diagnostics.of(Rule.E104, "Internal error while loading EVL data: " + e, Location.of(manifestFile)));
            String id = derivedAppId == null ? "app" : derivedAppId;
            return new EvlResult(EvlGraph.empty(id), diags);
        }
    }

    private EvlResult compute(List<Diagnostic> diags) {
        Manifest manifest;
        RawDecl appDecl = null;
        if (manifestContent == null) {
            String id = derivedAppId != null && Ids.isAppId(derivedAppId) ? derivedAppId : "app";
            manifest = Manifest.derived(id, Location.of(manifestFile));
            if (!annotations.isEmpty() || !documentSources.isEmpty()) {
                diags.add(Diagnostics.of(Rule.W001, "No evl.yaml found; using application id '" + id
                    + "'. Create evl.yaml to set the id, name and documents.", Location.of(manifestFile), null,
                    List.of(), List.of(new io.github.ilyaslabs.evl.core.model.Fix("create-manifest", "Create evl.yaml",
                        Map.of("id", id)))));
            }
        } else {
            ManifestParser.Result r = ManifestParser.parse(manifestFile, manifestContent);
            diags.addAll(r.diagnostics());
            if (r.manifest().isEmpty()) {
                return finish(EvlGraph.empty(derivedAppId == null ? "app" : derivedAppId), diags, Map.of());
            }
            manifest = r.manifest().get();
            appDecl = r.application().orElse(null);
            if (manifest.major() != Versions.MAJOR) {
                return finish(EvlGraph.empty(manifest.id()), diags, manifest.rules());
            }
        }
        if (appDecl == null) {
            appDecl = new RawDecl(io.github.ilyaslabs.evl.core.spec.Kind.APPLICATION, manifest.id(),
                (io.github.ilyaslabs.evl.core.parse.YNode.Mapping) io.github.ilyaslabs.evl.core.parse.YNode.fromPlain(
                    Map.of("id", manifest.id(), "name", manifest.name()), Location.of(manifestFile)),
                new io.github.ilyaslabs.evl.core.model.Origin(io.github.ilyaslabs.evl.core.model.OriginKind.MANIFEST,
                    Location.of(manifestFile), null, null), false);
        }
        List<ParsedDocument> docs = new ArrayList<>();
        for (Map.Entry<String, String> e : documentSources.entrySet()) {
            cancel.check();
            docs.add(documents.computeIfAbsent(e.getKey(), f -> DocumentParser.parse(f, e.getValue())));
        }
        List<AnnotationRecord> all = new ArrayList<>();
        annotations.values().forEach(all::addAll);
        MappedAnnotations mapped = AnnotationMapper.map(all);
        List<GraphBuilder.AppInput> libs = libraries(manifest.id());
        diags.addAll(libraryDiagnostics);
        Set<String> found = new LinkedHashSet<>();
        libs.forEach(l -> found.add(l.manifest().id()));
        for (String dep : manifest.dependencies()) {
            if (!found.contains(dep)) {
                diags.add(Diagnostics.of(Rule.E022, "Library '" + dep + "' listed in dependencies is not on the classpath",
                    manifest.location()));
            }
        }
        GraphBuilder.AppInput input = new GraphBuilder.AppInput(manifest, appDecl, docs, mapped, manifestFile);
        GraphBuilder.Output out = GraphBuilder.build(input, libs, resolver, cancel);
        diags.addAll(out.diagnostics());
        Map<String, Element> elements = share(out.elements());
        EvlGraph pre = new EvlGraph(out.appId(), out.appName(), out.libraries(), elements, out.edges(), out.flows(), Map.of());
        diags.addAll(Validator.validate(pre));
        return finish(pre, diags, manifest.rules());
    }

    /** Reuses element instances that did not change (EVL-CORE-002). */
    private Map<String, Element> share(Map<String, Element> elements) {
        if (last == null) {
            return elements;
        }
        Map<String, Element> prev = last.graph().elementMap();
        Map<String, Element> out = new LinkedHashMap<>();
        elements.forEach((k, e) -> {
            Element p = prev.get(k);
            out.put(k, e.equals(p) ? p : e);
        });
        return out;
    }

    private EvlResult finish(EvlGraph g, List<Diagnostic> diags, Map<String, String> ruleLevels) {
        List<Diagnostic> out = new ArrayList<>();
        Set<Diagnostic> seen = new LinkedHashSet<>();
        for (Diagnostic d : diags) {
            Diagnostic adjusted = adjust(d, ruleLevels);
            if (adjusted != null && seen.add(adjusted)) {
                out.add(adjusted);
            }
        }
        out.sort(null);
        Map<String, Level> worst = new HashMap<>();
        for (Diagnostic d : out) {
            if (d.elementId() != null && g.elementMap().containsKey(d.elementId())) {
                worst.merge(d.elementId(), d.level(), (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
            }
        }
        EvlGraph graph = worst.isEmpty() ? g
            : new EvlGraph(g.appId(), g.appName(), g.libraries(), g.elementMap(), g.edges(), g.flows(), worst);
        last = new EvlResult(graph, out);
        return last;
    }

    private static Diagnostic adjust(Diagnostic d, Map<String, String> levels) {
        if (levels.isEmpty()) {
            return d;
        }
        String configured = levels.get(d.code());
        if (configured == null) {
            configured = levels.get(d.code().replace("EVL-", ""));
        }
        if (configured == null) {
            return d;
        }
        Level level;
        try {
            level = Level.valueOf(configured.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return d;
        }
        Optional<Rule> rule = Rule.byCode(d.code());
        if (rule.isPresent() && rule.get().isLocked() && level.ordinal() > d.level().ordinal()) {
            return d;
        }
        return level == Level.OFF ? null : d.withLevel(level);
    }

    private List<GraphBuilder.AppInput> libraries(String appId) {
        if (parsedLibraries != null) {
            return parsedLibraries;
        }
        List<GraphBuilder.AppInput> out = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        for (Library lib : libraries) {
            ManifestParser.Result mr = ManifestParser.parse(lib.manifestFile(), lib.manifest());
            if (mr.manifest().isEmpty()) {
                diags.add(Diagnostics.of(Rule.E001, "Library " + lib.origin() + " has an invalid META-INF/evl/manifest.json",
                    Location.of(lib.manifestFile())));
                continue;
            }
            Manifest m = mr.manifest().get();
            if (m.id().equals(appId)) {
                continue; // the application's own output directory on its classpath
            }
            if (seen.containsKey(m.id())) {
                diags.add(Diagnostics.of(Rule.E032, "Two libraries with EVL id '" + m.id() + "' are on the classpath: "
                    + seen.get(m.id()) + " and " + lib.origin(), Location.of(lib.manifestFile())));
                continue;
            }
            seen.put(m.id(), lib.origin());
            List<ParsedDocument> docs = new ArrayList<>();
            lib.documents().forEach((f, c) -> docs.add(DocumentParser.parse(f, c)));
            MappedAnnotations ann = MappedAnnotations.EMPTY;
            if (lib.index() != null) {
                ann = AnnotationMapper.map(IndexFile.read(lib.indexFile(), lib.index()).records());
            }
            out.add(new GraphBuilder.AppInput(m, mr.application().orElse(null), docs, ann, lib.origin()));
        }
        parsedLibraries = out;
        libraryDiagnostics = diags;
        return out;
    }

    /** Builder for {@link EvlWorkspace}. */
    public static final class Builder {
        private String manifestFile = "evl.yaml";
        private String manifestContent;
        private String derivedAppId;
        private final Map<String, String> documents = new LinkedHashMap<>();
        private final Map<String, List<AnnotationRecord>> annotations = new LinkedHashMap<>();
        private final List<Library> libraries = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private SourceResolver resolver;
        private CancellationToken cancel = CancellationToken.NONE;
        private Path root;

        private Builder() {
        }

        /** Base directory used to make file names relative. */
        public Builder root(Path root) {
            this.root = root.toAbsolutePath().normalize();
            return this;
        }

        public Builder manifest(Path file) {
            return manifest(name(file), read(file));
        }

        public Builder manifest(String file, String content) {
            this.manifestFile = file;
            this.manifestContent = content;
            return this;
        }

        /** Application id used when there is no manifest (EVL-LNG-002). */
        public Builder derivedAppId(String id) {
            this.derivedAppId = id;
            return this;
        }

        public Builder addDocument(Path file) {
            return addDocument(name(file), read(file));
        }

        public Builder addDocuments(Iterable<Path> files) {
            files.forEach(this::addDocument);
            return this;
        }

        public Builder addDocument(String file, String content) {
            documents.put(file, content);
            return this;
        }

        /** Adds a processor {@code index.json}; its records form one annotation unit. */
        public Builder addIndex(Path file) {
            return addIndex(name(file), read(file));
        }

        public Builder addIndex(String file, String content) {
            IndexFile.Result r = IndexFile.read(file, content);
            diagnostics.addAll(r.diagnostics());
            annotations.put(file, r.records());
            return this;
        }

        public Builder addAnnotations(String unit, List<AnnotationRecord> records) {
            annotations.put(unit, List.copyOf(records));
            return this;
        }

        /** Adds a dependency jar or class directory; ignored when it has no EVL data. */
        public Builder addLibrary(Path jarOrDir) {
            Library.read(jarOrDir, diagnostics).ifPresent(libraries::add);
            return this;
        }

        public Builder addLibrary(Library library) {
            libraries.add(library);
            return this;
        }

        public Builder sourceResolver(SourceResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder cancellation(CancellationToken token) {
            this.cancel = token == null ? CancellationToken.NONE : token;
            return this;
        }

        /**
         * Reads {@code evl.yaml} in {@code dir} (or derives an id from the directory name) and every
         * document matching the manifest's {@code documents} globs, skipping build output.
         */
        public Builder discover(Path dir) {
            Path base = dir.toAbsolutePath().normalize();
            if (root == null) {
                root(base);
            }
            Path mf = base.resolve("evl.yaml");
            List<String> globs = Manifest.DEFAULT_DOCUMENTS;
            List<String> excludes = List.of();
            if (Files.isRegularFile(mf)) {
                manifest(mf);
                ManifestParser.Result r = ManifestParser.parse(manifestFile, manifestContent);
                if (r.manifest().isPresent()) {
                    globs = r.manifest().get().documents();
                    excludes = r.manifest().get().exclude();
                }
            } else {
                manifestFile = name(mf);
                derivedAppId(deriveId(base.getFileName() == null ? "app" : base.getFileName().toString()));
            }
            for (Path p : findDocuments(base, globs, excludes)) {
                addDocument(p);
            }
            return this;
        }

        public EvlWorkspace build() {
            return new EvlWorkspace(this);
        }

        private String name(Path file) {
            Path abs = file.toAbsolutePath().normalize();
            String s = root != null && abs.startsWith(root) ? root.relativize(abs).toString() : file.toString();
            return s.replace('\\', '/');
        }

        private static String read(Path file) {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Lowercases and dashes a directory name into an application id. */
    public static String deriveId(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            s = "app" + (s.isEmpty() ? "" : "-" + s);
        }
        return s;
    }

    /** Finds document files under {@code base} matching globs, sorted, skipping ignored directories. */
    public static List<Path> findDocuments(Path base, List<String> globs, List<String> excludes) {
        List<PathMatcher> include = matchers(globs);
        List<PathMatcher> exclude = matchers(excludes);
        List<Path> out = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (!d.equals(base) && IGNORED_DIRS.contains(d.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    Path rel = base.relativize(f);
                    if (include.stream().anyMatch(m -> m.matches(rel)) && exclude.stream().noneMatch(m -> m.matches(rel))) {
                        out.add(f);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(null);
        return out;
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        List<PathMatcher> out = new ArrayList<>();
        for (String g : globs) {
            out.add(FileSystems.getDefault().getPathMatcher("glob:" + g));
            if (g.startsWith("**/")) {
                out.add(FileSystems.getDefault().getPathMatcher("glob:" + g.substring(3)));
            }
            if (g.contains("/**/")) {
                out.add(FileSystems.getDefault().getPathMatcher("glob:" + g.replace("/**/", "/")));
            }
        }
        return out;
    }
}
