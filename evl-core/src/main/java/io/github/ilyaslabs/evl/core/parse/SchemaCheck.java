package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Ids;
import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.spec.FieldSpec;
import io.github.ilyaslabs.evl.core.spec.Kind;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.List;
import java.util.Map;

/** Per-field shape checks shared by documents, manifests and annotation mapping. */
public final class SchemaCheck {

    private SchemaCheck() {
    }

    /** Checks every field of an element body against its kind. */
    public static void element(Kind kind, YNode.Mapping body, String elementId, List<Diagnostic> out) {
        for (Map.Entry<String, YNode> e : body.entries().entrySet()) {
            String name = e.getKey();
            if (name.startsWith("x-")) {
                continue;
            }
            FieldSpec spec = kind.fields().get(name);
            if (spec == null) {
                out.add(Diagnostics.of(Rule.E102, "Unknown field '" + name + "' for " + kind.keyword(),
                    body.keyLocation(name), elementId));
                continue;
            }
            field(spec, e.getValue(), elementId, out);
        }
        for (FieldSpec spec : kind.fields().values()) {
            if (spec.required() && !body.entries().containsKey(spec.name())) {
                out.add(Diagnostics.of(Rule.E106, "Missing required field '" + spec.name() + "' for " + kind.keyword(),
                    body.location(), elementId));
            }
        }
    }

    public static void field(FieldSpec spec, YNode value, String elementId, List<Diagnostic> out) {
        String n = spec.name();
        switch (spec.type()) {
            case STRING, TEXT, REF -> {
                if (!isText(value)) {
                    out.add(type(n, "a string", value, elementId));
                }
            }
            case INT -> {
                if (!(value instanceof YNode.Scalar s && s.type() == YNode.ScalarType.INT)) {
                    out.add(type(n, "an integer", value, elementId));
                }
            }
            case BOOL -> {
                if (!(value instanceof YNode.Scalar s && s.type() == YNode.ScalarType.BOOL)) {
                    out.add(type(n, "true or false", value, elementId));
                }
            }
            case ENUM -> {
                if (!isText(value)) {
                    out.add(type(n, "one of " + spec.values(), value, elementId));
                } else if (!spec.values().contains(((YNode.Scalar) value).text())) {
                    out.add(Diagnostics.of(Rule.E107, "'" + ((YNode.Scalar) value).text() + "' is not a valid " + n
                        + "; expected one of " + String.join(", ", spec.values()), value.location(), elementId));
                }
            }
            case STRING_LIST, REF_LIST -> {
                if (value instanceof YNode.Sequence seq) {
                    for (YNode item : seq.items()) {
                        if (!isText(item)) {
                            out.add(type(n + " item", "a string", item, elementId));
                        }
                    }
                } else {
                    out.add(type(n, "a list of strings", value, elementId));
                }
            }
            case SOURCE -> source(n, value, elementId, out);
            case OBJECT_LIST, STEPS -> {
                if (value instanceof YNode.Sequence seq) {
                    for (YNode item : seq.items()) {
                        if (!(item instanceof YNode.Mapping)) {
                            out.add(type(n + " item", "a mapping", item, elementId));
                        }
                    }
                } else {
                    out.add(type(n, "a list of mappings", value, elementId));
                }
            }
            case STRING_OR_OBJECT -> {
                if (!isText(value) && !(value instanceof YNode.Mapping)) {
                    out.add(type(n, "a string or a mapping", value, elementId));
                }
            }
            default -> {
            }
        }
    }

    /** Checks a {@code source} value: a string or {@code {ref: ...}}. */
    public static void source(String n, YNode value, String elementId, List<Diagnostic> out) {
        String ref = sourceText(value);
        if (ref == null) {
            out.add(type(n, "{ ref: <source reference> }", value, elementId));
        } else if (SourceRef.parse(ref).isEmpty()) {
            out.add(Diagnostics.of(Rule.E033, "Malformed source reference '" + ref + "'", value.location(), elementId));
        }
    }

    /** The reference text of a source value, or {@code null}. */
    public static String sourceText(YNode value) {
        if (value instanceof YNode.Scalar s && s.type() != YNode.ScalarType.NULL) {
            return s.text();
        }
        if (value instanceof YNode.Mapping m && m.get("ref") instanceof YNode.Scalar s) {
            return s.text();
        }
        return null;
    }

    public static void id(String id, YNode node, String elementId, List<Diagnostic> out) {
        if (id.contains(":")) {
            return; // reported during merge (E024 for library ids, E011 otherwise)
        }
        if (!Ids.isElementId(id)) {
            out.add(Diagnostics.of(Rule.E011, "Malformed id '" + id + "'; use dotted lowercase segments like order.placement",
                node.location(), elementId));
        }
    }

    public static boolean isText(YNode n) {
        return n instanceof YNode.Scalar s && s.type() != YNode.ScalarType.NULL;
    }

    private static Diagnostic type(String field, String expected, YNode value, String elementId) {
        return Diagnostics.of(Rule.E103, "'" + field + "' must be " + expected, value.location(), elementId);
    }
}
