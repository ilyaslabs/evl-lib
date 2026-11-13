package io.github.ilyaslabs.evl.core.model;

import java.util.Comparator;
import java.util.List;

/** A validation finding. Ordering is deterministic: file, line, column, code, message. */
public record Diagnostic(String code, Level level, String message, Location location, String elementId,
                         List<Location> related, List<Fix> fixes) implements Comparable<Diagnostic> {

    private static final Comparator<Diagnostic> ORDER = Comparator
        .comparing((Diagnostic d) -> d.location().file() == null ? "" : d.location().file())
        .thenComparingInt(d -> d.location().line())
        .thenComparingInt(d -> d.location().column())
        .thenComparing(Diagnostic::code)
        .thenComparing(Diagnostic::message);

    public Diagnostic {
        related = List.copyOf(related);
        fixes = List.copyOf(fixes);
        location = location == null ? Location.UNKNOWN : location;
    }

    public Diagnostic withLevel(Level newLevel) {
        return new Diagnostic(code, newLevel, message, location, elementId, related, fixes);
    }

    @Override
    public int compareTo(Diagnostic o) {
        return ORDER.compare(this, o);
    }

    @Override
    public String toString() {
        return location + ": " + level.name().toLowerCase() + " " + code + ": " + message;
    }
}
