# CLI

`evl-cli-<version>-all.jar` is attached to every GitHub release (Java 17+). The CLI never contacts the network.

| Command | Purpose |
|---|---|
| `evl validate [dir] [--format text\|json\|sarif] [--fail-on-warning] [--min-level L]` | Load, validate, print diagnostics |
| `evl inspect <ref>` | One element with fields, origins, edges, diagnostics, "Did you mean" |
| `evl list [--kind K] [--module M] [--search TEXT]` | List or search elements |
| `evl graph [--format json\|dot\|mermaid] [--root REF] [--depth N]` | Export the graph |
| `evl index [--out FILE]` | Resolved index for agents ([format](../spec/agent-interface.md)) |
| `evl fmt [--check]` | Canonical YAML formatting, comment preserving |
| `evl package [dir] --out DIR [--artifact g:a:v]` | Write the `META-INF/evl` library layout without Gradle/Maven |
| `evl init` | Create `evl.yaml` and an example document |
| `evl schema` · `evl compat` · `evl rules` | Schema URLs, versions, rule list |

Common options: `--classpath` (compiled classes and dependency jars; EVL libraries on it are loaded), `--index` (processor `index.json`), `--no-scan`, `--no-verify`.

Exit codes: `0` no errors, `1` errors, `2` tool failure.
