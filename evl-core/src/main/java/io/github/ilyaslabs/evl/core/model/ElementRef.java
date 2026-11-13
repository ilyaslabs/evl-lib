package io.github.ilyaslabs.evl.core.model;

import io.github.ilyaslabs.evl.core.spec.Kind;
import java.util.Optional;

/**
 * A parsed element reference (spec 3.3): {@code [app-id:][kind:]element-id[/local-id]}.
 *
 * @param app      library EVL id, or {@code null} for the current application
 * @param kind     kind prefix, or {@code null}
 * @param id       element id
 * @param local    step or field id within the element, or {@code null}
 * @param raw      the original text
 */
public record ElementRef(String app, Kind kind, String id, String local, String raw) {

    /** Parses a reference; returns empty when the text is not a syntactically valid reference. */
    public static Optional<ElementRef> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = text.trim();
        String app = null;
        Kind kind = null;
        String[] parts = s.split(":", -1);
        if (parts.length > 3) {
            return Optional.empty();
        }
        int idx = 0;
        if (parts.length == 3) {
            app = parts[0];
            Optional<Kind> k = Kind.byKeyword(parts[1]);
            if (k.isEmpty()) {
                return Optional.empty();
            }
            kind = k.get();
            idx = 2;
        } else if (parts.length == 2) {
            Optional<Kind> k = Kind.byKeyword(parts[0]);
            if (k.isPresent()) {
                kind = k.get();
            } else {
                app = parts[0];
            }
            idx = 1;
        }
        if (app != null && !Ids.isAppId(app)) {
            return Optional.empty();
        }
        String rest = parts[idx];
        String local = null;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            local = rest.substring(slash + 1);
            rest = rest.substring(0, slash);
            if (!Ids.isLocalId(local)) {
                return Optional.empty();
            }
        }
        if (!Ids.isElementId(rest)) {
            return Optional.empty();
        }
        return Optional.of(new ElementRef(app, kind, rest, local, text));
    }

    /** The key of the target in a graph: {@code [app:]id[/local]}. */
    public String key() {
        String base = local == null ? id : id + "/" + local;
        return app == null ? base : app + ":" + base;
    }

    /** The key with {@code currentApp} filled in when the reference has no app prefix. */
    public String keyIn(String currentApp) {
        if (app != null || currentApp == null) {
            return key();
        }
        return currentApp + ":" + (local == null ? id : id + "/" + local);
    }
}
