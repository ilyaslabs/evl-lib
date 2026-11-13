package io.github.ilyaslabs.evl.core.model;

import java.util.Map;

/**
 * A machine-readable fix suggestion attached to a diagnostic. {@code action} is a stable verb
 * (for example {@code create-manifest}, {@code replace-reference}, {@code add-id}); {@code args}
 * carries what an IDE or agent needs to apply it.
 */
public record Fix(String action, String title, Map<String, String> args) {

    public Fix {
        args = Map.copyOf(args);
    }

    public static Fix of(String action, String title) {
        return new Fix(action, title, Map.of());
    }
}
