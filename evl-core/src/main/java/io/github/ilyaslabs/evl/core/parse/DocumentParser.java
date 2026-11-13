package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parses {@code *.evl.yaml} documents into raw declarations (spec 5.1). */
public final class DocumentParser {

    private DocumentParser() {
    }

    public static ParsedDocument parse(String file, String content) {
        YamlReader.Result read = YamlReader.read(file, content);
        List<Diagnostic> diags = new ArrayList<>(read.diagnostics());
        List<RawDecl> decls = new ArrayList<>();
        Map<String, Object> ext = new LinkedHashMap<>();
        String version = null;
        Location versionAt = Location.of(file);
        if (read.root().isEmpty()) {
            return new ParsedDocument(file, null, versionAt, decls, ext, diags);
        }
        if (!(read.root().get() instanceof YNode.Mapping root)) {
            diags.add(Diagnostics.of(Rule.E103, "A document must be a mapping of section keys (flows, components, ...)",
                read.root().get().location()));
            return new ParsedDocument(file, null, versionAt, decls, ext, diags);
        }
        for (Map.Entry<String, YNode> e : root.entries().entrySet()) {
            String key = e.getKey();
            YNode value = e.getValue();
            if (key.equals("evl")) {
                if (value instanceof YNode.Scalar s && Versions.isValid(s.text())) {
                    version = s.text();
                    versionAt = s.location();
                } else {
                    diags.add(Diagnostics.of(Rule.E103, "'evl' must be a version like \"1.0\"", value.location()));
                }
                continue;
            }
            if (key.startsWith("x-")) {
                ext.put(key, value.toPlain());
                continue;
            }
            Optional<Kind> kind = Kind.bySection(key);
            if (kind.isEmpty() || kind.get() == Kind.APPLICATION) {
                String hint = kind.isPresent() ? " (the application is declared in evl.yaml)" : "";
                diags.add(Diagnostics.of(Rule.E102, "Unknown section '" + key + "'" + hint, root.keyLocation(key)));
                continue;
            }
            if (!(value instanceof YNode.Sequence seq)) {
                if (!(value instanceof YNode.Scalar s && s.type() == YNode.ScalarType.NULL)) {
                    diags.add(Diagnostics.of(Rule.E103, "Section '" + key + "' must be a list", value.location()));
                }
                continue;
            }
            for (YNode item : seq.items()) {
                if (!(item instanceof YNode.Mapping m)) {
                    diags.add(Diagnostics.of(Rule.E103, "Items of '" + key + "' must be mappings", item.location()));
                    continue;
                }
                decls.add(element(kind.get(), m, diags).orElse(null));
            }
        }
        decls.removeIf(d -> d == null);
        return new ParsedDocument(file, version, versionAt, decls, ext, diags);
    }

    /** Parses one element mapping; empty when it has no usable id. */
    static Optional<RawDecl> element(Kind kind, YNode.Mapping m, List<Diagnostic> diags) {
        String id = m.get("id") instanceof YNode.Scalar s ? s.text() : null;
        if (id == null && kind.requiresId()) {
            diags.add(Diagnostics.of(Rule.E012, kind.displayName() + " has no id", m.location()));
        }
        if (id != null) {
            SchemaCheck.id(id, m.get("id"), id, diags);
        }
        SchemaCheck.element(kind, m, id, diags);
        if (id == null && kind.requiresId()) {
            return Optional.empty();
        }
        return Optional.of(new RawDecl(kind, id, m, Origin.document(m.location()), false));
    }
}
