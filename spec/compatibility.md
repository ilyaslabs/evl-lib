# EVL Compatibility

The spec uses `MAJOR.MINOR`. All `evl-lib` artifacts share one version whose `MAJOR.MINOR` equals the spec version they implement (EVL-LNG-055). This table is updated on every release (EVL-LNG-056).

| Spec | evl-lib artifacts | IntelliJ plugin | evl-skills | IDE | Java |
|---|---|---|---|---|---|
| 1.0 | 1.0.x | 2026.3.x | 1.0.x (`spec: 1.0`) | 2026.3+ (build 263+) | annotations/processor: 8+; core/CLI/build plugins: 17+; plugin: 25 |

## Rules

- **EVL-LNG-050** `evl.yaml` declares `evl: "1.0"`. A document MAY declare `evl:` but MUST NOT use a different major version (`EVL-E050`).
- **EVL-LNG-051** Minor versions only add optional fields, kinds, enum values and warning-level rules. Tools accept any lower or equal minor.
- **EVL-LNG-052** A higher minor loads with `EVL-W051`; unknown fields are kept as raw metadata.
- **EVL-LNG-053** A higher major is `EVL-E052` for that application only.
- **EVL-LNG-054** Enums accept `CUSTOM` plus `customType`; unknown values from a newer minor are treated as `CUSTOM` with a warning.

`evl compat` prints the spec version and the tool version.

## Library index format

`META-INF/evl/index.json` has `"format": 1`. Readers reject a higher format with `EVL-E052` and ignore unknown keys.
