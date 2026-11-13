package io.github.ilyaslabs.evl.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The published JSON Schemas accept the reference fixtures and reject obvious mistakes. */
class SchemaTest {

    static final Path SPEC = Path.of(System.getProperty("evl.spec", "../spec"));
    static final Path EXAMPLES = Path.of(System.getProperty("evl.examples", "../examples"));
    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    static JsonSchema schema(String name) throws IOException {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Files.readString(SPEC.resolve("schema/" + name)));
    }

    @Test
    void fixturesValidate() throws IOException {
        JsonSchema doc = schema("evl-document.schema.json");
        JsonSchema manifest = schema("evl-manifest.schema.json");
        List<Path> files;
        try (Stream<Path> s = Files.walk(EXAMPLES)) {
            files = s.filter(p -> p.toString().endsWith(".yaml") && !p.toString().contains("/build/")).sorted().toList();
        }
        assertThat(files).isNotEmpty();
        for (Path p : files) {
            JsonNode node = YAML.readTree(p.toFile());
            Set<ValidationMessage> errors = (p.getFileName().toString().equals("evl.yaml") ? manifest : doc).validate(node);
            assertThat(errors).as(p.toString()).isEmpty();
        }
    }

    @Test
    void rejectsMistakes() throws IOException {
        JsonSchema doc = schema("evl-document.schema.json");
        assertThat(doc.validate(YAML.readTree("components:\n  - { id: Bad_Id, name: X }\n"))).isNotEmpty();
        assertThat(doc.validate(YAML.readTree("components:\n  - { id: c, layer: TOP }\n"))).isNotEmpty();
        assertThat(doc.validate(YAML.readTree("nonsense: []\n"))).isNotEmpty();
        assertThat(doc.validate(YAML.readTree("flows:\n  - { id: f, steps: [ { id: s, type: WIGGLE } ] }\n"))).isNotEmpty();
        assertThat(doc.validate(YAML.readTree("x-anything: 1\nmodules: []\n"))).isEmpty();
    }
}
