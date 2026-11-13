package io.github.ilyaslabs.evl.core.parse;

import io.github.ilyaslabs.evl.core.internal.Diagnostics;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.spec.Rule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The EVL data of a dependency: {@code META-INF/evl/manifest.json}, {@code index.json} and
 * {@code documents/**}. Libraries are read-only.
 *
 * @param origin     the jar or directory it was read from
 * @param manifest   raw manifest content
 * @param index      raw index content or {@code null}
 * @param documents  document path → content
 */
public record Library(String origin, String manifest, String index, Map<String, String> documents) {

    public static final String ROOT = "META-INF/evl/";

    public Library {
        documents = Map.copyOf(documents);
    }

    /** Whether a jar or class directory contains EVL data. */
    public static boolean isLibrary(Path jarOrDir) {
        if (Files.isDirectory(jarOrDir)) {
            return Files.isRegularFile(jarOrDir.resolve(ROOT + "manifest.json"));
        }
        try (ZipFile zip = new ZipFile(jarOrDir.toFile())) {
            return zip.getEntry(ROOT + "manifest.json") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Reads library data; empty with a diagnostic when it cannot be read. */
    public static Optional<Library> read(Path jarOrDir, List<Diagnostic> diags) {
        String origin = jarOrDir.toString().replace('\\', '/');
        try {
            if (Files.isDirectory(jarOrDir)) {
                Path root = jarOrDir.resolve(ROOT);
                if (!Files.isRegularFile(root.resolve("manifest.json"))) {
                    return Optional.empty();
                }
                String manifest = Files.readString(root.resolve("manifest.json"));
                Path idx = root.resolve("index.json");
                String index = Files.isRegularFile(idx) ? Files.readString(idx) : null;
                Map<String, String> docs = new TreeMap<>();
                Path docRoot = root.resolve("documents");
                if (Files.isDirectory(docRoot)) {
                    try (Stream<Path> s = Files.walk(docRoot)) {
                        for (Path p : s.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".evl.yaml")).toList()) {
                            docs.put(origin + "!/" + ROOT + "documents/" + docRoot.relativize(p).toString().replace('\\', '/'),
                                Files.readString(p));
                        }
                    }
                }
                return Optional.of(new Library(origin, manifest, index, docs));
            }
            try (ZipFile zip = new ZipFile(jarOrDir.toFile())) {
                ZipEntry m = zip.getEntry(ROOT + "manifest.json");
                if (m == null) {
                    return Optional.empty();
                }
                String manifest = read(zip, m);
                ZipEntry i = zip.getEntry(ROOT + "index.json");
                String index = i == null ? null : read(zip, i);
                Map<String, String> docs = new LinkedHashMap<>();
                List<ZipEntry> entries = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                    ZipEntry e = en.nextElement();
                    if (!e.isDirectory() && e.getName().startsWith(ROOT + "documents/") && e.getName().endsWith(".evl.yaml")) {
                        entries.add(e);
                    }
                }
                entries.sort((a, b) -> a.getName().compareTo(b.getName()));
                for (ZipEntry e : entries) {
                    docs.put(origin + "!/" + e.getName(), read(zip, e));
                }
                return Optional.of(new Library(origin, manifest, index, docs));
            }
        } catch (IOException | RuntimeException e) {
            diags.add(Diagnostics.of(Rule.E104, "Cannot read EVL data from " + origin + ": " + e.getMessage(),
                Location.of(origin)));
            return Optional.empty();
        }
    }

    private static String read(ZipFile zip, ZipEntry e) throws IOException {
        if (e.getSize() > YamlReader.MAX_BYTES) {
            throw new IOException(e.getName() + " is larger than 5 MB");
        }
        try (InputStream in = zip.getInputStream(e)) {
            byte[] b = in.readNBytes(YamlReader.MAX_BYTES + 1);
            if (b.length > YamlReader.MAX_BYTES) {
                throw new IOException(e.getName() + " is larger than 5 MB");
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    public String manifestFile() {
        return origin + "!/" + ROOT + "manifest.json";
    }

    public String indexFile() {
        return origin + "!/" + ROOT + "index.json";
    }
}
