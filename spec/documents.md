# Document Schema (`.evl.yaml`)

Normative. JSON Schemas live in `evl-lib/spec/schema/` and are published at a stable URL (`https://ilyaslabs.github.io/evl-lib/schema/1.0/evl-document.schema.json`) and registered with SchemaStore so any editor validates `*.evl.yaml` without EVL installed.

## 5.1 File rules

- **EVL-DOC-001** Files are named `*.evl.yaml` (D12). The root manifest is `evl.yaml` ([03 §3.1](language.md#31-evl-application-and-manifest)).
- **EVL-DOC-002** YAML 1.2 core schema only. Anchors and aliases are allowed (alias expansion is limited to 10,000 nodes). Custom tags (`!foo`) are rejected (`EVL-E100`). Duplicate keys are rejected (`EVL-E101`).
- **EVL-DOC-003** Each file has an optional header `evl: "1.0"` and any number of top-level sections, one per element kind (plural keys below). A file may mix kinds.
- **EVL-DOC-004** Unknown top-level keys are errors in the declared version (`EVL-E102`), except keys starting with `x-`, which are preserved as extension metadata and shown in the Inspector.
- **EVL-DOC-005** Comment directives are not part of 1.0. YAML comments are ignored, and source positions (file, line, column) are kept for every key and value.

## 5.2 Element kinds

Every element shares the common fields:

```yaml
id: order.placement          # required (EVL-LNG-010)
name: Order Placement        # required in YAML; annotations default it from the Java name
description: "Markdown text"
tags: [checkout, critical]
doc: "Longer Markdown"       # same as @EVL.Doc.value
see: ["https://…", "evl:payment.authorize"]
requirements: [req.checkout-under-2s]
decisions: [adr.saga-over-2pc]
source: { ref: com.example.order.OrderService }   # where it lives in code (optional)
x-team: payments             # extensions
```

| Section key | Kind keyword | Kind-specific fields (mirror the annotation attributes in [04](annotations.md)) |
|---|---|---|
| `application` (single object, manifest only) | `application` | `domain` |
| `modules` | `module` | `domain`, `responsibilities`, `owner`, `parent`, `packages` (package globs the module covers) |
| `components` | `component` | `module`, `responsibilities`, `layer` |
| `dependencies` | `dependency` | `from` (ref), `to` (ref), `type`, `customType`, `reason`, `direction`, `optional` |
| `interfaces` | `interface` | `module`, `responsibilities`, `stability` |
| `implementations` | `implementation` | `of`, `variant` |
| `endpoints` | `endpoint` | `protocol`, `method`, `path`, `request`, `response`, `errors`, `auth`, `flow`, `component` |
| `requests` / `responses` / `errors` | `request` / `response` / `error` | as annotations, plus `fields` (inline field docs) |
| `dataModels` | `data-model` | `module`, `owner`, `storage`, `lifecycle` (string or state machine, below), `retention`, `pii`, `fields`, `relationships`, `indexes` |
| `repositories` | `repository` | `models`, `storage` |
| `events` | `event` | `channel`, `delivery`, `ordering`, `schema`, `broker`, `producers` (refs), `consumers` (list of `{ref, retry, onFailure, idempotent, flow}`) |
| `externalSystems` | `external-system` | `systemType`, `url`, `owner` |
| `caches` | `cache` | `store`, `ttl`, `key`, `invalidation` |
| `infrastructure` | `infrastructure` | `resourceType`, `environments` |
| `configurations` | `configuration` | `prefix`, `component`, `properties` (list of config properties) |
| `configProperties` | `config-property` | `key`, `defaultValue`, `required`, `sensitive`, `environments`, `affects` |
| `flows` | `flow` | see [06](workflows.md) |
| `decisions` | `decision` | `status`, `context`, `decision`, `reasoning`, `alternatives`, `consequences`, `supersedes`, `date`, `relates` (refs) |
| `requirements` | `requirement` | `kind`, `statement`, `ticket`, `priority`, `relates` (refs) |
| `actors` **(new)** | `actor` | `actorType` (`HUMAN, SYSTEM, SCHEDULER`) — who triggers business flows |

Data model lifecycle as a state machine (optional richer form):

```yaml
dataModels:
  - id: order.order
    name: Order
    source: { ref: com.example.order.Order }
    owner: order
    lifecycle:
      states: [DRAFT, PLACED, PAID, SHIPPED, CANCELLED]
      transitions:
        - { from: DRAFT, to: PLACED, by: order.placement/confirm-order }
        - { from: PLACED, to: CANCELLED, by: order.cancellation }
```

## 5.3 Declaring code elements in YAML

Any element can be declared purely in YAML and attached to code through `source.ref` (EVL-LNG-030). Examples:

```yaml
components:
  - id: order.service
    name: Order Service
    module: order
    source: { ref: com.example.order.OrderService }

endpoints:
  - id: order.create
    name: Create Order
    protocol: HTTP
    method: POST
    path: /orders
    request: order.create-request
    source: { ref: "com.example.order.OrderController#createOrder(CreateOrderRequest)" }

dataModels:
  - id: order.order
    name: Order
    source: { ref: com.example.order.Order }
    fields:
      - { name: total, source: { ref: "com.example.order.Order#total" }, meaning: "Gross amount incl. tax", unit: EUR }
```

- **EVL-DOC-010** A YAML element with a `source.ref` that resolves is shown as VERIFIED and navigates to code exactly like an annotated one. If the reference does not resolve, the element still loads (UNRESOLVED badge) and error `EVL-E030 unresolved source reference` is reported.
- **EVL-DOC-011** The plugin MUST offer "Convert to annotation" and "Move to YAML" intentions for every element kind, each producing the equivalent declaration and removing the original.

## 5.4 Dual authoring and overrides

An element may be declared by an annotation, by YAML, or by both (D22).

- **EVL-DOC-020** If an annotation and a YAML element share an `id` within one application, they are **merged**: every field present in YAML replaces the annotation's value; fields absent in YAML keep the annotation's value. Lists are replaced, not concatenated.
- **EVL-DOC-021** The merged element's `source` is the annotated declaration unless YAML sets `source.ref` explicitly. If YAML sets a different `source.ref`, that is error `EVL-E020 conflicting source` (an ID cannot live in two places).
- **EVL-DOC-022** Overrides MUST be visible:
  - Info diagnostic `EVL-I020 overridden by <file>:<line>` lists each overridden field.
  - The plugin shows an inlay hint on the annotation ("overridden by orders.evl.yaml").
  - The Inspector shows the origin (annotation or file:line) of every field.
- **EVL-DOC-023** Two YAML declarations with the same ID are error `EVL-E010` (duplicate), as are two annotations with the same ID. Only the annotation + YAML pair merges.
- **EVL-DOC-024** Library elements (from a dependency jar) are read-only and cannot be overridden by the consuming application. A consumer may only reference them. An attempt is error `EVL-E024`.

## 5.5 Library packaging

- **EVL-DOC-030** The processor and the build plugins package an application's EVL data into its jar at `META-INF/evl/`:
  - `manifest.json`: the EVL id, name, kind, spec version and artifact coordinates.
  - `index.json`: all annotation declarations extracted at compile time.
  - `documents/**/*.evl.yaml`: copies of the application's documents.
- **EVL-DOC-031** `index.json` format is defined by `spec/schema/evl-index.schema.json`. Its declarations use the same field names as YAML, plus `origin` (`{kind: "annotation", type, member, line}`).
- **EVL-DOC-032** The plugin and CLI load every jar on the classpath that contains `META-INF/evl/manifest.json` as a library application. Two libraries with the same EVL id on one classpath is error `EVL-E032`.
