package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.model.Location;
import java.util.List;
import java.util.Map;

/**
 * A parsed {@code evl.yaml} (or a library's {@code manifest.json}).
 *
 * @param version       spec version, e.g. {@code 1.0}
 * @param id            EVL application id
 * @param name          display name
 * @param description   description
 * @param kind          APPLICATION or LIBRARY
 * @param documents     document globs (relative to the manifest)
 * @param sources       source roots
 * @param exclude       exclude globs
 * @param dependencies  expected library ids
 * @param settings      settings map
 * @param rules         rule code to level name overrides
 * @param artifact      artifact coordinates for libraries ({@code group:name:version}) or {@code null}
 * @param derived       true when no manifest exists and the id was derived
 * @param location      manifest location
 */
public record Manifest(String version, String id, String name, String description, String kind,
                       List<String> documents, List<String> sources, List<String> exclude, List<String> dependencies,
                       Map<String, Object> settings, Map<String, String> rules, String artifact, boolean derived,
                       Location location) {

    public static final List<String> DEFAULT_DOCUMENTS = List.of("**/*.evl.yaml");

    public Manifest {
        documents = List.copyOf(documents);
        sources = List.copyOf(sources);
        exclude = List.copyOf(exclude);
        dependencies = List.copyOf(dependencies);
        settings = Map.copyOf(settings);
        rules = Map.copyOf(rules);
    }

    public static Manifest derived(String id, Location location) {
        return new Manifest("1.0", id, id, "", "APPLICATION", DEFAULT_DOCUMENTS, List.of(), List.of(), List.of(),
            Map.of(), Map.of(), null, true, location);
    }

    public boolean isLibrary() {
        return "LIBRARY".equals(kind);
    }

    public int major() {
        return Versions.major(version);
    }
}
