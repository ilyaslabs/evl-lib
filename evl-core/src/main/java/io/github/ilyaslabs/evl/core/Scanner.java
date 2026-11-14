package io.github.ilyaslabs.evl.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads {@code @Flow}, {@code // @flow} and {@code @EVL.*} from Java and Kotlin sources into a {@link FlowModel}.
 * It is a fast text scanner, not a compiler: it masks comments and strings, finds classes and methods by brace
 * depth, and follows calls through field, parameter and local types to order steps and find the resources
 * each step uses. Nothing is inferred from framework annotations.
 */
public final class Scanner {
    static final Set<String> RESOURCE_KINDS = Set.of("database", "storage", "queue", "cache", "external", "interface", "service", "actor");
    static final Set<String> SYMBOLS = Set.of("Endpoint", "Job", "Database", "Storage", "Queue", "Cache", "External", "Interface", "Service", "Actor");
    private static final Set<String> KEYWORDS = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new", "super",
            "this", "try", "else", "when", "throw", "assert", "case", "do", "in", "is", "as", "fun", "val", "var", "object", "class",
            "interface", "sizeof", "typeof", "yield", "default", "finally", "until", "step", "downTo");
    private static final Set<String> SKIP_DIRS = Set.of("build", "out", "target", "node_modules", ".git", ".gradle", ".idea", "bin", "dist");
    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9_.\\-]+(>[A-Za-z0-9_.\\-]+)*");
    private static final Pattern CLASS = Pattern.compile("(?<![\\w.@$])(class|interface|enum|record|object)\\s+([A-Za-z_]\\w*)");
    private static final Pattern CALL = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern METHOD_REF = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*::\\s*([a-z_]\\w*)");
    private static final Pattern KT_FUN = Pattern.compile("\\bfun\\s+(?:<[^>]*>\\s*)?(?:[\\w.]+\\.)?([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern ANNOTATION = Pattern.compile("@\\s*([A-Za-z_][\\w.]*)");
    private static final Pattern MARKER = Pattern.compile("^//+\\s*@flow\\s+([^\\s:]+)\\s*:?\\s*(.*?)\\s*$");
    private static final Pattern JAVA_VAR = Pattern.compile("\\b([A-Z]\\w*)(?:\\s*<[^;={}()]*>)?(?:\\s*\\[\\s*])?\\s+([a-z_]\\w*)\\s*[;=,)]");
    private static final Pattern KT_VAR = Pattern.compile("\\b([a-z_]\\w*)\\s*:\\s*([A-Z]\\w*)");
    private static final Pattern NEW_VAR = Pattern.compile("\\b([a-z_]\\w*)\\s*=\\s*(?:new\\s+)?([A-Z]\\w*)\\s*[(<]");
    private static final Pattern TYPE_USE = Pattern.compile("(?:\\bnew\\s+([A-Z]\\w*))|(?<![\\w.])([A-Z]\\w*)\\s*\\.\\s*[a-zA-Z_]");

    private final List<ParsedFile> files = new ArrayList<>();
    private final Map<String, List<ClassDecl>> classesByName = new HashMap<>();
    private final Map<String, List<MethodDecl>> topLevel = new HashMap<>();
    private final Map<MethodDecl, List<FlowNode>> anchoredAt = new HashMap<>();
    private final Map<ClassDecl, String> resourceOfClass = new HashMap<>();
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final FlowModel model = new FlowModel();

    private Scanner() {
    }

    /** Scans every .java and .kt file under {@code root}; paths in the model are relative to it. */
    public static FlowModel scan(Path root) {
        List<SourceFile> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> {
                for (Path part : root.relativize(p)) if (SKIP_DIRS.contains(part.toString())) return false;
                String n = p.getFileName().toString();
                return Files.isRegularFile(p) && (n.endsWith(".java") || n.endsWith(".kt"));
            }).sorted().forEach(p -> {
                try {
                    sources.add(new SourceFile(root.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException | UncheckedIOException e) {
                    // unreadable or not UTF-8: skip
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return scan(sources);
    }

    public static FlowModel scan(List<SourceFile> sources) {
        Scanner s = new Scanner();
        for (SourceFile f : sources) s.files.add(new ParsedFile(f));
        s.run();
        return s.model;
    }

    // ---------------------------------------------------------------- model building

    private void run() {
        for (ParsedFile f : files) {
            for (ClassDecl c : f.classes) classesByName.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c);
            for (MethodDecl m : f.methods) if (m.owner == null) topLevel.computeIfAbsent(m.name, k -> new ArrayList<>()).add(m);
        }
        collectResources();
        Map<String, FlowNode> nodes = new TreeMap<>();
        List<Tag> tags = new ArrayList<>();
        for (ParsedFile f : files) tags.addAll(f.tags(model.problems));
        for (Tag t : tags) {
            if (!PATH.matcher(t.path).matches()) {
                model.problems.add(new Problem("error", "Invalid flow path \"" + t.path + "\": use ids separated by >", t.file.path, t.line));
                continue;
            }
            FlowNode n = nodes.get(t.path);
            if (n != null && !n.virtual()) {
                model.problems.add(new Problem("warning", "Flow path \"" + t.path + "\" is tagged more than once; using " + n.code.file() + ":" + n.code.line(), t.file.path, t.line));
                continue;
            }
            if (n == null) {
                n = new FlowNode(t.path);
                nodes.put(t.path, n);
            }
            describe(n, t);
            if (t.method != null) anchoredAt.computeIfAbsent(t.method, k -> new ArrayList<>()).add(n);
            n.attrs.put("anchor", t.anchorName());
            tagOf.put(n, t);
        }
        // Parents that are only implied by deeper paths become virtual nodes.
        for (String p : new ArrayList<>(nodes.keySet())) {
            String cur = p;
            while (cur.contains(">")) {
                cur = cur.substring(0, cur.lastIndexOf('>'));
                nodes.computeIfAbsent(cur, FlowNode::new);
            }
        }
        for (FlowNode n : nodes.values()) {
            if (n.parent().isEmpty()) model.flows.add(n);
            else nodes.get(n.parent()).children.add(n);
        }
        for (FlowNode n : nodes.values()) {
            Tag t = tagOf.get(n);
            if (t != null) n.uses.addAll(usesOf(t, n));
            if (!n.after.isEmpty() && !nodes.containsKey(n.after) && t != null) {
                model.problems.add(new Problem("warning", "after = \"" + n.after + "\" does not match any flow path", t.file.path, t.line));
            }
        }
        for (FlowNode n : nodes.values()) order(n);
        model.flows.sort(Comparator.comparing(n -> n.path));
        model.resources.addAll(resources.values());
    }

    private final Map<FlowNode, Tag> tagOf = new HashMap<>();

    private void collectResources() {
        for (ParsedFile f : files) {
            for (ClassDecl c : f.classes) {
                Ann a = c.symbol();
                if (a == null || !RESOURCE_KINDS.contains(a.kind())) continue;
                String name = a.str("value", c.name);
                Resource r = resources.computeIfAbsent(a.kind() + ":" + name, k -> new Resource(a.kind(), name));
                if (r.code == null) {
                    r.code = Code.of(f.path, f.lines, f.line(a.off));
                    r.subtitle = subtitleFor(a, "");
                }
                r.members.add(c.name);
                resourceOfClass.put(c, r.id);
            }
        }
        // Classes that implement or extend a resource type belong to it too.
        for (ParsedFile f : files) {
            for (ClassDecl c : f.classes) {
                if (resourceOfClass.containsKey(c)) continue;
                for (String s : c.supers) {
                    for (ClassDecl sup : classesByName.getOrDefault(s, List.of())) {
                        String r = resourceOfClass.get(sup);
                        if (r != null) {
                            resourceOfClass.put(c, r);
                            resources.get(r).members.add(c.name);
                        }
                    }
                }
            }
        }
    }

    private void describe(FlowNode n, Tag t) {
        Ann sym = t.symbol();
        String kind = sym == null ? "step" : sym.kind();
        String label = t.label;
        n.inline = t.marker;
        n.after = t.after;
        n.async = !t.after.isEmpty();
        n.code = Code.of(t.file.path, t.file.lines, t.line);
        if (t.marker) {
            n.kind = "step";
            n.name = label.isEmpty() ? n.segment() : label;
            n.subtitle = t.description;
            ClassDecl owner = t.method == null ? null : t.method.owner;
            String r = owner == null ? null : resourceOfClass.get(owner);
            if (r != null) n.uses.add(r);
            return;
        }
        if (sym != null) for (Map.Entry<String, List<String>> e : sym.args.entrySet()) n.attrs.put(e.getKey(), String.join(", ", e.getValue()));
        if (RESOURCE_KINDS.contains(kind)) {
            // A step on a resource: drawn as the resource, captioned with what it does there.
            ClassDecl owner = t.method != null ? t.method.owner : t.cls;
            String r = owner == null ? null : resourceOfClass.get(owner);
            if (r == null && sym != null) {
                String name = sym.str("value", owner == null ? n.segment() : owner.name);
                r = resources.computeIfAbsent(kind + ":" + name, k -> new Resource(kind, name)).id;
            }
            n.kind = kind;
            n.resource = r;
            n.name = r == null ? n.segment() : resources.get(r).name;
            n.subtitle = !label.isEmpty() ? label : t.method != null ? humanize(t.method.name) : n.segment();
            return;
        }
        n.kind = kind;
        n.name = label.isEmpty() ? n.segment() : label;
        n.subtitle = t.description.isEmpty() ? subtitleFor(sym, t.classSymbolPath()) : t.description;
        if (sym != null && "endpoint".equals(kind)) n.caller = sym.str("caller", "");
    }

    static String subtitleFor(Ann a, String pathPrefix) {
        if (a == null) return "";
        String d = a.str("description", "");
        if (!d.isEmpty()) return d;
        switch (a.kind()) {
            case "endpoint": {
                String path = join(pathPrefix, a.str("path", ""));
                return (a.str("method", "") + " " + path).trim();
            }
            case "job": {
                String s = a.str("schedule", "");
                String tr = a.str("trigger", "");
                return !s.isEmpty() ? s : tr.isEmpty() ? "" : "on " + tr;
            }
            case "cache": return a.str("ttl", "").isEmpty() ? "" : "ttl " + a.str("ttl", "");
            case "database": return a.str("table", "");
            default: return "";
        }
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a.replaceAll("/+$", "") + "/" + b.replaceAll("^/+", "");
    }

    static String humanize(String camel) {
        String s = camel.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- uses and order

    /** Text ranges [start, end) a tag stands for: a marker's segment, or a method or class body minus its marker segments. */
    private List<int[]> ranges(Tag t) {
        List<int[]> out = new ArrayList<>();
        if (t.method != null && t.method.bodyStart >= 0) {
            if (t.marker) {
                out.add(new int[] {t.off, t.file.segmentEnd(t)});
            } else {
                int s = t.method.bodyStart;
                for (Tag m : t.file.markersIn(t.method)) {
                    if (m.off > s) out.add(new int[] {s, m.off});
                    s = Math.max(s, t.file.segmentEnd(m));
                }
                out.add(new int[] {s, t.method.bodyEnd});
            }
        } else if (t.cls != null) {
            for (MethodDecl m : t.cls.methods) if (m.bodyStart >= 0) out.add(new int[] {m.bodyStart, m.bodyEnd});
        }
        return out;
    }

    private Set<String> usesOf(Tag t, FlowNode n) {
        Set<String> uses = new LinkedHashSet<>();
        MethodDecl ctx = t.method;
        ClassDecl owner = ctx != null ? ctx.owner : t.cls;
        for (int[] r : ranges(t)) {
            for (Call c : t.file.calls(r[0], r[1])) {
                List<MethodDecl> targets = resolve(owner, c);
                if (targets.stream().anyMatch(anchoredAt::containsKey)) continue;
                if (c.receiver != null) addTypeResources(uses, typeOf(owner, ctx, c.receiver));
                for (MethodDecl m : targets) {
                    if (m.owner != null && m.owner != owner) addResource(uses, m.owner);
                    uses.addAll(deepUses(m, new HashSet<>(), 0));
                }
            }
            for (String type : t.file.typeUses(r[0], r[1])) addTypeResources(uses, type);
        }
        if (n.resource != null) uses.remove(n.resource);
        if (owner != null && n.resource == null && !t.marker) uses.remove(resourceOfClass.get(owner));
        return uses;
    }

    private Set<String> deepUses(MethodDecl m, Set<MethodDecl> seen, int depth) {
        Set<String> uses = new LinkedHashSet<>();
        if (depth > 4 || !seen.add(m) || m.bodyStart < 0) return uses;
        for (Call c : m.file.calls(m.bodyStart, m.bodyEnd)) {
            List<MethodDecl> targets = resolve(m.owner, c);
            if (targets.stream().anyMatch(anchoredAt::containsKey)) continue;
            if (c.receiver != null) addTypeResources(uses, typeOf(m.owner, m, c.receiver));
            for (MethodDecl t : targets) {
                if (t.owner != null && t.owner != m.owner) addResource(uses, t.owner);
                uses.addAll(deepUses(t, seen, depth + 1));
            }
        }
        for (String type : m.file.typeUses(m.bodyStart, m.bodyEnd)) addTypeResources(uses, type);
        return uses;
    }

    private void addResource(Set<String> uses, ClassDecl c) {
        String r = resourceOfClass.get(c);
        if (r != null) uses.add(r);
    }

    private void addTypeResources(Set<String> uses, String type) {
        if (type == null) return;
        for (ClassDecl c : classesByName.getOrDefault(type, List.of())) addResource(uses, c);
    }

    private String typeOf(ClassDecl owner, MethodDecl method, String name) {
        if (method != null) {
            String t = method.file.varsIn(method.paramsStart, method.bodyEnd < 0 ? method.paramsEnd : method.bodyEnd).get(name);
            if (t != null) return t;
        }
        for (ClassDecl c = owner; c != null; c = c.outer) {
            String t = c.fields().get(name);
            if (t != null) return t;
        }
        if (Character.isUpperCase(name.charAt(0)) && classesByName.containsKey(name)) return name;
        return null;
    }

    private List<MethodDecl> resolve(ClassDecl owner, Call c) {
        List<MethodDecl> out = new ArrayList<>();
        if (c.receiver == null || c.receiver.equals("this") || c.receiver.equals("super")) {
            for (ClassDecl k = owner; k != null; k = k.outer) {
                for (ClassDecl x : withSupers(k)) for (MethodDecl m : x.methods) if (m.name.equals(c.name)) out.add(m);
                if (!out.isEmpty()) return out;
            }
            List<MethodDecl> top = topLevel.getOrDefault(c.name, List.of());
            return top.size() == 1 ? top : List.of();
        }
        String type = typeOf(owner, c.method, c.receiver);
        if (type == null) return out;
        for (ClassDecl k : classesByName.getOrDefault(type, List.of())) {
            for (ClassDecl x : withSupers(k)) for (MethodDecl m : x.methods) if (m.name.equals(c.name)) out.add(m);
        }
        // Implementations of an interface type.
        for (ParsedFile f : files) {
            for (ClassDecl k : f.classes) {
                if (!k.supers.contains(type)) continue;
                for (MethodDecl m : k.methods) if (m.name.equals(c.name)) out.add(m);
            }
        }
        return out;
    }

    private List<ClassDecl> withSupers(ClassDecl c) {
        List<ClassDecl> out = new ArrayList<>();
        Deque<ClassDecl> todo = new ArrayDeque<>(List.of(c));
        while (!todo.isEmpty() && out.size() < 16) {
            ClassDecl k = todo.poll();
            if (out.contains(k)) continue;
            out.add(k);
            for (String s : k.supers) todo.addAll(classesByName.getOrDefault(s, List.of()));
        }
        return out;
    }

    /** Methods reachable from {@code m} within a few calls. */
    private Map<MethodDecl, Integer> reach(MethodDecl from, int start, int end) {
        Map<MethodDecl, Integer> firstAt = new LinkedHashMap<>();
        for (Call c : from.file.calls(start, end)) {
            Deque<MethodDecl> todo = new ArrayDeque<>(resolve(from.owner, c));
            Set<MethodDecl> seen = new HashSet<>();
            int depth = 0;
            while (!todo.isEmpty() && depth++ < 200) {
                MethodDecl m = todo.poll();
                if (!seen.add(m) || seen.size() > 60) continue;
                firstAt.putIfAbsent(m, c.off);
                if (m.bodyStart >= 0) for (Call cc : m.file.calls(m.bodyStart, m.bodyEnd)) todo.addAll(resolve(m.owner, cc));
            }
        }
        return firstAt;
    }

    private void order(FlowNode p) {
        if (p.children.isEmpty()) return;
        Tag pt = tagOf.get(p);
        Map<FlowNode, Double> key = new HashMap<>();
        if (pt != null) {
            Map<MethodDecl, Integer> reached = new HashMap<>();
            List<MethodDecl> sources = new ArrayList<>();
            if (pt.method != null) sources.add(pt.method);
            else if (pt.cls != null) sources.addAll(pt.cls.methods);
            for (MethodDecl m : sources) {
                if (m.bodyStart < 0) continue;
                int s = pt.marker ? pt.off : m.bodyStart;
                int e = pt.marker ? pt.file.segmentEnd(pt) : m.bodyEnd;
                reach(m, s, e).forEach(reached::putIfAbsent);
            }
            for (FlowNode c : p.children) {
                Tag ct = tagOf.get(c);
                if (ct == null || !c.after.isEmpty()) continue;
                boolean sameMethod = ct.marker && pt.method != null && ct.method == pt.method;
                if (sameMethod) key.put(c, (double) ct.off);
                else if (ct.method != null && reached.containsKey(ct.method)) key.put(c, (double) reached.get(ct.method));
                else if (ct.cls != null) {
                    for (MethodDecl m : ct.cls.methods) if (reached.containsKey(m)) key.merge(c, (double) reached.get(m), Math::min);
                }
            }
        }
        // Asynchronous steps follow their `after` sibling.
        for (int pass = 0; pass < 3; pass++) {
            for (FlowNode c : p.children) {
                if (key.containsKey(c) || c.after.isEmpty()) continue;
                for (FlowNode s : p.children) if (s.path.equals(c.after) && key.containsKey(s)) key.put(c, key.get(s) + 0.5);
            }
        }
        Comparator<FlowNode> byKey = Comparator.comparingDouble(c -> key.getOrDefault(c, Double.MAX_VALUE));
        p.children.sort(byKey.thenComparing(c -> c.code == null ? "~" : c.code.file()).thenComparingInt(c -> c.code == null ? 0 : c.code.line()));
        int seq = 0;
        for (FlowNode c : p.children) c.seq = c.async ? 0 : ++seq;
    }

    // ---------------------------------------------------------------- parsing

    record Call(int off, String receiver, String name, MethodDecl method) {
    }

    static final class Ann {
        final String name;
        final int off;
        final int end;
        final Map<String, List<String>> args = new LinkedHashMap<>();

        Ann(String name, int off, int end) {
            this.name = name;
            this.off = off;
            this.end = end;
        }

        String kind() {
            return name.toLowerCase(Locale.ROOT);
        }

        String str(String key, String def) {
            List<String> v = args.get(key);
            return v == null || v.isEmpty() || v.get(0).isEmpty() ? def : v.get(0);
        }
    }

    static final class ClassDecl {
        String name;
        ParsedFile file;
        int keywordOff;
        int bodyStart = -1;
        int bodyEnd = -1;
        int headerEnd;
        ClassDecl outer;
        final Set<String> supers = new LinkedHashSet<>();
        final List<Ann> anns = new ArrayList<>();
        final List<MethodDecl> methods = new ArrayList<>();
        private Map<String, String> fields;

        Ann symbol() {
            for (Ann a : anns) if (SYMBOLS.contains(a.name)) return a;
            return null;
        }

        Map<String, String> fields() {
            if (fields == null) fields = file.varsIn(keywordOff, bodyEnd < 0 ? headerEnd : bodyEnd);
            return fields;
        }
    }

    static final class MethodDecl {
        String name;
        ParsedFile file;
        ClassDecl owner;
        int startOff;
        int paramsStart;
        int paramsEnd;
        int bodyStart = -1;
        int bodyEnd = -1;
        final List<Ann> anns = new ArrayList<>();

        Ann symbol() {
            for (Ann a : anns) if (SYMBOLS.contains(a.name)) return a;
            return null;
        }
    }

    /** One {@code @Flow} annotation or {@code // @flow} marker. */
    static final class Tag {
        ParsedFile file;
        String path;
        String label = "";
        String after = "";
        String description = "";
        int off;
        int line;
        boolean marker;
        MethodDecl method;
        ClassDecl cls;

        Ann ownSymbol() {
            return method != null ? method.symbol() : cls != null ? cls.symbol() : null;
        }

        Ann symbol() {
            if (marker) return null;
            Ann own = ownSymbol();
            if (own != null) return own;
            ClassDecl c = method != null ? method.owner : null;
            return c == null ? null : c.symbol();
        }

        String classSymbolPath() {
            ClassDecl c = method != null ? method.owner : null;
            Ann a = c == null ? null : c.symbol();
            return a != null && "endpoint".equals(a.kind()) && method != null && method.symbol() != null ? a.str("path", "") : "";
        }

        String anchorName() {
            ClassDecl c = method != null ? method.owner : cls;
            String base = c == null ? "" : c.name;
            return method == null ? base : base.isEmpty() ? method.name : base + "." + method.name;
        }
    }

    static final class ParsedFile {
        final String path;
        final String text;
        final String masked;
        final String[] lines;
        final boolean kotlin;
        final int[] depth;
        final int[] lineStarts;
        final List<int[]> comments = new ArrayList<>();
        final List<ClassDecl> classes = new ArrayList<>();
        final List<MethodDecl> methods = new ArrayList<>();
        final List<Ann> anns = new ArrayList<>();
        final List<Tag> markers = new ArrayList<>();
        final boolean importsSymbols;
        private final Map<Long, List<Call>> callCache = new HashMap<>();
        private final Map<Long, Map<String, String>> varCache = new HashMap<>();

        ParsedFile(SourceFile f) {
            path = f.path();
            text = f.text().replace("\r\n", "\n");
            kotlin = f.kotlin();
            lines = text.split("\n", -1);
            masked = mask(text);
            depth = new int[masked.length() + 1];
            int d = 0;
            for (int i = 0; i < masked.length(); i++) {
                depth[i] = d;
                char ch = masked.charAt(i);
                if (ch == '{') d++;
                else if (ch == '}') d = Math.max(0, d - 1);
            }
            depth[masked.length()] = d;
            List<Integer> ls = new ArrayList<>();
            ls.add(0);
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') ls.add(i + 1);
            lineStarts = ls.stream().mapToInt(Integer::intValue).toArray();
            importsSymbols = Pattern.compile("import\\s+io\\.github\\.ilyaslabs\\.evl\\.EVL\\.").matcher(text).find();
            parseClasses();
            parseMethods();
            parseAnnotations();
        }

        int line(int off) {
            int lo = 0;
            int hi = lineStarts.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (lineStarts[mid] <= off) lo = mid;
                else hi = mid - 1;
            }
            return lo + 1;
        }

        private String mask(String s) {
            StringBuilder b = new StringBuilder(s);
            int n = s.length();
            int i = 0;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    int e = s.indexOf('\n', i);
                    if (e < 0) e = n;
                    comments.add(new int[] {i, e});
                    blank(b, i, e);
                    i = e;
                } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    int e = s.indexOf("*/", i + 2);
                    e = e < 0 ? n : e + 2;
                    blank(b, i, e);
                    i = e;
                } else if (c == '"' && s.startsWith("\"\"\"", i)) {
                    int e = s.indexOf("\"\"\"", i + 3);
                    e = e < 0 ? n : e + 3;
                    blank(b, i + 1, e - 1);
                    i = e;
                } else if (c == '"' || c == '\'') {
                    int e = i + 1;
                    while (e < n && s.charAt(e) != c && s.charAt(e) != '\n') e += s.charAt(e) == '\\' ? 2 : 1;
                    e = Math.min(n, e);
                    blank(b, i + 1, e);
                    i = e + 1;
                } else {
                    i++;
                }
            }
            return b.toString();
        }

        private static void blank(StringBuilder b, int from, int to) {
            for (int k = Math.max(0, from); k < Math.min(to, b.length()); k++) if (b.charAt(k) != '\n') b.setCharAt(k, ' ');
        }

        private int matching(int open) {
            char o = masked.charAt(open);
            char c = o == '(' ? ')' : o == '{' ? '}' : o == '[' ? ']' : '>';
            int d = 0;
            for (int i = open; i < masked.length(); i++) {
                char ch = masked.charAt(i);
                if (ch == o) d++;
                else if (ch == c && --d == 0) return i;
            }
            return masked.length() - 1;
        }

        private int skipWs(int i) {
            while (i < masked.length() && Character.isWhitespace(masked.charAt(i))) i++;
            return i;
        }

        private int backWs(int i) {
            while (i >= 0 && Character.isWhitespace(masked.charAt(i))) i--;
            return i;
        }

        private void parseClasses() {
            Matcher m = CLASS.matcher(masked);
            while (m.find()) {
                ClassDecl c = new ClassDecl();
                c.name = m.group(2);
                c.file = this;
                c.keywordOff = m.start(1);
                int i = m.end();
                int p = 0;
                int headerEnd = masked.length();
                StringBuilder header = new StringBuilder();
                for (; i < masked.length(); i++) {
                    char ch = masked.charAt(i);
                    if (ch == '(' || ch == '<') p++;
                    else if (ch == ')' || ch == '>') p = Math.max(0, p - 1);
                    else if (p == 0 && ch == '{') {
                        c.bodyStart = i;
                        c.bodyEnd = matching(i);
                        headerEnd = i;
                        break;
                    } else if (p == 0 && (ch == ';' || ch == '}')) {
                        headerEnd = i;
                        break;
                    } else if (p == 0 && kotlin && ch == '\n') {
                        int prev = backWs(i);
                        int next = skipWs(i);
                        char pc = prev >= 0 ? masked.charAt(prev) : ' ';
                        char nc = next < masked.length() ? masked.charAt(next) : ' ';
                        if (",:(".indexOf(pc) < 0 && "{:,".indexOf(nc) < 0 && !masked.startsWith("where", next)) {
                            headerEnd = i;
                            break;
                        }
                    }
                    if (p == 0) header.append(ch);
                }
                c.headerEnd = headerEnd;
                Matcher sup = Pattern.compile("\\b([A-Z]\\w*)").matcher(header.toString().replaceAll("<[^>]*>", ""));
                while (sup.find()) c.supers.add(sup.group(1));
                c.supers.remove(c.name);
                classes.add(c);
            }
            for (ClassDecl c : classes) {
                for (ClassDecl o : classes) {
                    if (o != c && o.bodyStart >= 0 && o.bodyStart < c.keywordOff && c.keywordOff < o.bodyEnd
                            && (c.outer == null || o.bodyStart > c.outer.bodyStart)) c.outer = o;
                }
            }
        }

        ClassDecl classAt(int off) {
            ClassDecl best = null;
            for (ClassDecl c : classes) if (c.bodyStart >= 0 && c.bodyStart < off && off < c.bodyEnd && (best == null || c.bodyStart > best.bodyStart)) best = c;
            return best;
        }

        private void parseMethods() {
            if (kotlin) {
                Matcher m = KT_FUN.matcher(masked);
                while (m.find()) {
                    ClassDecl owner = classAt(m.start());
                    int memberDepth = owner == null ? 0 : depth[owner.bodyStart] + 1;
                    if (depth[m.start()] != memberDepth) continue;
                    MethodDecl d = method(m.group(1), owner, m.start(), m.end() - 1);
                    int close = d.paramsEnd;
                    int i = close + 1;
                    int p = 0;
                    for (; i < masked.length(); i++) {
                        char ch = masked.charAt(i);
                        if (ch == '(' || ch == '<') p++;
                        else if (ch == ')' || ch == '>') p = Math.max(0, p - 1);
                        else if (p == 0 && ch == '{') {
                            d.bodyStart = i;
                            d.bodyEnd = matching(i);
                            break;
                        } else if (p == 0 && ch == '=') {
                            d.bodyStart = i;
                            d.bodyEnd = expressionEnd(i);
                            break;
                        } else if (p == 0 && (ch == '}' || ch == ';')) break;
                        else if (p == 0 && ch == '\n' && masked.charAt(backWs(i)) != ':' && masked.charAt(skipWs(i)) != ':'
                                && masked.charAt(skipWs(i)) != '{' && masked.charAt(skipWs(i)) != '=') break;
                    }
                }
            } else {
                for (ClassDecl owner : classes) {
                    if (owner.bodyStart < 0) continue;
                    int memberDepth = depth[owner.bodyStart] + 1;
                    Matcher m = CALL.matcher(masked);
                    m.region(owner.bodyStart + 1, owner.bodyEnd);
                    while (m.find()) {
                        int s = m.start();
                        if (depth[s] != memberDepth || classAt(s) != owner) continue;
                        String name = m.group(1);
                        if (KEYWORDS.contains(name)) continue;
                        int prev = backWs(s - 1);
                        char pc = prev >= 0 ? masked.charAt(prev) : ' ';
                        if (pc == '.' || pc == '@' || pc == '=' || pc == ',' || pc == '(') continue;
                        String prevWord = wordBefore(prev);
                        if (prevWord.equals("new") || prevWord.equals("return") || prevWord.equals("throw")) continue;
                        int close = matching(m.end() - 1);
                        int i = skipWs(close + 1);
                        if (masked.startsWith("throws", i)) {
                            while (i < masked.length() && masked.charAt(i) != '{' && masked.charAt(i) != ';') i++;
                        } else if (masked.startsWith("default", i)) {
                            continue;
                        }
                        if (i >= masked.length()) continue;
                        char after = masked.charAt(i);
                        if (after != '{' && after != ';') continue;
                        MethodDecl d = method(name, owner, s, m.end() - 1);
                        if (after == '{') {
                            d.bodyStart = i;
                            d.bodyEnd = matching(i);
                        }
                    }
                }
            }
        }

        private MethodDecl method(String name, ClassDecl owner, int start, int open) {
            MethodDecl d = new MethodDecl();
            d.name = name;
            d.file = this;
            d.owner = owner;
            d.startOff = start;
            d.paramsStart = open;
            d.paramsEnd = matching(open);
            methods.add(d);
            if (owner != null) owner.methods.add(d);
            return d;
        }

        private int expressionEnd(int eq) {
            int startLine = line(eq);
            String fun = lines[startLine - 1];
            int indent = fun.length() - fun.stripLeading().length();
            int ln = startLine;
            while (ln < lines.length) {
                String next = lines[ln];
                if (next.isBlank()) break;
                int ind = next.length() - next.stripLeading().length();
                if (ind <= indent) break;
                ln++;
            }
            return Math.min(masked.length(), ln < lineStarts.length ? lineStarts[ln] - 1 : masked.length());
        }

        private String wordBefore(int i) {
            int e = i;
            while (i >= 0 && Character.isJavaIdentifierPart(masked.charAt(i))) i--;
            return masked.substring(i + 1, e + 1);
        }

        private void parseAnnotations() {
            Matcher m = ANNOTATION.matcher(masked);
            while (m.find()) {
                String raw = m.group(1).replaceFirst("^io\\.github\\.ilyaslabs\\.evl\\.", "");
                String name;
                if (raw.equals("Flow")) name = "Flow";
                else if (raw.startsWith("EVL.") && SYMBOLS.contains(raw.substring(4))) name = raw.substring(4);
                else if (importsSymbols && SYMBOLS.contains(raw)) name = raw;
                else name = null;
                int end = m.end();
                int i = skipWs(end);
                int argsOpen = -1;
                if (i < masked.length() && masked.charAt(i) == '(') {
                    argsOpen = i;
                    end = matching(i) + 1;
                }
                if (name == null) continue;
                Ann a = new Ann(name, m.start(), end);
                if (argsOpen >= 0) parseArgs(a, argsOpen + 1, end - 1);
                anns.add(a);
            }
            // Attach each annotation to the first class or method after it, if only modifiers and annotations sit between.
            List<Object[]> decls = new ArrayList<>();
            for (ClassDecl c : classes) decls.add(new Object[] {c.keywordOff, c});
            for (MethodDecl d : methods) decls.add(new Object[] {d.startOff, d});
            decls.sort(Comparator.comparingInt(o -> (Integer) o[0]));
            for (Ann a : anns) {
                for (Object[] d : decls) {
                    int start = (Integer) d[0];
                    if (start < a.end) continue;
                    if (onlyModifiers(a.end, start)) {
                        if (d[1] instanceof ClassDecl c) c.anns.add(a);
                        else ((MethodDecl) d[1]).anns.add(a);
                    }
                    break;
                }
            }
        }

        private boolean onlyModifiers(int from, int to) {
            StringBuilder between = new StringBuilder(masked.substring(from, to));
            for (Ann other : anns) {
                if (other.off >= from && other.end <= to) for (int k = other.off - from; k < other.end - from; k++) between.setCharAt(k, ' ');
            }
            String s = between.toString().replaceAll("@\\s*[\\w.]+(\\s*\\([^)]*\\))?", " ");
            return s.indexOf(';') < 0 && s.indexOf('{') < 0 && s.indexOf('}') < 0 && s.indexOf('=') < 0 && s.indexOf('(') < 0;
        }

        private void parseArgs(Ann a, int from, int to) {
            List<int[]> parts = splitTop(from, to);
            for (int[] part : parts) {
                String m = masked.substring(part[0], part[1]);
                int eq = topLevelEq(m);
                String key = "value";
                int vs = part[0];
                if (eq >= 0 && m.substring(0, eq).trim().matches("[A-Za-z_]\\w*")) {
                    key = m.substring(0, eq).trim();
                    vs = part[0] + eq + 1;
                }
                a.args.put(key, values(vs, part[1]));
            }
        }

        private int topLevelEq(String m) {
            int d = 0;
            for (int i = 0; i < m.length(); i++) {
                char c = m.charAt(i);
                if ("({[".indexOf(c) >= 0) d++;
                else if (")}]".indexOf(c) >= 0) d--;
                else if (c == '=' && d == 0) return i;
            }
            return -1;
        }

        private List<int[]> splitTop(int from, int to) {
            List<int[]> out = new ArrayList<>();
            int d = 0;
            int s = from;
            for (int i = from; i < to; i++) {
                char c = masked.charAt(i);
                if ("({[".indexOf(c) >= 0) d++;
                else if (")}]".indexOf(c) >= 0) d--;
                else if (c == ',' && d == 0) {
                    out.add(new int[] {s, i});
                    s = i + 1;
                }
            }
            if (!masked.substring(s, to).isBlank()) out.add(new int[] {s, to});
            return out;
        }

        private List<String> values(int from, int to) {
            int s = skipWs(from);
            char c = s < to ? masked.charAt(s) : ' ';
            List<String> out = new ArrayList<>();
            if (c == '{' || c == '[') {
                int close = matching(s);
                for (int[] p : splitTop(s + 1, Math.min(close, to))) out.add(literal(p[0], p[1]));
            } else if (masked.startsWith("arrayOf(", s)) {
                int open = s + 7;
                for (int[] p : splitTop(open + 1, matching(open))) out.add(literal(p[0], p[1]));
            } else {
                out.add(literal(from, to));
            }
            return out;
        }

        /** String literals (joined when concatenated) or the raw text for constants. */
        private String literal(int from, int to) {
            String raw = text.substring(from, to).trim();
            Matcher m = Pattern.compile("\"\"\"([\\s\\S]*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\"").matcher(raw);
            StringBuilder b = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                any = true;
                b.append(m.group(1) != null ? m.group(1) : unescape(m.group(2)));
            }
            return any ? b.toString() : raw;
        }

        private static String unescape(String s) {
            return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
        }

        MethodDecl methodAt(int off) {
            MethodDecl best = null;
            for (MethodDecl m : methods) if (m.bodyStart >= 0 && m.bodyStart < off && off < m.bodyEnd && (best == null || m.bodyStart > best.bodyStart)) best = m;
            return best;
        }

        List<Tag> tags(List<Problem> problems) {
            List<Tag> out = new ArrayList<>();
            for (ClassDecl c : classes) for (Ann a : c.anns) if (a.name.equals("Flow")) out.addAll(flowTags(a, null, c));
            for (MethodDecl d : methods) for (Ann a : d.anns) if (a.name.equals("Flow")) out.addAll(flowTags(a, d, null));
            for (int[] cm : comments) {
                String comment = text.substring(cm[0], cm[1]);
                Matcher m = MARKER.matcher(comment);
                if (!m.find()) continue;
                Tag t = new Tag();
                t.file = this;
                t.marker = true;
                t.path = m.group(1);
                t.label = m.group(2);
                t.off = cm[0];
                t.line = line(cm[0]);
                t.method = methodAt(cm[0]);
                if (t.method == null) {
                    problems.add(new Problem("warning", "// @flow markers belong inside a method body", path, t.line));
                    continue;
                }
                markers.add(t);
                out.add(t);
            }
            markers.sort(Comparator.comparingInt(t -> t.off));
            return out;
        }

        private List<Tag> flowTags(Ann a, MethodDecl d, ClassDecl c) {
            List<Tag> out = new ArrayList<>();
            for (String p : a.args.getOrDefault("value", List.of())) {
                Tag t = new Tag();
                t.file = this;
                t.path = p.trim();
                t.label = a.str("label", "");
                t.after = a.str("after", "");
                t.description = a.str("description", "");
                t.off = a.off;
                t.line = line(a.off);
                t.method = d;
                t.cls = c;
                out.add(t);
            }
            return out;
        }

        List<Tag> markersIn(MethodDecl m) {
            List<Tag> out = new ArrayList<>();
            for (Tag t : markers) if (t.method == m) out.add(t);
            return out;
        }

        /** A marker's segment runs to the next marker in the same method that is not below it, or the method end. */
        int segmentEnd(Tag t) {
            for (Tag o : markers) {
                if (o.method == t.method && o.off > t.off && !o.path.startsWith(t.path + ">")) return o.off;
            }
            return t.method.bodyEnd;
        }

        List<Call> calls(int from, int to) {
            long k = ((long) from << 32) | to;
            List<Call> cached = callCache.get(k);
            if (cached != null) return cached;
            List<Call> out = new ArrayList<>();
            MethodDecl ctx = methodAt(from + 1);
            Matcher m = CALL.matcher(masked);
            m.region(Math.max(0, from), Math.min(to, masked.length()));
            while (m.find()) {
                String name = m.group(1);
                if (KEYWORDS.contains(name) || Character.isUpperCase(name.charAt(0))) continue;
                int prev = backWs(m.start() - 1);
                String receiver = null;
                if (prev >= 0 && masked.charAt(prev) == '.') {
                    int r = backWs(prev - 1);
                    if (r >= 0 && masked.charAt(r) == '?') r = backWs(r - 1);
                    if (r < 0 || !Character.isJavaIdentifierPart(masked.charAt(r))) continue;
                    receiver = wordBefore(r);
                    if (receiver.isEmpty() || Character.isDigit(receiver.charAt(0))) continue;
                } else if (prev >= 0 && masked.charAt(prev) == '?' && prev > 0 && masked.charAt(prev - 1) == '.') {
                    continue;
                } else if (wordBefore(prev).equals("fun") || wordBefore(prev).equals("new")) {
                    continue;
                }
                out.add(new Call(m.start(), receiver, name, ctx));
            }
            Matcher r = METHOD_REF.matcher(masked);
            r.region(Math.max(0, from), Math.min(to, masked.length()));
            while (r.find()) out.add(new Call(r.start(), r.group(1), r.group(2), ctx));
            out.sort(Comparator.comparingInt(Call::off));
            callCache.put(k, out);
            return out;
        }

        Set<String> typeUses(int from, int to) {
            Set<String> out = new LinkedHashSet<>();
            Matcher m = TYPE_USE.matcher(masked);
            m.region(Math.max(0, from), Math.min(to, masked.length()));
            while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
            return out;
        }

        Map<String, String> varsIn(int from, int to) {
            long k = ((long) from << 32) | to;
            Map<String, String> cached = varCache.get(k);
            if (cached != null) return cached;
            Map<String, String> out = new HashMap<>();
            int f = Math.max(0, from);
            int t = Math.min(Math.max(f, to), masked.length());
            Matcher m = (kotlin ? KT_VAR : JAVA_VAR).matcher(masked);
            m.region(f, t);
            while (m.find()) {
                if (kotlin) out.putIfAbsent(m.group(1), m.group(2));
                else out.putIfAbsent(m.group(2), m.group(1));
            }
            Matcher n = NEW_VAR.matcher(masked);
            n.region(f, t);
            while (n.find()) out.putIfAbsent(n.group(1), n.group(2));
            varCache.put(k, out);
            return out;
        }
    }
}
