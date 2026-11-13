package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.parse.Library;
import io.github.ilyaslabs.evl.core.parse.Manifest;
import io.github.ilyaslabs.evl.core.parse.ManifestParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes an application's EVL data for packaging into its jar (EVL-DOC-030):
 * {@code META-INF/evl/manifest.json} and {@code META-INF/evl/documents/**}. The processor writes
 * {@code index.json} next to them. Used by the Gradle and Maven plugins.
 */
public final class LibraryPackager {

    private LibraryPackager() {
    }

    /**
     * @param projectDir directory with {@code evl.yaml}
     * @param outputDir  resources output root (the jar root)
     * @param artifact   {@code group:name:version} or {@code null}
     * @return the files written (empty when there is no valid manifest)
     */
    public static List<Path> write(Path projectDir, Path outputDir, String artifact) throws IOException {
        List<Path> written = new ArrayList<>();
        Path mf = projectDir.resolve("evl.yaml");
        if (!Files.isRegularFile(mf)) {
            return written;
        }
        Optional<Manifest> parsed = ManifestParser.parse("evl.yaml", Files.readString(mf)).manifest();
        if (parsed.isEmpty()) {
            return written;
        }
        Manifest m = parsed.get();
        Path root = outputDir.resolve(Library.ROOT);
        Files.createDirectories(root);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("evl", m.version());
        json.put("id", m.id());
        json.put("name", m.name());
        if (!m.description().isEmpty()) {
            json.put("description", m.description());
        }
        json.put("kind", m.kind());
        if (artifact != null && !artifact.isBlank()) {
            json.put("artifact", artifact);
        }
        Path manifestJson = root.resolve("manifest.json");
        Files.writeString(manifestJson, Json.write(json));
        written.add(manifestJson);
        for (Path doc : EvlWorkspace.findDocuments(projectDir, m.documents(), m.exclude())) {
            Path target = root.resolve("documents").resolve(projectDir.relativize(doc).toString());
            Files.createDirectories(target.getParent());
            Files.copy(doc, target, StandardCopyOption.REPLACE_EXISTING);
            written.add(target);
        }
        return written;
    }
}
