# Phase 04 - Comparator, Output Formatter, and CLI Entry

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Combine the two parsers into a comparator that emits per-pin classification records, format them per D-5 grammar, and expose the whole thing as a single executable CLI entry point at `scripts/check-doc-vs-gradle.ps1`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.
- [ ] `DECISIONS.md` D-5 grammar lock present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/Comparator.ps1` | New | ≤ 220 |
| `scripts/doc-drift/Output.ps1` | New | ≤ 180 |
| `scripts/check-doc-vs-gradle.ps1` | New | ≤ 180 |

---

## Steps

### Step 04.1 - Implement `Comparator.ps1`

**Files:** `scripts/doc-drift/Comparator.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/doc-drift/Comparator.ps1`. Public function `Compare-PinsToDocs` accepts `-GradlePins <hashtable>` (from Phase 02) and `-DocMentions <array>` (from Phase 03), returns an array of `[pscustomobject]` records:
>
> ```powershell
> [pscustomobject]@{
>     Pin       = 'agp'
>     Status    = 'FAIL'   # PASS | FAIL | WARN | SKIP | MISSING | INCONSISTENT
>     Gradle    = '9.2.1'
>     DocPath   = 'dev/TECH_REQUIREMENTS.md'
>     DocValue  = '9.2.0'
>     Reason    = $null    # populated only for SKIP / MISSING / INCONSISTENT
> }
> ```
>
> Classification logic per D-2, D-3, D-4:
>
> 1. If gradle hashtable lacks the manifest's `gradleKey` → emit one `SKIP` record with `Reason = 'gradle key not extracted by parser'` and continue (defensive - should not happen for documented pins).
> 2. For each (pin, doc) doc-mention record:
>    - `Mentions.Count == 0` and `Required == $true` → `MISSING`.
>    - `Mentions.Count == 0` and `Required == $false` → `SKIP` with `Reason = 'not required in this doc'`.
>    - `Mentions.Count > 1` and policy `allMustMatch` and not all values identical → one `INCONSISTENT` record with `DocValue = '<v1> vs <v2> [vs <v3>...]'`.
>    - For each retained mention value:
>      - If value is a range marker matching `^(\d+(\.\d+)*)\+$` or starts with `>=` and gradle version falls inside → `WARN`.
>      - Else if value equals gradle version → `PASS`.
>      - Else → `FAIL`.
>
> Range-inside check helper compares dotted version segments numerically (3-segment max, padded with zeros). Document the helper as `Test-VersionInsideRange`.

**Verification:**

- `Glob` - `scripts/doc-drift/Comparator.ps1` exists.
- `Grep` - `function Compare-PinsToDocs` matches exactly once.
- `Grep` - all six status string literals present: `'PASS'`, `'FAIL'`, `'WARN'`, `'SKIP'`, `'MISSING'`, `'INCONSISTENT'`.
- `Grep` - `Test-VersionInsideRange` declared.

**Status:** `[x] done`

---

### Step 04.2 - Implement `Output.ps1`

**Files:** `scripts/doc-drift/Output.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `scripts/doc-drift/Output.ps1`. Public function `Format-DriftReport` accepts `-Records <array>` (from `Compare-PinsToDocs`), `-Verbose:$false`, `-Color:$false`. Returns an array of strings (caller decides where to print) - one per record plus the final SUMMARY line. Output grammar per D-5 (DECISIONS.md):
>
> - `FAIL | <pin> | gradle: <X> | <doc-path>: <Y>`
> - `WARN | <pin> | gradle: <X> | <doc-path>: <Y> (range)`
> - `INCONSISTENT | <pin> | <doc-path>: <Y1> vs <Y2>`
> - `MISSING | <pin> | <doc-path>: required mention not found`
> - `SKIP | <pin> | reason: <text>` - emitted always
> - `PASS | <pin> | <X>` - emitted only when `-Verbose` is set
> - `SUMMARY | total: N | pass: A | fail: B | warn: C | skip: D | inconsistent: E | missing: F`
>
> No emojis, no ANSI escapes by default. If `-Color` is set: prefix FAIL/MISSING/INCONSISTENT lines with `[91m` (red), WARN with `[93m` (yellow), PASS with `[92m` (green), reset with `[0m`. Counters in SUMMARY are computed from the records array - never from print-side counting. Records emitted to the output array preserve manifest order (stable) then alphabetic by doc-path within a pin.

**Verification:**

- `Glob` - `scripts/doc-drift/Output.ps1` exists.
- `Grep` - `function Format-DriftReport` matches exactly once.
- `Grep` - all six record-line templates present as literal format strings: `FAIL | `, `WARN | `, `INCONSISTENT | `, `MISSING | `, `SKIP | `, `PASS | `.
- `Grep` - `SUMMARY | total:` literal present.

**Status:** `[x] done`

---

### Step 04.3 - Create CLI entry `check-doc-vs-gradle.ps1`

**Files:** `scripts/check-doc-vs-gradle.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `scripts/check-doc-vs-gradle.ps1` as the operator-facing entry point. Top-level `param` block:
>
> ```powershell
> [CmdletBinding()]
> param(
>     [string] $Doc,                  # filter to a single doc path
>     [string] $Pin,                  # filter to a single pin name
>     [switch] $VerboseOutput,        # avoid name clash with $VerbosePreference
>     [switch] $Color,
>     [switch] $AsBootstrapWarning    # force exit 0 regardless of FAIL count
> )
> ```
>
> Body:
>
> 1. Resolve repo root from `$PSScriptRoot/..` (script lives in `scripts/`, repo root one up).
> 2. Dot-source `doc-drift/GradleParser.ps1`, `doc-drift/DocParser.ps1`, `doc-drift/Comparator.ps1`, `doc-drift/Output.ps1`.
> 3. `$manifest = Import-PowerShellDataFile (Join-Path $PSScriptRoot 'doc-drift/pins.psd1')`.
> 4. Apply `-Pin` filter to `$manifest.Pins` before parsing.
> 5. `$gradle = Get-GradlePins -RepoRoot $repoRoot`.
> 6. `$mentions = Get-DocMentions -Manifest $manifest -RepoRoot $repoRoot`; if `-Doc` filter set, narrow `$mentions` to that doc path.
> 7. `$records = Compare-PinsToDocs -GradlePins $gradle -DocMentions $mentions`.
> 8. `$lines = Format-DriftReport -Records $records -Verbose:$VerboseOutput -Color:$Color`.
> 9. Print each line to host.
> 10. Compute exit code: count of records where `Status -in @('FAIL','INCONSISTENT','MISSING')`. `0` if zero; `1` otherwise. If `-AsBootstrapWarning` is set, override to `0` after printing.
> 11. `exit $exitCode`.
>
> Head of file: comment block with usage examples for each flag combination. Cite strategic §5.4 channels.

**Verification:**

- `Glob` - `scripts/check-doc-vs-gradle.ps1` exists.
- `Grep` - `param\(` present with all five named parameters.
- `Grep` - `Import-PowerShellDataFile` present.
- `Grep` - `-AsBootstrapWarning` referenced in code (exit override) and in usage comment.
- Run `pwsh -NoProfile -File ./scripts/check-doc-vs-gradle.ps1 -Pin agp`. Expected exit: `1` (drift exists for `agp` per strategic §4) | actual: capture value. Expected stdout to contain `FAIL | agp` | actual: capture text.
- Run `pwsh -NoProfile -File ./scripts/check-doc-vs-gradle.ps1 -Pin agp -AsBootstrapWarning`. Expected exit: `0` (override) | actual: capture value.

**Status:** `[x] done`

---

### Step 04.4 - End-to-end smoke run on current repo

**Files:** none (validation only)
**Depends on:** Step 04.3

**Prompt for developer:**

> Run the full chequer with no filters: `pwsh -NoProfile -File ./scripts/check-doc-vs-gradle.ps1`. Capture exit code and full output to `temp/S0271_phase04_smoke.log`. Sanity-check the output against strategic §4 evidence: every pin listed there (AGP, KSP, Hilt-with-multi-mention, Room, Glide, core-ktx, appcompat, material, jsch) must appear as `FAIL` or `INCONSISTENT` in the output. Media3 must appear as `PASS` only if `-VerboseOutput` is set (negative-case control). If any expected FAIL is absent, the gap is in either Phase 02 (parser missed the pin) or Phase 03 (manifest entry missing) - identify which and fix before marking this step done.

**Verification:**

- `Glob` - `temp/S0271_phase04_smoke.log` exists.
- `Grep` - file contains `FAIL | agp` line.
- `Grep` - file contains `INCONSISTENT | lib.com.google.dagger:hilt-android` (or `FAIL | lib.com.google.dagger:hilt-android`) - the Hilt multi-mention case from strategic §4.
- `Grep` - file contains `SUMMARY | total:` line.
- Read first line of file: expected starts with one of `FAIL`, `WARN`, `INCONSISTENT`, `MISSING`, `SKIP`, `PASS` | actual: capture first token.
- Exit code recorded: expected `1` | actual: capture from log.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] CLI runs end-to-end without exception, deterministic output between two consecutive runs (idempotence per strategic §3.2).
- [x] No emoji or ANSI escapes in default output mode.
- [x] Grep for unresolved phase-04 placeholder markers returns zero hits.
- [x] Dev log entries added for `Comparator.ps1`, `Output.ps1`, `check-doc-vs-gradle.ps1`.

---

## Handoff Notes to Next Phase

The chequer is complete and demonstrably surfaces real drift. Phase 05 builds the test harness to lock the contract, and adds the README operator material that strategic §5.4 references.

---

## Rollback Plan

Delete `scripts/doc-drift/Comparator.ps1`, `scripts/doc-drift/Output.ps1`, `scripts/check-doc-vs-gradle.ps1`. No data migration.
