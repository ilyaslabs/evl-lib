package io.github.ilyaslabs.evl.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates a synthetic application of a given size (EVL-M0.5): modules with components and
 * dependencies, and flows whose steps call flows of other modules. Deterministic for a given size.
 */
final class SyntheticFixture {

    private SyntheticFixture() {
    }

    /** Documents (path to YAML) holding roughly {@code elements} elements. */
    static Map<String, String> documents(int elements) {
        int perModule = 50; // 10 components, 4 dependencies, 4 flows of 8 steps, 4 endpoints
        int modules = Math.max(1, elements / perModule);
        Map<String, String> docs = new LinkedHashMap<>();
        for (int m = 0; m < modules; m++) {
            StringBuilder sb = new StringBuilder("evl: \"1.0\"\nmodules:\n");
            String mod = "m" + m;
            sb.append("  - { id: ").append(mod).append(", name: Module ").append(m).append(", domain: d").append(m % 7).append(" }\n");
            sb.append("components:\n");
            for (int c = 0; c < 10; c++) {
                sb.append("  - { id: ").append(mod).append(".c").append(c).append(", name: Component ").append(m).append('-').append(c)
                    .append(", module: ").append(mod).append(", layer: APPLICATION }\n");
            }
            sb.append("dependencies:\n");
            for (int d = 0; d < 4; d++) {
                int target = (m + d + 1) % modules;
                sb.append("  - { from: ").append(mod).append(".c").append(d).append(", to: m").append(target).append(".c").append(d + 1)
                    .append(", type: API }\n");
            }
            sb.append("endpoints:\n");
            for (int e = 0; e < 4; e++) {
                sb.append("  - { id: ").append(mod).append(".e").append(e).append(", name: Endpoint ").append(m).append('-').append(e)
                    .append(", protocol: HTTP, method: GET, path: /m").append(m).append("/e").append(e).append(", component: ").append(mod).append(".c0 }\n");
            }
            sb.append("flows:\n");
            for (int f = 0; f < 4; f++) {
                sb.append("  - id: ").append(mod).append(".f").append(f).append('\n');
                sb.append("    name: Flow ").append(m).append('-').append(f).append('\n');
                sb.append("    module: ").append(mod).append('\n');
                sb.append("    category: TECHNICAL\n    steps:\n");
                for (int s = 0; s < 7; s++) {
                    sb.append("      - { id: s").append(s).append(", type: ACTION, name: Step ").append(s).append(" }\n");
                }
                if (f == 0 && modules > 1) {
                    sb.append("      - { id: call, type: CALL_FLOW, name: Call next, flow: m").append((m + 1) % modules).append(".f1 }\n");
                } else {
                    sb.append("      - { id: done, type: END, name: Done }\n");
                }
            }
            docs.put("docs/evl/" + mod + ".evl.yaml", sb.toString());
        }
        return docs;
    }
}
