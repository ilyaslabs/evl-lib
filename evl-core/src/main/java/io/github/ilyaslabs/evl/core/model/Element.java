package io.github.ilyaslabs.evl.core.model;

import io.github.ilyaslabs.evl.core.spec.Kind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An element of the canonical EVL graph. Immutable.
 *
 * @param key           graph key: {@code id} for the current application, {@code app:id} for library
 *                      elements; steps use {@code flowId/stepId}
 * @param id            element id ({@code flowId/stepId} for steps)
 * @param kind          element kind
 * @param name          display name (never null; falls back to the id)
 * @param app           EVL id of the library that declares it, or {@code null} for the current application
 * @param fields        field values after merging (String, Boolean, Long, List, Map)
 * @param fieldOrigins  where each field value was written
 * @param origins       every declaration that contributed to this element, annotation first
 * @param source        parsed source reference, or {@code null}
 * @param resolution    result of checking {@code source} against code
 * @param evidence      DECLARED or VERIFIED
 */
public record Element(String key, String id, Kind kind, String name, String app, Map<String, Object> fields,
                      Map<String, Origin> fieldOrigins, List<Origin> origins, SourceRef source,
                      SourceResolution resolution, Evidence evidence) {

    public Element {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        fieldOrigins = Collections.unmodifiableMap(new LinkedHashMap<>(fieldOrigins));
        origins = List.copyOf(origins);
        resolution = resolution == null ? SourceResolution.UNCHECKED : resolution;
    }

    public Optional<String> string(String field) {
        Object v = fields.get(field);
        return v instanceof String s && !s.isEmpty() ? Optional.of(s) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public List<String> strings(String field) {
        Object v = fields.get(field);
        if (v instanceof List<?> l) {
            return l.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (v instanceof String s && !s.isEmpty()) {
            return List.of(s);
        }
        return List.of();
    }

    public boolean flag(String field) {
        return Boolean.TRUE.equals(fields.get(field));
    }

    public String description() {
        return string("description").orElse("");
    }

    /** Primary location: the first origin's location. */
    public Location location() {
        return origins.isEmpty() ? Location.UNKNOWN : origins.get(0).location();
    }

    public Location locationOf(String field) {
        Origin o = fieldOrigins.get(field);
        return o == null ? location() : o.location();
    }

    public boolean isLibrary() {
        return app != null;
    }

    public Element withResolution(SourceResolution r) {
        Evidence e = r.isResolved() ? Evidence.VERIFIED : Evidence.DECLARED;
        return new Element(key, id, kind, name, app, fields, fieldOrigins, origins, source, r, e);
    }
}
