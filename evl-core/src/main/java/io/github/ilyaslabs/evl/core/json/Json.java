package io.github.ilyaslabs.evl.core.json;

import java.util.Collection;
import java.util.Map;

/**
 * Minimal deterministic JSON writer for plain values (Map, Collection, String, Number, Boolean,
 * null, records via {@link JsonValue}). Maps keep their iteration order.
 */
public final class Json {

    private Json() {
    }

    /** Values that know their JSON shape. */
    public interface JsonValue {
        Object toJson();
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, true);
        sb.append('\n');
        return sb.toString();
    }

    public static String compact(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, boolean pretty) {
        if (v instanceof JsonValue jv) {
            v = jv.toJson();
        }
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            string(sb, s);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short) {
            sb.append(v);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            sb.append(d == Math.rint(d) && !Double.isInfinite(d) ? Long.toString((long) d) : Double.toString(d));
        } else if (v instanceof Enum<?> e) {
            string(sb, e.name());
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, indent + 1, pretty);
                string(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), indent + 1, pretty);
            }
            newline(sb, indent, pretty);
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            if (c.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, indent + 1, pretty);
                write(sb, o, indent + 1, pretty);
            }
            newline(sb, indent, pretty);
            sb.append(']');
        } else {
            string(sb, v.toString());
        }
    }

    private static void newline(StringBuilder sb, int indent, boolean pretty) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(indent));
        }
    }

    public static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
