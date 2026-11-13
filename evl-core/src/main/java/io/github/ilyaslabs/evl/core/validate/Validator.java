package io.github.ilyaslabs.evl.core.validate;

import io.github.ilyaslabs.evl.core.api.EvlGraph;
import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Edge;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.FlowStructure;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.model.StepNode;
import io.github.ilyaslabs.evl.core.resolve.Packages;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import io.github.ilyaslabs.evl.core.spec.StepType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Graph-level rules that need the resolved graph (spec 6.7, 7.2). */
public final class Validator {

    private static final Set<String> NOT_SCANNED = Set.of("id", "source", "entryPoint", "url", "see", "packages", "type");

    private Validator() {
    }

    public static List<Diagnostic> validate(EvlGraph g) {
        List<Diagnostic> out = new ArrayList<>();
        for (Element e : g.elements()) {
            if (e.isLibrary()) {
                continue;
            }
            required(e, out);
            secrets(e, out);
            if (e.kind() == Kind.CONFIG_PROPERTY && e.flag("sensitive") && e.string("defaultValue").isPresent()) {
                out.add(Diagnostics.of(Rule.E070, "Sensitive property '" + e.string("key").orElse(e.id())
                    + "' must not declare a default value", e.locationOf("defaultValue"), e.id()));
            }
            modulePackages(g, e, out);
        }
        for (FlowStructure f : g.flows().values()) {
            Element flow = g.element(f.flowKey()).orElse(null);
            if (flow == null || flow.isLibrary()) {
                continue;
            }
            callInputs(g, f, out);
            if ("BUSINESS".equals(flow.string("category").orElse(null))) {
                f.forEach(s -> {
                    Element step = g.element(s.key()).orElse(null);
                    if (step != null && s.type() != StepType.NOTE && s.type() != StepType.DECISION
                        && s.type() != StepType.PARALLEL && step.source() == null && step.strings("realizedBy").isEmpty()) {
                        out.add(Diagnostics.of(Rule.I030, "Business step '" + step.name() + "' has neither realizedBy nor source",
                            step.location(), step.id()));
                    }
                });
            }
        }
        recursion(g, out);
        return out;
    }

    private static void required(Element e, List<Diagnostic> out) {
        boolean annotated = e.origins().stream().anyMatch(o -> o.kind() == OriginKind.ANNOTATION);
        boolean yaml = e.origins().stream().anyMatch(o -> o.kind() == OriginKind.DOCUMENT);
        if (e.kind() == Kind.FLOW && e.string("category").isEmpty()) {
            if (annotated) {
                out.add(Diagnostics.of(Rule.W061, "Flow '" + e.id() + "' has no category; APPLICATION is assumed",
                    e.location(), e.id()));
            } else {
                out.add(Diagnostics.of(Rule.E106, "Flow '" + e.id() + "' needs a category (BUSINESS, APPLICATION, ...)",
                    e.location(), e.id()));
            }
        }
        if (yaml && !annotated && e.string("name").isEmpty() && e.kind() != Kind.DEPENDENCY && e.kind() != Kind.STEP
            && e.kind() != Kind.CONFIG_PROPERTY) {
            out.add(Diagnostics.of(Rule.E106, e.kind().displayName() + " '" + e.id() + "' needs a name", e.location(), e.id()));
        }
    }

    private static void secrets(Element e, List<Diagnostic> out) {
        for (Map.Entry<String, Object> f : e.fields().entrySet()) {
            if (NOT_SCANNED.contains(f.getKey())) {
                continue;
            }
            List<String> texts = new ArrayList<>();
            collect(f.getValue(), texts);
            for (String t : texts) {
                var hit = SecretScanner.scan(t);
                if (hit.isPresent()) {
                    out.add(Diagnostics.of(Rule.E071, "Field '" + f.getKey() + "' looks like it contains " + hit.get()
                        + "; never put secrets in EVL documentation", e.locationOf(f.getKey()), e.id()));
                    break;
                }
            }
        }
    }

    private static void collect(Object v, List<String> out) {
        if (v instanceof String s) {
            out.add(s);
        } else if (v instanceof List<?> l) {
            l.forEach(i -> collect(i, out));
        } else if (v instanceof Map<?, ?> m) {
            m.forEach((k, i) -> {
                if (!"source".equals(k) && !"ref".equals(k)) {
                    collect(i, out);
                }
            });
        }
    }

    private static void modulePackages(EvlGraph g, Element e, List<Diagnostic> out) {
        if (e.kind() == Kind.MODULE || e.kind() == Kind.STEP || e.source() == null || !e.source().isCode()) {
            return;
        }
        String pkg = Packages.packageOf(e.source());
        for (Edge c : g.incoming(e.key(), EdgeType.CONTAINS)) {
            Element parent = g.element(c.from()).orElse(null);
            if (parent == null || parent.kind() != Kind.MODULE) {
                continue;
            }
            List<String> globs = parent.strings("packages");
            if (!globs.isEmpty() && globs.stream().noneMatch(glob -> Packages.matches(glob, pkg))) {
                out.add(Diagnostics.of(Rule.W080, e.kind().displayName() + " '" + e.id() + "' lives in " + pkg
                    + ", outside the packages of module " + parent.id() + " (" + String.join(", ", globs) + ")",
                    e.locationOf("source"), e.id()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void callInputs(EvlGraph g, FlowStructure f, List<Diagnostic> out) {
        f.forEach(s -> {
            if (s.type() != StepType.CALL_FLOW) {
                return;
            }
            Element step = g.element(s.key()).orElse(null);
            if (step == null || !(step.fields().get("inputs") instanceof Map<?, ?> inputs)) {
                return;
            }
            for (Edge c : g.outgoing(s.key(), EdgeType.CALLS_FLOW)) {
                Element child = g.element(c.to()).orElse(null);
                if (child == null || !(child.fields().get("inputs") instanceof List<?> declared)) {
                    continue;
                }
                Set<String> names = new HashSet<>();
                for (Object o : declared) {
                    if (o instanceof Map<?, ?> m && m.get("name") != null) {
                        names.add(String.valueOf(m.get("name")));
                    }
                }
                for (Object k : ((Map<Object, Object>) inputs).keySet()) {
                    if (!names.contains(String.valueOf(k))) {
                        out.add(Diagnostics.of(Rule.E068, "Flow " + child.id() + " has no input '" + k + "' (declared: "
                            + String.join(", ", new java.util.TreeSet<>(names)) + ")", step.locationOf("inputs"), step.id()));
                    }
                }
            }
        });
    }

    /** Flow call graph cycles (W021): reported once per cycle, on the step that closes it. */
    private static void recursion(EvlGraph g, List<Diagnostic> out) {
        Map<String, List<Edge>> calls = new LinkedHashMap<>();
        for (FlowStructure f : g.flows().values()) {
            List<Edge> list = new ArrayList<>();
            for (StepNode s : f.all()) {
                list.addAll(g.outgoing(s.key(), EdgeType.CALLS_FLOW));
            }
            calls.put(f.flowKey(), list);
        }
        Set<String> done = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (String start : calls.keySet()) {
            dfs(start, calls, new LinkedHashSet<>(), done, reported, g, out);
        }
    }

    private static void dfs(String flow, Map<String, List<Edge>> calls, LinkedHashSet<String> stack, Set<String> done,
                            Set<String> reported, EvlGraph g, List<Diagnostic> out) {
        if (done.contains(flow)) {
            return;
        }
        stack.add(flow);
        for (Edge e : calls.getOrDefault(flow, List.of())) {
            if (stack.contains(e.to())) {
                List<String> cycle = new ArrayList<>();
                boolean in = false;
                for (String s : stack) {
                    in |= s.equals(e.to());
                    if (in) {
                        cycle.add(s);
                    }
                }
                cycle.add(e.to());
                String sig = String.join(">", new java.util.TreeSet<>(cycle));
                Element step = g.element(e.from()).orElse(null);
                if (reported.add(sig) && step != null && !step.isLibrary()) {
                    Location loc = step.locationOf("flow");
                    out.add(Diagnostics.of(Rule.W021, "Recursive flow call: " + String.join(" → ", cycle), loc, step.id()));
                }
            } else {
                dfs(e.to(), calls, stack, done, reported, g, out);
            }
        }
        stack.remove(flow);
        done.add(flow);
    }
}
