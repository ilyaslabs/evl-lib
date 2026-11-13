package io.github.ilyaslabs.evl.core;

/** One Java or Kotlin source file; {@code path} is shown to users and used for navigation. */
public record SourceFile(String path, String text) {
    public boolean kotlin() {
        return path.endsWith(".kt") || path.endsWith(".kts");
    }
}
