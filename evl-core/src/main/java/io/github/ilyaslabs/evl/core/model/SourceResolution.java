package io.github.ilyaslabs.evl.core.model;

import java.util.List;

/** Result of checking a source reference against code. */
public record SourceResolution(State state, Location location, List<String> candidates, String reason) {

    /** Resolution state. */
    public enum State { UNCHECKED, RESOLVED, UNRESOLVED, AMBIGUOUS }

    public static final SourceResolution UNCHECKED = new SourceResolution(State.UNCHECKED, null, List.of(), null);

    public SourceResolution {
        candidates = List.copyOf(candidates);
    }

    public static SourceResolution resolved(Location location) {
        return new SourceResolution(State.RESOLVED, location, List.of(), null);
    }

    public static SourceResolution unresolved(String reason) {
        return new SourceResolution(State.UNRESOLVED, null, List.of(), reason);
    }

    public static SourceResolution ambiguous(List<String> candidates) {
        return new SourceResolution(State.AMBIGUOUS, null, candidates, "matches " + candidates.size() + " members");
    }

    public boolean isResolved() {
        return state == State.RESOLVED;
    }
}
