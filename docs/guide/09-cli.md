# 9. The CLI

The CLI runs the same scanner as the plugin and prints the result as text. Use it to check tags without an IDE, in CI, or in code review.

## Run it

```bash
java -jar evl-cli-2.0.0-all.jar [scan] [dir] [--json] [--out file]
```

| Option | Meaning |
| --- | --- |
| `dir` | folder to scan (default: the current folder); every `.java` and `.kt` file under it is read |
| `--json` | print the full model instead of the tree |
| `--out file` | write the output to a file |

Problems go to standard error, so they don't mix with `--json` output.

## Reading the tree

```
asset-cleanup [job] 0 0 2 * * *  src/main/java/com/foodstack/media/CleanupJob.java:14
  1. find "postgres" [database] Find pending older than  .../AssetRepository.java:25
  2. anything? [decision]
    when no stale assets: none "Nothing to clean" [step]  .../CleanupJob.java:18
  3. objects "Amazon S3" [storage] Delete all  .../S3Storage.java:26
  on error StorageException: alert "Alert on-call" [step]  .../CleanupJob.java:24
  4. rows "Delete rows" [step] (each stale asset) uses database:postgres  .../CleanupJob.java:28
```

Each line is one path: the step number (or `when …:`, `on error …:`, or `~` for an async step), the id, the label in quotes when it differs, the kind, the subtitle, `(after …)` and `(each …)` notes, the resources it uses, and where it is tagged. A `Resources` section at the end lists every resource.

## JSON

`--json` prints the model the plugin draws (version 2): `flows` (a tree of paths with `kind`, `name`, `seq`, `attrs` such as `when`, `each`, `onError` and the full endpoint `path`, `uses`, `code`), `resources` and `problems`. Use it to build your own reports or checks.

## In CI

Fail the build when tags are broken:

```bash
java -jar evl-cli-2.0.0-all.jar src/main/java 2> evl-problems.txt
if grep -q '^error' evl-problems.txt; then cat evl-problems.txt; exit 1; fi
```

Next: [Good maps](10-best-practices.md)
