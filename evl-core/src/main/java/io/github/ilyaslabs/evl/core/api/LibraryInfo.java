package io.github.ilyaslabs.evl.core.api;

/** A loaded EVL library: its id, name, artifact coordinates and where it was read from. */
public record LibraryInfo(String id, String name, String description, String artifact, String origin) {
}
