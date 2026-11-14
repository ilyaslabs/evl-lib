# Changelog

## 2.0.0 (unreleased)

- New model: `@Flow("a>b>c")` paths on classes and methods, plus `// @flow a>b: Label` markers inside methods.
- `@EVL.*` annotations only pick the symbol: Endpoint, Job, Database, Storage, Queue, Cache, External, Interface, Service, Actor.
- Step order comes from the call graph; `after` marks asynchronous steps.
- Explicit branches: `x?` path segments are decisions; `when`, `each` and `onError` (or `[when …]`, `[each …]`, `[on error …]` on markers) label arms, loops and error paths. Nothing is inferred from code.
- One marker can carry several paths (`// @flow a>x, b>x`) for code shared by flows.
- CLI prints `when`, `each` and `on error`; error-path steps take no step number.
- Removed: `.evl.yaml` documents, the annotation processors, and the Gradle and Maven plugins.

## 1.0.0

- First release (YAML documents). Deprecated.
