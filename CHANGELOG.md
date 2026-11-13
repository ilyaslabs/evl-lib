# Changelog

## 1.0.0 (2026-11-13)

- Specification 1.0: language, annotations, documents, workflows, evidence, compatibility, agent interface; JSON Schemas and `rules.yaml` generated from `evl-core`; conformance suite with one file per rule.
- `evl-annotations`, `evl-processor` (Java), `evl-processor-ksp` (Kotlin).
- `evl-core`: parse, merge, resolve, validate, incremental update, queries, ranked search, JSON/DOT/Mermaid export.
- `evl-cli`: validate, inspect, list, graph, index, fmt, package, init, schema, compat, rules; text, JSON and SARIF output.
- Gradle plugin `io.github.ilyaslabs.evl` and `evl-maven-plugin`; `evl-bom`.
