package io.github.ilyaslabs.evl.core.resolve;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.parse.RawDecl;
import io.github.ilyaslabs.evl.core.parse.SchemaCheck;
import io.github.ilyaslabs.evl.core.parse.YNode;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Merges declarations of one application into drafts (spec 5.4). */
final class Merger {

    private Merger() {
    }

    /**
     * @param appId      the application id
     * @param library    whether keys get the {@code app:} prefix
     * @param manifest   the application declaration from the manifest (may be null)
     * @param docs       document declarations
     * @param anns       annotation declarations
     * @param libraries  ids of loaded libraries (for E024)
     */
    static Map<String, Draft> merge(String appId, boolean library, RawDecl manifest, List<RawDecl> docs,
                                    List<RawDecl> anns, Set<String> libraries, List<Diagnostic> diags) {
        Map<String, List<RawDecl>> docById = new LinkedHashMap<>();
        Map<String, List<RawDecl>> annById = new LinkedHashMap<>();
        List<RawDecl> dependencies = new ArrayList<>();
        Map<String, List<RawDecl>> order = new LinkedHashMap<>();
        if (manifest != null) {
            docById.computeIfAbsent(appId, k -> new ArrayList<>()).add(manifest);
            order.put(appId, null);
        }
        for (RawDecl d : anns) {
            if (d.kind() == Kind.DEPENDENCY) {
                dependencies.add(d);
                continue;
            }
            String id = d.kind() == Kind.APPLICATION ? appId : d.id();
            if (!validId(id, d, libraries, false, diags)) {
                continue;
            }
            annById.computeIfAbsent(id, k -> new ArrayList<>()).add(d);
            order.put(id, null);
        }
        for (RawDecl d : docs) {
            if (d.kind() == Kind.DEPENDENCY && d.id() == null) {
                dependencies.add(d);
                continue;
            }
            if (!validId(d.id(), d, libraries, true, diags)) {
                continue;
            }
            docById.computeIfAbsent(d.id(), k -> new ArrayList<>()).add(d);
            order.put(d.id(), null);
        }

        Map<String, Draft> out = new LinkedHashMap<>();
        for (String id : order.keySet()) {
            List<RawDecl> a = annById.getOrDefault(id, List.of());
            List<RawDecl> y = docById.getOrDefault(id, List.of());
            RawDecl a0 = duplicates(id, a, diags);
            RawDecl y0 = duplicates(id, y, diags);
            if (a0 != null && y0 != null && a0.kind() != y0.kind()) {
                diags.add(Diagnostics.of(Rule.E010, "Id '" + id + "' is used by a " + a0.kind().displayName().toLowerCase()
                        + " annotation and a " + y0.kind().displayName().toLowerCase() + " in YAML",
                    y0.origin().location(), id, List.of(a0.origin().location()), List.of()));
                a0 = null;
            }
            Kind kind = (y0 != null ? y0 : a0).kind();
            Draft d = new Draft(kind, id, key(appId, library, id), library ? appId : null);
            if (a0 != null) {
                apply(d, a0);
                d.derivedId = a0.derivedId();
            }
            if (y0 != null) {
                if (a0 != null) {
                    overrideChecks(d, a0, y0, diags);
                }
                apply(d, y0);
            }
            out.put(d.key, d);
        }

        Map<String, Integer> depCount = new LinkedHashMap<>();
        for (RawDecl dep : dependencies) {
            String from = text(dep.body(), "from");
            String to = text(dep.body(), "to");
            String base = "dependency(" + from + "->" + to + ")";
            int n = depCount.merge(base, 1, Integer::sum);
            String id = n == 1 ? base : base + "#" + n;
            Draft d = new Draft(Kind.DEPENDENCY, id, key(appId, library, id), library ? appId : null);
            apply(d, dep);
            out.put(d.key, d);
        }
        return out;
    }

    static String key(String appId, boolean library, String id) {
        return library ? appId + ":" + id : id;
    }

    private static boolean validId(String id, RawDecl d, Set<String> libraries, boolean doc, List<Diagnostic> diags) {
        if (id == null) {
            return false;
        }
        int colon = id.indexOf(':');
        if (colon >= 0) {
            String app = id.substring(0, colon);
            if (doc && libraries.contains(app)) {
                diags.add(Diagnostics.of(Rule.E024, "'" + id + "' belongs to library " + app
                    + "; library elements are read-only and can only be referenced", d.origin().location(), id));
            } else {
                diags.add(Diagnostics.of(Rule.E011, "Malformed id '" + id + "'; ids cannot contain ':'",
                    d.origin().location(), id));
            }
            return false;
        }
        return true;
    }

    private static RawDecl duplicates(String id, List<RawDecl> list, List<Diagnostic> diags) {
        if (list.isEmpty()) {
            return null;
        }
        RawDecl first = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            RawDecl dup = list.get(i);
            diags.add(Diagnostics.of(Rule.E010, "Duplicate id '" + id + "' (also declared at " + first.origin().location() + ")",
                dup.origin().location(), id, List.of(first.origin().location()), List.of()));
        }
        return first;
    }

    private static void apply(Draft d, RawDecl decl) {
        d.origins.add(decl.origin());
        for (Map.Entry<String, YNode> e : decl.body().entries().entrySet()) {
            Location keyLoc = decl.body().keyLocation(e.getKey());
            Origin o = new Origin(decl.origin().kind(), keyLoc, decl.origin().javaElement(), decl.origin().app());
            d.put(e.getKey(), e.getValue(), o);
        }
    }

    private static void overrideChecks(Draft d, RawDecl ann, RawDecl yaml, List<Diagnostic> diags) {
        String annSource = ann.origin().javaElement();
        YNode ySource = yaml.body().get("source");
        if (ySource != null && annSource != null) {
            String ys = SchemaCheck.sourceText(ySource);
            if (ys != null && !sameSource(annSource, ys)) {
                diags.add(Diagnostics.of(Rule.E020, "'" + d.id + "' is annotated on " + annSource
                        + " but YAML sets source " + ys + "; an id can only live in one place",
                    ySource.location(), d.id, List.of(ann.origin().location()), List.of()));
            }
        }
        Set<String> overridden = new TreeSet<>();
        for (Map.Entry<String, YNode> e : yaml.body().entries().entrySet()) {
            String f = e.getKey();
            if (f.equals("id") || f.equals("source")) {
                continue;
            }
            YNode a = ann.body().get(f);
            if (a != null && !Objects.equals(a.toPlain(), e.getValue().toPlain())) {
                overridden.add(f);
            }
        }
        if (!overridden.isEmpty()) {
            Location yLoc = yaml.origin().location();
            diags.add(Diagnostics.of(Rule.I020, "Overridden by " + yLoc + ": " + String.join(", ", overridden),
                ann.origin().location(), d.id, List.of(yLoc), List.of()));
        }
    }

    /** Whether two source references denote the same declaration (parameter types compared by simple name). */
    static boolean sameSource(String a, String b) {
        SourceRef ra = SourceRef.parse(a).orElse(null);
        SourceRef rb = SourceRef.parse(b).orElse(null);
        if (ra == null || rb == null) {
            return a.equals(b);
        }
        if (!ra.type().replace('$', '.').equals(rb.type().replace('$', '.')) || !Objects.equals(ra.member(), rb.member())) {
            return false;
        }
        if (ra.params() == null || rb.params() == null) {
            return true;
        }
        if (ra.params().size() != rb.params().size()) {
            return false;
        }
        for (int i = 0; i < ra.params().size(); i++) {
            if (!simple(ra.params().get(i)).equals(simple(rb.params().get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String simple(String t) {
        int dot = t.lastIndexOf('.', t.endsWith("...") ? t.length() - 4 : t.length());
        return dot < 0 ? t : t.substring(dot + 1);
    }

    private static String text(YNode.Mapping m, String key) {
        return m.get(key) instanceof YNode.Scalar s ? s.text() : null;
    }

    static boolean isAnnotation(Origin o) {
        return o.kind() == OriginKind.ANNOTATION;
    }
}
