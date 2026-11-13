package io.github.ilyaslabs.evl.core;

import static io.github.ilyaslabs.evl.core.TestSupport.codes;
import static io.github.ilyaslabs.evl.core.TestSupport.load;
import static io.github.ilyaslabs.evl.core.TestSupport.rec;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.api.EvlWorkspace;
import io.github.ilyaslabs.evl.core.model.Diagnostic;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.Evidence;
import io.github.ilyaslabs.evl.core.model.OriginKind;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord.ElementKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class MergeAndReferenceTest {

    private static final String PKG = "com.example.order";

    @Test
    void yamlOverridesAnnotationFieldByField() {
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addAnnotations("OrderService.java", List.of(
                rec("Component", PKG + ".OrderService", ElementKind.TYPE, PKG, 10, "id", "order.service",
                    "name", "Order Service", "description", "From code", "responsibilities", List.of("a", "b"))))
            .addDocument("order.evl.yaml", """
                components:
                  - id: order.service
                    description: From YAML
                """)
            .build().load();
        Element e = r.graph().element("order.service").orElseThrow();
        assertThat(e.name()).isEqualTo("Order Service");
        assertThat(e.description()).isEqualTo("From YAML");
        assertThat(e.strings("responsibilities")).containsExactly("a", "b");
        assertThat(e.fieldOrigins().get("description").kind()).isEqualTo(OriginKind.DOCUMENT);
        assertThat(e.fieldOrigins().get("name").kind()).isEqualTo(OriginKind.ANNOTATION);
        assertThat(e.evidence()).isEqualTo(Evidence.VERIFIED);
        Diagnostic i020 = r.diagnostics().stream().filter(d -> d.code().equals("EVL-I020")).findFirst().orElseThrow();
        assertThat(i020.message()).contains("description").contains("order.evl.yaml");
    }

    @Test
    void conflictingSourceAndDuplicates() {
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addAnnotations("a", List.of(
                rec("Component", PKG + ".OrderService", ElementKind.TYPE, PKG, 1, "id", "order.service"),
                rec("Component", PKG + ".Other", ElementKind.TYPE, PKG, 1, "id", "dup"),
                rec("Component", PKG + ".Other2", ElementKind.TYPE, PKG, 1, "id", "dup")))
            .addDocument("a.evl.yaml", """
                components:
                  - id: order.service
                    source: { ref: com.example.order.Elsewhere }
                  - { id: y, name: Y }
                  - { id: y, name: Y2 }
                """)
            .build().load();
        assertThat(codes(r)).contains("EVL-E020").filteredOn("EVL-E010"::equals).hasSize(2);
    }

    @Test
    void referenceErrors() {
        EvlResult r = load("""
            modules:
              - { id: order, name: Order }
            components:
              - { id: order.service, name: Svc, module: ordr }
              - { id: order.api, name: Api, module: "component:order" }
              - { id: order.x, name: X, module: order.service }
              - { id: order.y, name: Y, module: "payments-sdk:payments" }
            """);
        assertThat(codes(r)).contains("EVL-E025", "EVL-E021", "EVL-E023", "EVL-E022");
        Diagnostic e025 = r.diagnostics().stream().filter(d -> d.code().equals("EVL-E025")).findFirst().orElseThrow();
        assertThat(e025.message()).contains("did you mean 'order'");
        assertThat(e025.fixes()).isNotEmpty();
    }

    @Test
    void containmentEdgesAndDefaults() {
        EvlResult r = load("""
            modules:
              - { id: order, name: Order, packages: ["com.example.order.**"] }
            components:
              - { id: order.service, name: Svc, source: { ref: com.example.order.OrderService } }
              - { id: misc, name: Misc }
            """);
        var q = r.queries();
        assertThat(q.children("order")).extracting(Element::id).containsExactly("order.service");
        assertThat(q.pathToRoot("order.service")).extracting(Element::id).containsExactly("shop", "order", "order.service");
        assertThat(q.parents("misc")).extracting(Element::id).containsExactly("shop");
    }

    @Test
    void annotationMappingDerivesIdsAndInheritsModules() {
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST)
            .addAnnotations("a", List.of(
                rec("Module", "package:" + PKG, ElementKind.PACKAGE, PKG, 1, "id", "order", "name", "Order"),
                rec("Component", PKG + ".OrderService", ElementKind.TYPE, PKG, 3),
                rec("Dependency", PKG + ".OrderService", ElementKind.TYPE, PKG, 4, "target", "order"),
                rec("Doc", PKG + ".OrderService#place(Order)", ElementKind.METHOD, PKG, 8, "value", "Places"),
                rec("Flow", PKG + ".OrderService#place(Order)", ElementKind.METHOD, PKG, 9, "id", "order.placement",
                    "category", "BUSINESS"),
                rec("Step", PKG + ".OrderService#validate()", ElementKind.METHOD, PKG, 12, "flow", "order.placement",
                    "step", "validate"),
                rec("Doc", "com.other.Lonely", ElementKind.TYPE, "com.other", 1, "value", "orphan")))
            .build().load();
        Element c = r.graph().element("order.order-service").orElseThrow();
        assertThat(c.name()).isEqualTo("Order Service");
        assertThat(r.queries().parents(c.key())).extracting(Element::id).containsExactly("order");
        assertThat(r.graph().element("order.placement").orElseThrow().string("doc")).contains("Places");
        assertThat(r.graph().outgoing("order.order-service", EdgeType.DEPENDS_ON)).hasSize(1);
        assertThat(r.queries().steps("order.placement")).extracting(Element::id).containsExactly("order.placement/validate");
        assertThat(codes(r)).contains("EVL-W002", "EVL-W060", "EVL-W071");
    }

    @Test
    void ruleLevelsAreConfigurable() {
        EvlResult r = EvlWorkspace.builder().manifest("evl.yaml", TestSupport.MANIFEST + "rules:\n  EVL-E106: off\n  EVL-E104: off\n")
            .addDocument("a.evl.yaml", "flows:\n  - { id: f, name: F }\nbroken: [\n")
            .build().load();
        assertThat(codes(r)).doesNotContain("EVL-E106").contains("EVL-E104");
    }
}
