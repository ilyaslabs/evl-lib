package io.github.ilyaslabs.evl.core.model;

/** A directed relationship between two elements, identified by their full ids. */
public record Edge(String from, String to, EdgeType type, Evidence evidence, String label, Origin origin) {

    public Edge withEvidence(Evidence e) {
        return new Edge(from, to, type, e, label, origin);
    }
}
