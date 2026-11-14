package io.github.ilyaslabs.evl.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where something is declared, with the lines around it for the code preview. */
public record Code(String file, int line, int from, List<String> lines) {
    static final int CONTEXT = 10;

    static Code of(String file, String[] all, int line) {
        int from = Math.max(1, line - CONTEXT);
        int to = Math.min(all.length, line + CONTEXT);
        return new Code(file, line, from, List.of(all).subList(from - 1, to));
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("line", line);
        m.put("from", from);
        m.put("lines", lines);
        return m;
    }
}
