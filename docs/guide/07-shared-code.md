# 7. Shared code and big systems

## Code used by several flows

Some code is a real step in more than one flow. Give it every path where it matters:

```java
@Flow({"media-view>url?>public", "media-asset>url?>public"})
String publicUrl(Asset a) { ... }
```

Inline markers take a comma-separated list:

```java
// @flow media-view>url?>public, media-asset>url?>public: Public base URL [when public]
```

**Rule of thumb:** tag shared code in every flow where it's a meaningful step, and leave it out of flows that only use it to build a response. A helper that formats URLs for every response would otherwise put the same diamond on five maps without explaining anything.

Leaving it untagged in a flow does not hide its effects: if a flow calls code that is tagged only for another flow, the resources that code touches still get arrows from the calling step.

## Many modules, one map

EVL scans every `.java` and `.kt` file in the project, across modules. A flow can start in one module and continue in another as long as both use the same root id:

```java
// store module
@Flow("menu-item-save")
public MenuItem save(MenuItemRequest r) { ... media.claim(r.photoId()) ... }

// media module
@Flow("menu-item-save>claim-photo")
public void claim(UUID id) { ... }
```

That gives readers the end-to-end story ("what happens when I save a menu item with a photo?") instead of one map per module.

## Naming at scale

- **Flows**: what the user or system does, as `noun-verb` or `verb-noun`: `image-upload`, `menu-item-save`, `asset-cleanup`.
- **Steps**: one or two words for what the step does: `validate`, `presign`, `notify`.
- **Decisions**: the thing being decided, with `?`: `payment?`, `status?`, `url?`.
- Keep ids stable. Labels can change freely; ids are what tags, searches and links rely on.

## Growing a map gradually

1. Start with the 3 to 5 flows people ask about most.
2. Tag their entry points and the main steps (aim for 3 to 7 children per level).
3. Mark the resources once.
4. Add decisions and error paths where a reader would otherwise draw the wrong conclusion.
5. Extend to neighbouring modules when a question crosses them.

Next: [Using the map](08-map.md)
