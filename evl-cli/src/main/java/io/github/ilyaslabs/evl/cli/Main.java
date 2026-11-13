package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.cli.out.Printer;
import io.github.ilyaslabs.evl.core.api.EvlGraph;
import io.github.ilyaslabs.evl.core.api.EvlQueries;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.api.GraphExport;
import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.parse.Manifest;
import io.github.ilyaslabs.evl.core.parse.ManifestParser;
import io.github.ilyaslabs.evl.core.parse.Versions;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code evl} command line (spec 7.4). Exit codes: 0 ok, 1 EVL errors, 2 tool failure. */
@Command(name = "evl", mixinStandardHelpOptions = true, versionProvider = Main.Version.class,
    description = "EVL: validate and explore architecture and workflow documentation.",
    subcommands = {Main.Validate.class, Main.Inspect.class, Main.ListCmd.class, Main.Graph.class, Main.Index.class,
        Main.Fmt.class, Main.Package.class, Main.Init.class, Main.Schema.class, Main.Compat.class, Main.Rules.class})
public final class Main implements Callable<Integer> {

    public static final int OK = 0;
    public static final int ERRORS = 1;
    public static final int FAILURE = 2;

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true)));
    }

    /** Runs the CLI with the given streams (used by tests and build plugins). */
    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine cl = new CommandLine(new Main());
        cl.setOut(out);
        cl.setErr(err);
        cl.setCaseInsensitiveEnumValuesAllowed(true);
        cl.setExecutionExceptionHandler((ex, cmd, parsed) -> {
            err.println("evl: " + ex.getMessage());
            return FAILURE;
        });
        cl.setParameterExceptionHandler((ex, a) -> {
            err.println("evl: " + ex.getMessage());
            ex.getCommandLine().usage(err);
            return FAILURE;
        });
        int code = cl.execute(args);
        out.flush();
        err.flush();
        return code;
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return OK;
    }

    public static String version() {
        try (InputStream in = Main.class.getResourceAsStream("evl-version.properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            }
            return p.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }

    /** Version provider. */
    public static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"evl " + version() + " (EVL spec " + Versions.SPEC + ")"};
        }
    }

    static boolean color(PrintWriter out) {
        return System.console() != null && System.getenv("NO_COLOR") == null && out != null
            && System.getenv("TERM") != null && !"dumb".equals(System.getenv("TERM"));
    }

    // ------------------------------------------------------------------ validate

    @Command(name = "validate", description = "Load the application, validate it and print diagnostics.")
    static final class Validate implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Mixin
        ProjectOptions project;
        @Option(names = "--format", defaultValue = "text", description = "text, json or sarif.")
        Printer.Format format;
        @Option(names = "--fail-on-warning", description = "Exit 1 when there are warnings.")
        boolean failOnWarning;
        @Option(names = "--min-level", defaultValue = "info", description = "Hide diagnostics below this level: error, warning, weak_warning, info.")
        Level minLevel;

        @Override
        public Integer call() throws IOException {
            EvlResult r = project.load();
            List<Diagnostic> shown = r.diagnostics().stream().filter(d -> d.level().ordinal() <= minLevel.ordinal()).toList();
            EvlResult filtered = new EvlResult(r.graph(), shown);
            PrintWriter out = spec.commandLine().getOut();
            Printer.diagnostics(filtered, format, format == Printer.Format.TEXT && color(out), version(), out);
            if (r.hasErrors()) {
                return ERRORS;
            }
            boolean warnings = r.count(Level.WARNING) > 0;
            return failOnWarning && warnings ? ERRORS : OK;
        }
    }

    // ------------------------------------------------------------------ inspect

    @Command(name = "inspect", description = "Print one element with its fields, origins, edges and diagnostics.")
    static final class Inspect implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Parameters(index = "0", description = "Element reference, e.g. flow:order.placement")
        String ref;
        @Option(names = "--dir", defaultValue = ".", description = "Project directory.")
        Path dir;
        @Option(names = "--classpath", description = "Classes and dependency jars.")
        String classpath;
        @Option(names = "--format", defaultValue = "text", description = "text or json.")
        Printer.Format format;

        @Override
        public Integer call() throws IOException {
            EvlResult r = new ProjectLoader().dir(dir).classpath(ProjectLoader.splitClasspath(classpath)).load();
            PrintWriter out = spec.commandLine().getOut();
            Element e = r.graph().resolve(ref).orElse(null);
            if (e == null) {
                spec.commandLine().getErr().println("evl: no element '" + ref + "'");
                List<String> close = closest(r.graph(), ref.contains(":") ? ref.substring(ref.lastIndexOf(':') + 1) : ref);
                if (!close.isEmpty()) {
                    spec.commandLine().getErr().println("Did you mean: " + String.join(", ", close));
                }
                return ERRORS;
            }
            if (format == Printer.Format.JSON) {
                Map<String, Object> m = GraphExport.element(e, r.graph());
                List<Object> out1 = new ArrayList<>();
                r.graph().outgoing(e.key()).forEach(x -> out1.add(GraphExport.edge(x)));
                List<Object> in1 = new ArrayList<>();
                r.graph().incoming(e.key()).forEach(x -> in1.add(GraphExport.edge(x)));
                m.put("outgoing", out1);
                m.put("incoming", in1);
                m.put("path", r.queries().pathToRoot(e.key()).stream().map(Element::key).toList());
                m.put("diagnostics", GraphExport.diagnostics(r.diagnosticsFor(e.key())));
                if (e.kind() == Kind.FLOW) {
                    m.put("structure", r.queries().flowTree(e.key()));
                }
                out.print(Json.write(m));
            } else {
                text(r, e, out);
            }
            out.flush();
            return OK;
        }

        private static List<String> closest(EvlGraph g, String text) {
            record C(String key, int d) {
            }
            List<C> cs = new ArrayList<>();
            for (Element e : g.elements()) {
                int d = distance(text, e.id());
                if (d <= Math.max(2, text.length() / 4) || e.id().contains(text)) {
                    cs.add(new C(e.key(), d));
                }
            }
            cs.sort(java.util.Comparator.comparingInt(C::d).thenComparing(C::key));
            return cs.stream().limit(5).map(C::key).toList();
        }

        private static int distance(String a, String b) {
            int[] prev = new int[b.length() + 1];
            for (int j = 0; j <= b.length(); j++) {
                prev[j] = j;
            }
            for (int i = 1; i <= a.length(); i++) {
                int[] cur = new int[b.length() + 1];
                cur[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                }
                prev = cur;
            }
            return prev[b.length()];
        }

        private static void text(EvlResult r, Element e, PrintWriter out) {
            out.println(e.kind().displayName() + " " + e.key() + " — " + e.name());
            out.println("  path:     " + String.join(" › ", r.queries().pathToRoot(e.key()).stream().map(Element::name).toList()));
            out.println("  evidence: " + e.evidence() + (e.source() == null ? "" : "  source: " + e.source().raw()
                + " (" + e.resolution().state() + ")"));
            e.fields().forEach((k, v) -> {
                if (!k.equals("id") && !k.equals("name") && !k.equals("source") && !k.equals("steps")) {
                    out.println("  " + k + ": " + Json.compact(v) + "   ← " + e.locationOf(k));
                }
            });
            e.origins().forEach(o -> out.println("  declared: " + o.kind().name().toLowerCase(Locale.ROOT) + " at " + o.location()
                + (o.javaElement() == null ? "" : " (" + o.javaElement() + ")")));
            for (Edge x : r.graph().outgoing(e.key())) {
                out.println("  → " + x.type().name().toLowerCase(Locale.ROOT) + " " + x.to() + (x.label() == null ? "" : " [" + x.label() + "]"));
            }
            for (Edge x : r.graph().incoming(e.key())) {
                out.println("  ← " + x.type().name().toLowerCase(Locale.ROOT) + " " + x.from() + (x.label() == null ? "" : " [" + x.label() + "]"));
            }
            if (e.kind() == Kind.FLOW) {
                out.println("  steps:");
                r.graph().flow(e.key()).ifPresent(f -> printSteps(r.graph(), f.steps(), "    ", out));
            }
            r.diagnosticsFor(e.key()).forEach(d -> out.println("  " + d));
        }

        private static void printSteps(EvlGraph g, List<io.github.ilyaslabs.evl.core.model.StepNode> steps, String indent,
                                       PrintWriter out) {
            for (var s : steps) {
                Element st = g.element(s.key()).orElse(null);
                out.println(indent + "- " + s.type() + " " + s.key().substring(s.key().lastIndexOf('/') + 1)
                    + (st == null ? "" : ": " + st.name()));
                for (var b : s.blocks()) {
                    out.println(indent + "  [" + b.role().name().toLowerCase(Locale.ROOT) + (b.label() == null ? "" : " " + b.label()) + "]");
                    printSteps(g, b.steps(), indent + "    ", out);
                }
            }
        }
    }

    // ------------------------------------------------------------------ list

    @Command(name = "list", description = "List elements.")
    static final class ListCmd implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Mixin
        ProjectOptions project;
        @Option(names = "--kind", description = "Element kind keyword, e.g. flow, component.")
        String kind;
        @Option(names = "--module", description = "Only elements inside this module.")
        String module;
        @Option(names = "--search", description = "Ranked search text.")
        String search;
        @Option(names = "--format", defaultValue = "text", description = "text or json.")
        Printer.Format format;

        @Override
        public Integer call() throws IOException {
            EvlResult r = project.load();
            Set<Kind> kinds = null;
            if (kind != null) {
                Kind k = Kind.byKeyword(kind).orElse(null);
                if (k == null) {
                    spec.commandLine().getErr().println("evl: unknown kind '" + kind + "'");
                    return FAILURE;
                }
                kinds = Set.of(k);
            }
            var filters = new EvlQueries.Filters(kinds, null, null, module, null, null, null);
            List<EvlQueries.Hit> hits = r.queries().search(search, filters, 0);
            PrintWriter out = spec.commandLine().getOut();
            if (format == Printer.Format.JSON) {
                List<Object> list = new ArrayList<>();
                hits.forEach(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", h.element().key());
                    m.put("kind", h.element().kind().keyword());
                    m.put("name", h.element().name());
                    if (search != null) {
                        m.put("score", h.score());
                    }
                    list.add(m);
                });
                out.print(Json.write(list));
            } else {
                for (EvlQueries.Hit h : hits) {
                    out.printf("%-16s %-48s %s%n", h.element().kind().keyword(), h.element().key(), h.element().name());
                }
            }
            out.flush();
            return OK;
        }
    }

    // ------------------------------------------------------------------ graph

    @Command(name = "graph", description = "Export the canonical graph or a sub-graph.")
    static final class Graph implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Mixin
        ProjectOptions project;
        @Option(names = "--format", defaultValue = "json", description = "json, dot or mermaid.")
        String format;
        @Option(names = "--root", description = "Element reference to start from.")
        String root;
        @Option(names = "--depth", defaultValue = "3", description = "Depth from --root.")
        int depth;

        @Override
        public Integer call() throws IOException {
            EvlResult r = project.load();
            PrintWriter out = spec.commandLine().getOut();
            Set<String> keys = null;
            String rootKey = null;
            if (root != null) {
                Element e = r.graph().resolve(root).orElse(null);
                if (e == null) {
                    spec.commandLine().getErr().println("evl: no element '" + root + "'");
                    return ERRORS;
                }
                rootKey = e.key();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> els = (List<Map<String, Object>>) GraphExport.subgraph(r, rootKey, depth).get("elements");
                keys = new HashSet<>();
                for (Map<String, Object> m : els) {
                    keys.add((String) m.get("key"));
                }
            }
            switch (format.toLowerCase(Locale.ROOT)) {
                case "dot" -> out.print(GraphExport.dot(r.graph(), keys));
                case "mermaid" -> out.print(GraphExport.mermaid(r.graph(), keys));
                case "json" -> out.print(Json.write(rootKey == null ? GraphExport.index(r) : GraphExport.subgraph(r, rootKey, depth)));
                default -> {
                    spec.commandLine().getErr().println("evl: unknown format '" + format + "' (json, dot, mermaid)");
                    return FAILURE;
                }
            }
            out.flush();
            return OK;
        }
    }

    // ------------------------------------------------------------------ index

    @Command(name = "index", description = "Write the full resolved index (the agent interface).")
    static final class Index implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Mixin
        ProjectOptions project;
        @Option(names = "--out", description = "Output file (default: stdout).")
        Path outFile;

        @Override
        public Integer call() throws IOException {
            EvlResult r = project.load();
            String json = GraphExport.json(r);
            if (outFile == null) {
                spec.commandLine().getOut().print(json);
                spec.commandLine().getOut().flush();
            } else {
                if (outFile.toAbsolutePath().getParent() != null) {
                    Files.createDirectories(outFile.toAbsolutePath().getParent());
                }
                Files.writeString(outFile, json);
            }
            return OK;
        }
    }

    // ------------------------------------------------------------------ package

    @Command(name = "package", description = "Write the EVL library layout (META-INF/evl) of a LIBRARY application to a directory.")
    static final class Package implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Project directory (contains evl.yaml).")
        Path dir;
        @Option(names = "--out", required = true, description = "Output root; files go to <out>/META-INF/evl/.")
        Path out;
        @Option(names = "--artifact", description = "Maven coordinates group:name:version recorded in the manifest.")
        String artifact;

        @Override
        public Integer call() throws IOException {
            Path root = dir.toAbsolutePath().normalize();
            java.util.List<Path> written = LibraryPackager.write(root, out, artifact);
            if (written.isEmpty()) {
                spec.commandLine().getErr().println("evl: no valid evl.yaml in " + root);
                return FAILURE;
            }
            io.github.ilyaslabs.evl.core.parse.Manifest m = io.github.ilyaslabs.evl.core.parse.ManifestParser
                .parse("evl.yaml", Files.readString(root.resolve("evl.yaml"))).manifest().orElseThrow();
            java.util.List<Path> sourceRoots = new java.util.ArrayList<>();
            for (String s : m.sources().isEmpty() ? java.util.List.of("src/main/java") : m.sources()) {
                sourceRoots.add(root.resolve(s));
            }
            java.util.List<io.github.ilyaslabs.evl.core.parse.AnnotationRecord> records = new java.util.ArrayList<>();
            new io.github.ilyaslabs.evl.cli.scan.JavaSourceScanner(root).scan(sourceRoots).values().forEach(records::addAll);
            Path index = out.resolve(io.github.ilyaslabs.evl.core.parse.Library.ROOT + "index.json");
            Files.writeString(index, io.github.ilyaslabs.evl.core.parse.IndexFile.write(records, "evl-cli"));
            spec.commandLine().getOut().println("Wrote " + (written.size() + 1) + " file(s) to " + out.resolve("META-INF/evl"));
            return OK;
        }
    }

    // ------------------------------------------------------------------ fmt

    @Command(name = "fmt", description = "Format .evl.yaml files canonically (stable key order, 2-space indent).")
    static final class Fmt implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Project directory.")
        Path dir;
        @Option(names = "--check", description = "Only report files that are not formatted (exit 1).")
        boolean check;

        @Override
        public Integer call() throws IOException {
            Path root = dir.toAbsolutePath().normalize();
            List<String> globs = Manifest.DEFAULT_DOCUMENTS;
            List<String> excludes = List.of();
            Path mf = root.resolve("evl.yaml");
            if (Files.isRegularFile(mf)) {
                var m = ManifestParser.parse("evl.yaml", Files.readString(mf)).manifest();
                if (m.isPresent()) {
                    globs = m.get().documents();
                    excludes = m.get().exclude();
                }
            }
            int unformatted = 0;
            PrintWriter out = spec.commandLine().getOut();
            for (Path p : EvlWorkspace.findDocuments(root, globs, excludes)) {
                String content = Files.readString(p);
                var formatted = Formatter.format(content);
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (formatted.isEmpty()) {
                    spec.commandLine().getErr().println("evl: " + rel + " is not valid YAML; skipped");
                    continue;
                }
                if (!formatted.get().equals(content)) {
                    unformatted++;
                    if (check) {
                        out.println("not formatted: " + rel);
                    } else {
                        Files.writeString(p, formatted.get());
                        out.println("formatted: " + rel);
                    }
                }
            }
            out.flush();
            return check && unformatted > 0 ? ERRORS : OK;
        }
    }

    // ------------------------------------------------------------------ init

    @Command(name = "init", description = "Create evl.yaml and an example document.")
    static final class Init implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Project directory.")
        Path dir;
        @Option(names = "--id", description = "Application id (default: derived from the directory name).")
        String id;
        @Option(names = "--name", description = "Display name.")
        String name;

        @Override
        public Integer call() throws IOException {
            Path root = dir.toAbsolutePath().normalize();
            Path mf = root.resolve("evl.yaml");
            if (Files.exists(mf)) {
                spec.commandLine().getErr().println("evl: evl.yaml already exists");
                return ERRORS;
            }
            String appId = id != null ? id : EvlWorkspace.deriveId(root.getFileName() == null ? "app" : root.getFileName().toString());
            String display = name != null ? name : appId.substring(0, 1).toUpperCase(Locale.ROOT) + appId.substring(1).replace('-', ' ');
            Files.createDirectories(root);
            Files.writeString(mf, "evl: \"" + Versions.SPEC + "\"\nid: " + appId + "\nname: " + display
                + "\ndocuments:\n  - \"docs/evl/**/*.evl.yaml\"\n");
            Path doc = root.resolve("docs/evl/example.evl.yaml");
            Files.createDirectories(doc.getParent());
            if (!Files.exists(doc)) {
                Files.writeString(doc, """
                    # yaml-language-server: $schema=https://ilyaslabs.github.io/evl-lib/schema/1.0/evl-document.schema.json
                    evl: "1.0"

                    modules:
                      - id: example
                        name: Example
                        description: Replace with a real module.

                    flows:
                      - id: example.hello
                        name: Say Hello
                        category: APPLICATION
                        module: example
                        steps:
                          - { id: greet, type: ACTION, name: Greet the user }
                    """);
            }
            spec.commandLine().getOut().println("Created evl.yaml and docs/evl/example.evl.yaml for application '" + appId + "'.");
            return OK;
        }
    }

    // ------------------------------------------------------------------ schema / compat / rules

    @Command(name = "schema", description = "Print the JSON Schema URLs for editor setup.")
    static final class Schema implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            String base = "https://ilyaslabs.github.io/evl-lib/schema/" + Versions.SPEC + "/";
            out.println("documents (*.evl.yaml): " + base + "evl-document.schema.json");
            out.println("manifest (evl.yaml):    " + base + "evl-manifest.schema.json");
            out.println("index (index.json):     " + base + "evl-index.schema.json");
            out.println("diagnostics (--format json): " + base + "evl-diagnostics.schema.json");
            out.println("Add to the first line of a document:");
            out.println("# yaml-language-server: $schema=" + base + "evl-document.schema.json");
            out.flush();
            return OK;
        }
    }

    @Command(name = "compat", description = "Print the spec version and compatible tools.")
    static final class Compat implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("EVL spec:      " + Versions.SPEC + " (reads documents " + Versions.MAJOR + ".0 to " + Versions.SPEC + ")");
            out.println("evl-lib:       " + version());
            out.println("IntelliJ plugin: 2026.3.x supports spec " + Versions.MAJOR + ".x");
            out.println("evl-skills:    1.x (spec: " + Versions.SPEC + ")");
            out.flush();
            return OK;
        }
    }

    @Command(name = "rules", description = "List every validation rule.")
    static final class Rules implements Callable<Integer> {
        @Spec
        CommandSpec spec;
        @Option(names = "--format", defaultValue = "text", description = "text or json.")
        Printer.Format format;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if (format == Printer.Format.JSON) {
                List<Object> list = new ArrayList<>();
                for (Rule r : Rule.values()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", r.code());
                    m.put("level", r.level().name());
                    m.put("title", r.title());
                    m.put("description", r.description());
                    m.put("configurable", !r.isLocked());
                    list.add(m);
                }
                out.print(Json.write(list));
            } else {
                for (Rule r : Rule.values()) {
                    out.printf("%-9s %-12s %s%n", r.code(), r.level().name().toLowerCase(Locale.ROOT), r.title());
                }
            }
            out.flush();
            return OK;
        }
    }
}
