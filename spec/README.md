# EVL™ Specification 1.0

| File | Contents |
|---|---|
| [language.md](language.md) | Applications, identifiers, references, source references, text |
| [annotations.md](annotations.md) | The `@EVL.*` annotation catalogue |
| [documents.md](documents.md) | `.evl.yaml` documents, element kinds, merging, libraries |
| [workflows.md](workflows.md) | Flows, steps, branches, error handling |
| [evidence.md](evidence.md) | DECLARED / VERIFIED / INFERRED |
| [compatibility.md](compatibility.md) | Versions of spec, artifacts, plugin and skills |
| [agent-interface.md](agent-interface.md) | JSON output of `evl index`, `evl graph`, `evl validate` |
| [rules.yaml](rules.yaml) | Every validation rule (generated) |
| [schema/](schema/) | JSON Schemas (generated) |
| [conformance/](conformance/) | One test file per rule; run by `evl-core`'s `ConformanceTest` |

`rules.yaml` and `schema/*.json` are generated from `evl-core` (`./gradlew :evl-core:generateSpec`). A test fails when they are out of date.

## Implementation notes for 1.0

- Requirements use `ticket` for the issue link (not `source`, which is reserved for code locations).
- The application element is keyed `application:<id>`, so a module may share the application's id.
- Library `index.json` stores raw annotation records; every tool maps them with the same `AnnotationMapper` in `evl-core`.
- A directory with its own `evl.yaml` below the root is a separate application and is skipped by discovery (`EVL-E002` if referenced).
