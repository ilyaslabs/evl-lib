package io.github.ilyaslabs.evl.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.ChangeSet;
import io.github.ilyaslabs.evl.core.api.EvlQueries;
import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.api.GraphExport;
import io.github.ilyaslabs.evl.core.api.SourceResolver;
import io.github.ilyaslabs.evl.core.json.Json;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Evidence;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.model.SourceResolution;
import io.github.ilyaslabs.evl.core.spec.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTest {

    private static final String DOC_A = """
        flows:
          - { id: order.placement, name: Order Placement, category: BUSINESS }
          - { id: reserve.inventory, name: Reserve Inventory, category: TECHNICAL, description: stock }
        """;

    @Test
    void incrementalUpdateEqualsFullLoad() {
        EvlWorkspace ws = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addDocument("a.evl.yaml", DOC_A).addDocument("b.evl.yaml", "modules:\n  - { id: order, name: Order }\n")
            .build();
        EvlResult first = ws.load();
        String changed = "modules:\n  - { id: order, name: Orders }\n";
        EvlResult next = ws.update(ChangeSet.builder().putDocument("b.evl.yaml", changed).build());
        EvlResult full = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addDocument("a.evl.yaml", DOC_A).addDocument("b.evl.yaml", changed).build().load();
        assertThat(Json.write(GraphExport.index(next))).isEqualTo(Json.write(GraphExport.index(full)));
        // Structural sharing: unchanged elements keep identity.
        assertThat(next.graph().element("order.placement").get()).isSameAs(first.graph().element("order.placement").get());
        assertThat(next.graph().element("order").get().name()).isEqualTo("Orders");
        EvlResult removed = ws.update(ChangeSet.builder().removeDocument("b.evl.yaml").build());
        assertThat(removed.graph().element("order")).isEmpty();
    }

    @Test
    void searchRanking() {
        EvlResult r = TestSupport.load(DOC_A, """
            flows:
              - { id: reserve, name: Something, category: TECHNICAL }
            """);
        EvlQueries q = r.queries();
        assertThat(q.search("reserve", EvlQueries.Filters.kinds(Kind.FLOW), 10)).extracting(h -> h.element().id())
            .containsExactly("reserve", "reserve.inventory");
        assertThat(q.search("ReIn", EvlQueries.Filters.NONE, 10).get(0).element().id()).isEqualTo("reserve.inventory");
        assertThat(q.search("stock", EvlQueries.Filters.NONE, 10).get(0).score()).isEqualTo(20);
        assertThat(q.search("Order Placement", null, 1).get(0).score()).isEqualTo(90);
    }

    @Test
    void librariesAreLoadedReadOnly(@TempDir Path dir) throws Exception {
        Path lib = dir.resolve("payments-sdk");
        Files.createDirectories(lib.resolve("META-INF/evl/documents"));
        Files.writeString(lib.resolve("META-INF/evl/manifest.json"),
            "{\"evl\":\"1.0\",\"id\":\"payments-sdk\",\"name\":\"Payments SDK\",\"kind\":\"LIBRARY\"}");
        Files.writeString(lib.resolve("META-INF/evl/documents/payments.evl.yaml"), """
            flows:
              - id: payment.authorize
                name: Authorize Payment
                category: INTEGRATION
                steps:
                  - { id: call, name: Call PSP, type: CALL_FLOW, flow: payment.capture }
              - { id: payment.capture, name: Capture, category: INTEGRATION }
            """);
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addDocument("a.evl.yaml", """
                flows:
                  - id: checkout
                    name: Checkout
                    category: APPLICATION
                    steps:
                      - { id: pay, name: Pay, type: CALL_FLOW, flow: "payments-sdk:payment.authorize" }
                  - { id: "payments-sdk:payment.authorize", name: Hijack, category: APPLICATION }
                """)
            .addLibrary(lib).build().load();
        Element f = r.graph().element("payments-sdk:payment.authorize").orElseThrow();
        assertThat(f.isLibrary()).isTrue();
        assertThat(r.queries().callees("checkout")).extracting(Element::key).containsExactly("payments-sdk:payment.authorize");
        assertThat(r.queries().callees("payments-sdk:payment.authorize")).extracting(Element::key)
            .containsExactly("payments-sdk:payment.capture");
        assertThat(TestSupport.codes(r)).contains("EVL-E024");
        assertThat(r.graph().libraries()).hasSize(1);
        assertThat(r.queries().flowsCatalogue()).containsKey("Libraries");
    }

    @Test
    void sourceResolverVerifiesAndReports() {
        SourceResolver resolver = new SourceResolver() {
            @Override
            public SourceResolution resolve(io.github.ilyaslabs.evl.core.model.SourceRef ref) {
                return switch (ref.simpleTypeName()) {
                    case "Good" -> SourceResolution.resolved(new Location("Good.java", 3, 1));
                    case "Many" -> SourceResolution.ambiguous(List.of("a.Many#m(int)", "a.Many#m(String)"));
                    case "Kt" -> SourceResolution.UNCHECKED;
                    default -> SourceResolution.unresolved("no such type");
                };
            }

            @Override
            public Optional<Boolean> references(io.github.ilyaslabs.evl.core.model.SourceRef from,
                                                io.github.ilyaslabs.evl.core.model.SourceRef to) {
                return Optional.of(false);
            }
        };
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addDocument("a.evl.yaml", """
                components:
                  - { id: good, name: Good, source: { ref: a.Good } }
                  - { id: many, name: Many, source: { ref: "a.Many#m" } }
                  - { id: missing, name: Missing, source: { ref: a.Missing } }
                  - { id: kt, name: Kt, source: { ref: a.Kt } }
                dependencies:
                  - { from: good, to: kt, type: DIRECT }
                """)
            .sourceResolver(resolver).build().load();
        assertThat(r.graph().element("good").get().evidence()).isEqualTo(Evidence.VERIFIED);
        assertThat(TestSupport.codes(r)).contains("EVL-E030", "EVL-E031", "EVL-I041", "EVL-I040");
    }

    @Test
    void discoverFindsDocuments(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("evl.yaml"), "evl: \"1.0\"\nid: shop\nname: Shop\ndocuments: [\"docs/evl/**/*.evl.yaml\"]\n");
        Files.createDirectories(dir.resolve("docs/evl/sub"));
        Files.createDirectories(dir.resolve("build/docs/evl"));
        Files.writeString(dir.resolve("docs/evl/a.evl.yaml"), "modules:\n  - { id: a, name: A }\n");
        Files.writeString(dir.resolve("docs/evl/sub/b.evl.yaml"), "modules:\n  - { id: b, name: B }\n");
        Files.writeString(dir.resolve("other.evl.yaml"), "modules:\n  - { id: c, name: C }\n");
        EvlResult r = EvlWorkspace.builder().discover(dir).build().load();
        assertThat(r.graph().elementsOf(Kind.MODULE)).extracting(Element::id).containsExactly("a", "b");
        assertThat(r.graph().element("a").get().location().file()).isEqualTo("docs/evl/a.evl.yaml");
    }

    @Test
    void neverThrowsOnGarbage() {
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", "\u0000\u0001: [")
            .addDocument("x.evl.yaml", "- - - : : :").addIndex("index.json", "{nope").build().load();
        assertThat(r.hasErrors()).isTrue();
    }
}
