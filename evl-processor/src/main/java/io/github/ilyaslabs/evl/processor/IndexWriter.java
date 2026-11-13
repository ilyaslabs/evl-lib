package io.github.ilyaslabs.evl.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code index.json} and {@code manifest.json} without dependencies. Output is
 * deterministic: records are sorted by file, line, column, element and annotation. Shared with the
 * KSP processor.
 */
public final class IndexWriter {

    public static final String SPEC = "1.0";

    private IndexWriter() {
    }

    public static String index(List<Record> records, String generator) {
        List<Record> sorted = new ArrayList<Record>(records);
        Collections.sort(sorted, new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                int c = a.file.compareTo(b.file);
                if (c == 0) {
                    c = Integer.compare(a.line, b.line);
                }
                if (c == 0) {
                    c = Integer.compare(a.column, b.column);
                }
                if (c == 0) {
                    c = a.element.compareTo(b.element);
                }
                return c == 0 ? a.annotation.compareTo(b.annotation) : c;
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"evl\": \"").append(SPEC).append("\",\n  \"format\": 1,\n  \"generator\": ");
        string(sb, generator);
        sb.append(",\n  \"annotations\": [");
        boolean first = true;
        for (Record r : sorted) {
            sb.append(first ? "\n" : ",\n");
            first = false;
            sb.append("    {\"annotation\": ");
            string(sb, r.annotation);
            sb.append(", \"element\": ");
            string(sb, r.element);
            sb.append(", \"elementKind\": ");
            string(sb, r.elementKind);
            sb.append(", \"package\": ");
            string(sb, r.packageName);
            sb.append(", \"file\": ");
            string(sb, r.file);
            sb.append(", \"line\": ").append(r.line).append(", \"column\": ").append(r.column);
            sb.append(", \"attributes\": ");
            value(sb, r.attributes);
            sb.append('}');
        }
        sb.append(first ? "]\n}\n" : "\n  ]\n}\n");
        return sb.toString();
    }

    /** {@code manifest.json} from manifest fields (id, name, description, kind) and artifact coordinates. */
    public static String manifest(Map<String, String> fields, String artifact) {
        StringBuilder sb = new StringBuilder("{\n  \"evl\": ");
        string(sb, fields.containsKey("evl") ? fields.get("evl") : SPEC);
        for (String k : new String[] {"id", "name", "description", "kind"}) {
            if (fields.get(k) != null) {
                sb.append(",\n  \"").append(k).append("\": ");
                string(sb, fields.get(k));
            }
        }
        if (!fields.containsKey("kind")) {
            sb.append(",\n  \"kind\": \"LIBRARY\"");
        }
        if (artifact != null && !artifact.isEmpty()) {
            sb.append(",\n  \"artifact\": ");
            string(sb, artifact);
        }
        return sb.append("\n}\n").toString();
    }

    @SuppressWarnings("unchecked")
    static void value(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            string(sb, (String) v);
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                string(sb, e.getKey());
                sb.append(": ");
                value(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Collection<Object>) v) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                value(sb, o);
            }
            sb.append(']');
        } else {
            string(sb, v.toString());
        }
    }

    static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
