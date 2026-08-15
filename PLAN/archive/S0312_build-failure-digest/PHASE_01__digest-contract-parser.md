# Phase 01 - Digest Contract and Parser

**Strategic spec:** [`../S0312_build-failure-digest.md`](../S0312_build-failure-digest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Define the machine-readable digest data contract and a pure parser that converts the raw failure-block text (the output of `scripts/builders/get-last-build-failure.ps1`) into structured fields: command, exitCode, the first actionable failure (module, flavor, file, line, message), rawLogPath, and verdict. No build is run in this phase - the parser operates on already-saved `temp/*build*.log` content.

---

## Prerequisites

- [x] INDEX.md Pre-Implementation Blocker (strategic §6.1 trigger model) is checked, with the one-shot default recorded in the Blockers Log.
- [x] `scripts/builders/get-last-build-failure.ps1` exists and exits 2 when no log is found, 3 when the log is empty (read its header before parsing its output).
- [x] No `.kt`, `.xml`, or flavor source set is touched by this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/build-failure-digest.contract.ps1` | New | ≤ 220 |

> File projected >500 lines after change → backup step required. This file is projected below 500 lines.

This phase ships the contract and parser as an internal, dot-sourceable helper (`build-failure-digest.contract.ps1`). The user-facing command wrapper that consumes it is added in Phase 02. Keeping the parser in its own file lets Phase 02 dot-source it and lets Verification exercise the parser in isolation without triggering a build.

---

## Data Contract (authoritative for this ticket)

The digest is a single JSON object with these top-level fields. Field names are the contract token - they are asserted verbatim in Verification.

- `command` - string. The build/lint command whose log was digested (e.g. `assembleStandardDebug`), or `unknown` when not recoverable from the log.
- `exitCode` - integer. The digest tool's own exit code (mirrors the verdict; see §exit codes in Phase 02).
- `firstActionableFailure` - object or `null`. The first actionable failure found, with:
  - `module` - string or `null` (e.g. `app_v2`, `wear`).
  - `flavor` - string or `null` (e.g. `standardDebug`, `noLegalDebug`).
  - `file` - string or `null` (absolute or `file:///` path of the offending source).
  - `line` - integer or `null`.
  - `message` - string or `null` (the compiler/lint message text).
- `rawLogPath` - string or `null`. Absolute path to the full raw log the digest was derived from.
- `verdict` - string enum, one of: `failure`, `success`, `blocked`.

`verdict` semantics:

- `failure` - a `FAILURE:` block or at least one actionable failure was found.
- `success` - the log contains `BUILD SUCCESSFUL` and no failure block.
- `blocked` - the log could not be resolved or read (no log found, empty log), so neither success nor failure can be asserted. This is the anti-stale-success guard from strategic §2.4 and §11.3.

---

## Steps

### Step 01.1 - Author the digest data-contract builder

**Files:** `scripts/builders/build-failure-digest.contract.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/builders/build-failure-digest.contract.ps1`. Add a function `New-BuildFailureDigest` that returns an ordered hashtable with exactly these keys, in this order: `command`, `exitCode`, `firstActionableFailure`, `rawLogPath`, `verdict`. `firstActionableFailure` is itself an ordered hashtable with keys `module`, `flavor`, `file`, `line`, `message`. Default every field to `$null` except `verdict`, which defaults to `blocked`, and `exitCode`, which defaults to the blocked exit code. The function must not read any file or run any process - it only constructs the shape. Add a `param()`-less `.SYNOPSIS`/`.DESCRIPTION` header block documenting the contract and the three `verdict` values.

**Verification:**

- `Glob` - `scripts/builders/build-failure-digest.contract.ps1` exists.
- `Grep` - `function New-BuildFailureDigest` appears exactly once in the file.
- `Grep` - each contract token appears in the file: `firstActionableFailure`, `rawLogPath`, `verdict`, `module`, `flavor`, `exitCode`, `command`. Expected: 7/7 present | actual: 7/7.
- `Grep` - the three verdict literals `failure`, `success`, `blocked` each appear in the file.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (New-BuildFailureDigest).Keys -join ','"` prints `command,exitCode,firstActionableFailure,rawLogPath,verdict`. Expected key order: `command,exitCode,firstActionableFailure,rawLogPath,verdict` | actual: recorded.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (New-BuildFailureDigest).verdict"` prints `blocked`. Expected: `blocked` | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Created `build-failure-digest.contract.ps1` with `New-BuildFailureDigest` (ordered hashtable, default verdict `blocked`, default exitCode = blocked code 20).
- Glob: exists. `function New-BuildFailureDigest` count expected 1 | actual 1.
- Contract tokens expected 7/7 | actual 7/7. Verdict literals `failure`/`success`/`blocked` all present.
- Key order expected `command,exitCode,firstActionableFailure,rawLogPath,verdict` | actual `command,exitCode,firstActionableFailure,rawLogPath,verdict`.
- Default verdict expected `blocked` | actual `blocked`.

---

### Step 01.2 - Author the failure-block parser over the existing `bf` output

**Files:** `scripts/builders/build-failure-digest.contract.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the same file add a function `ConvertFrom-BuildFailureLog` that accepts a `[string[]]$LogLines` parameter and returns a digest hashtable built via `New-BuildFailureDigest`. The parser must:
> - Find the first compiler error line matching `^\s*e: file:///(?<path>.+?):(?<line>\d+):\d+:?\s*(?<msg>.*)$` and populate `firstActionableFailure.file`, `.line`, `.message`. Use the same `e: file:///` and `> Task ... FAILED` markers that `get-last-build-failure.ps1` already recognizes - do not invent new failure markers.
> - When no compiler error line exists, fall back to the first failed-task line matching `^\s*> Task (?<task>:[^\s]+) FAILED$` and populate `firstActionableFailure.message` with the task token.
> - Derive `firstActionableFailure.module` and `.flavor` from a `> Task :<module>:<taskName> FAILED` line: module is the segment between the first two colons; flavor is the lowercase variant token inside `taskName` when the task name matches `(assemble|compile|lint|test)(?<variant>[A-Z][A-Za-z]*?)(Debug|Release|UnitTest|Kotlin|Sources|...)`-style names, else `$null`. Keep the regex documented inline with a single WHY comment.
> - Set `verdict` to `failure` when any `FAILURE:` line or any actionable failure is found; to `success` when a `BUILD SUCCESSFUL` line exists and no failure is found; otherwise leave `blocked`.
> - Never throw on malformed input - unrecognized lines leave the corresponding fields `$null`.
> The function must not read files or run processes; it only transforms the supplied lines.

**Verification:**

- `Grep` - `function ConvertFrom-BuildFailureLog` appears exactly once in the file.
- `Grep` - the parser references the existing markers `e: file:///` and `> Task` (both literals present in the file).
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (ConvertFrom-BuildFailureLog -LogLines @('e: file:///C:/x/Foo.kt:42:7: unresolved reference')).firstActionableFailure.line"` prints `42`. Expected: `42` | actual: recorded.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (ConvertFrom-BuildFailureLog -LogLines @('> Task :app_v2:compileStandardDebugKotlin FAILED')).firstActionableFailure.module"` prints `app_v2`. Expected: `app_v2` | actual: recorded.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (ConvertFrom-BuildFailureLog -LogLines @('BUILD SUCCESSFUL in 3s')).verdict"` prints `success`. Expected: `success` | actual: recorded.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (ConvertFrom-BuildFailureLog -LogLines @('FAILURE: Build failed with an exception.')).verdict"` prints `failure`. Expected: `failure` | actual: recorded.
- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; (ConvertFrom-BuildFailureLog -LogLines @('random noise line')).verdict"` prints `blocked`. Expected: `blocked` | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Added `ConvertFrom-BuildFailureLog` reusing the existing `e: file:///` and `> Task ... FAILED` markers (no new markers invented).
- `function ConvertFrom-BuildFailureLog` count expected 1 | actual 1. Markers `e: file:///` and `> Task` both present in file.
- `e: file:///C:/x/Foo.kt:42:7: unresolved reference` -> line expected 42 | actual 42.
- `> Task :app_v2:compileStandardDebugKotlin FAILED` -> module expected `app_v2` | actual `app_v2`; flavor actual `standard`.
- `BUILD SUCCESSFUL in 3s` -> verdict expected `success` | actual `success`.
- `FAILURE: Build failed with an exception.` -> verdict expected `failure` | actual `failure`.
- `random noise line` -> verdict expected `blocked` | actual `blocked`.

---

### Step 01.3 - Parse-time safety and `-NoProfile` load proof

**Files:** `scripts/builders/build-failure-digest.contract.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Confirm the helper file dot-sources cleanly with `-NoProfile` and defines both functions without side effects (no file read, no process spawn, no console output on load). Add a top-of-file guard comment stating the file is a dot-source-only helper and must not be invoked as a script entry point.

**Verification:**

- `pwsh -NoProfile -Command ". ./scripts/builders/build-failure-digest.contract.ps1; if ((Get-Command New-BuildFailureDigest,ConvertFrom-BuildFailureLog).Count -eq 2) { 'OK' }"` prints `OK`. Expected: `OK` | actual: recorded.
- `pwsh -NoProfile -Command "$o = . ./scripts/builders/build-failure-digest.contract.ps1; if ($null -eq $o) { 'NO-OUTPUT' }"` prints `NO-OUTPUT` (dot-sourcing emits nothing). Expected: `NO-OUTPUT` | actual: recorded.
- `Grep` - a dot-source-only guard comment containing `dot-source` appears in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Added a top-of-file `# GUARD: ... dot-source-only helper ...` comment; functions are side-effect-free on load.
- Both functions defined after dot-source: expected `OK` | actual `OK`.
- Dot-sourcing emits nothing: expected `NO-OUTPUT` | actual `NO-OUTPUT`.
- Guard comment containing `dot-source`: present (line 42, case-sensitive match).
- Phase dry-path gate `pwsh -NoProfile -Command ". ...contract.ps1; exit 0"`: expected exit 0 | actual exit 0.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `scripts/builders/build-failure-digest.contract.ps1` dot-sources under `-NoProfile` and exposes `New-BuildFailureDigest` and `ConvertFrom-BuildFailureLog`.
- [x] `New-BuildFailureDigest` returns keys in the contract order `command,exitCode,firstActionableFailure,rawLogPath,verdict`.
- [x] `ConvertFrom-BuildFailureLog` maps a `e: file:///` line to `file`/`line`/`message` and a `> Task :module:variant FAILED` line to `module`/`flavor`, with `verdict` resolving to `failure`/`success`/`blocked` per the contract.
- [x] Dot-sourcing the file produces no console output and reads no file.
- [ ] Dev log entry added for `scripts/builders/build-failure-digest.contract.ps1` via `scripts/post-change.ps1`. (Closure step - handled centrally by the operator; not run by `/spec-dev` execution per HARD PROHIBITIONS.)

---

## Handoff Notes to Next Phase

Phase 02 dot-sources this helper, supplies the raw log lines obtained via the existing `bf` path, sets `command`/`rawLogPath`/`exitCode`, and serializes the digest to JSON plus a human summary.

---

## Rollback Plan

Delete `scripts/builders/build-failure-digest.contract.ps1`. No other file depends on it until Phase 02; no build, app behavior, or data is affected.
