package io.github.ilyaslabs.evl.core;

import static io.github.ilyaslabs.evl.core.TestSupport.codes;
import static io.github.ilyaslabs.evl.core.TestSupport.load;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.parse.DocumentParser;
import io.github.ilyaslabs.evl.core.parse.ParsedDocument;
import io.github.ilyaslabs.evl.core.spec.Kind;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

    @Test
    void parsesSectionsWithPositions() {
        ParsedDocument d = DocumentParser.parse("a.evl.yaml", """
            evl: "1.0"
            modules:
              - id: order
                name: Order
            components:
              - id: order.service
                name: Order Service
                module: order
            x-team: payments
            """);
        assertThat(d.diagnostics()).isEmpty();
        assertThat(d.version()).isEqualTo("1.0");
        assertThat(d.decls()).extracting(x -> x.kind()).containsExactly(Kind.MODULE, Kind.COMPONENT);
        assertThat(d.decls().get(1).origin().location().line()).isEqualTo(6);
        assertThat(d.extensions()).containsKey("x-team");
    }

    @Test
    void schemaErrors() {
        EvlResult r = load("""
            components:
              - id: Bad_Id
                name: X
              - name: No id
              - id: c
                name: C
                layer: TOP
                unknown: 1
                tags: notalist
            nonsense: []
            """);
        assertThat(codes(r)).contains("EVL-E011", "EVL-E012", "EVL-E107", "EVL-E102", "EVL-E103");
    }

    @Test
    void yamlSafety() {
        assertThat(codes(load("a: !custom 1\n"))).contains("EVL-E100");
        assertThat(codes(load("modules:\n  - id: a\n    id: b\n"))).contains("EVL-E101");
        EvlResult bad = load("modules: [\n");
        assertThat(codes(bad)).contains("EVL-E104");
        Diagnostic d = bad.diagnostics().stream().filter(x -> x.code().equals("EVL-E104")).findFirst().orElseThrow();
        assertThat(d.location().line()).isPositive();
    }

    @Test
    void versionRules() {
        assertThat(codes(load("evl: \"2.0\"\n"))).contains("EVL-E050");
        assertThat(codes(load("evl: \"1.9\"\n"))).contains("EVL-W051");
    }

    @Test
    void manifestRules() {
        EvlResult r = io.github.ilyaslabs.evl.core.api.EvlWorkspace.builder().manifest("evl.yaml", "id: Shop\n").build().load();
        assertThat(codes(r)).contains("EVL-E001");
        EvlResult r2 = io.github.ilyaslabs.evl.core.api.EvlWorkspace.builder()
            .manifest("evl.yaml", "evl: \"3.0\"\nid: shop\nname: Shop\n").build().load();
        assertThat(codes(r2)).contains("EVL-E052");
    }
}
