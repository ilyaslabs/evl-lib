package io.github.ilyaslabs.evl.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Something the scanner could not make sense of. {@code level} is "error" or "warning". */
public record Problem(String level, String message, String file, int line) {
    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("message", message);
        m.put("file", file);
        m.put("line", line);
        return m;
    }
}
