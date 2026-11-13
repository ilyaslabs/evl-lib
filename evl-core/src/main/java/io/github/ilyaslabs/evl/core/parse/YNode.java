package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A position-preserving YAML (or annotation) value tree. Every node keeps the location where it was
 * written so diagnostics point at the exact key or value.
 */
public sealed interface YNode permits YNode.Scalar, YNode.Mapping, YNode.Sequence {

    Location location();

    /** Converts to plain Java values: String, Boolean, Long, null, List, Map. */
    Object toPlain();

    /** Type of a scalar after YAML 1.2 core schema resolution. */
    enum ScalarType { STRING, BOOL, INT, FLOAT, NULL }

    record Scalar(String value, ScalarType type, Location location) implements YNode {
        public static Scalar string(String v, Location l) {
            return new Scalar(v, ScalarType.STRING, l);
        }

        public static Scalar bool(boolean v, Location l) {
            return new Scalar(Boolean.toString(v), ScalarType.BOOL, l);
        }

        @Override
        public Object toPlain() {
            return switch (type) {
                case BOOL -> Boolean.parseBoolean(value.toLowerCase());
                case INT -> {
                    try {
                        yield Long.parseLong(value.replace("_", ""));
                    } catch (NumberFormatException e) {
                        yield value;
                    }
                }
                case NULL -> null;
                default -> value;
            };
        }

        /** The scalar as text regardless of its resolved type ({@code null} for YAML null). */
        public String text() {
            return type == ScalarType.NULL ? null : value;
        }
    }

    /** A mapping. Keys are kept in document order; {@code keyLocations} point at each key. */
    record Mapping(Map<String, YNode> entries, Map<String, Location> keyLocations, Location location) implements YNode {
        public Mapping {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            keyLocations = Collections.unmodifiableMap(new LinkedHashMap<>(keyLocations));
        }

        public YNode get(String key) {
            return entries.get(key);
        }

        public String text(String key) {
            return entries.get(key) instanceof Scalar s ? s.text() : null;
        }

        public Location keyLocation(String key) {
            Location l = keyLocations.get(key);
            return l == null ? location : l;
        }

        @Override
        public Object toPlain() {
            Map<String, Object> m = new LinkedHashMap<>();
            entries.forEach((k, v) -> m.put(k, v.toPlain()));
            return m;
        }
    }

    record Sequence(List<YNode> items, Location location) implements YNode {
        public Sequence {
            items = List.copyOf(items);
        }

        @Override
        public Object toPlain() {
            List<Object> l = new ArrayList<>();
            for (YNode n : items) {
                l.add(n.toPlain());
            }
            return l;
        }
    }

    /** Builds a node tree from plain Java values, all at one location (used for annotations). */
    static YNode fromPlain(Object value, Location location) {
        if (value instanceof Map<?, ?> m) {
            Map<String, YNode> entries = new LinkedHashMap<>();
            Map<String, Location> keys = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                entries.put(String.valueOf(k), fromPlain(v, location));
                keys.put(String.valueOf(k), location);
            });
            return new Mapping(entries, keys, location);
        }
        if (value instanceof List<?> l) {
            List<YNode> items = new ArrayList<>();
            for (Object o : l) {
                items.add(fromPlain(o, location));
            }
            return new Sequence(items, location);
        }
        if (value instanceof Boolean b) {
            return Scalar.bool(b, location);
        }
        if (value instanceof Number n) {
            return new Scalar(n.toString(), n instanceof Double || n instanceof Float ? ScalarType.FLOAT : ScalarType.INT, location);
        }
        if (value == null) {
            return new Scalar("", ScalarType.NULL, location);
        }
        return Scalar.string(String.valueOf(value), location);
    }
}
