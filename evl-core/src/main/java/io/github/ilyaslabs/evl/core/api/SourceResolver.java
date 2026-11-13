package io.github.ilyaslabs.evl.core.api;

import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.model.SourceResolution;
import java.util.Optional;

/**
 * Checks source references against code. The plugin implements it with PSI, the CLI with class
 * files (ASM) or JavaParser. Implementations return {@link SourceResolution#UNCHECKED} when they
 * cannot tell (for example Kotlin sources without compiled classes), which is never reported as an
 * error.
 */
public interface SourceResolver {

    SourceResolution resolve(SourceRef ref);

    /**
     * Whether code in {@code from}'s scope references code in {@code to}'s scope (used to verify
     * declared dependencies, EVL-LNG-042). Empty when unknown.
     */
    default Optional<Boolean> references(SourceRef from, SourceRef to) {
        return Optional.empty();
    }
}
