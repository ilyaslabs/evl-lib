# Workflow Model

Normative. A **flow** is a documented sequence of activities. Flows are block-structured (D3): a flow body is a sequence of steps, and some steps contain nested sequences.

## 6.1 Flow fields

```yaml
flows:
  - id: order.placement
    name: Order Placement
    category: BUSINESS          # BUSINESS | APPLICATION | INTEGRATION | BACKGROUND | SCHEDULED | TECHNICAL | CUSTOM
    domain: sales
    module: order
    actors: [actor.customer]
    trigger: "Customer clicks Place order"
    entryPoint: { ref: "com.example.order.OrderController#createOrder(CreateOrderRequest)" }
    preconditions: ["Cart is not empty"]
    postconditions: ["Order is PLACED or rejected with a reason"]
    inputs:  [{ name: request, type: order.create-request }]
    outputs: [{ name: order, type: order.order }]
    context: [{ name: correlationId, description: "Propagated to all child flows" }]
    implementedBy: [order.placement.technical]   # D18, business → technical (flow level)
    steps: [ ... ]
```

- **EVL-WFL-001** `steps` is optional. A flow without steps renders as a single node and is valid.
- **EVL-WFL-002** A flow declared with `@EVL.Flow` and a YAML flow with the same `id` merge per EVL-DOC-020; YAML usually supplies the steps.
- **EVL-WFL-003** `category` is required in YAML (default `APPLICATION` for annotations, with weak warning).

## 6.2 Step types

| Type | Fields | Semantics |
|---|---|---|
| `ACTION` | `source`, `component`, `realizedBy` | One activity. |
| `DECISION` | `question`, `branches: [{ when, steps }]`, optional `otherwise: steps` | Exactly one branch runs. Branches rejoin after the decision (implicit join). |
| `PARALLEL` | `branches: [{ name, steps }]`, `join: ALL \| ANY \| NONE` (default `ALL`) | Branches run concurrently; `join` defines when the flow continues. `NONE` = fire-and-forget. |
| `CALL_FLOW` | `flow` (ref, required), `inputs: {param: expr}`, `outputs: {param: name}`, `async` (bool) | Invokes another flow. The child flow keeps its own identity (one definition, any number of callers). |
| `EVENT` | `event` (ref), `direction: PUBLISH \| CONSUME` | Publishes, or waits for and consumes, an event. |
| `WAIT` | `for` (text), `timeout` (ISO-8601 duration), `onTimeout: steps` | Waits for a condition, response or time. |
| `RETRY` | `policy: { maxAttempts, backoff, retryOn[] }`, `steps` | Runs its nested steps with the retry policy. |
| `COMPENSATE` | `for` (step id(s)), `steps` | Compensating actions, run when a later failure unwinds. |
| `FAIL` | `error` (ref to error response or text), `reason` | Terminates the flow (or current `tryCatch` scope) with a failure. |
| `END` | `outcome` (text) | Explicit early successful termination. Normal end is implicit after the last step. |
| `GOTO` | `target` (step id within the same flow), `when` (text) | Escape hatch for loops and jumps (D3). |
| `NOTE` | `text` | Annotation shown on the diagram; does not affect control flow. |

Every step has `id` (required, EVL-LNG-012), `name` (required except `NOTE`), `description`, `doc`, `source`, `requirements`, `decisions`, `tags`, and `errors` (see 6.4).

**Changes from the idea document:** `START` and `JOIN` are implicit and no longer step types (D3). `FAILURE` is renamed `FAIL`, `COMPENSATION` is renamed `COMPENSATE`, and `GOTO` is new. `RETRY` now wraps the steps it retries.

## 6.3 Example

```yaml
flows:
  - id: order.placement
    name: Order Placement
    category: BUSINESS
    steps:
      - { id: validate, type: ACTION, name: Validate Order,
          source: { ref: "com.example.order.OrderValidator#validate" } }
      - id: reserve
        type: CALL_FLOW
        name: Reserve Inventory
        flow: inventory.reserve
      - id: stock-ok
        type: DECISION
        name: Stock available?
        branches:
          - when: reserved
            steps:
              - { id: authorize, type: CALL_FLOW, name: Authorize Payment, flow: payment-sdk:payment.authorize }
          - when: out of stock
            steps:
              - { id: reject, type: FAIL, name: Reject Order, error: order.out-of-stock }
      - id: confirm
        type: ACTION
        name: Confirm Order
        realizedBy: [order.confirmation.technical]
        errors:
          - { on: payment.declined, then: [ { id: release, type: CALL_FLOW, name: Release Stock, flow: inventory.release } ] }
```

## 6.4 Error paths

- **EVL-WFL-010** Any step may declare `errors: [{ on, then }]`. `on` is an error ref or text, and `then` is a nested step sequence. After `then`, the flow ends with failure unless `then` ends with `GOTO`.
- **EVL-WFL-011** A flow-level `onError: steps` handles failures not handled by a step.
- **EVL-WFL-012** `COMPENSATE` steps are collected in reverse order when a failure unwinds past the step they compensate. The diagram shows them on a dedicated compensation lane.

## 6.5 Nested and reusable flows

- **EVL-WFL-020** A flow called from N parents has exactly one definition and one node identity. Views reference it; they never copy it.
- **EVL-WFL-021** Call cycles (A → B → A, via `CALL_FLOW` at any depth) are reported as warning `EVL-W021 recursive flow` (recursion can be legitimate). The canvas stops expansion at the repeated flow with a "↻ already open above" marker.
- **EVL-WFL-022** Input/output mapping: `inputs` keys MUST match the child's declared `inputs[].name` (error `EVL-E068` otherwise); unmapped required inputs are warnings.
- **EVL-WFL-023** Navigation is bidirectional: the graph indexes every caller of every flow ("Called by" in the Inspector and Flow Explorer).

## 6.6 Business and technical perspectives

- **EVL-WFL-030** Any step may declare `realizedBy: [refs]`, pointing at flows, steps, endpoints, components or events that implement it (D18). A flow may declare `implementedBy: [flow refs]`.
- **EVL-WFL-031** On the canvas, zooming into a business step with `realizedBy` reveals the realizing technical flow(s) inside it, exactly like a `CALL_FLOW` (see [09](https://ilyaslabs.github.io/evl-lib/)). This lets a user zoom from business intent down to code without switching views.
- **EVL-WFL-032** `realizedBy`/`implementedBy` are documentation links, not control flow. They are drawn in a distinct style (thin dotted, labeled "realized by").
- **EVL-WFL-033** Info diagnostic `EVL-I030` lists BUSINESS flow steps without `realizedBy` and without `source` (unrealized business steps), so teams can track coverage.

## 6.7 Validation rules for flows

| Code | Level | Rule |
|---|---|---|
| `EVL-E060` | error | Unknown step type |
| `EVL-E061` | error | Duplicate step id in a flow |
| `EVL-E062` | error | `GOTO` target does not exist in the same flow |
| `EVL-E063` | error | `DECISION` with fewer than 2 branches (counting `otherwise`) |
| `EVL-E064` | error | `PARALLEL` with fewer than 2 branches |
| `EVL-E065` | error | `CALL_FLOW` target is not a flow |
| `EVL-E068` | error | `CALL_FLOW` input name not declared by the child flow |
| `EVL-W066` | warning | Steps after `FAIL`, `END` or an unconditional `GOTO` are unreachable |
| `EVL-W067` | warning | `GOTO` creates a loop with no `when` condition |
| `EVL-W021` | warning | Recursive flow call |
| `EVL-I030` | info | Unrealized business step |

## 6.8 Visual sequence is not runtime proof

A flow describes documented intent. The canvas labels flow diagrams "Documented flow". Nothing in EVL claims a step executed, and step order is the documented order only.
