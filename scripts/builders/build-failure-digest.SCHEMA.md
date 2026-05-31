# Build Failure Digest - JSON Schema

Reference for the JSON object emitted by `scripts/builders/build-failure-digest.ps1`
(alias `.\a.ps1 bfd`). The object is also written to `temp/build-failure-digest.json` on
every real run. Field names are the stable contract (S0312).

## Top-level fields

| Field | Type | Nullable | Description |
|-------|------|:--------:|-------------|
| `command` | string | no | Build/lint command whose log was digested (e.g. `assembleStandardDebug`). `unknown` when not supplied. |
| `exitCode` | integer | no | The digest tool's own exit code; mirrors `verdict` (see map below). |
| `firstActionableFailure` | object | yes | The first actionable failure, or `null` when none was extracted. Object fields below. |
| `rawLogPath` | string | yes | Absolute path to the full raw log the digest was derived from; `null` when no log resolved. |
| `verdict` | string | no | One of `failure`, `success`, `blocked`. |

## `firstActionableFailure` object

| Field | Type | Nullable | Description |
|-------|------|:--------:|-------------|
| `module` | string | yes | Gradle module, e.g. `app_v2`, `wear`. `null` when not derivable. |
| `flavor` | string | yes | Variant token, e.g. `standard`, `noLegal`. `null` when the task name has no recognizable build type. |
| `file` | string | yes | Path of the offending source (as printed by the compiler). `null` when no compiler error line was found. |
| `line` | integer | yes | 1-based line number of the failure. `null` when unknown. |
| `message` | string | yes | Compiler/lint message text, or the failed-task token when no compiler error line exists. `null` when neither is present. |

## `verdict` values

- `failure` - a `FAILURE:` block or at least one actionable failure was found.
- `success` - the log contains `BUILD SUCCESSFUL` and no failure block.
- `blocked` - the log could not be resolved or read (no log found, empty log). Anti-stale-success guard: this is reported instead of a fake `success`.

## Exit-code map

| Exit code | Meaning |
|:---------:|---------|
| `0` | digest produced, verdict `success`. |
| `10` | digest produced, verdict `failure`. |
| `20` | digest produced, verdict `blocked`. |
| `2` | usage error (bad parameter combination, e.g. `-Json` with `-DryRun`). |

## Examples

`failure` verdict (compiler error in a Kotlin source):

```json
{
  "command": "assembleStandardDebug",
  "exitCode": 10,
  "firstActionableFailure": {
    "module": "app_v2",
    "flavor": "standard",
    "file": "C:/proj/app_v2/src/main/java/com/sza/Foo.kt",
    "line": 128,
    "message": "unresolved reference: bar"
  },
  "rawLogPath": "P:\\ANDROID\\FastMediaSorter_mob_v2\\temp\\build_standard_debug.log",
  "verdict": "failure"
}
```

`blocked` verdict (no log could be resolved):

```json
{
  "command": "unknown",
  "exitCode": 20,
  "firstActionableFailure": {
    "module": null,
    "flavor": null,
    "file": null,
    "line": null,
    "message": null
  },
  "rawLogPath": null,
  "verdict": "blocked"
}
```
