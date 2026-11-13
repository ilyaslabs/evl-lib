package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code META-INF/evl/index.json}: the annotation records of one compilation unit
 * set (schema {@code spec/schema/evl-index.schema.json}).
 */
public final class IndexFile {

    public static final int FORMAT = 1;

    private IndexFile() {
    }

    /** Result of reading an index. */
    public record Result(List<AnnotationRecord> records, List<Diagnostic> diagnostics) {
    }

    public static Result read(String file, String content) {
        YamlReader.Result r = YamlReader.read(file, content);
        List<Diagnostic> diags = new ArrayList<>(r.diagnostics());
        List<AnnotationRecord> out = new ArrayList<>();
        if (r.root().isEmpty() || !(r.root().get() instanceof YNode.Mapping root)) {
            if (diags.isEmpty()) {
                diags.add(Diagnostics.of(Rule.E103, "index.json must be an object", Location.of(file)));
            }
            return new Result(out, diags);
        }
        if (!(root.get("annotations") instanceof YNode.Sequence seq)) {
            diags.add(Diagnostics.of(Rule.E103, "index.json needs an 'annotations' array", root.location()));
            return new Result(out, diags);
        }
        for (YNode item : seq.items()) {
            if (!(item instanceof YNode.Mapping m)) {
                continue;
            }
            String annotation = m.text("annotation");
            String element = m.text("element");
            String kind = m.text("elementKind");
            if (annotation == null || element == null || kind == null) {
                diags.add(Diagnostics.of(Rule.E106, "index entry needs annotation, element and elementKind", m.location()));
                continue;
            }
            AnnotationRecord.ElementKind ek;
            try {
                ek = AnnotationRecord.ElementKind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                diags.add(Diagnostics.of(Rule.E107, "Unknown elementKind '" + kind + "'", m.location()));
                continue;
            }
            String f = m.text("file");
            Location loc = new Location(f == null ? file : f, intOf(m.text("line")), intOf(m.text("column")));
            Map<String, Object> attrs = new LinkedHashMap<>();
            if (m.get("attributes") instanceof YNode.Mapping am) {
                am.entries().forEach((k, v) -> attrs.put(k, v.toPlain()));
            }
            out.add(new AnnotationRecord(annotation, element, ek, m.text("package"), attrs, loc));
        }
        return new Result(out, diags);
    }

    public static String write(List<AnnotationRecord> records, String generator) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evl", Versions.SPEC);
        root.put("format", FORMAT);
        root.put("generator", generator);
        List<Object> list = new ArrayList<>();
        for (AnnotationRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("annotation", r.annotation());
            m.put("element", r.element());
            m.put("elementKind", r.elementKind().name());
            m.put("package", r.packageName());
            m.put("file", r.location().file());
            m.put("line", r.location().line());
            m.put("column", r.location().column());
            m.put("attributes", r.attributes());
            list.add(m);
        }
        root.put("annotations", list);
        return Json.write(root);
    }

    private static int intOf(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
