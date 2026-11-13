package io.github.ilyaslabs.evl.core.resolve;

import io.github.ilyaslabs.evl.core.api.CancellationToken;
import io.github.ilyaslabs.evl.core.api.LibraryInfo;
import io.github.ilyaslabs.evl.core.api.SourceResolver;
import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.ElementRef;
import io.github.ilyaslabs.evl.core.model.Evidence;
import io.github.ilyaslabs.evl.core.model.Fix;
import io.github.ilyaslabs.evl.core.model.FlowStructure;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.model.SourceResolution;
import io.github.ilyaslabs.evl.core.parse.AnnotationMapper;
import io.github.ilyaslabs.evl.core.parse.Manifest;
import io.github.ilyaslabs.evl.core.parse.MappedAnnotations;
import io.github.ilyaslabs.evl.core.parse.ParsedDocument;
import io.github.ilyaslabs.evl.core.parse.RawDecl;
import io.github.ilyaslabs.evl.core.parse.SchemaCheck;
import io.github.ilyaslabs.evl.core.parse.Versions;
import io.github.ilyaslabs.evl.core.parse.YNode;
import io.github.ilyaslabs.evl.core.spec.FieldSpec;
import io.github.ilyaslabs.evl.core.spec.FieldType;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the canonical graph from parsed inputs: merge, flow structure, reference resolution,
 * edges, containment and source verification.
 */
public final class GraphBuilder {

    /** Parsed inputs of one EVL application. */
    public record AppInput(Manifest manifest, RawDecl application, List<ParsedDocument> documents,
                           MappedAnnotations annotations, String origin) {
    }

    /** Graph parts before validation. */
    public record Output(String appId, String appName, List<LibraryInfo> libraries, Map<String, Element> elements,
                         List<Edge> edges, Map<String, FlowStructure> flows, List<Diagnostic> diagnostics) {
    }

    private static final Set<String> STEP_ANY = Set.of();

    private final AppInput app;
    private final List<AppInput> libraries;
    private final SourceResolver resolver;
    private final CancellationToken cancel;
    private final List<Diagnostic> diags = new ArrayList<>();
    private final List<Diagnostic> sink = new ArrayList<>(); // library problems are not reported to consumers
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Map<String, FlowStructure> flows = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Set<String> libraryIds = new LinkedHashSet<>();

    private GraphBuilder(AppInput app, List<AppInput> libraries, SourceResolver resolver, CancellationToken cancel) {
        this.app = app;
        this.libraries = libraries;
        this.resolver = resolver;
        this.cancel = cancel == null ? CancellationToken.NONE : cancel;
    }

    public static Output build(AppInput app, List<AppInput> libraries, SourceResolver resolver, CancellationToken cancel) {
        return new GraphBuilder(app, libraries, resolver, cancel).run();
    }

    private String appId() {
        return app.manifest().id();
    }

    private Output run() {
        for (AppInput lib : libraries) {
            libraryIds.add(lib.manifest().id());
        }
        List<LibraryInfo> infos = new ArrayList<>();
        for (AppInput lib : libraries) {
            Manifest m = lib.manifest();
            infos.add(new LibraryInfo(m.id(), m.name(), m.description(), m.artifact(), lib.origin()));
            mergeApp(lib, true, sink);
            cancel.check();
        }
        mergeApp(app, false, diags);
        cancel.check();
        for (Draft d : new ArrayList<>(drafts.values())) {
            linkDraft(d, d.app == null ? diags : sink);
        }
        cancel.check();
        annotationEdges();
        containment();
        Map<String, Element> elements = new LinkedHashMap<>();
        for (Draft d : drafts.values()) {
            elements.put(d.key, toElement(d, d.app == null ? diags : sink));
        }
        cancel.check();
        verifyDependencies(elements);
        List<Edge> unique = new ArrayList<>(new LinkedHashSet<>(edges));
        return new Output(appId(), app.manifest().name(), infos, elements, unique, flows, diags);
    }

    // ------------------------------------------------------------------ merge

    private void mergeApp(AppInput in, boolean library, List<Diagnostic> out) {
        String id = in.manifest().id();
        List<RawDecl> docDecls = new ArrayList<>();
        for (ParsedDocument doc : in.documents()) {
            out.addAll(doc.diagnostics());
            versionCheck(in.manifest(), doc, out);
            for (RawDecl d : doc.decls()) {
                docDecls.add(d);
                if (d.kind() == Kind.CONFIGURATION) {
                    docDecls.addAll(inlineProperties(d, out));
                }
            }
        }
        MappedAnnotations ann = in.annotations() == null ? MappedAnnotations.EMPTY : in.annotations();
        out.addAll(ann.diagnostics());
        Map<String, Draft> merged = Merger.merge(id, library, in.application(), docDecls, ann.decls(),
            library ? Set.of() : libraryIds, out);
        for (Draft d : merged.values()) {
            if (drafts.containsKey(d.key)) {
                continue; // a library id colliding with another library is reported as E032 by the workspace
            }
            drafts.put(d.key, d);
        }
        contributions(id, library, ann, out);
        Map<String, List<MappedAnnotations.Step>> stepsByFlow = new HashMap<>();
        for (MappedAnnotations.Step s : ann.steps()) {
            ElementRef ref = s.flow() == null ? null : ElementRef.parse(s.flow()).orElse(null);
            String key = ref == null ? null : Merger.key(id, library, ref.id());
            Draft flow = key == null ? null : drafts.get(key);
            if (flow == null || flow.kind != Kind.FLOW) {
                out.add(Diagnostics.of(Rule.E025, "@EVL.Step refers to unknown flow '" + s.flow() + "'", s.location()));
                continue;
            }
            stepsByFlow.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        for (Draft d : merged.values()) {
            if (d.kind != Kind.FLOW || drafts.get(d.key) != d) {
                continue;
            }
            FlowBuilder.Built b = FlowBuilder.build(d, stepsByFlow.getOrDefault(d.key, List.of()), out);
            flows.put(d.key, b.structure());
            for (Draft s : b.steps().values()) {
                drafts.put(s.key, s);
            }
            edges.addAll(b.edges());
        }
    }

    private void versionCheck(Manifest m, ParsedDocument doc, List<Diagnostic> out) {
        if (doc.version() == null) {
            return;
        }
        if (Versions.major(doc.version()) != m.major()) {
            out.add(Diagnostics.of(Rule.E050, "Document declares evl " + doc.version() + " but the manifest declares "
                + m.version(), doc.versionAt()));
        } else if (Versions.minor(doc.version()) > Versions.MINOR) {
            out.add(Diagnostics.of(Rule.W051, "Document uses evl " + doc.version() + ", newer than " + Versions.SPEC,
                doc.versionAt()));
        }
    }

    private List<RawDecl> inlineProperties(RawDecl conf, List<Diagnostic> out) {
        List<RawDecl> list = new ArrayList<>();
        if (!(conf.body().get("properties") instanceof YNode.Sequence seq)) {
            return list;
        }
        for (YNode item : seq.items()) {
            if (!(item instanceof YNode.Mapping m)) {
                continue;
            }
            String key = m.text("key");
            String id = m.text("id");
            if (id == null && key != null) {
                id = AnnotationMapper.configId(key);
            }
            if (id == null) {
                out.add(Diagnostics.of(Rule.E106, "Config property needs a key", m.location(), conf.id()));
                continue;
            }
            Map<String, YNode> body = new LinkedHashMap<>(m.entries());
            body.putIfAbsent("id", YNode.Scalar.string(id, m.location()));
            if (key != null) {
                body.putIfAbsent("name", YNode.Scalar.string(key, m.location()));
            }
            YNode.Mapping bm = new YNode.Mapping(body, m.keyLocations(), m.location());
            SchemaCheck.element(Kind.CONFIG_PROPERTY, bm, id, out);
            list.add(new RawDecl(Kind.CONFIG_PROPERTY, id, bm, Origin.document(m.location()), false));
            edges.add(new Edge(conf.id(), id, EdgeType.CONTAINS, Evidence.DECLARED, null, Origin.document(m.location())));
        }
        return list;
    }

    private void contributions(String id, boolean library, MappedAnnotations ann, List<Diagnostic> out) {
        for (MappedAnnotations.Contribution c : ann.contributions()) {
            ElementRef ref = c.target() == null ? null : ElementRef.parse(c.target()).orElse(null);
            if (ref == null) {
                out.add(Diagnostics.of(Rule.E025, "Unresolved event reference '" + c.target() + "'", c.location()));
                continue;
            }
            boolean toLibrary = ref.app() != null && !ref.app().equals(id) && !library;
            String key = toLibrary ? ref.key() : Merger.key(id, library, ref.id());
            Draft target = drafts.get(key);
            if (target == null) {
                if (toLibrary && !libraryIds.contains(ref.app())) {
                    out.add(Diagnostics.of(Rule.E022, "Library '" + ref.app() + "' is not on the classpath", c.location()));
                } else {
                    out.add(Diagnostics.of(Rule.E025, "Unresolved event reference '" + c.target() + "'", c.location()));
                }
                continue;
            }
            if (target.kind != Kind.EVENT) {
                out.add(Diagnostics.of(Rule.E023, "'" + c.target() + "' is a " + target.kind.keyword() + ", not an event",
                    c.location()));
                continue;
            }
            String owner = Merger.key(id, library, c.value() instanceof Map<?, ?> m ? String.valueOf(m.get("ref"))
                : String.valueOf(c.value()));
            Origin origin = Origin.annotation(c.location(), c.javaElement());
            if (toLibrary) {
                EdgeType t = c.field().equals("producers") ? EdgeType.PRODUCES : EdgeType.CONSUMES;
                edges.add(new Edge(owner, key, t, Evidence.DECLARED, null, origin));
                continue;
            }
            List<YNode> items = new ArrayList<>();
            if (target.fields.get(c.field()) instanceof YNode.Sequence seq) {
                items.addAll(seq.items());
            }
            items.add(YNode.fromPlain(c.value(), c.location()));
            target.put(c.field(), new YNode.Sequence(items, c.location()), origin);
        }
    }

    // ------------------------------------------------------------------ references

    private void linkDraft(Draft d, List<Diagnostic> out) {
        if (d.kind == Kind.STEP) {
            linkStep(d, out);
            return;
        }
        if (d.kind == Kind.DEPENDENCY) {
            String from = ref(d, "from", d.text("from"), Set.of(), false, out);
            String to = ref(d, "to", d.text("to"), Set.of(), false, out);
            if (from != null && to != null) {
                edges.add(new Edge(from, to, EdgeType.DEPENDS_ON, Evidence.DECLARED, d.text("type"), d.origins.get(0)));
            }
            commonRefs(d, out);
            return;
        }
        for (FieldSpec spec : d.kind.fields().values()) {
            YNode v = d.fields.get(spec.name());
            if (v == null) {
                continue;
            }
            if (spec.type() == FieldType.REF) {
                String target = ref(d, spec.name(), text(v), spec.refKinds(), false, out);
                if (target != null) {
                    edges.add(edgeFor(d, spec.name(), target));
                }
            } else if (spec.type() == FieldType.REF_LIST && v instanceof YNode.Sequence seq) {
                for (YNode item : seq.items()) {
                    String target = ref(d, spec.name(), text(item), spec.refKinds(), false, out);
                    if (target != null) {
                        edges.add(edgeFor(d, spec.name(), target));
                    }
                }
            }
        }
        objectRefs(d, out);
    }

    private void commonRefs(Draft d, List<Diagnostic> out) {
        for (String f : List.of("requirements", "decisions")) {
            if (d.fields.get(f) instanceof YNode.Sequence seq) {
                Set<String> kinds = Set.of(f.equals("requirements") ? "requirement" : "decision");
                for (YNode item : seq.items()) {
                    String t = ref(d, f, text(item), kinds, false, out);
                    if (t != null) {
                        edges.add(edge(d.key, t, EdgeType.RELATES, f, d));
                    }
                }
            }
        }
    }

    private void objectRefs(Draft d, List<Diagnostic> out) {
        if (d.kind == Kind.EVENT && d.fields.get("consumers") instanceof YNode.Sequence seq) {
            for (YNode item : seq.items()) {
                if (item instanceof YNode.Mapping m) {
                    String c = ref(d, "consumers.ref", m.text("ref"), Set.of(), false, out);
                    if (c != null) {
                        edges.add(edge(c, d.key, EdgeType.CONSUMES, null, d));
                    }
                    String f = ref(d, "consumers.flow", m.text("flow"), Set.of("flow"), false, out);
                    if (f != null) {
                        edges.add(edge(d.key, f, EdgeType.CALLS_FLOW, "consumer", d));
                    }
                }
            }
        }
        if (d.kind == Kind.DATA_MODEL && d.fields.get("relationships") instanceof YNode.Sequence seq) {
            for (YNode item : seq.items()) {
                if (item instanceof YNode.Mapping m) {
                    String t = ref(d, "relationships.target", m.text("target"), Set.of("data-model"), false, out);
                    if (t != null) {
                        edges.add(edge(d.key, t, EdgeType.RELATIONSHIP, m.text("cardinality"), d));
                    }
                }
            }
        }
    }

    private void linkStep(Draft d, List<Diagnostic> out) {
        String type = d.text("type");
        String flow = d.text("flow");
        if (flow != null) {
            String t = ref(d, "flow", flow, STEP_ANY, false, out);
            if (t != null) {
                Draft target = drafts.get(t);
                if (target.kind != Kind.FLOW) {
                    out.add(Diagnostics.of(Rule.E065, "CALL_FLOW target '" + flow + "' is a " + target.kind.keyword()
                        + ", not a flow", d.fields.get("flow").location(), d.id));
                } else {
                    edges.add(edge(d.key, t, EdgeType.CALLS_FLOW, null, d));
                }
            }
        }
        String event = d.text("event");
        if (event != null) {
            String t = ref(d, "event", event, Set.of("event"), false, out);
            if (t != null) {
                EdgeType et = "CONSUME".equals(d.text("direction")) ? EdgeType.CONSUMES : EdgeType.PRODUCES;
                edges.add(edge(d.key, t, et, null, d));
            }
        }
        String component = d.text("component");
        if (component != null) {
            String t = ref(d, "component", component, Set.of("component"), false, out);
            if (t != null) {
                edges.add(edge(d.key, t, EdgeType.RELATES, "component", d));
            }
        }
        if (d.fields.get("realizedBy") instanceof YNode.Sequence seq) {
            for (YNode item : seq.items()) {
                String t = ref(d, "realizedBy", text(item), STEP_ANY, false, out);
                if (t != null) {
                    edges.add(edge(d.key, t, EdgeType.REALIZED_BY, "realized by", d));
                }
            }
        }
        if ("FAIL".equals(type) && d.text("error") != null) {
            String t = ref(d, "error", d.text("error"), Set.of("error"), true, out);
            if (t != null) {
                edges.add(edge(d.key, t, EdgeType.RELATES, "error", d));
            }
        }
        if (d.fields.get("errors") instanceof YNode.Sequence seq) {
            for (YNode item : seq.items()) {
                if (item instanceof YNode.Mapping m && m.text("on") != null) {
                    String t = ref(d, "errors.on", m.text("on"), Set.of("error"), true, out);
                    if (t != null) {
                        edges.add(edge(d.key, t, EdgeType.RELATES, "on error", d));
                    }
                }
            }
        }
        commonRefs(d, out);
    }

    private Edge edgeFor(Draft d, String field, String target) {
        Kind k = d.kind;
        return switch (field) {
            case "parent" -> k == Kind.MODULE ? edge(target, d.key, EdgeType.CONTAINS, null, d) : edge(d.key, target, EdgeType.RELATES, field, d);
            case "module" -> edge(target, d.key, EdgeType.CONTAINS, null, d);
            case "owner" -> edge(target, d.key, EdgeType.OWNS, null, d);
            case "of" -> edge(d.key, target, EdgeType.IMPLEMENTS, null, d);
            case "component" -> edge(target, d.key, EdgeType.CONTAINS, null, d);
            case "producers" -> edge(target, d.key, EdgeType.PRODUCES, null, d);
            case "implementedBy" -> edge(d.key, target, EdgeType.REALIZED_BY, "implemented by", d);
            case "flow" -> edge(d.key, target, EdgeType.CALLS_FLOW, k.keyword(), d);
            default -> edge(d.key, target, EdgeType.RELATES, field, d);
        };
    }

    private Edge edge(String from, String to, EdgeType type, String label, Draft d) {
        return new Edge(from, to, type, Evidence.DECLARED, label, d.origins.isEmpty() ? null : d.origins.get(0));
    }

    /**
     * Resolves a reference from draft {@code d}. Returns the target key or {@code null}. Soft
     * references (text or ref) produce no diagnostics when they do not resolve.
     */
    private String ref(Draft d, String field, String text, Set<String> kinds, boolean soft, List<Diagnostic> out) {
        if (text == null) {
            return null;
        }
        Location loc = location(d, field);
        ElementRef r = ElementRef.parse(text).orElse(null);
        if (r == null) {
            if (!soft) {
                out.add(Diagnostics.of(Rule.E025, "'" + text + "' is not a valid element reference", loc, d.id));
            }
            return null;
        }
        String contextApp = d.app;
        String refApp = r.app() == null ? contextApp : (r.app().equals(appId()) ? null : r.app());
        if (refApp != null && contextApp == null && !libraryIds.contains(refApp)) {
            if (!soft) {
                out.add(Diagnostics.of(Rule.E022, "Library '" + refApp + "' is not on the classpath (referenced as '"
                    + text + "')", loc, d.id));
            }
            return null;
        }
        String rid = r.kind() == Kind.APPLICATION ? Merger.APPLICATION_PREFIX + r.id() : r.id();
        String base = refApp == null ? rid : refApp + ":" + rid;
        Draft target = drafts.get(r.local() == null ? base : base + "/" + r.local());
        if (target == null && r.local() != null) {
            Draft owner = drafts.get(base);
            if (owner != null && owner.kind == Kind.DATA_MODEL && hasField(owner, r.local())) {
                target = owner;
            }
        }
        if (target == null) {
            if (!soft) {
                List<Fix> fixes = suggestions(r.id(), refApp, kinds);
                String hint = fixes.isEmpty() ? "" : "; did you mean '" + fixes.get(0).args().get("replacement") + "'?";
                out.add(Diagnostics.of(Rule.E025, "Unresolved reference '" + text + "' in " + field + hint, loc, d.id,
                    List.of(), fixes));
            }
            return null;
        }
        if (r.kind() != null && r.kind() != target.kind) {
            out.add(Diagnostics.of(Rule.E021, "'" + text + "' says " + r.kind().keyword() + " but '" + r.id() + "' is a "
                + target.kind.keyword(), loc, d.id));
            return null;
        }
        if (!kinds.isEmpty() && !kinds.contains(target.kind.keyword())) {
            if (!soft) {
                out.add(Diagnostics.of(Rule.E023, "'" + field + "' cannot reference a " + target.kind.keyword() + " ('" + text
                    + "'); allowed: " + String.join(", ", new java.util.TreeSet<>(kinds)), loc, d.id));
            }
            return null;
        }
        return target.key;
    }

    private static boolean hasField(Draft model, String name) {
        if (model.fields.get("fields") instanceof YNode.Sequence seq) {
            for (YNode n : seq.items()) {
                if (n instanceof YNode.Mapping m && name.equals(m.text("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Location location(Draft d, String field) {
        String top = field.contains(".") ? field.substring(0, field.indexOf('.')) : field;
        YNode v = d.fields.get(top);
        if (v != null && v.location().isKnown()) {
            return v.location();
        }
        Origin o = d.fieldOrigins.get(top);
        return o != null ? o.location() : (d.origins.isEmpty() ? Location.UNKNOWN : d.origins.get(0).location());
    }

    private List<Fix> suggestions(String id, String refApp, Set<String> kinds) {
        record Candidate(String id, int distance) {
        }
        List<Candidate> cands = new ArrayList<>();
        for (Draft d : drafts.values()) {
            if (d.kind == Kind.STEP || d.kind == Kind.DEPENDENCY || !java.util.Objects.equals(d.app, refApp)) {
                continue;
            }
            if (!kinds.isEmpty() && !kinds.contains(d.kind.keyword())) {
                continue;
            }
            int dist = levenshtein(id, d.id, 3);
            if (dist <= 3) {
                cands.add(new Candidate(d.id, dist));
            }
        }
        cands.sort(Comparator.comparingInt(Candidate::distance).thenComparing(Candidate::id));
        List<Fix> fixes = new ArrayList<>();
        for (Candidate c : cands.subList(0, Math.min(3, cands.size()))) {
            String replacement = refApp == null ? c.id() : refApp + ":" + c.id();
            fixes.add(new Fix("replace-reference", "Change to '" + replacement + "'", Map.of("replacement", replacement)));
        }
        return fixes;
    }

    static int levenshtein(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static String text(YNode n) {
        return n instanceof YNode.Scalar s ? s.text() : null;
    }

    // ------------------------------------------------------------------ annotation calls and containment

    private void annotationEdges() {
        for (MappedAnnotations.Call c : (app.annotations() == null ? MappedAnnotations.EMPTY : app.annotations()).calls()) {
            Draft from = drafts.get(c.from());
            if (from == null) {
                continue;
            }
            Draft probe = new Draft(from.kind, from.id, from.key, null);
            probe.origins.add(Origin.annotation(c.location(), null));
            probe.fieldOrigins.put("target", probe.origins.get(0));
            String t = ref(probe, "target", c.target(), Set.of("flow", "endpoint", "component", "external-system", "event"),
                false, diags);
            if (t != null) {
                edges.add(new Edge(from.key, t, EdgeType.CALLS, Evidence.DECLARED,
                    c.kind() + (c.reason() == null ? "" : ": " + c.reason()), probe.origins.get(0)));
            }
        }
    }

    private void containment() {
        Set<String> contained = new HashSet<>();
        for (Edge e : edges) {
            if (e.type() == EdgeType.CONTAINS) {
                contained.add(e.to());
            }
        }
        Map<String, List<Draft>> modulesByApp = new HashMap<>();
        Map<String, List<Draft>> configsByApp = new HashMap<>();
        for (Draft d : drafts.values()) {
            String a = d.app == null ? "" : d.app;
            if (d.kind == Kind.MODULE) {
                modulesByApp.computeIfAbsent(a, k -> new ArrayList<>()).add(d);
            } else if (d.kind == Kind.CONFIGURATION && d.text("prefix") != null) {
                configsByApp.computeIfAbsent(a, k -> new ArrayList<>()).add(d);
            }
        }
        for (Draft d : drafts.values()) {
            if (d.kind == Kind.APPLICATION || d.kind == Kind.STEP || d.kind == Kind.DEPENDENCY || contained.contains(d.key)) {
                continue;
            }
            String a = d.app == null ? "" : d.app;
            String root = d.app == null ? Merger.applicationKey(appId(), false) : Merger.applicationKey(d.app, true);
            String parent = null;
            if (d.kind == Kind.CONFIG_PROPERTY && d.text("key") != null) {
                for (Draft c : configsByApp.getOrDefault(a, List.of())) {
                    if (d.text("key").startsWith(c.text("prefix") + ".")) {
                        parent = c.key;
                        break;
                    }
                }
            }
            if (parent == null && d.kind != Kind.MODULE) {
                String pkg = Packages.packageOf(sourceOf(d));
                int best = -1;
                for (Draft m : modulesByApp.getOrDefault(a, List.of())) {
                    for (String glob : strings(m.fields.get("packages"))) {
                        if (Packages.matches(glob, pkg) && Packages.specificity(glob) > best) {
                            best = Packages.specificity(glob);
                            parent = m.key;
                        }
                    }
                }
            }
            if (parent == null && drafts.containsKey(root)) {
                parent = root;
            }
            if (parent != null && !parent.equals(d.key)) {
                edges.add(new Edge(parent, d.key, EdgeType.CONTAINS, Evidence.DECLARED, null, null));
            }
        }
    }

    static List<String> strings(YNode n) {
        List<String> out = new ArrayList<>();
        if (n instanceof YNode.Sequence seq) {
            for (YNode i : seq.items()) {
                if (i instanceof YNode.Scalar s && s.text() != null) {
                    out.add(s.text());
                }
            }
        }
        return out;
    }

    private static SourceRef sourceOf(Draft d) {
        YNode s = d.fields.get("source");
        String t = s == null ? null : SchemaCheck.sourceText(s);
        return t == null ? null : SourceRef.parse(t).orElse(null);
    }

    // ------------------------------------------------------------------ elements and verification

    private Element toElement(Draft d, List<Diagnostic> out) {
        Map<String, Object> fields = new LinkedHashMap<>();
        d.fields.forEach((k, v) -> fields.put(k, v.toPlain()));
        String name = d.text("name");
        if (name == null || name.isBlank()) {
            name = d.kind == Kind.STEP ? d.id.substring(d.id.lastIndexOf('/') + 1) : d.id;
        }
        SourceRef source = sourceOf(d);
        SourceResolution res = SourceResolution.UNCHECKED;
        Origin ann = d.annotationOrigin();
        if (source != null && ann != null && ann.javaElement() != null && Merger.sameSource(ann.javaElement(), source.raw())) {
            res = SourceResolution.resolved(ann.location());
        } else if (source != null && resolver != null && d.app == null) {
            res = check(d, source, "source", out);
        }
        YNode ep = d.fields.get("entryPoint");
        if (ep != null && resolver != null && d.app == null && SchemaCheck.sourceText(ep) != null) {
            boolean annotated = ann != null && ann.javaElement() != null
                && Merger.sameSource(ann.javaElement(), SchemaCheck.sourceText(ep));
            if (!annotated) {
                SourceRef.parse(SchemaCheck.sourceText(ep)).ifPresent(r -> check(d, r, "entryPoint", out));
            }
        }
        Element e = new Element(d.key, d.id, d.kind, name, d.app, fields, d.fieldOrigins, d.origins, source, res,
            Evidence.DECLARED);
        return res.isResolved() ? e.withResolution(res) : e;
    }

    private SourceResolution check(Draft d, SourceRef ref, String field, List<Diagnostic> out) {
        SourceResolution r;
        try {
            r = resolver.resolve(ref);
        } catch (RuntimeException ex) {
            r = SourceResolution.UNCHECKED;
        }
        Location loc = location(d, field);
        switch (r.state()) {
            case UNRESOLVED -> {
                if (ref.form() == SourceRef.Form.CONFIG) {
                    out.add(Diagnostics.of(Rule.I041, "Config key '" + ref.type() + "' was not found in configuration files;"
                        + " it may come from the environment", loc, d.id));
                } else {
                    out.add(Diagnostics.of(Rule.E030, "Source reference '" + ref.raw() + "' does not resolve"
                        + (r.reason() == null ? "" : ": " + r.reason()), loc, d.id));
                }
            }
            case AMBIGUOUS -> {
                List<Fix> fixes = new ArrayList<>();
                for (String c : r.candidates()) {
                    fixes.add(new Fix("replace-source", "Use " + c, Map.of("replacement", c)));
                }
                out.add(Diagnostics.of(Rule.E031, "Source reference '" + ref.raw() + "' is ambiguous: "
                    + String.join(", ", r.candidates()), loc, d.id, List.of(), fixes));
            }
            case UNCHECKED -> out.add(Diagnostics.of(Rule.I041, "Source reference '" + ref.raw() + "' could not be checked",
                loc, d.id));
            default -> {
            }
        }
        return r;
    }

    private void verifyDependencies(Map<String, Element> elements) {
        if (resolver == null) {
            return;
        }
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            if (e.type() != EdgeType.DEPENDS_ON) {
                continue;
            }
            Element from = elements.get(e.from());
            Element to = elements.get(e.to());
            if (from == null || to == null || from.isLibrary() || from.source() == null || to.source() == null
                || !from.source().isCode() || !to.source().isCode()) {
                continue;
            }
            java.util.Optional<Boolean> seen;
            try {
                seen = resolver.references(from.source(), to.source());
            } catch (RuntimeException ex) {
                seen = java.util.Optional.empty();
            }
            if (seen.isPresent() && seen.get()) {
                edges.set(i, e.withEvidence(Evidence.VERIFIED));
            } else if (seen.isPresent()) {
                Location loc = e.origin() == null ? from.location() : e.origin().location();
                diags.add(Diagnostics.of(Rule.I040, "No code in " + from.id() + " references " + to.id()
                    + "; the dependency may be indirect", loc, from.id()));
            }
        }
    }

    static boolean isAnnotationOnly(Element e) {
        return e.origins().stream().allMatch(o -> o.kind() == OriginKind.ANNOTATION);
    }
}
