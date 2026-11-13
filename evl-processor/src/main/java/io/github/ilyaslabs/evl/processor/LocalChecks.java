package io.github.ilyaslabs.evl.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks that need no other declarations (EVL-PROC-003): id grammar, mutually exclusive and
 * required attributes, sensitive defaults and obvious secrets. Cross-file checks run in evl-core.
 */
public final class LocalChecks {

    private static final Pattern ELEMENT_ID = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*");
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern[] SECRETS = {
        Pattern.compile("-----BEGIN ([A-Z]+ )?PRIVATE KEY-----"),
        Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
        Pattern.compile("\\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\\b"),
        Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{10,}"),
        Pattern.compile("\\b[sr]k_live_[A-Za-z0-9]{16,}"),
        Pattern.compile("[a-z][a-z0-9+.-]*://[^/\\s:@]+:[^/\\s@]+@"),
    };

    private LocalChecks() {
    }

    /** A problem found locally: code (EVL-Exxx) and message. */
    public static final class Problem {
        public final String code;
        public final String message;

        Problem(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static List<Problem> check(Record r) {
        List<Problem> out = new ArrayList<Problem>();
        Object id = r.attributes.get("id");
        if (id instanceof String && !((String) id).isEmpty() && !ELEMENT_ID.matcher((String) id).matches()) {
            out.add(new Problem("EVL-E011", "Malformed id '" + id + "'; use dotted lowercase segments like order.placement"));
        }
        if (r.annotation.equals("Step")) {
            Object step = r.attributes.get("step");
            if (step instanceof String && !LOCAL_ID.matcher((String) step).matches()) {
                out.add(new Problem("EVL-E011", "Malformed step id '" + step + "'"));
            }
        }
        if (r.annotation.equals("Endpoint")) {
            if (r.attributes.containsKey("requestRef") && r.attributes.containsKey("requestType")) {
                out.add(new Problem("EVL-E103", "Set either requestRef or requestType, not both"));
            }
            if (r.attributes.containsKey("responseRef") && r.attributes.containsKey("responseType")) {
                out.add(new Problem("EVL-E103", "Set either responseRef or responseType, not both"));
            }
        }
        if (r.annotation.equals("ConfigProperty")) {
            if (Boolean.TRUE.equals(r.attributes.get("sensitive")) && r.attributes.containsKey("defaultValue")) {
                out.add(new Problem("EVL-E070", "A sensitive config property must not declare a default value"));
            }
            if ("TYPE".equals(r.elementKind) && !r.attributes.containsKey("key")) {
                out.add(new Problem("EVL-E106", "@EVL.ConfigProperty on a type needs a key"));
            }
        }
        for (Map.Entry<String, Object> e : r.attributes.entrySet()) {
            if (!e.getKey().equals("defaultValue") && secret(e.getValue())) {
                out.add(new Problem("EVL-E071", "Attribute '" + e.getKey() + "' looks like it contains a secret"));
            }
        }
        return out;
    }

    private static boolean secret(Object v) {
        if (v instanceof String) {
            for (Pattern p : SECRETS) {
                if (p.matcher((String) v).find()) {
                    return true;
                }
            }
        } else if (v instanceof Collection) {
            for (Object o : (Collection<?>) v) {
                if (secret(o)) {
                    return true;
                }
            }
        }
        return false;
    }
}
