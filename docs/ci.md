# CI setup

## Gradle

`./gradlew check` runs `evlValidate`. Options:

```kotlin
evl {
    failOnWarning = true
    format = "sarif"   // writes build/reports/evl/evl.sarif
}
```

## GitHub code scanning

With `format = "sarif"` set as above:

```yaml
- run: ./gradlew evlValidate || true
- uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: build/reports/evl/evl.sarif
```

## Without a build tool

```yaml
- run: curl -fsSLo evl.jar https://github.com/ilyaslabs/evl-lib/releases/download/v1.0.0/evl-cli-1.0.0-all.jar
- run: java -jar evl.jar validate . --format sarif > evl.sarif
```

## Formatting

`evl fmt --check` fails when `.evl.yaml` files are not canonically formatted.
