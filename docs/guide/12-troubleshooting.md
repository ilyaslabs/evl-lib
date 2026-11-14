# 12. Troubleshooting

**The map is empty.** EVL found no `@Flow` or `// @flow` tags. Check that the files are `.java` or `.kt` inside the open project, and that the import is `io.github.ilyaslabs.evl.Flow`. Run **Tools > EVL > Rescan EVL Flows**.

**A box says "path only".** You tagged `a>b>c` but not `a>b`. Tag the parent, or shorten the child's path.

**Steps are in the wrong order.** EVL orders steps by where the parent's code reaches them. Check that the parent actually calls the step (directly, or through other methods). If the step is called through a framework (reflection, events, a proxy EVL can't see), make it async with `after`, or put a marker at the call site.

**A step has no arrow to a resource it uses.** Mark the resource class (or the interface it implements) with `@EVL.Database`, `@EVL.Storage` and so on. EVL follows calls a few levels deep; very indirect calls may need a tag on the method that does the work.

**The same diamond appears on many maps.** Shared helper code is tagged in flows where it only formats a response. Keep the tag only where the decision is a meaningful step (chapter 7).

**A refusal looks like it always happens.** Give it `when` or `onError` (chapter 6).

**Kotlin won't compile `when = "…"`.** `when` is a Kotlin keyword: write `` `when` = "…" ``.

**The map doesn't update.** It redraws a moment after you stop typing. If it seems stuck, run **Tools > EVL > Rescan EVL Flows**.

Back to the [guide](index.md).
