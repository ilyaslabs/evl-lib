package io.github.ilyaslabs.evl.core.spec;

import java.util.Optional;
import java.util.Set;

/** Workflow step types (spec 6.2) and the type-specific fields each accepts. */
public enum StepType {
    ACTION(Set.of("component")),
    DECISION(Set.of("question", "branches", "otherwise")),
    PARALLEL(Set.of("branches", "join")),
    CALL_FLOW(Set.of("flow", "inputs", "outputs", "async")),
    EVENT(Set.of("event", "direction")),
    WAIT(Set.of("for", "timeout", "onTimeout")),
    RETRY(Set.of("policy", "steps")),
    COMPENSATE(Set.of("for", "steps")),
    FAIL(Set.of("error", "reason")),
    END(Set.of("outcome")),
    GOTO(Set.of("target", "when")),
    NOTE(Set.of("text"));

    /** Fields every step accepts. */
    public static final Set<String> COMMON = Set.of("id", "type", "name", "description", "doc", "source", "tags",
        "requirements", "decisions", "realizedBy", "errors", "concurrency", "performance", "security", "failure");

    private final Set<String> fields;

    StepType(Set<String> fields) {
        this.fields = fields;
    }

    public Set<String> specificFields() {
        return fields;
    }

    public boolean allows(String field) {
        return COMMON.contains(field) || fields.contains(field) || field.startsWith("x-");
    }

    /** Whether this step type ends the current sequence (later siblings are unreachable). */
    public boolean terminates() {
        return this == FAIL || this == END;
    }

    public static Optional<StepType> parse(String s) {
        try {
            return Optional.of(valueOf(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
