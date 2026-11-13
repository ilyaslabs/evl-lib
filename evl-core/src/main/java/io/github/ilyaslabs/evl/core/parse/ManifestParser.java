package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Ids;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses {@code evl.yaml} and library {@code manifest.json} files. */
public final class ManifestParser {

    private static final Set<String> KEYS = Set.of("evl", "id", "name", "description", "kind", "documents", "sources",
        "exclude", "dependencies", "settings", "rules", "artifact", "domain", "tags", "doc", "see", "requirements",
        "decisions", "source", "concurrency", "performance", "security", "failure");

    private ManifestParser() {
    }

    /** Result: the manifest (absent when unusable), the application declaration and diagnostics. */
    public record Result(Optional<Manifest> manifest, Optional<RawDecl> application, List<Diagnostic> diagnostics) {
    }

    public static Result parse(String file, String content) {
        YamlReader.Result read = YamlReader.read(file, content);
        List<Diagnostic> diags = new ArrayList<>(read.diagnostics());
        if (read.root().isEmpty()) {
            if (diags.isEmpty()) {
                diags.add(Diagnostics.of(Rule.E001, "evl.yaml is empty; it needs evl, id and name", Location.of(file)));
            }
            return new Result(Optional.empty(), Optional.empty(), diags);
        }
        if (!(read.root().get() instanceof YNode.Mapping m)) {
            diags.add(Diagnostics.of(Rule.E001, "evl.yaml must be a mapping", read.root().get().location()));
            return new Result(Optional.empty(), Optional.empty(), diags);
        }
        for (String key : m.entries().keySet()) {
            if (!KEYS.contains(key) && !key.startsWith("x-")) {
                diags.add(Diagnostics.of(Rule.E102, "Unknown manifest field '" + key + "'", m.keyLocation(key)));
            }
        }
        String version = text(m, "evl");
        String id = text(m, "id");
        String name = text(m, "name");
        boolean ok = true;
        if (version == null) {
            diags.add(Diagnostics.of(Rule.E001, "evl.yaml is missing 'evl' (the spec version, e.g. \"1.0\")", m.location()));
            ok = false;
        } else if (!Versions.isValid(version)) {
            diags.add(Diagnostics.of(Rule.E001, "'evl' must be MAJOR.MINOR, e.g. \"1.0\"", m.get("evl").location()));
            ok = false;
        } else if (Versions.major(version) != Versions.MAJOR) {
            diags.add(Diagnostics.of(Rule.E052, "Spec version " + version + " is not supported (this tool supports "
                + Versions.MAJOR + ".x)", m.get("evl").location()));
        } else if (Versions.minor(version) > Versions.MINOR) {
            diags.add(Diagnostics.of(Rule.W051, "Spec version " + version + " is newer than " + Versions.SPEC
                + "; unknown fields are kept as metadata", m.get("evl").location()));
        }
        if (id == null) {
            diags.add(Diagnostics.of(Rule.E001, "evl.yaml is missing 'id' (the EVL application id)", m.location()));
            ok = false;
        } else if (!Ids.isAppId(id)) {
            diags.add(Diagnostics.of(Rule.E001, "Application id '" + id + "' must be lowercase words joined by '-', e.g. payments-sdk",
                m.get("id").location()));
            ok = false;
        }
        if (name == null) {
            diags.add(Diagnostics.of(Rule.E001, "evl.yaml is missing 'name'", m.location()));
            ok = false;
        }
        String kind = text(m, "kind");
        if (kind == null) {
            kind = "APPLICATION";
        } else if (!kind.equals("APPLICATION") && !kind.equals("LIBRARY")) {
            diags.add(Diagnostics.of(Rule.E001, "'kind' must be APPLICATION or LIBRARY", m.get("kind").location()));
            kind = "APPLICATION";
        }
        Map<String, String> rules = new LinkedHashMap<>();
        if (m.get("rules") instanceof YNode.Mapping rm) {
            rm.entries().forEach((k, v) -> {
                if (v instanceof YNode.Scalar s && s.text() != null) {
                    rules.put(k, s.text());
                }
            });
        } else if (m.get("rules") != null) {
            diags.add(Diagnostics.of(Rule.E103, "'rules' must be a mapping of rule code to level", m.get("rules").location()));
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        if (m.get("settings") instanceof YNode.Mapping sm) {
            sm.entries().forEach((k, v) -> settings.put(k, v.toPlain()));
        }
        String artifact = null;
        if (m.get("artifact") instanceof YNode.Mapping am) {
            artifact = text(am, "group") + ":" + text(am, "name") + ":" + text(am, "version");
        } else {
            artifact = text(m, "artifact");
        }
        if (!ok) {
            return new Result(Optional.empty(), Optional.empty(), diags);
        }
        List<String> docs = strings(m, "documents", diags);
        Manifest manifest = new Manifest(version, id, name, orEmpty(text(m, "description")), kind,
            docs.isEmpty() ? Manifest.DEFAULT_DOCUMENTS : docs, strings(m, "sources", diags), strings(m, "exclude", diags),
            strings(m, "dependencies", diags), settings, rules, artifact, false, m.location());

        // The application element: manifest fields that are element fields.
        Map<String, YNode> body = new LinkedHashMap<>();
        Map<String, Location> keys = new LinkedHashMap<>();
        for (Map.Entry<String, YNode> e : m.entries().entrySet()) {
            String k = e.getKey();
            if (Kind.APPLICATION.fields().containsKey(k) || k.startsWith("x-")) {
                body.put(k, e.getValue());
                keys.put(k, m.keyLocation(k));
            }
        }
        YNode.Mapping appBody = new YNode.Mapping(body, keys, m.location());
        SchemaCheck.element(Kind.APPLICATION, appBody, id, diags);
        RawDecl app = new RawDecl(Kind.APPLICATION, id, appBody,
            new Origin(OriginKind.MANIFEST, m.location(), null, null), false);
        return new Result(Optional.of(manifest), Optional.of(app), diags);
    }

    private static String text(YNode.Mapping m, String key) {
        return m.get(key) instanceof YNode.Scalar s ? s.text() : null;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> strings(YNode.Mapping m, String key, List<Diagnostic> diags) {
        YNode n = m.get(key);
        if (n == null) {
            return List.of();
        }
        if (!(n instanceof YNode.Sequence seq)) {
            diags.add(Diagnostics.of(Rule.E103, "'" + key + "' must be a list of strings", n.location()));
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (YNode item : seq.items()) {
            if (item instanceof YNode.Scalar s && s.text() != null) {
                out.add(s.text());
            } else {
                diags.add(Diagnostics.of(Rule.E103, "'" + key + "' items must be strings", item.location()));
            }
        }
        return out;
    }
}
