package io.github.ilyaslabs.evl.core.api;

import java.util.concurrent.CancellationException;

/** Cooperative cancellation for long operations (EVL-CORE-004). */
@FunctionalInterface
public interface CancellationToken {

    CancellationToken NONE = () -> false;

    boolean isCancelled();

    /** Throws {@link CancellationException} when cancelled. */
    default void check() {
        if (isCancelled()) {
            throw new CancellationException("EVL operation cancelled");
        }
    }
}
