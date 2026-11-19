# 1. Install

EVL has three parts. You need the first one; the other two are how you look at the result.

| Part | What it is | Where it runs |
| --- | --- | --- |
| `evl-annotations` | `@Flow` and `@EVL.*`, a tiny jar with no dependencies | your build, compile time only |
| EVL IntelliJ plugin | draws the map inside the IDE and keeps it live as you type | IntelliJ IDEA 2026.3 or newer |
| `evl-cli` | prints the same tree in a terminal, for CI and code review | any machine with Java 17+ |

## Add the annotations

The annotations have `CLASS` retention and target Java 8, so they add nothing at runtime and work in any Java or Kotlin project.

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
    compileOnly("io.github.ilyaslabs:evl-annotations:2.0.0")
}
```

**Gradle (Groovy)**

```groovy
dependencies {
    compileOnly 'io.github.ilyaslabs:evl-annotations:2.0.0'
}
```

**Maven**

```xml
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>evl-annotations</artifactId>
  <version>2.0.0</version>
  <scope>provided</scope>
</dependency>
```

Use the BOM (`io.github.ilyaslabs:evl-bom`) if you want one version for every EVL artifact.

Then import the two types:

```java
import io.github.ilyaslabs.evl.EVL;
import io.github.ilyaslabs.evl.Flow;
```

```kotlin
import io.github.ilyaslabs.evl.EVL
import io.github.ilyaslabs.evl.Flow
```

## Install the IntelliJ plugin

The plugin is required to see the flow maps. Get it from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/36145):

<div id="evl-plugin-card-install"></div>
<script src="https://plugins.jetbrains.com/assets/scripts/mp-widget.js"></script>
<script>
  MarketplaceWidget.setupMarketplaceWidget('card', 36145, "#evl-plugin-card-install");
</script>

Or install it from the IDE:

1. Open **Settings > Plugins**.
2. Search the Marketplace for **EVL** and click **Install**.
3. Restart the IDE.

The plugin is free for up to 10 flows. EVL Pro unlocks every flow, export and presentation mode.

After the restart you get:

- an **EVL** tool window on the left side bar, with a tree of every flow;
- **Tools > EVL > Open EVL Map** (`⌥⌘E` on macOS, `Ctrl+Alt+Shift+E` elsewhere);
- an EVL icon in the gutter next to every `@Flow` and `// @flow` line.

The plugin scans the `.java` and `.kt` files of the open project, including unsaved edits, and redraws a moment after you stop typing. There is nothing to configure.

## Get the CLI

Download `evl-cli-<version>-all.jar` from the release page and run it with Java 17 or newer:

```bash
java -jar evl-cli-2.0.0-all.jar src/main/java
```

Chapter 9 covers the CLI in detail.

Next: [Your first flow](02-first-flow.md)
