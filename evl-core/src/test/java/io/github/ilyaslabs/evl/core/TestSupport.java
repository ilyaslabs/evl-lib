package io.github.ilyaslabs.evl.core;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestSupport {

    static final String MANIFEST = """
        evl: "1.0"
        id: shop
        name: Shop
        """;

    private TestSupport() {
    }

    static EvlResult load(String... docs) {
        EvlWorkspace.Builder b = EvlWorkspace.builder().manifest("evl.yaml", MANIFEST);
        for (int i = 0; i < docs.length; i++) {
            b.addDocument("doc" + i + ".evl.yaml", docs[i]);
        }
        return b.build().load();
    }

    static List<String> codes(EvlResult r) {
        List<String> out = new ArrayList<>();
        for (Diagnostic d : r.diagnostics()) {
            out.add(d.code());
        }
        return out;
    }

    static AnnotationRecord rec(String annotation, String element, AnnotationRecord.ElementKind kind, String pkg,
                                int line, Object... attrs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < attrs.length; i += 2) {
            m.put((String) attrs[i], attrs[i + 1]);
        }
        return new AnnotationRecord(annotation, element, kind, pkg, m, new Location("src/" + element.replace('#', '_') + ".java", line, 1));
    }
}
