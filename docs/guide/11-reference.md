# 11. Reference

## `@Flow`

Package `io.github.ilyaslabs.evl`. Targets types, methods and constructors. `CLASS` retention, Java 8. Repeatable.

| Attribute | Type | Meaning |
| --- | --- | --- |
| `value` | `String[]` | one or more paths, e.g. `"image-upload>s3"` or `{"a>x", "b>x"}` |
| `label` | `String` | title on the map (default: the last id) |
| `description` | `String` | smaller line under the title |
| `after` | `String` | path this step follows asynchronously (dashed arrow, no number) |
| `when` | `String` | condition: arm label under a decision, or "if …" on the arrow |
| `each` | `String` | loop note, drawn as a ↻ badge |
| `onError` | `String` | error this step handles: red dashed arrow, no number |

## Inline marker

```
// @flow <path>[, <path>...][:] [label] [when …] [each …] [on error …]
```

- Must be inside a method body. One or more slashes (`//`, `///`).
- Several paths share the same label and brackets.
- Covers the code from the marker to the next marker in the method that isn't its own child, or to the method's end.

## Paths

- Ids: `[A-Za-z0-9_.-]+`, optionally ending in `?` (a decision).
- Separated by `>` with no spaces.
- The first id is the flow; missing parents are created as "path only".

## `@EVL.*`

| Annotation | Attributes |
| --- | --- |
| `@EVL.Endpoint` | `value`, `method`, `path`, `caller`, `description` |
| `@EVL.Job` | `value`, `schedule`, `trigger`, `description` |
| `@EVL.Database` | `value`, `table`, `description` |
| `@EVL.Storage` | `value`, `description` |
| `@EVL.Queue` | `value`, `description` |
| `@EVL.Cache` | `value`, `ttl`, `description` |
| `@EVL.External` | `value`, `description` |
| `@EVL.Interface` | `value`, `description` |
| `@EVL.Service` | `value`, `description` |
| `@EVL.Actor` | `value`, `description` |

Resources are identified as `kind:name`, for example `database:postgres`. An endpoint's full path is the class-level `path` followed by the method's.

## Problems

| Level | Message | Fix |
| --- | --- | --- |
| error | `Invalid flow path "…": use ids separated by >` | use only letters, digits, `_ . -`, a trailing `?`, and `>` between ids |
| warning | `Flow path "…" is tagged more than once; using <file:line>` | keep one tag per path; for shared code list several paths in one tag |
| warning | `after = "…" does not match any flow path` | point `after` at an existing path |
| warning | `// @flow markers belong inside a method body` | move the marker into a method |

Next: [Troubleshooting](12-troubleshooting.md)
