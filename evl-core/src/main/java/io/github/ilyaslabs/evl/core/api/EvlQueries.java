package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.FlowStructure;
import io.github.ilyaslabs.evl.core.model.Level;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.StepNode;
import io.github.ilyaslabs.evl.core.spec.Kind;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Graph queries shared by the CLI, the plugin and the canvas (spec 7.1). */
public final class EvlQueries {

    private final EvlGraph g;

    public EvlQueries(EvlGraph graph) {
        this.g = graph;
    }

    /** Elements directly contained by {@code key}, in declaration order. */
    public List<Element> children(String key) {
        return targets(g.outgoing(key, EdgeType.CONTAINS));
    }

    public List<Element> parents(String key) {
        List<Element> out = new ArrayList<>();
        for (Edge e : g.incoming(key, EdgeType.CONTAINS)) {
            g.element(e.from()).ifPresent(out::add);
        }
        return out;
    }

    /** The containment path from the application down to {@code key} (inclusive). */
    public List<Element> pathToRoot(String key) {
        List<Element> path = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String cur = key;
        while (cur != null && seen.add(cur)) {
            Optional<Element> e = g.element(cur);
            if (e.isEmpty()) {
                break;
            }
            path.add(0, e.get());
            List<Edge> in = g.incoming(cur, EdgeType.CONTAINS);
            cur = in.isEmpty() ? null : in.get(0).from();
        }
        return path;
    }

    /** The flow that owns a step key, or the element itself for flows. */
    public Optional<Element> flowOf(String key) {
        Optional<Element> e = g.element(key);
        if (e.isEmpty()) {
            return Optional.empty();
        }
        if (e.get().kind() == Kind.FLOW) {
            return e;
        }
        if (e.get().kind() == Kind.STEP) {
            return g.element(key.substring(0, key.lastIndexOf('/')));
        }
        return Optional.empty();
    }

    /** Elements that call {@code flowKey}: flows (through CALL_FLOW steps), endpoints and events. */
    public List<Element> callers(String flowKey) {
        Set<String> out = new LinkedHashSet<>();
        for (Edge e : g.incoming(flowKey, EdgeType.CALLS_FLOW)) {
            Optional<Element> from = g.element(e.from());
            if (from.isEmpty()) {
                continue;
            }
            if (from.get().kind() == Kind.STEP) {
                flowOf(from.get().key()).ifPresent(f -> out.add(f.key()));
            } else {
                out.add(from.get().key());
            }
        }
        return keys(out);
    }

    /** Flows called from the steps of {@code flowKey}, in step order. */
    public List<Element> callees(String flowKey) {
        Set<String> out = new LinkedHashSet<>();
        g.flow(flowKey).ifPresent(f -> f.forEach(s -> g.outgoing(s.key(), EdgeType.CALLS_FLOW).forEach(e -> out.add(e.to()))));
        g.outgoing(flowKey, EdgeType.CALLS_FLOW).forEach(e -> out.add(e.to()));
        return keys(out);
    }

    /** Elements that realize a business step or flow ({@code realizedBy}, {@code implementedBy}). */
    public List<Element> realizations(String key) {
        return targets(g.outgoing(key, EdgeType.REALIZED_BY));
    }

    /** Direction for {@link #dependencies}. */
    public enum Direction { OUTGOING, INCOMING, BOTH }

    /** Declared dependencies reachable within {@code depth} hops. */
    public List<Edge> dependencies(String key, int depth, Direction direction) {
        List<Edge> out = new ArrayList<>();
        Set<String> visited = new HashSet<>(Set.of(key));
        Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(key, 0));
        while (!queue.isEmpty()) {
            Map.Entry<String, Integer> cur = queue.poll();
            if (cur.getValue() >= depth) {
                continue;
            }
            List<Edge> next = new ArrayList<>();
            if (direction != Direction.INCOMING) {
                next.addAll(g.outgoing(cur.getKey(), EdgeType.DEPENDS_ON));
            }
            if (direction != Direction.OUTGOING) {
                next.addAll(g.incoming(cur.getKey(), EdgeType.DEPENDS_ON));
            }
            for (Edge e : next) {
                out.add(e);
                String other = e.from().equals(cur.getKey()) ? e.to() : e.from();
                if (visited.add(other)) {
                    queue.add(Map.entry(other, cur.getValue() + 1));
                }
            }
        }
        return out;
    }

    /** Elements declared or resolved at a file and line (line 0 = anywhere in the file). */
    public List<Element> elementsAt(String file, int line) {
        List<Element> out = new ArrayList<>();
        for (Element e : g.elements()) {
            boolean hit = e.origins().stream().anyMatch(o -> at(o.location(), file, line))
                || (e.resolution().location() != null && at(e.resolution().location(), file, line));
            if (hit) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean at(Location l, String file, int line) {
        return l != null && file.equals(l.file()) && (line == 0 || l.line() == line);
    }

    /** Search filters (EVL-FEX-005). {@code null} means no filter. */
    public record Filters(Set<Kind> kinds, String category, String domain, String module, Boolean hasErrors,
                          Boolean rootOnly, Boolean library) {
        public static final Filters NONE = new Filters(null, null, null, null, null, null, null);

        public static Filters kinds(Kind... kinds) {
            return new Filters(Set.of(kinds), null, null, null, null, null, null);
        }
    }

    /** A ranked search hit. */
    public record Hit(Element element, int score) {
    }

    /** Ranked search (EVL-FEX-004). Empty text lists all matching elements by name. */
    public List<Hit> search(String text, Filters filters, int limit) {
        String q = fold(text == null ? "" : text.trim());
        List<Hit> hits = new ArrayList<>();
        for (Element e : g.elements()) {
            if (!accepts(e, filters == null ? Filters.NONE : filters)) {
                continue;
            }
            int score = q.isEmpty() ? 1 : score(e, q, text.trim());
            if (score > 0) {
                hits.add(new Hit(e, score));
            }
        }
        hits.sort(Comparator.comparingInt((Hit h) -> -h.score()).thenComparing(h -> h.element().name(),
            String.CASE_INSENSITIVE_ORDER).thenComparing(h -> h.element().key()));
        return limit > 0 && hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : hits;
    }

    private boolean accepts(Element e, Filters f) {
        if (f.kinds() != null && !f.kinds().contains(e.kind())) {
            return false;
        }
        if (f.kinds() == null && (e.kind() == Kind.STEP || e.kind() == Kind.DEPENDENCY)) {
            return false;
        }
        if (f.category() != null && !f.category().equals(e.string("category").orElse(null))) {
            return false;
        }
        if (f.domain() != null && !f.domain().equals(e.string("domain").orElse(null))) {
            return false;
        }
        if (f.module() != null && pathToRoot(e.key()).stream().noneMatch(p -> p.kind() == Kind.MODULE && p.id().equals(f.module()))
            && !f.module().equals(e.string("module").orElse(null))) {
            return false;
        }
        if (f.hasErrors() != null && f.hasErrors() != (g.validation(e.key()).orElse(Level.OFF) == Level.ERROR)) {
            return false;
        }
        if (f.rootOnly() != null && f.rootOnly() && e.kind() == Kind.FLOW && !callers(e.key()).isEmpty()) {
            return false;
        }
        return f.library() == null || f.library() == e.isLibrary();
    }

    private int score(Element e, String q, String raw) {
        String id = fold(e.id());
        String name = fold(e.name());
        if (id.equals(q)) {
            return 100;
        }
        if (name.equals(q)) {
            return 90;
        }
        if (id.startsWith(q)) {
            return 80;
        }
        if (wordPrefix(name, q) || camelMatch(e.name(), raw)) {
            return 70;
        }
        if (name.contains(q)) {
            return 60;
        }
        if (participants(e).stream().anyMatch(p -> fold(p).contains(q))) {
            return 40;
        }
        String desc = fold(e.description() + " " + e.string("doc").orElse("") + " " + e.string("category").orElse("")
            + " " + e.string("domain").orElse("") + " " + e.string("module").orElse(""));
        return desc.contains(q) ? 20 : 0;
    }

    private List<String> participants(Element e) {
        List<String> out = new ArrayList<>();
        if (e.kind() != Kind.FLOW) {
            return out;
        }
        g.flow(e.key()).ifPresent(f -> f.forEach(s -> {
            g.element(s.key()).ifPresent(st -> {
                out.add(st.name());
                if (st.source() != null) {
                    out.add(st.source().raw());
                }
            });
            for (Edge x : g.outgoing(s.key())) {
                if (x.type() == EdgeType.CALLS_FLOW || x.type() == EdgeType.RELATES || x.type() == EdgeType.REALIZED_BY) {
                    g.element(x.to()).ifPresent(t -> out.add(t.name()));
                }
            }
        }));
        callers(e.key()).forEach(c -> out.add(c.name()));
        return out;
    }

    private static boolean wordPrefix(String name, String q) {
        for (String w : name.split("[\\s._/-]+")) {
            if (w.startsWith(q)) {
                return true;
            }
        }
        return false;
    }

    /** "ReIn" matches "Reserve Inventory": each capitalised chunk prefixes consecutive words. */
    static boolean camelMatch(String name, String query) {
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (Character.isUpperCase(c) && cur.length() > 0) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            chunks.add(cur.toString());
        }
        if (chunks.size() < 2) {
            return false;
        }
        List<String> words = new ArrayList<>();
        for (String w : name.split("[\\s._/-]+|(?<=[a-z])(?=[A-Z])")) {
            if (!w.isEmpty()) {
                words.add(fold(w));
            }
        }
        outer:
        for (int start = 0; start + chunks.size() <= words.size(); start++) {
            for (int i = 0; i < chunks.size(); i++) {
                if (!words.get(start + i).startsWith(fold(chunks.get(i)))) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** All flows grouped by category, then by name (Flow Explorer catalogue). */
    public Map<String, List<Element>> flowsCatalogue() {
        Map<String, List<Element>> out = new TreeMap<>();
        for (Element f : g.elementsOf(Kind.FLOW)) {
            String group = f.isLibrary() ? "Libraries" : f.string("category").orElse("APPLICATION");
            out.computeIfAbsent(group, k -> new ArrayList<>()).add(f);
        }
        out.values().forEach(l -> l.sort(Comparator.comparing(Element::name, String.CASE_INSENSITIVE_ORDER)));
        return out;
    }

    /** Steps of a flow in document order, depth-first. */
    public List<Element> steps(String flowKey) {
        List<Element> out = new ArrayList<>();
        g.flow(flowKey).ifPresent(f -> f.forEach(s -> g.element(s.key()).ifPresent(out::add)));
        return out;
    }

    /** Structure of a flow as nested maps (for the canvas and JSON export). */
    public Map<String, Object> flowTree(String flowKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<FlowStructure> f = g.flow(flowKey);
        m.put("flow", flowKey);
        m.put("steps", f.map(x -> nodes(x.steps())).orElse(List.of()));
        m.put("onError", f.map(x -> nodes(x.onError())).orElse(List.of()));
        return m;
    }

    private List<Object> nodes(List<StepNode> list) {
        List<Object> out = new ArrayList<>();
        for (StepNode s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", s.key());
            m.put("type", s.type().name());
            g.element(s.key()).ifPresent(e -> m.put("name", e.name()));
            List<Object> blocks = new ArrayList<>();
            for (StepNode.Block b : s.blocks()) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("role", b.role().name());
                if (b.label() != null) {
                    bm.put("label", b.label());
                }
                bm.put("steps", nodes(b.steps()));
                blocks.add(bm);
            }
            if (!blocks.isEmpty()) {
                m.put("blocks", blocks);
            }
            out.add(m);
        }
        return out;
    }

    private List<Element> targets(List<Edge> edges) {
        List<Element> out = new ArrayList<>();
        for (Edge e : edges) {
            g.element(e.to()).ifPresent(out::add);
        }
        return out;
    }

    private List<Element> keys(Set<String> keys) {
        List<Element> out = new ArrayList<>();
        for (String k : keys) {
            g.element(k).ifPresent(out::add);
        }
        return out;
    }
}
