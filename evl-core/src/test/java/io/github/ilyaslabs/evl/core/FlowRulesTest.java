package io.github.ilyaslabs.evl.core;

import static io.github.ilyaslabs.evl.core.TestSupport.codes;
import static io.github.ilyaslabs.evl.core.TestSupport.load;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.ilyaslabs.evl.core.api.EvlResult;
import io.github.ilyaslabs.evl.core.model.EdgeType;
import io.github.ilyaslabs.evl.core.model.Element;
import io.github.ilyaslabs.evl.core.model.StepNode;
import org.junit.jupiter.api.Test;

class FlowRulesTest {

    private static final String GOOD = """
        flows:
          - id: order.placement
            name: Order Placement
            category: BUSINESS
            inputs: [{ name: request }]
            steps:
              - { id: validate, type: ACTION, name: Validate, source: { ref: "com.example.OrderValidator#validate" } }
              - { id: reserve, type: CALL_FLOW, name: Reserve, flow: inventory.reserve, inputs: { sku: request.sku } }
              - id: stock-ok
                type: DECISION
                name: Stock ok?
                branches:
                  - when: reserved
                    steps:
                      - { id: pay, type: ACTION, name: Pay, realizedBy: [inventory.reserve] }
                  - when: out of stock
                    steps:
                      - { id: reject, type: FAIL, name: Reject, error: order.out-of-stock }
              - id: confirm
                type: ACTION
                name: Confirm
                errors:
                  - { on: declined, then: [ { id: release, type: CALL_FLOW, name: Release, flow: inventory.reserve } ] }
          - id: inventory.reserve
            name: Reserve Inventory
            category: TECHNICAL
            inputs: [{ name: sku }]
        """;

    @Test
    void buildsBlockStructure() {
        EvlResult r = load(GOOD);
        assertThat(r.diagnostics()).filteredOn(d -> d.level().ordinal() <= 1).isEmpty();
        var f = r.graph().flow("order.placement").orElseThrow();
        assertThat(f.steps()).extracting(StepNode::key).containsExactly("order.placement/validate",
            "order.placement/reserve", "order.placement/stock-ok", "order.placement/confirm");
        StepNode decision = f.steps().get(2);
        assertThat(decision.blocks()).hasSize(2);
        assertThat(decision.blocks().get(0).label()).isEqualTo("reserved");
        assertThat(r.graph().outgoing("order.placement/validate", EdgeType.CONTROL_NEXT)).hasSize(1);
        assertThat(r.queries().callees("order.placement")).extracting(Element::id).containsExactly("inventory.reserve");
        assertThat(r.queries().callers("inventory.reserve")).extracting(Element::id).containsExactly("order.placement");
        assertThat(r.queries().realizations("order.placement/pay")).extracting(Element::id).containsExactly("inventory.reserve");
        assertThat(codes(r)).contains("EVL-I030"); // reserve? no: CALL_FLOW without realizedBy/source in BUSINESS flow
    }

    @Test
    void flowErrors() {
        EvlResult r = load("""
            components:
              - { id: comp, name: Comp }
            flows:
              - id: f
                name: F
                category: APPLICATION
                steps:
                  - { id: a, type: WIGGLE, name: A }
                  - { id: a, type: ACTION, name: Dup }
                  - { id: g, type: GOTO, name: G, target: nowhere }
                  - { id: d, type: DECISION, name: D, branches: [ { when: x, steps: [] } ] }
                  - { id: p, type: PARALLEL, name: P, branches: [ { name: x, steps: [] } ] }
                  - { id: c, type: CALL_FLOW, name: C, flow: comp }
                  - { id: c2, type: CALL_FLOW, name: C2, flow: f, inputs: { bogus: 1 } }
                  - { id: q, type: ACTION, name: Q, question: nope }
                  - { id: m, type: CALL_FLOW, name: M }
                  - { id: e, type: END, name: E }
                  - { id: after, type: ACTION, name: After }
                  - { id: loop, type: GOTO, name: Loop, target: a }
            """);
        assertThat(codes(r)).contains("EVL-E060", "EVL-E061", "EVL-E062", "EVL-E063", "EVL-E064", "EVL-E065",
            "EVL-E066", "EVL-E067", "EVL-W066", "EVL-W021");
    }

    @Test
    void unconditionalBackwardGoto() {
        EvlResult r = load("""
            flows:
              - id: f
                name: F
                category: APPLICATION
                steps:
                  - { id: a, name: A }
                  - { id: back, type: GOTO, name: Back, target: a }
            """);
        assertThat(codes(r)).contains("EVL-W067");
    }

    @Test
    void inputMismatch() {
        EvlResult r = load("""
            flows:
              - { id: child, name: Child, category: TECHNICAL, inputs: [{ name: sku }] }
              - id: parent
                name: Parent
                category: APPLICATION
                steps:
                  - { id: call, type: CALL_FLOW, name: Call, flow: child, inputs: { qty: 1 } }
            """);
        assertThat(codes(r)).contains("EVL-E068");
    }
}
