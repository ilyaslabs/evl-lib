package io.github.ilyaslabs.evl.core.resolve;

import io.github.ilyaslabs.evl.core.model.Origin;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.parse.YNode;
import io.github.ilyaslabs.evl.core.spec.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A mutable element under construction. Internal to the resolver. */
final class Draft {

    final Kind kind;
    final String id;
    final String key;
    final String app;
    final Map<String, YNode> fields = new LinkedHashMap<>();
    final Map<String, Origin> fieldOrigins = new LinkedHashMap<>();
    final List<Origin> origins = new ArrayList<>();
    boolean derivedId;
    /** Key of the flow that owns this step; {@code null} for non-steps. */
    String flowKey;

    Draft(Kind kind, String id, String key, String app) {
        this.kind = kind;
        this.id = id;
        this.key = key;
        this.app = app;
    }

    void put(String field, YNode value, Origin origin) {
        fields.put(field, value);
        fieldOrigins.put(field, origin);
    }

    String text(String field) {
        return fields.get(field) instanceof YNode.Scalar s ? s.text() : null;
    }

    boolean hasAnnotation() {
        return origins.stream().anyMatch(o -> o.kind() == OriginKind.ANNOTATION);
    }

    boolean hasDocument() {
        return origins.stream().anyMatch(o -> o.kind() == OriginKind.DOCUMENT || o.kind() == OriginKind.MANIFEST);
    }

    Origin annotationOrigin() {
        return origins.stream().filter(o -> o.kind() == OriginKind.ANNOTATION).findFirst().orElse(null);
    }
}
