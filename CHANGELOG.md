# Changelog

## 2.0.0 (unreleased)

- New model: `@Flow("a>b>c")` paths on classes and methods, plus `// @flow a>b: Label` markers inside methods.
- `@EVL.*` annotations only pick the symbol: Endpoint, Job, Database, Storage, Queue, Cache, External, Interface, Service, Actor.
- Step order comes from the call graph; `after` marks asynchronous steps.
- Removed: `.evl.yaml` documents, the annotation processors, and the Gradle and Maven plugins.

## 1.0.0

- First release (YAML documents). Deprecated.
