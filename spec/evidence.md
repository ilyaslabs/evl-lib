# EVL Evidence Model

Every element and edge in the resolved graph carries exactly one evidence class.

| Class | Meaning | Produced by | Shown |
|---|---|---|---|
| `DECLARED` | Stated in an annotation or an `.evl.yaml` document | Parser | Always |
| `VERIFIED` | Declared, and its source reference resolved against code (for dependencies: a matching code reference exists) | Source resolver | Always (badge) |
| `INFERRED` | A possible relationship found by static analysis but not declared | IDE source analysis | Only with "Show inferred" |
| `OBSERVED` | Seen at runtime | Reserved | Never in 1.0 |

Rules:

- **EVL-LNG-040** `VERIFIED` refines `DECLARED`. An element is never both `DECLARED` and `INFERRED`.
- **EVL-LNG-041** `INFERRED` edges are never written to documents, `index.json` or validation output (except `evl inspect --inferred`), and are drawn dashed, grey and labeled "inferred".
- **EVL-LNG-042** A declared dependency from A to B is `VERIFIED` when any type in A's source scope references any type in B's source scope. Otherwise it stays `DECLARED` and `EVL-I040 dependency not seen in code` is reported. This is never an error, since dependencies can be indirect (events, HTTP).

Source resolution outcomes:

| Outcome | Diagnostic |
|---|---|
| Resolved | none; evidence becomes `VERIFIED` |
| Not found | `EVL-E030` (type) / `EVL-E031` (member) |
| Cannot check (Kotlin without classes, no index) | `EVL-I041` `UNCHECKED`, never an error |
