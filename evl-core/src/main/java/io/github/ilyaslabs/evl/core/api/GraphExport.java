package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Fix;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.parse.Versions;
import io.github.ilyaslabs.evl.core.spec.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machine-readable agent interface (spec {@code agent-interface.md}): the resolved graph and
 * diagnostics as JSON, plus DOT and Mermaid renderings. Stable within a spec major version.
 */
public final class GraphExport {

    public static final int FORMAT = 1;

    private GraphExport() {
    }

    /** The full resolved index ({@code evl index}). */
    public static Map<String, Object> index(EvlResult r) {
        EvlGraph g = r.graph();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evl", Versions.SPEC);
        root.put("format", FORMAT);
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("id", g.appId());
        app.put("name", g.appName());
        root.put("application", app);
        List<Object> libs = new ArrayList<>();
        for (LibraryInfo l : g.libraries()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id());
            m.put("name", l.name());
            if (l.artifact() != null) {
                m.put("artifact", l.artifact());
            }
            libs.add(m);
        }
        root.put("libraries", libs);
        List<Object> elements = new ArrayList<>();
        for (Element e : g.elements()) {
            elements.add(element(e, g));
        }
        root.put("elements", elements);
        List<Object> edges = new ArrayList<>();
        for (Edge e : g.edges()) {
            edges.add(edge(e));
        }
        root.put("edges", edges);
        Map<String, Object> flows = new LinkedHashMap<>();
        EvlQueries q = new EvlQueries(g);
        for (String f : g.flows().keySet()) {
            flows.put(f, q.flowTree(f).get("steps"));
        }
        root.put("flows", flows);
        root.put("diagnostics", diagnostics(r.diagnostics()));
        return root;
    }

    /** A sub-graph rooted at {@code rootKey} (containment and calls up to {@code depth}). */
    public static Map<String, Object> subgraph(EvlResult r, String rootKey, int depth) {
        EvlGraph g = r.graph();
        Set<String> keep = new HashSet<>();
        collect(g, rootKey, depth, keep);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evl", Versions.SPEC);
        root.put("format", FORMAT);
        root.put("root", rootKey);
        List<Object> elements = new ArrayList<>();
        for (Element e : g.elements()) {
            if (keep.contains(e.key())) {
                elements.add(element(e, g));
            }
        }
        root.put("elements", elements);
        List<Object> edges = new ArrayList<>();
        for (Edge e : g.edges()) {
            if (keep.contains(e.from()) && keep.contains(e.to())) {
                edges.add(edge(e));
            }
        }
        root.put("edges", edges);
        return root;
    }

    private static void collect(EvlGraph g, String key, int depth, Set<String> keep) {
        if (!keep.add(key) || depth <= 0) {
            return;
        }
        for (Edge e : g.outgoing(key)) {
            if (e.type() != EdgeType.RELATES) {
                collect(g, e.to(), depth - 1, keep);
            }
        }
    }

    public static Map<String, Object> element(Element e, EvlGraph g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", e.key());
        m.put("id", e.id());
        m.put("kind", e.kind().keyword());
        m.put("name", e.name());
        if (e.app() != null) {
            m.put("library", e.app());
        }
        m.put("evidence", e.evidence().name());
        g.validation(e.key()).ifPresent(l -> m.put("validation", l.name()));
        if (e.source() != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("ref", e.source().raw());
            s.put("resolution", e.resolution().state().name());
            if (e.resolution().location() != null) {
                s.put("location", location(e.resolution().location()));
            }
            m.put("source", s);
        }
        Map<String, Object> fields = new LinkedHashMap<>(e.fields());
        fields.remove("id");
        fields.remove("name");
        fields.remove("source");
        m.put("fields", fields);
        List<Object> origins = new ArrayList<>();
        for (Origin o : e.origins()) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("kind", o.kind().name().toLowerCase());
            om.putAll(location(o.location()));
            if (o.javaElement() != null) {
                om.put("element", o.javaElement());
            }
            origins.add(om);
        }
        m.put("origins", origins);
        return m;
    }

    public static Map<String, Object> edge(Edge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", e.from());
        m.put("to", e.to());
        m.put("type", e.type().name());
        m.put("evidence", e.evidence().name());
        if (e.label() != null) {
            m.put("label", e.label());
        }
        return m;
    }

    public static Map<String, Object> location(Location l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", l.file());
        if (l.line() > 0) {
            m.put("line", l.line());
        }
        if (l.column() > 0) {
            m.put("column", l.column());
        }
        return m;
    }

    /** Diagnostics in the {@code evl-diagnostics.schema.json} shape. */
    public static List<Object> diagnostics(List<Diagnostic> list) {
        List<Object> out = new ArrayList<>();
        for (Diagnostic d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", d.code());
            m.put("level", d.level().name());
            m.put("message", d.message());
            m.put("location", location(d.location()));
            if (d.elementId() != null) {
                m.put("element", d.elementId());
            }
            if (!d.related().isEmpty()) {
                List<Object> rel = new ArrayList<>();
                d.related().forEach(l -> rel.add(location(l)));
                m.put("related", rel);
            }
            if (!d.fixes().isEmpty()) {
                List<Object> fixes = new ArrayList<>();
                for (Fix f : d.fixes()) {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("action", f.action());
                    fm.put("title", f.title());
                    fm.put("args", f.args());
                    fixes.add(fm);
                }
                m.put("fixes", fixes);
            }
            out.add(m);
        }
        return out;
    }

    public static String json(EvlResult r) {
        return Json.write(index(r));
    }

    /** Graphviz DOT of the elements in {@code keys} (all when null). */
    public static String dot(EvlGraph g, Set<String> keys) {
        StringBuilder sb = new StringBuilder("digraph evl {\n  rankdir=LR;\n  node [shape=box, fontname=\"Helvetica\"];\n");
        for (Element e : g.elements()) {
            if (keys != null && !keys.contains(e.key()) || e.kind() == Kind.DEPENDENCY) {
                continue;
            }
            sb.append("  ").append(quote(e.key())).append(" [label=").append(quote(e.name() + "\\n«" + e.kind().keyword() + "»"))
                .append("];\n");
        }
        for (Edge e : g.edges()) {
            if (keys != null && (!keys.contains(e.from()) || !keys.contains(e.to()))) {
                continue;
            }
            sb.append("  ").append(quote(e.from())).append(" -> ").append(quote(e.to())).append(" [label=")
                .append(quote(e.type().name().toLowerCase() + (e.label() == null ? "" : " " + e.label())));
            if (e.type() == EdgeType.CONTAINS) {
                sb.append(", style=dotted");
            }
            sb.append("];\n");
        }
        return sb.append("}\n").toString();
    }

    /** Mermaid flowchart of the elements in {@code keys} (all when null). */
    public static String mermaid(EvlGraph g, Set<String> keys) {
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        Map<String, String> ids = new LinkedHashMap<>();
        for (Element e : g.elements()) {
            if (keys != null && !keys.contains(e.key()) || e.kind() == Kind.DEPENDENCY) {
                continue;
            }
            String id = "n" + ids.size();
            ids.put(e.key(), id);
            sb.append("  ").append(id).append("[\"").append(e.name().replace("\"", "'")).append("\"]\n");
        }
        for (Edge e : g.edges()) {
            String a = ids.get(e.from());
            String b = ids.get(e.to());
            if (a == null || b == null) {
                continue;
            }
            String arrow = e.type() == EdgeType.CONTAINS ? " -.-> " : " -->|" + e.type().name().toLowerCase() + "| ";
            sb.append("  ").append(a).append(arrow).append(b).append('\n');
        }
        return sb.toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\\\\n", "\\n") + "\"";
    }
}
