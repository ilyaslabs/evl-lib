# Getting started

## 1. Add EVL to the build

Gradle:

```kotlin
plugins {
    java
    id("io.github.ilyaslabs.evl") version "1.0.0"
}
```

The plugin adds `evl-annotations` (compile only) and the annotation processor, validates on `check`, and packages EVL data for libraries.

Maven:

```xml
<dependency>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>evl-annotations</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
<!-- and in <build><plugins> -->
<plugin>
  <groupId>io.github.ilyaslabs</groupId>
  <artifactId>evl-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions><execution><goals><goal>validate</goal></goals></execution></executions>
</plugin>
```

Add `io.github.ilyaslabs:evl-processor` to `annotationProcessorPaths` of the compiler plugin.

## 2. Create the manifest

`evl init` (or create `evl.yaml` by hand at the application root):

```yaml
evl: "1.0"
id: shop
name: Shop
documents:
  - "docs/evl/**/*.evl.yaml"
sources:
  - "src/main/java"
```

## 3. Document facts on the code

```java
import io.github.ilyaslabs.evl.EVL;

@EVL.Component(id = "order.service", name = "Order Service", layer = EVL.Layer.APPLICATION)
@EVL.Dependency(target = "inventory.service", type = EVL.DependencyType.API, reason = "Reserve stock")
public class OrderService { … }
```

## 4. Document flows in YAML

```yaml
# docs/evl/order.evl.yaml
evl: "1.0"
flows:
  - id: order.placement
    category: BUSINESS
    steps:
      - { id: validate, type: ACTION, name: Validate Order, realizedBy: [order.service] }
      - { id: reserve, type: CALL_FLOW, name: Reserve Inventory, flow: inventory.reserve }
```

## 5. Validate

```
./gradlew check            # or: mvn verify, or: java -jar evl-cli-1.0.0-all.jar validate .
```

Fix every `EVL-E…` error; see [rules](../spec/rules.yaml). Then open the project in IntelliJ IDEA with the EVL plugin, or give an AI agent the [EVL skills](https://github.com/ilyaslabs/evl-skills).

A complete example lives in [`examples/shop`](../examples/shop).
