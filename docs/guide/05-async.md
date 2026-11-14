# 5. Async steps and jobs

Not everything happens in one call stack. A request publishes an event, and a consumer picks it up later; a job runs every night. EVL draws these as dashed arrows so readers can see where the time gap is.

## `after`: a step that runs later

Give the asynchronous step an `after` that names the step it follows:

```java
@EVL.Queue("media.uploaded")
public class ImageEvents {
    @Flow(value = "image-upload>queue", label = "Publish")
    public void publish(ImageUploaded e) { ... }
}

public class ThumbnailJob {
    @EVL.Job(trigger = "media.uploaded")
    @Flow(value = "image-upload>thumbnail", after = "image-upload>queue")
    public void onUploaded(ImageUploaded e) {
        // @flow image-upload>thumbnail>download
        // @flow image-upload>thumbnail>resize
        // @flow image-upload>thumbnail>upload
    }
}
```

- `thumbnail` is a child of `image-upload`, so it appears in that flow.
- `after = "image-upload>queue"` draws a dashed **async** arrow from the queue step to it, instead of a numbered arrow from the parent.
- `after` must name an existing path; otherwise you get a warning.

## Where to put the async step

Put the consumer under the flow it continues, as above, when a reader of that flow needs to see it. If the consumer is really its own process (several producers, its own error handling), make it a flow of its own with `@EVL.Job(trigger = ...)`, and mention the producer in its `description`.

## Scheduled jobs

```java
public class CleanupJob {
    @EVL.Job(schedule = "0 0 2 * * *")
    @Flow("asset-cleanup")
    void run() { ... }
}
```

The flow starts at a clock on the left, labelled with the schedule.

## Events nobody consumes yet

If a step publishes an event that nothing in the scanned code consumes, the arrow to the queue simply ends there. That is accurate; say so in the queue's or step's `description` (for example `description = "no consumers yet"`) so readers don't think a tag is missing.

Next: [Branches, loops and errors](06-branches.md)
