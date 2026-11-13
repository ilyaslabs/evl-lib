package io.github.ilyaslabs.evl.core.model;

/** Relationship types in the canonical graph. */
public enum EdgeType {
    /** Default navigation hierarchy (application to module to component, flow to step, ...). */
    CONTAINS,
    DEPENDS_ON,
    IMPLEMENTS,
    CALLS,
    CALLS_FLOW,
    REALIZED_BY,
    PRODUCES,
    CONSUMES,
    OWNS,
    RELATIONSHIP,
    EXPOSES,
    RELATES,
    /** Control flow inside a flow: sequence. */
    CONTROL_NEXT,
    /** Control flow inside a flow: decision or parallel branch. */
    BRANCH,
    /** Control flow inside a flow: error handler. */
    ERROR,
    COMPENSATES,
    GOTO;

    public boolean isControlFlow() {
        return this == CONTROL_NEXT || this == BRANCH || this == ERROR || this == GOTO || this == COMPENSATES;
    }
}
