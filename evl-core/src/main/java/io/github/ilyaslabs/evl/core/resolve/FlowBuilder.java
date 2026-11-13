package io.github.ilyaslabs.evl.core.resolve;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Evidence;
import io.github.ilyaslabs.evl.core.model.FlowStructure;
import io.github.ilyaslabs.evl.core.model.Ids;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.model.StepNode;
import io.github.ilyaslabs.evl.core.parse.MappedAnnotations;
import io.github.ilyaslabs.evl.core.parse.SchemaCheck;
import io.github.ilyaslabs.evl.core.parse.YNode;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import io.github.ilyaslabs.evl.core.spec.StepType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the block-structured step tree of one flow (spec chapter 6). */
final class FlowBuilder {

    private static final Set<String> STRUCTURAL = Set.of("steps", "branches", "otherwise", "onTimeout");

    /** Output for one flow. */
    record Built(FlowStructure structure, Map<String, Draft> steps, List<Edge> edges) {
    }

    private final Draft flow;
    private final List<Diagnostic> diags;
    private final Map<String, Draft> steps = new LinkedHashMap<>();
    private final Map<String, Integer> order = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Draft> gotos = new ArrayList<>();
    private final List<Draft> compensates = new ArrayList<>();
    private final Map<String, StepType> types = new HashMap<>();

    private FlowBuilder(Draft flow, List<Diagnostic> diags) {
        this.flow = flow;
        this.diags = diags;
    }

    static Built build(Draft flow, List<MappedAnnotations.Step> annotated, List<Diagnostic> diags) {
        FlowBuilder b = new FlowBuilder(flow, diags);
        List<StepNode> main = b.sequence(flow.fields.get("steps"), flow.key);
        List<StepNode> onError = b.sequence(flow.fields.get("onError"), flow.key);
        main = b.annotated(main, annotated);
        b.control(main, flow.key, true);
        b.control(onError, flow.key, true);
        b.links();
        return new Built(new FlowStructure(flow.key, main, onError), b.steps, b.edges);
    }

    private List<StepNode> sequence(YNode node, String parentKey) {
        List<StepNode> out = new ArrayList<>();
        if (!(node instanceof YNode.Sequence seq)) {
            return out;
        }
        for (YNode item : seq.items()) {
            if (item instanceof YNode.Mapping m) {
                StepNode s = step(m, parentKey);
                if (s != null) {
                    out.add(s);
                }
            } else {
                diags.add(Diagnostics.of(Rule.E103, "A step must be a mapping", item.location(), flow.id));
            }
        }
        return out;
    }

    private StepNode step(YNode.Mapping m, String parentKey) {
        String localId = m.text("id");
        if (localId == null) {
            diags.add(Diagnostics.of(Rule.E012, "Step has no id", m.location(), flow.id));
            return null;
        }
        if (!Ids.isLocalId(localId)) {
            diags.add(Diagnostics.of(Rule.E011, "Malformed step id '" + localId + "'; use lowercase words joined by '-'",
                m.get("id").location(), flow.id));
            return null;
        }
        String key = flow.key + "/" + localId;
        String id = flow.id + "/" + localId;
        if (steps.containsKey(key)) {
            diags.add(Diagnostics.of(Rule.E061, "Duplicate step id '" + localId + "' in flow " + flow.id,
                m.get("id").location(), id, List.of(steps.get(key).origins.get(0).location()), List.of()));
            return null;
        }
        String typeText = m.text("type");
        StepType type = typeText == null ? StepType.ACTION : StepType.parse(typeText).orElse(null);
        if (type == null) {
            diags.add(Diagnostics.of(Rule.E060, "Unknown step type '" + typeText + "'", m.get("type").location(), id));
            type = StepType.ACTION;
        }
        Origin origin = Origin.document(m.location());
        if (flow.origins.size() > 0 && flow.fieldOrigins.get("steps") != null) {
            Origin fo = flow.fieldOrigins.get("steps");
            origin = new Origin(fo.kind(), m.location(), fo.javaElement(), fo.app());
        }
        Draft d = new Draft(Kind.STEP, id, key, flow.app);
        d.flowKey = flow.key;
        d.origins.add(origin);
        for (Map.Entry<String, YNode> e : m.entries().entrySet()) {
            String f = e.getKey();
            if (!type.allows(f)) {
                diags.add(Diagnostics.of(Rule.E066, "Field '" + f + "' is not allowed for " + type + " steps",
                    m.keyLocation(f), id));
                continue;
            }
            if (STRUCTURAL.contains(f)) {
                continue;
            }
            if (f.equals("errors")) {
                d.put(f, stripThen(e.getValue()), new Origin(origin.kind(), m.keyLocation(f), null, origin.app()));
                continue;
            }
            d.put(f, e.getValue(), new Origin(origin.kind(), m.keyLocation(f), null, origin.app()));
        }
        if (!d.fields.containsKey("type")) {
            d.put("type", YNode.Scalar.string(type.name(), m.location()), origin);
        }
        steps.put(key, d);
        order.put(key, order.size());
        types.put(key, type);
        checkFields(type, m, id);

        List<StepNode.Block> blocks = new ArrayList<>();
        switch (type) {
            case DECISION -> {
                int count = 0;
                if (m.get("branches") instanceof YNode.Sequence seq) {
                    for (YNode b : seq.items()) {
                        if (b instanceof YNode.Mapping bm) {
                            blocks.add(new StepNode.Block(StepNode.Role.BRANCH, bm.text("when"), sequence(bm.get("steps"), key)));
                            count++;
                        }
                    }
                }
                if (m.get("otherwise") != null) {
                    blocks.add(new StepNode.Block(StepNode.Role.OTHERWISE, "otherwise", sequence(m.get("otherwise"), key)));
                    count++;
                }
                if (count < 2) {
                    diags.add(Diagnostics.of(Rule.E063, "Decision '" + localId + "' needs at least two branches (counting otherwise)",
                        m.location(), id));
                }
            }
            case PARALLEL -> {
                int count = 0;
                if (m.get("branches") instanceof YNode.Sequence seq) {
                    for (YNode b : seq.items()) {
                        if (b instanceof YNode.Mapping bm) {
                            blocks.add(new StepNode.Block(StepNode.Role.BRANCH, bm.text("name"), sequence(bm.get("steps"), key)));
                            count++;
                        }
                    }
                }
                if (count < 2) {
                    diags.add(Diagnostics.of(Rule.E064, "Parallel '" + localId + "' needs at least two branches", m.location(), id));
                }
            }
            case RETRY, COMPENSATE -> blocks.add(new StepNode.Block(StepNode.Role.BODY, null, sequence(m.get("steps"), key)));
            case WAIT -> {
                if (m.get("onTimeout") != null) {
                    blocks.add(new StepNode.Block(StepNode.Role.ON_TIMEOUT, "timeout", sequence(m.get("onTimeout"), key)));
                }
            }
            default -> {
            }
        }
        if (m.get("errors") instanceof YNode.Sequence errs) {
            for (YNode e : errs.items()) {
                if (e instanceof YNode.Mapping em) {
                    blocks.add(new StepNode.Block(StepNode.Role.ERROR, em.text("on"), sequence(em.get("then"), key)));
                } else {
                    diags.add(Diagnostics.of(Rule.E103, "errors items must be { on, then }", e.location(), id));
                }
            }
        } else if (m.get("errors") != null) {
            diags.add(Diagnostics.of(Rule.E103, "'errors' must be a list of { on, then }", m.get("errors").location(), id));
        }
        if (type == StepType.GOTO) {
            gotos.add(d);
        }
        if (type == StepType.COMPENSATE) {
            compensates.add(d);
        }
        return new StepNode(key, type, blocks);
    }

    private static YNode stripThen(YNode errors) {
        if (!(errors instanceof YNode.Sequence seq)) {
            return errors;
        }
        List<YNode> items = new ArrayList<>();
        for (YNode e : seq.items()) {
            if (e instanceof YNode.Mapping m) {
                Map<String, YNode> entries = new LinkedHashMap<>(m.entries());
                entries.remove("then");
                items.add(new YNode.Mapping(entries, m.keyLocations(), m.location()));
            } else {
                items.add(e);
            }
        }
        return new YNode.Sequence(items, seq.location());
    }

    private void checkFields(StepType type, YNode.Mapping m, String id) {
        if (type != StepType.NOTE && m.text("name") == null) {
            diags.add(Diagnostics.of(Rule.E067, "Step '" + m.text("id") + "' needs a name", m.location(), id));
        }
        switch (type) {
            case CALL_FLOW -> require(m, "flow", type, id);
            case GOTO -> require(m, "target", type, id);
            case EVENT -> {
                require(m, "event", type, id);
                enumField(m, "direction", id, "PUBLISH", "CONSUME");
            }
            case PARALLEL -> enumField(m, "join", id, "ALL", "ANY", "NONE");
            case NOTE -> require(m, "text", type, id);
            default -> {
            }
        }
        if (m.get("source") != null) {
            SchemaCheck.source("source", m.get("source"), id, diags);
        }
        for (String f : List.of("realizedBy", "requirements", "decisions", "tags")) {
            YNode v = m.get(f);
            if (v != null && !(v instanceof YNode.Sequence)) {
                diags.add(Diagnostics.of(Rule.E103, "'" + f + "' must be a list", v.location(), id));
            }
        }
        for (String f : List.of("inputs", "outputs", "policy")) {
            YNode v = m.get(f);
            if (v != null && !(v instanceof YNode.Mapping)) {
                diags.add(Diagnostics.of(Rule.E103, "'" + f + "' must be a mapping", v.location(), id));
            }
        }
        YNode async = m.get("async");
        if (async != null && !(async instanceof YNode.Scalar s && s.type() == YNode.ScalarType.BOOL)) {
            diags.add(Diagnostics.of(Rule.E103, "'async' must be true or false", async.location(), id));
        }
    }

    private void require(YNode.Mapping m, String field, StepType type, String id) {
        if (m.text(field) == null) {
            diags.add(Diagnostics.of(Rule.E067, type + " step '" + m.text("id") + "' needs '" + field + "'", m.location(), id));
        }
    }

    private void enumField(YNode.Mapping m, String field, String id, String... values) {
        String v = m.text(field);
        if (v != null && !List.of(values).contains(v)) {
            diags.add(Diagnostics.of(Rule.E107, "'" + v + "' is not a valid " + field + "; expected one of "
                + String.join(", ", values), m.get(field).location(), id));
        }
    }

    /** Merges @EVL.Step links into existing steps or appends new top-level steps. */
    private List<StepNode> annotated(List<StepNode> main, List<MappedAnnotations.Step> annotated) {
        if (annotated.isEmpty()) {
            return main;
        }
        List<StepNode> out = new ArrayList<>(main);
        for (MappedAnnotations.Step a : annotated) {
            Origin origin = Origin.annotation(a.location(), a.source());
            if (a.step() == null || !Ids.isLocalId(a.step())) {
                diags.add(Diagnostics.of(Rule.E011, "Malformed step id '" + a.step() + "'", a.location(), flow.id));
                continue;
            }
            String key = flow.key + "/" + a.step();
            Draft existing = steps.get(key);
            YNode src = YNode.fromPlain(Map.of("ref", a.source()), a.location());
            if (existing != null) {
                String ys = existing.fields.get("source") == null ? null : SchemaCheck.sourceText(existing.fields.get("source"));
                if (ys != null && !Merger.sameSource(ys, a.source())) {
                    diags.add(Diagnostics.of(Rule.E020, "Step '" + existing.id + "' is annotated on " + a.source()
                            + " but YAML sets source " + ys, existing.fields.get("source").location(), existing.id,
                        List.of(a.location()), List.of()));
                } else if (ys == null) {
                    existing.put("source", src, origin);
                }
                existing.origins.add(0, origin);
                continue;
            }
            StepType type = StepType.parse(a.type()).orElse(StepType.ACTION);
            Draft d = new Draft(Kind.STEP, flow.id + "/" + a.step(), key, flow.app);
            d.flowKey = flow.key;
            d.origins.add(origin);
            d.put("id", YNode.Scalar.string(a.step(), a.location()), origin);
            d.put("type", YNode.Scalar.string(type.name(), a.location()), origin);
            d.put("name", YNode.Scalar.string(a.name() != null ? a.name()
                : io.github.ilyaslabs.evl.core.parse.AnnotationMapper.humanize(a.step()), a.location()), origin);
            if (a.description() != null) {
                d.put("description", YNode.Scalar.string(a.description(), a.location()), origin);
            }
            d.put("source", src, origin);
            steps.put(key, d);
            order.put(key, order.size());
            types.put(key, type);
            out.add(new StepNode(key, type, List.of()));
            diags.add(Diagnostics.of(Rule.W060, "Step '" + a.step() + "' of flow " + flow.id
                + " is declared only by @EVL.Step; its position follows source order. Declare the step order in YAML.",
                a.location(), d.id));
        }
        return out;
    }

    /** Containment, sequencing and branch edges; reachability warnings. */
    private void control(List<StepNode> seq, String parentKey, boolean top) {
        StepNode prev = null;
        boolean unreachableReported = false;
        boolean terminated = false;
        for (StepNode s : seq) {
            edges.add(edge(parentKey, s.key(), EdgeType.CONTAINS, null));
            if (terminated && !unreachableReported) {
                diags.add(Diagnostics.of(Rule.W066, "Step '" + local(s.key()) + "' is unreachable: it follows a step that ends the sequence",
                    loc(s.key()), steps.get(s.key()).id));
                unreachableReported = true;
            }
            if (prev != null && !terminated) {
                edges.add(edge(prev.key(), s.key(), EdgeType.CONTROL_NEXT, null));
            }
            for (StepNode.Block b : s.blocks()) {
                control(b.steps(), s.key(), false);
                if (!b.steps().isEmpty()) {
                    EdgeType t = b.role() == StepNode.Role.ERROR ? EdgeType.ERROR : EdgeType.BRANCH;
                    if (b.role() != StepNode.Role.BODY) {
                        edges.add(edge(s.key(), b.steps().get(0).key(), t, b.label()));
                    }
                }
            }
            if (ends(s)) {
                terminated = true;
            }
            prev = s;
        }
    }

    private boolean ends(StepNode s) {
        if (s.type().terminates()) {
            return true;
        }
        return s.type() == StepType.GOTO && steps.get(s.key()).text("when") == null;
    }

    private void links() {
        for (Draft g : gotos) {
            String target = g.text("target");
            if (target == null) {
                continue;
            }
            String tk = flow.key + "/" + target;
            if (!steps.containsKey(tk)) {
                diags.add(Diagnostics.of(Rule.E062, "GOTO target '" + target + "' is not a step of flow " + flow.id,
                    g.fields.get("target").location(), g.id));
                continue;
            }
            edges.add(edge(g.key, tk, EdgeType.GOTO, g.text("when")));
            if (g.text("when") == null && order.get(tk) <= order.get(g.key)) {
                diags.add(Diagnostics.of(Rule.W067, "GOTO '" + local(g.key) + "' jumps back to '" + target
                    + "' without a when condition, which loops forever", g.fields.get("target").location(), g.id));
            }
        }
        for (Draft c : compensates) {
            YNode f = c.fields.get("for");
            List<String> targets = new ArrayList<>();
            if (f instanceof YNode.Scalar s && s.text() != null) {
                targets.add(s.text());
            } else if (f instanceof YNode.Sequence seq) {
                for (YNode n : seq.items()) {
                    if (n instanceof YNode.Scalar s && s.text() != null) {
                        targets.add(s.text());
                    }
                }
            }
            for (String t : targets) {
                String tk = flow.key + "/" + t;
                if (!steps.containsKey(tk)) {
                    diags.add(Diagnostics.of(Rule.E025, "COMPENSATE refers to unknown step '" + t + "' of flow " + flow.id,
                        f.location(), c.id));
                } else {
                    edges.add(edge(c.key, tk, EdgeType.COMPENSATES, null));
                }
            }
        }
    }

    private Edge edge(String from, String to, EdgeType type, String label) {
        return new Edge(from, to, type, Evidence.DECLARED, label, null);
    }

    private Location loc(String key) {
        return steps.get(key).origins.get(0).location();
    }

    private static String local(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }
}
