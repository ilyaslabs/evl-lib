package io.github.ilyaslabs.evl.core.model;

/** Origin and certainty of a fact in the EVL graph (spec 3.5). */
public enum Evidence {
    /** Stated in an annotation or EVL document. */
    DECLARED,
    /** Declared, and its source reference resolved against code. */
    VERIFIED,
    /** Possible relationship found by static analysis; never stored in documents. */
    INFERRED,
    /** Seen at runtime. Reserved; no 1.0 component produces it. */
    OBSERVED
}
