# EVL: flow maps from your code

EVL 2.0 turns `@Flow("image-upload>s3")` tags in Java or Kotlin code into hand-drawn, drill-down flow maps. Nothing else is needed: no YAML, no framework detection.

| Module | What it is |
| --- | --- |
| `evl-annotations` | `@Flow` and the `@EVL.*` symbol annotations. Java 8+, no dependencies. |
| `evl-core` | The scanner: reads `@Flow`, `// @flow` markers and `@EVL.*` into a flow tree with call order and resource use. |
| `evl-cli` | `evl <dir>` prints the tree; `--json` prints the model. |
| `evl-bom` | Aligns the versions. |

Read the [guide](docs/index.md) and the [media example](examples/media). The IntelliJ plugin draws the maps.

## Snapshots

Every push to a branch other than `main` publishes `<branch>-SNAPSHOT` builds to the Maven Central snapshot repository. Releases come only from `v*` tags.

EVL 1.0 (YAML documents) is deprecated; 1.0.0 stays on Maven Central.
