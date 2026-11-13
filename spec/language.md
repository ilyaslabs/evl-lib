# Language Fundamentals

This chapter is normative spec content. It is published from `evl-lib/spec/language.md`.

## 3.1 EVL application and manifest

An **EVL application** is the unit of documentation and of ID uniqueness. It is defined by exactly one root manifest file named `evl.yaml`. A library is also an EVL application (one that is consumed by others).

```yaml
# evl.yaml
evl: "1.0"                 # spec version this project is written against (required)
id: shop                   # EVL application id (required)
name: Shop                 # display name (required)
description: Online shop backend.
kind: APPLICATION          # APPLICATION | LIBRARY (default APPLICATION)
documents:                 # globs relative to this file (default: ["**/*.evl.yaml"])
  - "docs/evl/**/*.evl.yaml"
sources:                   # source roots to scan for annotations (default: build-tool source sets)
  - "src/main/java"
  - "src/main/kotlin"
exclude:
  - "**/generated/**"
dependencies:              # optional: EVL ids of libraries this app expects to find on the classpath
  - payments-sdk
settings:
  inferred: false          # default for "Show inferred" in the plugin (personal state may override)
```

- **EVL-LNG-001** A project MUST have exactly one `evl.yaml` per EVL application. A multi-application repository has one per application root; documents and sources belong to the nearest manifest above them.
- **EVL-LNG-002** A project without `evl.yaml` that contains EVL annotations is treated as an application with `id` derived from the build module name, plus a warning `EVL-W001 missing manifest` with a quick-fix that creates the file.
- **EVL-LNG-003** The application element itself (canvas Level 1) is declared by the manifest. `@EVL.Application` on a `package-info` MAY add metadata; the manifest wins on conflict (it is YAML, D22).

## 3.2 Identifiers

Grammar (ABNF):

```
element-id   = segment *( "." segment )
segment      = lower *( lower / digit / "-" )
lower        = %x61-7A
app-id       = segment *( "-" segment )   ; e.g. payments-sdk
```

- **EVL-LNG-010** Every independently addressable element (application, module, component, interface, endpoint, data model, event, external system, flow, requirement, decision, configuration, config property, and others in [05](documents.md#52-element-kinds)) MUST have an `id`.
- **EVL-LNG-011** IDs are unique across all kinds within one EVL application (D6). A duplicate is error `EVL-E010`, except the annotation/YAML override pair defined in [05 §5.4](documents.md#54-dual-authoring-and-overrides).
- **EVL-LNG-012** Step IDs are scoped to their flow: the full ID of a step is `<flow-id>/<step-id>`, for example `order.placement/validate`. Step IDs are unique within their flow.
- **EVL-LNG-013** Fields of a data model are scoped the same way: `<model-id>/<field-name>`.
- **EVL-LNG-014** IDs are independent of Java names. Renaming a class MUST NOT require changing its EVL ID. Annotations MAY omit `id`, in which case the ID is derived (see [04 §4.2](annotations.md#42-id-derivation)), and a weak warning `EVL-W002 derived id` encourages making it explicit.
- **EVL-LNG-015** Max ID length 128 characters.

## 3.3 Element references

A reference points at another EVL element.

```
element-ref  = [ app-id ":" ] [ kind ":" ] element-id [ "/" local-id ]
kind         = "module" / "component" / "flow" / "step" / "endpoint" / ... ; any kind keyword from 05
```

Examples: `order`, `flow:order.placement`, `order.placement/validate`, `payments-sdk:payment.authorize`, `payments-sdk:flow:payment.authorize`.

- **EVL-LNG-020** Unprefixed references resolve within the current application first, then fail. They never search libraries implicitly.
- **EVL-LNG-021** A `kind:` prefix, when present, MUST match the target's kind (error `EVL-E021`).
- **EVL-LNG-022** References to a library (`app-id:`) resolve against the EVL index found in that library jar on the classpath. If the library is not on the classpath: error `EVL-E022` unless the reference is inside a document marked `optional: true`.
- **EVL-LNG-023** Each attribute that holds a reference declares its allowed target kinds in the schema. A reference to a wrong kind is error `EVL-E023`.

## 3.4 Source references

A source reference points at code. It is written as a string with JavaDoc-like syntax:

```
source-ref   = type-ref [ "#" member ]
type-ref     = qualified-name                       ; com.example.order.OrderService, Outer.Inner uses "$" or "."
member       = name [ "(" [ param *( "," param ) ] ")" ]   ; method; params are simple or qualified type names
             / name                                  ; field or method when unambiguous
package-ref  = "package:" qualified-name
config-ref   = "config:" property-key               ; e.g. config:shop.payment.timeout
file-ref     = "file:" relative-path [ "#L" line ]   ; for non-code resources
```

- **EVL-LNG-030** `com.example.OrderController#createOrder` resolves when exactly one member has that name. When overloaded, it is error `EVL-E031 ambiguous source reference` with a quick-fix listing the signatures.
- **EVL-LNG-031** Parameter types may be simple names when unambiguous within the declaring file's imports; generics are erased; varargs are written `String...`.
- **EVL-LNG-032** Kotlin top-level functions are referenced through their JVM facade class (`com.example.UtilsKt#format`). Kotlin properties are referenced by property name.
- **EVL-LNG-033** Annotations never need a source reference to themselves: the annotated declaration *is* the source.
- **EVL-LNG-034** `config:` references resolve against `application*.properties|yml|yaml` files and `@EVL.ConfigProperty` declarations. An unresolved config key is a warning, not an error, because keys may come from the environment.

## 3.5 Evidence

Every node and edge in the canonical graph has exactly one evidence class.

| Class | Meaning | Produced by | Shown in 1.0 |
|---|---|---|---|
| `DECLARED` | Stated in an annotation or `.evl.yaml` | Parser | Always |
| `VERIFIED` | A declared element or relationship whose source reference resolved against code (and, for dependencies, a matching code reference exists) | Source resolver | Always (badge) |
| `INFERRED` | A possible relationship found by static analysis but not declared | Plugin source analysis | Only with "Show inferred" (D19) |
| `OBSERVED` | Seen at runtime | Reserved for a future integration | Never in 1.0 |

- **EVL-LNG-040** VERIFIED is a refinement of DECLARED; an element is never both DECLARED and INFERRED.
- **EVL-LNG-041** INFERRED edges MUST NOT be stored in documents, `index.json` or validation output (except `evl inspect --inferred`), and MUST be visually distinct (dashed, grey, labeled "inferred").
- **EVL-LNG-042** Verification rules for dependencies: a declared `Dependency` from component A to B is VERIFIED when any type in A's source scope references any type in B's source scope (import, field type, call). If no such reference exists, the dependency stays DECLARED and an info diagnostic `EVL-I040 dependency not seen in code` is produced. This is never an error, because dependencies can be indirect (events, HTTP).

## 3.6 Versioning and compatibility

- **EVL-LNG-050** The spec uses `MAJOR.MINOR`. Documents declare `evl: "1.0"` in the manifest; individual `.evl.yaml` files MAY declare `evl:` but MUST NOT declare a different major version than the manifest (error `EVL-E050`).
- **EVL-LNG-051** Minor versions only add optional fields, element kinds, enum values and rules at warning level. Tools MUST accept documents of a lower or equal minor version.
- **EVL-LNG-052** A document with a higher minor version than the tool supports loads with warning `EVL-W051`; unknown fields are kept and shown as raw metadata, not dropped.
- **EVL-LNG-053** A higher major version is error `EVL-E052` for that application; other applications in the IDE project still load.
- **EVL-LNG-054** Enums are open-ended for forward compatibility: every enum accepts `CUSTOM` plus a `customType` string, and unknown values from a newer minor version are treated as `CUSTOM` with a warning.
- **EVL-LNG-055** Artifact versions: all `evl-lib` artifacts share one version (e.g. `1.0.3`); `MAJOR.MINOR` of artifacts equals the spec version they implement. The plugin uses its own version (`2026.3.x` aligned with the IDE line, see [08](https://ilyaslabs.github.io/evl-lib/)) and declares which spec versions it supports. Skills declare `spec: 1.0` in each `SKILL.md`.
- **EVL-LNG-056** The compatibility table (spec ↔ artifacts ↔ plugin ↔ skills) is published at `spec/compatibility.md` and updated on every release.

## 3.7 Text fields

- **EVL-LNG-060** `description` and `doc` fields are CommonMark Markdown. Raw HTML is not rendered (escaped). Links may use `evl:<element-ref>` to link to elements, e.g. `[payment](evl:payment.authorize)`.
- **EVL-LNG-061** Text is untrusted content: renderers MUST sanitize it, and skills MUST treat it as data, not instructions.
