# Annotation Catalogue

Normative. Published from `evl-lib/spec/annotations.md`. Implemented in `evl-annotations`.

## 4.1 Design rules

- **EVL-ANN-001** All annotations are nested in one holder class: `io.github.ilyaslabs.evl.EVL` (a `final` class with a private constructor). Users write `import io.github.ilyaslabs.evl.EVL;` and `@EVL.Component(...)`.
- **EVL-ANN-002** Retention is `RetentionPolicy.CLASS` for every annotation (D10). No annotation is visible at runtime by reflection.
- **EVL-ANN-003** The jar has zero dependencies, targets Java 8 (`--release 8`), and is a valid JPMS module (`Automatic-Module-Name: io.github.ilyaslabs.evl` plus a `module-info.class` compiled in a multi-release layer for Java 9+).
- **EVL-ANN-004** Java annotation attributes cannot be `null`. "Not set" is the empty string `""`, an empty array `{}`, or the enum constant `UNSPECIFIED`. The processor treats these as absent.
- **EVL-ANN-005** Annotations that may appear more than once on one declaration are `@Repeatable` with a nested container (`@EVL.Dependency.List`).
- **EVL-ANN-006** Every annotation has JavaDoc with a purpose, valid targets, one complete example, and a link to the spec page. JavaDoc examples are compiled in tests.
- **EVL-ANN-007** Every annotation that declares an element has the common attributes: `id`, `name`, `description`, `tags`. Their meaning is identical everywhere and is not repeated in the tables below.
- **EVL-ANN-008** Annotations only declare *facts about a declaration*. Ordering, branching and other workflow structure are YAML-only (D3). There is no annotation that encodes step order.
- **EVL-ANN-009** Kotlin: all annotations work on Kotlin declarations. Use-site targets are documented (`@field:EVL.Field`, `@get:EVL.ConfigProperty`).

## 4.2 ID derivation

When `id` is `""` the processor derives one: lowercase the simple name, split camelCase with `-`, and prefix with the owning module id when known. Example: `OrderService` in module `order` → `order.order-service`. Derived IDs produce weak warning `EVL-W002`. Derived IDs change when classes are renamed, which is why explicit IDs are recommended.

## 4.3 Catalogue

Changes from the idea document are marked **(changed)** or **(new)**. Review these first.

### Architecture

| Annotation | Targets | Attributes (beyond common) | Notes |
|---|---|---|---|
| `@EVL.Application` **(new)** | PACKAGE | `domain` | Optional; adds metadata to the manifest-declared application (EVL-LNG-003). At most one per application. |
| `@EVL.Module` | PACKAGE, TYPE | `domain`, `responsibilities[]`, `owner` (team or person, free text), `parent` (module ref) | On `package-info.java` the module covers that package and sub-packages. Modules can nest via `parent`. |
| `@EVL.Component` | TYPE | `module` (ref), `responsibilities[]`, `layer` (enum `Layer`: `PRESENTATION, APPLICATION, DOMAIN, INFRASTRUCTURE, INTEGRATION, CUSTOM, UNSPECIFIED`) | If `module` is empty, the module is inherited from the nearest `@EVL.Module` package. |
| `@EVL.Dependency` (repeatable) | TYPE, PACKAGE | `target` (ref, required), `type` (enum `DependencyType`), `reason`, `direction` (`OUTBOUND` default, `INBOUND`, `BIDIRECTIONAL`), `optional` (boolean) | Declared on the source element. |
| `@EVL.Interface` | TYPE | `module`, `responsibilities[]`, `stability` (`STABLE, EVOLVING, EXPERIMENTAL, DEPRECATED, UNSPECIFIED`) | For important contracts (Java interfaces, abstract classes, or SPI). |
| `@EVL.Implementation` **(changed: was InterfaceImplementation)** | TYPE | `of` (ref to interface, required), `variant` (free text, e.g. "Stripe") | Declared implementations are DECLARED; implementations found by class hierarchy only are INFERRED. |

`DependencyType`: `DIRECT, API, EVENT, DATABASE, CACHE, EXTERNAL, ASYNC, CONFIG, CUSTOM`, plus `customType` string attribute.

### API

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.Endpoint` | METHOD, TYPE | `protocol` (`HTTP, GRPC, GRAPHQL, WEBSOCKET, MESSAGING, RMI, CLI, CUSTOM`), `method` (e.g. `POST`), `path` (or operation name), `request` (ref or `Class<?>`), `response` (ref or `Class<?>`), `errors[]` (refs), `auth` (free text), `flow` (ref), `component` (ref; default enclosing component) | `request`/`response` accept either an EVL ref string (`requestRef`) or a class literal (`requestType`); exactly one may be set. |
| `@EVL.Request` | TYPE | `endpoint[]` (refs), `validation` (free text) | |
| `@EVL.Response` | TYPE | `endpoint[]` (refs) | |
| `@EVL.ErrorResponse` (repeatable) | TYPE, METHOD | `code` (e.g. `409`, `NOT_FOUND`), `condition`, `retryable` (boolean) | On a type: declares an error contract. On an endpoint method: declares that the endpoint returns it. |

### Data

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.DataModel` | TYPE | `module`, `owner` (ref to module/component) **(changed: replaces @EVL.DataOwnership)**, `storage` (free text, e.g. "postgres.orders"), `lifecycle` (free text) **(changed: replaces @EVL.DataLifecycle)**, `retention` (free text), `pii` (boolean) | Ownership and lifecycle were separate annotations; they are now attributes because they only make sense on a data model. Rich lifecycle state machines go in YAML. |
| `@EVL.Field` | FIELD, METHOD (record components and getters) | `meaning`, `required` (boolean), `unit`, `sensitive` (boolean), `example` | Only annotated fields appear in EVL (EVL-PRD-001). |
| `@EVL.Relationship` (repeatable) | FIELD, TYPE | `target` (ref to data model), `cardinality` (`ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY`), `ownership` (`COMPOSITION, AGGREGATION, REFERENCE`) | |
| `@EVL.Index` (repeatable) | TYPE | `fields[]`, `unique` (boolean), `reason` | Documents important access paths. |
| `@EVL.Repository` | TYPE | `models[]` (refs), `storage` | Persistence boundary. |

### Workflow

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.Flow` | METHOD, TYPE | `category` (`BUSINESS, APPLICATION, INTEGRATION, BACKGROUND, SCHEDULED, TECHNICAL, CUSTOM`), `domain`, `module`, `trigger` (free text) | Declares a flow whose **entry point** is the annotated method. Steps come from a `.evl.yaml` flow with the same `id` (merged per D22) or from `@EVL.Step` annotations. A flow with no steps is valid and renders as one node. |
| `@EVL.Step` (repeatable) | METHOD | `flow` (ref, required), `step` (local step id, required), `type` (step type, default `ACTION`) | Links a method to a step. If the YAML flow defines the step, this only adds a VERIFIED source. If it does not, the step is appended to the flow's top-level sequence in source-declaration order, with weak warning `EVL-W060 step order from annotations` (order across files is not guaranteed). |
| `@EVL.Call` (repeatable) | METHOD | `target` (ref to flow, endpoint, component, external system or event), `kind` (`SYNC, ASYNC, EVENT, SCHEDULED`), `reason` | Declares that this method calls another documented element. Shown as a declared edge. |

### Messaging

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.Event` | TYPE | `channel` (topic/queue name), `delivery` (`AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE, UNSPECIFIED`), `ordering` (free text), `schema` (free text or ref), `broker` (ref to external system or infrastructure) | |
| `@EVL.Producer` (repeatable) | METHOD, TYPE | `event` (ref, required), `when` (free text) | |
| `@EVL.Consumer` (repeatable) | METHOD, TYPE | `event` (ref, required), `retry` (free text), `onFailure` (free text), `idempotent` (boolean), `flow` (ref) | A framework listener is not a consumer unless annotated or declared in YAML. |

### Infrastructure

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.ExternalSystem` | TYPE, PACKAGE | `systemType` (`API, DATABASE, BROKER, STORAGE, SEARCH, IDENTITY, PAYMENT, EMAIL, CUSTOM`), `url` (documentation URL, never credentials), `owner` | Usually declared on the client/adapter class. |
| `@EVL.Cache` | TYPE, METHOD, FIELD | `store` (free text), `ttl` (free text), `key` (free text), `invalidation` (free text) | |
| `@EVL.Infrastructure` | TYPE, PACKAGE | `resourceType` (free text, e.g. "S3 bucket"), `environment[]` | |

### Configuration

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.Configuration` | TYPE | `prefix` (property prefix), `component` (ref) | A configuration group. |
| `@EVL.ConfigProperty` (repeatable) **(changed: was Config)** | FIELD, METHOD, TYPE | `key` (required on TYPE), `defaultValue`, `required` (boolean), `sensitive` (boolean), `environments[]`, `affects[]` (refs) | Renamed so it is clearly different from `@EVL.Configuration`. If `sensitive = true`, `defaultValue` MUST be empty (error `EVL-E070`). |

### Knowledge

| Annotation | Targets | Attributes | Notes |
|---|---|---|---|
| `@EVL.Doc` **(changed: replaces @EVL.Documentation and @EVL.Explain)** | any declaration | `value` (Markdown), `see[]` (URLs or refs), `requirements[]` (refs), `decisions[]` (refs), `concurrency`, `performance`, `security`, `failure` | Explains any EVL-annotated declaration. On a declaration with no other EVL annotation it is attached to the nearest enclosing EVL element (method → class → package); if none exists, warning `EVL-W071 orphan doc`. |
| `@EVL.Decision` (repeatable) | TYPE, METHOD, PACKAGE | `status` (`PROPOSED, ACCEPTED, SUPERSEDED, DEPRECATED`), `context`, `decision`, `reasoning`, `alternatives[]`, `consequences`, `supersedes` (ref), `date` (ISO date) | ADR-style. Long decisions belong in YAML. |
| `@EVL.Requirement` (repeatable) | TYPE, METHOD, PACKAGE | `kind` (`BUSINESS, FUNCTIONAL, NON_FUNCTIONAL, SECURITY, COMPLIANCE`), `statement`, `source` (ticket URL or text), `priority` (`MUST, SHOULD, COULD`) | Traceability: elements link to requirements with `@EVL.Doc(requirements = ...)`. |

## 4.4 Example

```java
@EVL.Component(
    id = "order.service",
    name = "Order Service",
    module = "order",
    responsibilities = {"Create orders", "Coordinate fulfilment"})
@EVL.Dependency(target = "inventory.reservations", type = DependencyType.API,
    reason = "Reserve stock before payment")
public class OrderService {

  @EVL.Flow(id = "order.placement", name = "Order Placement", category = Category.BUSINESS)
  @EVL.Doc(value = "Entry point for checkout.", requirements = "req.checkout-under-2s")
  public Order placeOrder(CreateOrderRequest request) { ... }

  @EVL.Step(flow = "order.placement", step = "validate")
  void validate(CreateOrderRequest request) { ... }
}
```

## 4.5 Acceptance

- Every annotation compiles on Java 8, 11, 17, 21 and 25 and on Kotlin 2.x (test matrix).
- A reflection test asserts every annotation's retention is CLASS and targets match this table.
- An API-compatibility check (japicmp) fails the build on any binary-incompatible change after 1.0.
- The jar size is ≤ 60 KB and has no dependencies.
