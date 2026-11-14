# 3. Paths in depth

Everything in EVL hangs off paths. Once paths click, the rest is detail.

## A path is a list of ids

```
image-upload > validate > quota
```

- Ids use letters, digits, `_`, `.` and `-`. Use short kebab-case words: `image-upload`, `s3`, `quota`.
- `>` separates levels. No spaces around it inside the string: `"image-upload>validate"`.
- An id may end in `?`; that makes it a decision (chapter 6).
- The first id is the **flow**. Everything after it is a **step** of that flow.

## All paths merge into one tree

You can write paths in any file, in any order. EVL merges them:

```java
@Flow("image-upload")                 // in MediaController
@Flow("image-upload>validate")        // in ImageValidator
@Flow("image-upload>s3")              // in S3Storage
// @flow image-upload>s3>key          // inside S3Storage.presignUpload()
```

becomes

```
image-upload
├── validate
└── s3
    └── key
```

If you tag `a>b>c` but never tag `a>b`, EVL still creates `a>b` so the tree stays connected. It is drawn as a box marked **path only**, which is a hint that it has no code of its own yet.

Each path can be tagged once. Tagging the same path twice gives a warning, and EVL keeps the first one.

## Where `@Flow` can go

| On | Meaning |
| --- | --- |
| a method or constructor | the step is that method; its calls decide the order of its children |
| a class | the step is the whole class; every method in it counts for ordering |
| a comment inside a method (`// @flow`) | the step is a few lines of a method (see below) |

`@Flow` is repeatable, and its `value` takes several paths (chapter 7).

## Labels and descriptions

The id is for you and your tools. What people read on the map is the label:

```java
@Flow(value = "image-upload>validate", label = "Validate image", description = "size, type and quota")
```

- `label` replaces the id as the box title (the id stays in the breadcrumbs and search).
- `description` is a smaller line under the title.

## Inline markers

Java and Kotlin can't annotate a statement, so for a step that is only a few lines inside a method, write a comment:

```java
public URI presignUpload(Asset asset) {
    // @flow image-upload>s3>key: Build the object key
    String key = "media/" + asset.userId() + "/" + asset.id();

    // @flow image-upload>s3>presign: Presign a PUT URL
    return presigner.presignPut(key, Duration.ofMinutes(15));
}
```

- The text after `:` is the label. It's optional: `// @flow image-upload>s3>key` works too.
- A marker covers the code from the comment down to the next marker in the same method that is not one of its own children, or to the end of the method.
- Markers must be inside a method body. Anywhere else you get a warning.
- `//` and `///` both work, in Java and Kotlin.

## How EVL orders steps

For each parent, EVL looks at the parent's code (its method, its class's methods, or its marker's lines) and finds where each child is reached:

1. a child marker in the same method is placed where the comment is;
2. a child method is placed where the parent first calls it, directly or through other methods (EVL follows calls through fields, parameters, local variables and interfaces);
3. children reached the same way are ordered by file and line.

Steps are then numbered 1, 2, 3... on the arrows. Asynchronous steps (chapter 5) and error paths (chapter 6) don't take a number.

So if the map's order looks wrong, the code's order is usually the cause: EVL draws what the code does.

## Endpoint paths

`@EVL.Endpoint(path = ...)` on the class is a prefix for the method's path, the same way most web frameworks work:

```java
@EVL.Endpoint(path = "/api/v1/media")
public class MediaApi {
    @EVL.Endpoint(method = "POST", path = "/uploads")
    @Flow("media-upload")
    public Upload start(...) { ... }        // drawn as POST /api/v1/media/uploads
}
```

Search finds the flow by any part of the full path, such as `/uploads` or `v1/media`.

Next: [Symbols and resources](04-symbols.md)
