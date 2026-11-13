package io.github.ilyaslabs.evl.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.api.SourceResolver;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.SourceRef;
import io.github.ilyaslabs.evl.core.model.SourceResolution;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import io.github.ilyaslabs.evl.core.spec.Rule;
import io.github.ilyaslabs.evl.core.spec.SpecGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Runs spec/conformance/*.yaml: every rule has a case that reports it and a case that does not. */
class ConformanceTest {

    static final Path SPEC = Path.of(System.getProperty("evl.spec", "../spec"));
    static final String MANIFEST = "evl: \"1.0\"\nid: shop\nname: Shop\n";

    @TestFactory
    Stream<DynamicTest> conformance() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (Stream<Path> files = Files.list(SPEC.resolve("conformance"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) new Load(LoadSettings.builder().build())
                    .loadFromString(Files.readString(f));
                String rule = (String) doc.get("rule");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cases = (List<Map<String, Object>>) doc.get("cases");
                for (Map<String, Object> c : cases) {
                    tests.add(DynamicTest.dynamicTest(rule + " " + c.get("expect") + ": " + c.get("name"), () -> run(rule, c)));
                }
            }
        }
        return tests.stream();
    }

    @Test
    void everyRuleHasPresentAndAbsentCases() throws IOException {
        for (Rule r : Rule.values()) {
            Path f = SPEC.resolve("conformance/" + r.code() + ".yaml");
            assertThat(f).as(r.code()).exists();
            String s = Files.readString(f);
            assertThat(s).contains("expect: present").contains("expect: absent");
        }
    }

    @Test
    void generatedSpecFilesAreUpToDate() throws IOException {
        for (Map.Entry<String, String> e : SpecGenerator.files().entrySet()) {
            Path p = SPEC.resolve(e.getKey());
            assertThat(p).as("run ./gradlew :evl-core:generateSpec").exists();
            assertThat(Files.readString(p)).as(e.getKey() + " is stale; run ./gradlew :evl-core:generateSpec")
                .isEqualTo(e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void run(String rule, Map<String, Object> c) throws IOException {
        Path dir = Files.createTempDirectory("evl-conformance");
        Object manifest = c.containsKey("manifest") ? c.get("manifest") : MANIFEST;
        if (manifest != null) {
            Files.writeString(dir.resolve("evl.yaml"), (String) manifest);
        }
        for (Map.Entry<String, Object> f : ((Map<String, Object>) c.getOrDefault("files", Map.of())).entrySet()) {
            Path p = dir.resolve(f.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, (String) f.getValue());
        }
        EvlWorkspace.Builder b = EvlWorkspace.builder().discover(dir);
        for (Map.Entry<String, Object> lib : ((Map<String, Object>) c.getOrDefault("libraries", Map.of())).entrySet()) {
            String id = lib.getKey().contains("#") ? lib.getKey().substring(0, lib.getKey().indexOf('#')) : lib.getKey();
            Path root = Files.createTempDirectory("evl-lib").resolve("META-INF/evl");
            Files.createDirectories(root.resolve("documents"));
            Files.writeString(root.resolve("manifest.json"), "{\"evl\":\"1.0\",\"id\":\"" + id + "\",\"name\":\"" + id
                + "\",\"kind\":\"LIBRARY\"}");
            for (Map.Entry<String, Object> d : ((Map<String, Object>) lib.getValue()).entrySet()) {
                Files.writeString(root.resolve("documents").resolve(d.getKey()), (String) d.getValue());
            }
            b.addLibrary(root.getParent().getParent());
        }
        List<AnnotationRecord> records = new ArrayList<>();
        for (Map<String, Object> a : (List<Map<String, Object>>) c.getOrDefault("annotations", List.of())) {
            records.add(new AnnotationRecord((String) a.get("annotation"), (String) a.get("element"),
                AnnotationRecord.ElementKind.valueOf((String) a.get("elementKind")), (String) a.get("package"),
                (Map<String, Object>) a.get("attributes"), new Location("Code.java", ((Number) a.get("line")).intValue(), 1)));
        }
        if (!records.isEmpty()) {
            b.addAnnotations("Code.java", records);
        }
        if (c.containsKey("resolver")) {
            b.sourceResolver(stub((Map<String, Object>) c.get("resolver")));
        }
        EvlResult r = b.build().load();
        List<String> codes = r.diagnostics().stream().map(Diagnostic::code).toList();
        if ("present".equals(c.get("expect"))) {
            assertThat(codes).as(r.diagnostics().toString()).contains(rule);
        } else {
            assertThat(codes).as(r.diagnostics().toString()).doesNotContain(rule);
        }
    }

    @SuppressWarnings("unchecked")
    private static SourceResolver stub(Map<String, Object> cfg) {
        List<String> unresolved = (List<String>) cfg.getOrDefault("unresolved", List.of());
        List<String> ambiguous = (List<String>) cfg.getOrDefault("ambiguous", List.of());
        List<String> unchecked = (List<String>) cfg.getOrDefault("unchecked", List.of());
        Object refs = cfg.get("references");
        return new SourceResolver() {
            @Override
            public SourceResolution resolve(SourceRef ref) {
                if (unresolved.contains(ref.raw())) {
                    return SourceResolution.unresolved("not found");
                }
                if (ambiguous.contains(ref.raw())) {
                    return SourceResolution.ambiguous(List.of(ref.raw() + "(int)", ref.raw() + "(String)"));
                }
                if (unchecked.contains(ref.raw())) {
                    return SourceResolution.UNCHECKED;
                }
                return SourceResolution.resolved(new Location("Code.java", 1, 1));
            }

            @Override
            public Optional<Boolean> references(SourceRef from, SourceRef to) {
                return refs == null ? Optional.empty() : Optional.of((Boolean) refs);
            }
        };
    }
}
