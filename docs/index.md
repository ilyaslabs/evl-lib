# EVL 2.0 guide

EVL draws your system as hand-drawn flow maps, straight from tags in the code. There is no YAML.

## 1. Add the annotations

```kotlin
dependencies { compileOnly("io.github.ilyaslabs:evl-annotations:2.0.0") }
```

## 2. Tag flows with paths

A path is a list of ids separated by `>`. Every path you write merges into one tree.

```java
@EVL.Endpoint(method = "POST", path = "/media", caller = "Mobile app")
@Flow("image-upload")
public UploadResponse upload(UploadRequest request) {
    validator.check(request);          // @Flow("image-upload>validate") on check()
    URI url = storage.presignUpload(); // @Flow("image-upload>s3") on presignUpload()
}
```

Inside a method, use a comment marker (Java can't annotate statements):

```java
// @flow image-upload>s3>key: Build object key
String key = "media/" + asset.userId() + "/" + asset.id();
```

- Steps are numbered in call order: EVL follows the calls from the parent's code to each child.
- `@Flow(value = "image-upload>thumbnail", after = "image-upload>queue")` marks an asynchronous step that follows another one (dashed arrow).
- `label = "…"` sets the display name and `description = "…"` the line under it.

## 3. Pick symbols

Symbols only change how something is drawn. EVL is framework-agnostic: it never reads Spring or other annotations.

| Annotation | Drawn as |
| --- | --- |
| `@EVL.Endpoint(method, path, caller)` | box with the method and path; `caller` adds a stick figure |
| `@EVL.Job(schedule \| trigger)` | clock |
| `@EVL.Database("postgres")` | cylinder |
| `@EVL.Storage("Amazon S3")` | bucket |
| `@EVL.Queue("media.uploaded")` | pipe |
| `@EVL.Cache(ttl = "15m")` | lightning bolt |
| `@EVL.External("Stripe")` | cloud |
| `@EVL.Interface`, `@EVL.Service`, `@EVL.Actor` | lollipop box, box, stick figure |

Put resource symbols on the class (or the interface it implements). Classes with the same kind and name are one resource, so every repository marked `@EVL.Database("postgres")` points at the same cylinder. A step that calls into a resource class gets an arrow to it.

## 4. See it

- **IntelliJ plugin:** Tools > EVL > Open EVL Map. Click a box to preview its code (10 lines around the tag), click again or press ⊕ to open its path level, and use Go to source to jump to the line.
- **CLI:** `java -jar evl-cli-all.jar <dir>` prints the tree; `--json` prints the model the plugin draws.

See [examples/media](../examples/media) for a complete example.
