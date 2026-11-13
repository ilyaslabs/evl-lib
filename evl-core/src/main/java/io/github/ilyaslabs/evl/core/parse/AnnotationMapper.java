package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Ids;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord.ElementKind;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps {@link AnnotationRecord}s to EVL declarations (spec chapter 4). This is the only place that
 * knows how annotation attributes translate to document fields, so the processor, KSP, the CLI
 * scanner and the IDE all produce identical graphs.
 */
public final class AnnotationMapper {

    private static final Map<String, Kind> ELEMENT_ANNOTATIONS = Map.ofEntries(
        Map.entry("Application", Kind.APPLICATION), Map.entry("Module", Kind.MODULE),
        Map.entry("Component", Kind.COMPONENT), Map.entry("Interface", Kind.INTERFACE),
        Map.entry("Implementation", Kind.IMPLEMENTATION), Map.entry("Endpoint", Kind.ENDPOINT),
        Map.entry("Request", Kind.REQUEST), Map.entry("Response", Kind.RESPONSE),
        Map.entry("ErrorResponse", Kind.ERROR), Map.entry("DataModel", Kind.DATA_MODEL),
        Map.entry("Repository", Kind.REPOSITORY), Map.entry("Flow", Kind.FLOW), Map.entry("Event", Kind.EVENT),
        Map.entry("ExternalSystem", Kind.EXTERNAL_SYSTEM), Map.entry("Cache", Kind.CACHE),
        Map.entry("Infrastructure", Kind.INFRASTRUCTURE), Map.entry("Configuration", Kind.CONFIGURATION),
        Map.entry("ConfigProperty", Kind.CONFIG_PROPERTY), Map.entry("Decision", Kind.DECISION),
        Map.entry("Requirement", Kind.REQUIREMENT));

    /** Annotation attribute → document field renames per kind. */
    private static final Map<Kind, Map<String, String>> RENAMES = Map.of(
        Kind.ENDPOINT, Map.of("requestRef", "request", "responseRef", "response"),
        Kind.REQUIREMENT, Map.of("source", "ticket"));

    private static final Set<Kind> MODULE_INHERITING = Set.of(Kind.COMPONENT, Kind.INTERFACE, Kind.DATA_MODEL, Kind.FLOW);
    private static final Set<String> SUPPORTED = Set.of("Dependency", "Field", "Relationship", "Index", "Step", "Call",
        "Producer", "Consumer", "Doc");

    private AnnotationMapper() {
    }

    private static final class Decl {
        final Kind kind;
        final AnnotationRecord rec;
        final Map<String, Object> body = new LinkedHashMap<>();
        String id;
        boolean derived;

        Decl(Kind kind, AnnotationRecord rec) {
            this.kind = kind;
            this.rec = rec;
        }
    }

    public static MappedAnnotations map(List<AnnotationRecord> input) {
        List<AnnotationRecord> records = new ArrayList<>(input);
        records.sort(Comparator.comparing((AnnotationRecord r) -> r.location().file())
            .thenComparingInt(r -> r.location().line()).thenComparingInt(r -> r.location().column()));
        List<Diagnostic> diags = new ArrayList<>();
        List<Decl> decls = new ArrayList<>();
        Map<String, List<Decl>> byElement = new HashMap<>();

        // 1. Package modules first, outermost package first, so ids and parents can be derived.
        Map<String, Decl> packageModules = new HashMap<>();
        List<AnnotationRecord> modules = records.stream()
            .filter(r -> r.annotation().equals("Module") && r.elementKind() == ElementKind.PACKAGE)
            .sorted(Comparator.comparingInt(r -> r.packageName().length())).toList();
        for (AnnotationRecord r : modules) {
            Decl d = new Decl(Kind.MODULE, r);
            Decl parent = nearestModule(packageModules, parentPackage(r.packageName()));
            fill(d, r, parent == null ? null : parent.id, diags);
            if (!d.body.containsKey("parent") && parent != null) {
                d.body.put("parent", parent.id);
            }
            d.body.putIfAbsent("packages", List.of(r.packageName() + ".**"));
            packageModules.put(r.packageName(), d);
            decls.add(d);
            byElement.computeIfAbsent(r.element(), k -> new ArrayList<>()).add(d);
        }

        // 2. All other element annotations.
        for (AnnotationRecord r : records) {
            Kind kind = ELEMENT_ANNOTATIONS.get(r.annotation());
            if (kind == null || (kind == Kind.MODULE && r.elementKind() == ElementKind.PACKAGE)) {
                continue;
            }
            if (kind == Kind.ERROR && r.elementKind() == ElementKind.METHOD && isLinkOnly(r)) {
                continue; // handled as an endpoint error link below
            }
            Decl module = nearestModule(packageModules, r.packageName());
            Decl d = new Decl(kind, r);
            fill(d, r, module == null ? null : module.id, diags);
            if (MODULE_INHERITING.contains(kind) && !d.body.containsKey("module") && module != null) {
                d.body.put("module", module.id);
            }
            decls.add(d);
            byElement.computeIfAbsent(r.element(), k -> new ArrayList<>()).add(d);
        }

        // 3. Defaults that need other declarations: endpoint component and class-literal contracts.
        Map<String, Decl> byType = new HashMap<>();
        for (Decl d : decls) {
            if (d.rec.elementKind() == ElementKind.TYPE) {
                byType.putIfAbsent(d.rec.element() + "|" + d.kind, d);
            }
        }
        for (Decl d : decls) {
            if (d.kind == Kind.ENDPOINT) {
                if (!d.body.containsKey("component") && d.rec.typeName() != null) {
                    Decl c = byType.get(d.rec.typeName() + "|" + Kind.COMPONENT);
                    if (c != null && c != d) {
                        d.body.put("component", c.id);
                    }
                }
                contract(d, "requestType", "request", Kind.REQUEST, byType);
                contract(d, "responseType", "response", Kind.RESPONSE, byType);
            }
        }

        // 4. Attribute annotations that attach to an owner.
        List<MappedAnnotations.Step> steps = new ArrayList<>();
        List<MappedAnnotations.Call> calls = new ArrayList<>();
        List<MappedAnnotations.Contribution> contributions = new ArrayList<>();
        int order = 0;
        for (AnnotationRecord r : records) {
            String a = r.annotation();
            if (ELEMENT_ANNOTATIONS.containsKey(a)) {
                if (a.equals("ErrorResponse") && r.elementKind() == ElementKind.METHOD) {
                    Decl ep = first(byElement.get(r.element()), Kind.ENDPOINT);
                    String errorId = r.string("id");
                    if (isLinkOnly(r)) {
                        if (ep == null || errorId == null) {
                            diags.add(Diagnostics.of(Rule.E106, "@EVL.ErrorResponse on a method needs an @EVL.Endpoint and an id",
                                r.location()));
                        } else {
                            appendList(ep.body, "errors", errorId);
                        }
                    } else if (ep != null) {
                        Decl err = first(byElement.get(r.element()), Kind.ERROR);
                        if (err != null) {
                            appendList(ep.body, "errors", err.id);
                        }
                    }
                }
                continue;
            }
            if (!SUPPORTED.contains(a)) {
                continue;
            }
            Decl owner = owner(byElement, r);
            switch (a) {
                case "Dependency" -> {
                    if (owner == null) {
                        diags.add(Diagnostics.of(Rule.E106, "@EVL.Dependency needs an EVL element on the same or an enclosing declaration",
                            r.location()));
                        continue;
                    }
                    Decl d = new Decl(Kind.DEPENDENCY, r);
                    d.body.put("from", owner.id);
                    copy(r.attributes(), d.body, Map.of("target", "to"));
                    d.body.putIfAbsent("direction", "OUTBOUND");
                    d.body.put("source", Map.of("ref", r.element()));
                    decls.add(d);
                }
                case "Field", "Relationship", "Index" -> {
                    Decl model = owner == null || owner.kind != Kind.DATA_MODEL ? ownerOfKind(byElement, r, Kind.DATA_MODEL) : owner;
                    if (model == null) {
                        diags.add(Diagnostics.of(Rule.W071, "@EVL." + a + " is not inside an @EVL.DataModel", r.location()));
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    switch (a) {
                        case "Field" -> {
                            item.put("name", r.string("name") != null ? r.string("name") : r.simpleName());
                            copy(r.attributes(), item, Map.of());
                            item.put("source", Map.of("ref", r.element()));
                            appendList(model.body, "fields", item);
                        }
                        case "Relationship" -> {
                            copy(r.attributes(), item, Map.of());
                            if (r.elementKind() == ElementKind.FIELD) {
                                item.put("field", r.simpleName());
                            }
                            appendList(model.body, "relationships", item);
                        }
                        default -> {
                            copy(r.attributes(), item, Map.of());
                            appendList(model.body, "indexes", item);
                        }
                    }
                }
                case "Step" -> steps.add(new MappedAnnotations.Step(r.string("flow"), r.string("step"),
                    r.string("type") == null ? "ACTION" : r.string("type"), r.string("name"), r.string("description"),
                    r.element(), r.location(), order++));
                case "Call" -> {
                    if (owner == null) {
                        diags.add(Diagnostics.of(Rule.E106, "@EVL.Call needs an EVL element on the same or an enclosing declaration",
                            r.location()));
                        continue;
                    }
                    calls.add(new MappedAnnotations.Call(owner.id, r.string("target"),
                        r.string("kind") == null ? "SYNC" : r.string("kind"), r.string("reason"), r.location()));
                }
                case "Producer", "Consumer" -> {
                    if (owner == null) {
                        diags.add(Diagnostics.of(Rule.E106, "@EVL." + a + " needs an EVL element on the same or an enclosing declaration",
                            r.location()));
                        continue;
                    }
                    if (a.equals("Producer")) {
                        contributions.add(new MappedAnnotations.Contribution(r.string("event"), "producers", owner.id,
                            r.location(), r.element()));
                    } else {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("ref", owner.id);
                        copy(r.attributes(), c, Map.of());
                        c.remove("event");
                        contributions.add(new MappedAnnotations.Contribution(r.string("event"), "consumers", c,
                            r.location(), r.element()));
                    }
                }
                case "Doc" -> {
                    List<Decl> targets = docTargets(byElement, r);
                    if (targets.isEmpty()) {
                        diags.add(Diagnostics.of(Rule.W071, "@EVL.Doc on " + r.element() + " is not attached to any EVL element",
                            r.location()));
                        continue;
                    }
                    for (Decl t : targets) {
                        r.attributes().forEach((k, v) -> {
                            String field = k.equals("value") ? "doc" : k;
                            if (!t.kind.fields().containsKey(field)) {
                                return;
                            }
                            if (v instanceof List<?> l) {
                                for (Object o : l) {
                                    appendList(t.body, field, o);
                                }
                            } else {
                                t.body.putIfAbsent(field, v);
                            }
                        });
                    }
                }
                default -> {
                }
            }
        }

        List<RawDecl> out = new ArrayList<>();
        for (Decl d : decls) {
            Origin origin = Origin.annotation(d.rec.location(), d.rec.element());
            YNode.Mapping body = (YNode.Mapping) YNode.fromPlain(d.body, d.rec.location());
            out.add(new RawDecl(d.kind, d.id, body, origin, d.derived));
        }
        return new MappedAnnotations(out, steps, calls, contributions, diags);
    }

    private static boolean isLinkOnly(AnnotationRecord r) {
        return r.attributes().keySet().stream().allMatch(k -> k.equals("id"));
    }

    private static void fill(Decl d, AnnotationRecord r, String modulePrefix, List<Diagnostic> diags) {
        Map<String, String> renames = RENAMES.getOrDefault(d.kind, Map.of());
        Map<String, String> all = new HashMap<>(renames);
        all.put("requestType", "x-requestType");
        all.put("responseType", "x-responseType");
        copy(r.attributes(), d.body, all);
        String id = r.string("id");
        if (d.kind == Kind.APPLICATION) {
            id = ""; // the merger assigns the application id from the manifest
        } else if (id == null) {
            String base = d.kind == Kind.CONFIG_PROPERTY && r.string("key") != null ? configId(r.string("key")) : null;
            id = base != null ? base : Ids.derive(d.kind == Kind.MODULE ? modulePrefix : prefixFor(d.kind, modulePrefix),
                r.simpleName());
            d.derived = true;
            diags.add(Diagnostics.of(Rule.W002, "Derived id '" + id + "' for @EVL." + r.annotation() + " on "
                + r.element() + "; set id explicitly so renaming the class does not change it", r.location(), id));
        }
        d.id = id;
        d.body.put("id", id);
        if (!d.body.containsKey("name") && d.kind != Kind.APPLICATION) {
            d.body.put("name", d.kind == Kind.CONFIG_PROPERTY && r.string("key") != null ? r.string("key")
                : humanize(r.simpleName()));
        }
        d.body.put("source", Map.of("ref", r.element()));
        if (d.kind == Kind.FLOW && r.elementKind() == ElementKind.METHOD) {
            d.body.put("entryPoint", Map.of("ref", r.element()));
        }
    }

    private static String prefixFor(Kind kind, String modulePrefix) {
        return modulePrefix;
    }

    public static String configId(String key) {
        StringBuilder sb = new StringBuilder("config");
        for (String seg : key.split("[.\\[\\]]")) {
            if (!seg.isEmpty()) {
                sb.append('.').append(Ids.derive(null, seg));
            }
        }
        return sb.toString();
    }

    private static void contract(Decl ep, String typeAttr, String field, Kind kind, Map<String, Decl> byType) {
        Object type = ep.body.remove("x-" + typeAttr);
        if (type == null || ep.body.containsKey(field)) {
            return;
        }
        Decl target = byType.get(type + "|" + kind);
        if (target == null) {
            target = byType.get(type + "|" + Kind.DATA_MODEL);
        }
        if (target != null) {
            ep.body.put(field, target.id);
        } else {
            ep.body.put("x-" + typeAttr, type);
        }
    }

    private static void copy(Map<String, Object> attrs, Map<String, Object> into, Map<String, String> renames) {
        attrs.forEach((k, v) -> {
            if ("UNSPECIFIED".equals(v) || (v instanceof String s && s.isEmpty())
                || (v instanceof List<?> l && l.isEmpty())) {
                return;
            }
            into.put(renames.getOrDefault(k, k), v);
        });
    }

    @SuppressWarnings("unchecked")
    private static void appendList(Map<String, Object> body, String field, Object value) {
        Object existing = body.get(field);
        List<Object> list = existing instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
        if (!list.contains(value)) {
            list.add(value);
        }
        body.put(field, list);
    }

    private static Decl first(List<Decl> list, Kind kind) {
        if (list == null) {
            return null;
        }
        for (Decl d : list) {
            if (kind == null || d.kind == kind) {
                return d;
            }
        }
        return null;
    }

    /** The element on this declaration, else on the enclosing type(s), else on enclosing packages. */
    private static Decl owner(Map<String, List<Decl>> byElement, AnnotationRecord r) {
        for (String e : chain(r)) {
            Decl d = first(byElement.get(e), null);
            if (d != null && d.kind != Kind.APPLICATION) {
                return d;
            }
        }
        return null;
    }

    private static Decl ownerOfKind(Map<String, List<Decl>> byElement, AnnotationRecord r, Kind kind) {
        for (String e : chain(r)) {
            Decl d = first(byElement.get(e), kind);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static List<Decl> docTargets(Map<String, List<Decl>> byElement, AnnotationRecord r) {
        for (String e : chain(r)) {
            List<Decl> l = byElement.get(e);
            if (l != null && !l.isEmpty()) {
                return l;
            }
        }
        return List.of();
    }

    /** The declaration itself, its enclosing types, then its packages from innermost to outermost. */
    static List<String> chain(AnnotationRecord r) {
        List<String> out = new ArrayList<>();
        out.add(r.element());
        String type = r.typeName();
        if (type != null) {
            if (!type.equals(r.element())) {
                out.add(type);
            }
            String t = type;
            int pkgLen = r.packageName().length();
            while (true) {
                int cut = Math.max(t.lastIndexOf('$'), t.lastIndexOf('.'));
                if (cut <= pkgLen || cut < 0) {
                    break;
                }
                t = t.substring(0, cut);
                out.add(t);
            }
        }
        String p = r.packageName();
        while (!p.isEmpty()) {
            String key = "package:" + p;
            if (!out.contains(key)) {
                out.add(key);
            }
            p = parentPackage(p);
        }
        return out;
    }

    private static Decl nearestModule(Map<String, Decl> packageModules, String pkg) {
        String p = pkg;
        while (p != null && !p.isEmpty()) {
            Decl d = packageModules.get(p);
            if (d != null) {
                return d;
            }
            p = parentPackage(p);
        }
        return null;
    }

    private static String parentPackage(String p) {
        int dot = p.lastIndexOf('.');
        return dot < 0 ? "" : p.substring(0, dot);
    }

    /** {@code OrderService} → {@code Order Service}, {@code placeOrder} → {@code Place Order}. */
    public static String humanize(String simpleName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (c == '_' || c == '-' || c == '$') {
                sb.append(' ');
                continue;
            }
            if (i > 0 && Character.isUpperCase(c) && (Character.isLowerCase(simpleName.charAt(i - 1))
                || (i + 1 < simpleName.length() && Character.isLowerCase(simpleName.charAt(i + 1))
                && Character.isUpperCase(simpleName.charAt(i - 1))))) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }
}
