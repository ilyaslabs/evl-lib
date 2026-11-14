# 10. Good maps

A good map answers "what happens when..." in under a minute. These habits get you there.

## Tag

- **Entry points**: endpoints, listeners, jobs. Every flow starts at one.
- **Business steps**: things a product owner would name: validate, charge, reserve stock, notify.
- **Decisions that change the outcome**: payment method, already processed, quota exceeded.
- **Error paths people ask about**: what happens when storage is down, when the card is declined.
- **Resources, once**, on their classes.

## Don't tag

- Getters, setters, mappers, DTO conversion, logging, metrics.
- Code that only formats a response, even if it's shared with a flow where it matters.
- Every `if`. A null check or a defensive guard is not a business decision.
- Framework glue (filters, interceptors) unless it changes what the user sees.

## Shape

- **3 to 7 children per level.** More than that, group them under a step; fewer than 3, maybe the level isn't needed.
- **Labels are sentences people read**: "Presign a PUT URL", not `presignPut`.
- **Use `description` for the why**: limits, timeouts, "no consumers yet".
- **Keep ids short and stable**; change labels instead.
- **Prefer method tags over markers** when a step is a whole method; markers are for a few lines inside one.

## Review checklist

Before you merge a change that touches tagged code:

- [ ] Does every new business step have a tag under the right parent?
- [ ] Do refusals and error paths use `when` or `onError`, so they don't look like they always run?
- [ ] Do the step numbers match what the code really does?
- [ ] Are there no "path only" boxes left over from renamed or deleted steps?
- [ ] Does the CLI report no problems?

## When code changes

- **Rename or move** a tagged method or class: keep the tag; the id stays the same, and the map follows the code.
- **Delete** a tagged step: delete the tag too, and check that its children don't now hang under a "path only" box.
- **New call** in a flow: tag it if it's a business step; otherwise leave it, the resource arrows already show what it touches.

Next: [Reference](11-reference.md)
