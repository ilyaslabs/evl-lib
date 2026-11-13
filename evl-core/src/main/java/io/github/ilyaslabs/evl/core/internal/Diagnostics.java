package io.github.ilyaslabs.evl.core.internal;

import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Fix;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.util.List;

/** Factory helpers for diagnostics. */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static Diagnostic of(Rule rule, String message, Location location) {
        return new Diagnostic(rule.code(), rule.level(), message, location, null, List.of(), List.of());
    }

    public static Diagnostic of(Rule rule, String message, Location location, String elementId) {
        return new Diagnostic(rule.code(), rule.level(), message, location, elementId, List.of(), List.of());
    }

    public static Diagnostic of(Rule rule, String message, Location location, String elementId, List<Location> related,
                                List<Fix> fixes) {
        return new Diagnostic(rule.code(), rule.level(), message, location, elementId, related, fixes);
    }
}
