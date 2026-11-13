package io.github.ilyaslabs.evl.core.model;

/** Diagnostic severity, most severe first. */
public enum Level {
    ERROR, WARNING, WEAK_WARNING, INFO, OFF;

    public boolean atLeast(Level other) {
        return ordinal() <= other.ordinal();
    }
}
