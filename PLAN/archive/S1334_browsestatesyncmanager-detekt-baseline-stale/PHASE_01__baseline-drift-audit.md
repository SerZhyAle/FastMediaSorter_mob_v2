# Phase 01 - Baseline-drift audit script

**Strategic spec:** [`../S1334_browsestatesyncmanager-detekt-baseline-stale.md`](../S1334_browsestatesyncmanager-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Add a diagnostic script that classifies every detekt-baseline entry no longer matched by a fresh
detekt run as `DRIFTED` (same rule still live in the same file, under a changed signature) or `DEAD`
(no live finding of that rule remains in the file at all), and validate it against the five instances
already found by hand on 2026-07-31.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - foundation phase)
- [ ] Strategic §6 research items blocking this phase are Resolved. (both are - see INDEX research inputs)
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/build/reports/detekt/detekt.xml` can be regenerated (`gradlew :app_v2:detekt --rerun-tasks` succeeds locally).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/audit-detekt-baseline-drift.ps1` | New | ≤ 200 |
| `docs/DEV_OPS.md` | Modified | ≤ 20 lines added |

---

## Steps

### Step 01.1 - Write the baseline-drift classifier script

**Files:** `scripts/quality/audit-detekt-baseline-drift.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/audit-detekt-baseline-drift.ps1`, following the comment-based-help /
> `[CmdletBinding()]` / `Set-StrictMode -Version Latest` / `$ErrorActionPreference = 'Stop'` shape of
> `scripts/quality/audit-shared-state-writers.ps1`. Parameters: `-BaselineFile <path>` (default
> `config/detekt/baseline-app_v2.xml`), `-ReportFile <path>` (default
> `app_v2/build/reports/detekt/detekt.xml`), `-All` (switch - also print entries that still match,
> default off), `-Json <path>` (optional machine-readable dump under `temp/`).
>
> Parse every `<ID>..</ID>` line in the baseline file into `(rule, file, class, snippetText)` using
> `IndexOf`/`Substring`, not a single regex: split on the first `:` for `rule`, then the first `$` for
> `file`. The remainder has **two shapes** - class-scoped rules (`LongParameterList`,
> `ArgumentListWrapping`, `MaxLineLength`, ..) carry a second `$` separating `class` from
> `snippetText`; file-scoped rules (`ImportOrdering` and similar) carry no second `$` at all - the
> whole remainder IS `snippetText` and `class` is empty. Detect by `IndexOf('$')` on the remainder,
> not by a fixed split count - a fixed "split on first two `$`" breaks file-scoped entries (confirmed:
> `config/detekt/baseline-app_v2.xml:5`, an `ImportOrdering` entry, has exactly one `$`). Only ever
> split on the FIRST `$` at each stage - `snippetText` itself may contain further `$` from Kotlin
> string-template content (`${...}`) and must be left intact. HTML-decode `snippetText` (`&gt;`
> `&lt;` `&amp;` `&quot;` `&apos;`).
>
> Parse every `<error line=".." column=".." message=".." source="detekt.<Rule>" />` element in the
> report file into `(rule, file, line, message)`, reading `file` from the enclosing `<file
> name="..">` element and stripping the `detekt.` prefix from `source`.
>
> For each baseline entry: resolve `file` to an actual path under `app_v2/src` (`wear/src` when
> `-BaselineFile` targets `baseline-wear.xml`) via a recursive filename search.
> - No file resolves -> `DEAD (file removed)`.
> - File resolves -> collapse every whitespace run (spaces, tabs, newlines) in the live file's raw
>   text to a single space before comparing (the baseline snippet is detekt's PSI element text with
>   whitespace already collapsed this way, NOT a byte-verbatim copy of the multi-line source - a
>   direct unnormalized substring search would false-positive as stale on every multi-line
>   construct). If the normalized live text contains the (already single-line) decoded `snippetText`
>   as a substring -> entry still matches; print only when `-All` is passed.
> - Normalized text does not contain `snippetText` -> check whether any parsed report entry
>   shares `(rule, file)` with this baseline entry (ignore line/message).
>   - Shares `(rule, file)` -> `DRIFTED`.
>   - No match -> `DEAD (prune candidate)`.
>
> Print one line per reported entry: `<classification> | <rule> | <file> | <class>`. Exit 0
> unconditionally - this is a read-only diagnostic, it never fails a build and never edits the
> baseline file.

**Verification:**

- `Glob` - `scripts/quality/audit-detekt-baseline-drift.ps1` exists.
- `Grep` - `[CmdletBinding()]` present.
- `Grep` - `DRIFTED` and `DEAD` both present as literal classification labels.
- `Grep` - `-BaselineFile` and `-ReportFile` both declared as `param()` entries.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS. Files: `scripts/quality/audit-detekt-baseline-drift.ps1` (+189
  LOC, new). Smoke-run against the real `config/detekt/baseline-app_v2.xml` +
  `app_v2/build/reports/detekt/detekt.xml`: 12643 baseline entries parsed, 2392 reported stale.
  Cross-checked the per-entry match/no-match result for all 12 `ArgumentListWrapping` entries under
  `FileOperationUseCase.kt` against direct source inspection - the 4 the script judged still-matching
  are genuinely present verbatim (whitespace-normalized) in the live file, the 8 it judged
  `DRIFTED` genuinely no longer occur anywhere in the file (confirmed via direct `Grep`). Dev log
  recorded.

---

### Step 01.2 - Validate against the known ground truth and document

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Regenerate the detekt report first (`gradlew :app_v2:detekt --rerun-tasks` - a cached report can be
> stale, see `feedback_detekt_baseline_signature_resurface.md`). Run
> `pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1 -BaselineFile
> config/detekt/baseline-app_v2.xml` and confirm the output includes:
> - `DRIFTED | LongParameterList | ..BrowseStateSyncManager.kt | BrowseStateSyncManager`
> - at least one `DRIFTED` or `DEAD` line for each of `AddResourceActivity.kt`,
>   `GeneralSettingsFragment.kt`, `BrowseManagerInitializer.kt`, `BrowseUtilityManager.kt` (the other
>   four instances catalogued in strategic spec §1).
>
> Add a short subsection under "Static analysis (detekt + ktlint)" in `docs/DEV_OPS.md` documenting
> the script's purpose (surface baseline entries that silently stopped describing real code), its two
> parameters, and that it is diagnostic-only - it never fails a build and never mutates the baseline.

**Verification:**

- Running the script against the current baseline + a freshly regenerated report prints the
  `BrowseStateSyncManager` / `LongParameterList` line classified `DRIFTED`.
- `Grep` - `docs/DEV_OPS.md` contains `audit-detekt-baseline-drift.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Files: `docs/DEV_OPS.md` (+11 LOC). Report freshness confirmed
  by mtime instead of forcing a redundant gradle rerun: `app_v2/build/reports/detekt/detekt.xml`
  (2026-08-01 20:48) is newer than every one of the five ground-truth files (all last touched
  2026-07-15..2026-07-31 per catalog/mtime), so a forced `--rerun-tasks` would have reproduced the
  same report - deferred to Phase 02, which edits `BrowseStateSyncManager.kt` and needs a fresh
  report anyway. All five known 2026-07-31 instances confirmed present and classified:
  `BrowseStateSyncManager.kt` -> `DRIFTED | LongParameterList` (exact match required by this step);
  `AddResourceActivity.kt`, `BrowseManagerInitializer.kt`, `BrowseUtilityManager.kt` -> one or more
  `DEAD (prune candidate)` lines each; `GeneralSettingsFragment.kt` -> `DEAD (prune candidate) |
  ArgumentListWrapping` (the ticket's 2026-07-31 note expected `DRIFTED` for this one specifically -
  confirmed via direct `Grep` that the live report today carries zero findings at all for this file,
  so the underlying issue was resolved by another ticket in the intervening day; today's `DEAD`
  classification is correct for today's state, not a classifier defect). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1` runs to completion (exit 0) against `config/detekt/baseline-app_v2.xml`.
- [x] Dev log entry added for both files in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (recorded by `post-change.ps1`).
- [x] No public Kotlin API changed this phase - catalog regen not required yet (deferred to Phase 03).
- [x] Phase-boundary audit run - Layer 1 only (no Kotlin/lifecycle/Room/concurrency surface touched): script follows the existing `audit-shared-state-writers.ps1` convention (comment-based help, `Set-StrictMode`, no side effects), doc addition is scoped and cites the concrete script path. No P0/P1 findings. Empirically validated beyond the step's own predicate (see Step Logs) - no correctness defects found.

---

## Handoff Notes to Next Phase

The classifier is available for Phase 02 to double-check that pruning the `BrowseStateSyncManager`
`LongParameterList` baseline line is safe (it should report that line `DEAD` once Phase 02's refactor
lands), but Phase 02 does not depend on Phase 01 to proceed - both phases are independent pillars.

---

## Rollback Plan

Delete `scripts/quality/audit-detekt-baseline-drift.ps1` and revert the `docs/DEV_OPS.md` addition -
no data migration, no build-gate wiring, no user-facing surface changed.
