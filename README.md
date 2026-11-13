# EVL™ — Engineering Visualization Language

EVL documents a codebase's architecture, flows, APIs, data and decisions next to the code, as `@EVL.*` annotations and `.evl.yaml` files, and validates them against the code.

| Module | Java | Purpose |
|---|---|---|
| `evl-annotations` | 8+ | `@EVL.*` annotations (CLASS retention, no runtime dependency) |
| `evl-processor` | 8+ | Annotation processor writing `META-INF/evl/index.json` |
| `evl-processor-ksp` | 8+ | Same for Kotlin via KSP |
| `evl-core` | 17+ | Parse, merge, resolve, validate, query, export |
| `evl-cli` | 17+ | `evl validate`, `inspect`, `list`, `graph`, `index`, `fmt`, `init`, `schema`, `compat`, `rules` |
| `evl-gradle-plugin` | 17+ | Plugin `io.github.ilyaslabs.evl`: `evlValidate` (on `check`), `evlIndex`, `evlPackage` |
| `evl-maven-plugin` | 17+ | `evl:validate`, `evl:index`, `evl:package` |
| `evl-bom` | | Aligned versions |

The specification lives in [`spec/`](spec/README.md). Example projects are in [`examples/`](examples/).

## Quick start (Gradle)

```kotlin
plugins { id("io.github.ilyaslabs.evl") version "1.0.0" }
```

Then add `evl.yaml` at the project root (`evl init`) and run `./gradlew check`.

## CLI

```
java -jar evl-cli-1.0.0-all.jar validate .
java -jar evl-cli-1.0.0-all.jar index --out build/evl/index.json
```

## Build

`./gradlew build` runs all tests, including the conformance suite in `spec/conformance/`. Regenerate schemas and `rules.yaml` with `./gradlew :evl-core:generateSpec`.

## License

Apache License 2.0. "EVL" is a trademark, see [TRADEMARKS.md](TRADEMARKS.md).
