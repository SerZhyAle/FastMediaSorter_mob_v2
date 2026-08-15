# Phase 02 - Diff Classify & Gate

**Strategic spec:** [`../S0313_flavor-isolation-diff-guard.md`](../S0313_flavor-isolation-diff-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Build the driver entrypoint `scripts/guard/flavor-isolation-guard.ps1`: classify each match as new/touched vs legacy, gate the exit code on new/touched only, summarize legacy debt non-blocking, and emit a JSON artifact plus a human summary with `expected vs actual` exit semantics.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (three library modules importable).
- [ ] INDEX.md Pre-Implementation Blocker (strategic §6.1 baseline = diff-only blocking, opt-in `-LegacyAudit`) is checked.
- [ ] `temp/` is writable for report artifacts.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/guard/Classifier.ps1` | New | ≤ 180 |
| `scripts/guard/flavor-isolation-guard.ps1` | New | ≤ 320 |

> Entrypoint stays under 500 lines; if it crosses 500 during implementation, split detection into `scripts/guard/Scanner.ps1` first and re-budget. This tool reads Kotlin only; it writes no `.kt`.

---

## Steps

### Step 02.1 - Classify new/touched vs legacy

**Files:** `scripts/guard/Classifier.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/guard/Classifier.ps1` exposing `Get-LineClassification`. For diff-sourced files, classify a matched line as `new-or-touched` when its line number is in the set of added/modified lines from `git diff --unified=0` (added hunks, `+` lines) against the resolved ref; otherwise `legacy`. For explicit `-Path` files with no diff context, classify every match as `new-or-touched` (caller asserted intent to scan them). Return the same violation record with `classification` filled. Pre-existing lines that merely sit in a changed file but were not added/modified stay `legacy`.

**Verification:**

- `Glob` - `scripts/guard/Classifier.ps1` exists.
- `Grep` - `function Get-LineClassification` matches exactly once.
- `Grep` - `new-or-touched` is present.
- `Grep` - `legacy` is present.
- `Grep` - `git diff` with `--unified=0` (or `-U0`) is present.

> Verification results: `Get-LineClassification` exactly once | `new-or-touched` present (4) | `legacy` present (2) | `--unified=0` present (5). Module dot-sources clean (exit 0).

**Status:** `[x]` done

---

### Step 02.2 - Scan and assemble violation records

**Files:** `scripts/guard/flavor-isolation-guard.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create the driver `scripts/guard/flavor-isolation-guard.ps1`. Dot-source the four library modules. Resolve the file set via `Get-ChangedMainKotlin` (honour `-Path`, `-Ref`). For each file, read its lines, match every `Get-FlavorTokenPatterns` regex, build a record via `New-FlavorViolation`, and fill `classification` via `Get-LineClassification`. Collect all records. Expose params `-Path [string[]]`, `-Ref [string]`, `-Json [switch]`, `-LegacyAudit [switch]`, `-DryRun [switch]`. In this step do not yet branch the exit code - just assemble and store the record list. The `-DryRun` switch must make the run fully non-mutating (it already is read-only; `-DryRun` additionally suppresses writing the JSON artifact and prints the intended artifact path instead).

**Verification:**

- `Glob` - `scripts/guard/flavor-isolation-guard.ps1` exists.
- `Grep` - the `param` block contains `[switch]$DryRun`, `[switch]$Json`, `[switch]$LegacyAudit`, `[string[]]$Path`, `[string]$Ref`.
- `Grep` - `Get-ChangedMainKotlin` is invoked.
- `Grep` - `Get-LineClassification` is invoked.
- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -DryRun -Path @()` - expected exit: 0 (empty set, nothing to gate) | actual: 0. (Run via `pwsh -NoProfile -Command './...guard.ps1 -DryRun -Path @()'` so PowerShell parses the empty array literal.)

**Status:** `[x]` done

---

### Step 02.3 - Gate exit code on new/touched only

**Files:** `scripts/guard/flavor-isolation-guard.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Branch the exit code. Define stable documented exit codes in the script header: `0` = no new/touched violations (legacy may exist), `1` = at least one `new-or-touched` violation (blocking), `2` = bad usage (e.g. `-Path` outside `src/main/java`, unreadable ref), `3` = internal error. The blocking decision counts `classification -eq 'new-or-touched'` records only; `legacy` records never affect the exit code. When `-LegacyAudit` is set, additionally run a full scan of all `app_v2/src/main/java` Kotlin (not just the diff), report the legacy count, but still keep exit code governed solely by new/touched diff violations.

**Verification:**

- `Grep` - the header documents `Exit codes:` with `0`, `1`, `2`, `3`.
- `Grep` - `new-or-touched` is the only classification used in the blocking-count expression (no `legacy` token inside the exit-decision block).
- `Grep` - `-LegacyAudit` branch performs a full `app_v2/src/main/java` scan.
- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -Path 'src/wrong/path/X.kt' -DryRun` - expected exit: 2 (path outside main java root) | actual: 2.

> Verification results: header lists exit codes 0/1/2/3 (lines 39-44) | blocking-count expression filters `new-or-touched` only, no `legacy` in the exit decision | `-LegacyAudit` runs `Get-LegacyAuditCount` full-scan of `app_v2/src/main/java` (EnumerateFiles `*.kt` AllDirectories).

**Status:** `[x]` done

---

### Step 02.4 - Emit JSON artifact and human summary

**Files:** `scripts/guard/flavor-isolation-guard.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Emit output per the shared contract (S0311 §5.1). Human mode (default): one line per `new-or-touched` violation (`file:line  matchedToken  ->  remediationCategory`), a legacy-debt count line, and a final verdict line `BLOCK (N new/touched)` or `OK (0 new/touched, M legacy)` with an explicit `expected exit vs actual exit` style verdict. JSON mode (`-Json`): a single compact JSON object on stdout with keys `blocking`, `exitCode`, `newOrTouched` (array of records), `legacyCount`, `scannedFiles`, `ref`, `mode`; all human noise suppressed. Always write a JSON report artifact to `temp/flavor-guard/report.json` (unless `-DryRun`, which prints the path instead). Reports live only under `temp/`; do not embed machine secrets - paths are repo-relative.

**Verification:**

- `Grep` - `temp/flavor-guard` is the artifact directory.
- `Grep` - `ConvertTo-Json` is present.
- `Grep` - JSON keys `blocking`, `exitCode`, `newOrTouched`, `legacyCount` are all present.
- `Grep` - `-Json` branch suppresses human lines (guarded `Write-Host` / `if (-not $Json)`).
- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -Path @() -Json` - expected: stdout is a single JSON object with `"blocking":false` and exit 0 | actual: `{"blocking":false,"exitCode":0,"newOrTouched":[],"legacyCount":0,"scannedFiles":0,"ref":"working-tree","mode":"diff"}` exit 0.

**Status:** `[x]` done

---

### Step 02.5 - Verify legacy debt does not block on a real legacy file

**Files:** `scripts/guard/flavor-isolation-guard.ps1`
**Depends on:** Step 02.4

**Prompt for developer:**

> Drive the guard against an explicit known-legacy main-source file that already contains a flavor gate on disk but has no staged diff, using diff mode (no `-Path` override) so the file is not in the change set. Confirm the guard exits 0 and the run reports zero new/touched. Then run `-LegacyAudit` against the whole tree and confirm a non-zero legacy count is reported while the exit code stays 0. Capture both outcomes in the step log as `expected | actual` pairs. Do not stage or modify the legacy file.

**Verification:**

- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1` (clean tree, diff mode) - expected exit: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -LegacyAudit -Json` - expected: `"legacyCount"` > 0 and `"blocking":false` and exit 0 | actual: `legacyCount=175`, `"blocking":false`, exit 0.
- `Grep` - `temp/flavor-guard/report.json` was produced by the non-dry run (file exists after the run). actual: present, content `{"blocking":false,"exitCode":0,"newOrTouched":[],"legacyCount":175,...}`.

> Behavioural proof on the REAL tree: 175 standing legacy flavor-gate matches in `app_v2/src/main/java` are reported by `-LegacyAudit` yet do NOT block (exit 0). The positive (blocking) path is proven on seeded fixtures in Phase 03.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Build gate: `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -DryRun -Path @()` exits 0 - expected: 0 | actual: 0.
- [x] `Grep` - `scripts/guard/flavor-isolation-guard.ps1` header documents all four exit codes (`0`,`1`,`2`,`3`).
- [x] `Grep` - the exit-decision block counts only `new-or-touched` records (legacy never gates).
- [x] `Glob` - `temp/flavor-guard/report.json` exists after a non-dry run.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1` (ChangeType `Script`).

---

## Handoff Notes to Next Phase

Phase 03 seeds fixtures and asserts the exit-code contract end to end: clean = 0, a new flavor gate in an added line = 1, a legacy-only file = 0, `-LegacyAudit` legacy count > 0 with exit 0.

---

## Rollback Plan

Delete `scripts/guard/Classifier.ps1` and `scripts/guard/flavor-isolation-guard.ps1`; remove `temp/flavor-guard/`. Phase 01 library modules remain valid. No source, catalog, or user-facing surface changed.
