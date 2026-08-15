# Phase 01 - Log Hygiene

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Add a repeatable audit for ticket-bearing permanent logs and remove every stale `Sxxxx` from permanent `Timber.i/w/e` and long-lived `Timber.d` across `app_v2` and `wear`, then make the audit a fail-closed gate.

> Owner decision (strategic §6.1): full scope - the gate in Step 01.3 is fail-closed, so all flagged permanent-log ticket-ids must be rewritten in this phase, not a partial batch. Probes `Timber.d("Sxxxx: ..")` of tickets currently in `BlockNeedUserTest` are exempt and stay.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] First-wave boundary and permanent-log ticket-id policy are approved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-no-ticket-logs.ps1` | New | ≤ 250 |
| All `.kt` files flagged by Step 01.1 across `app_v2/**` + `wear/**` | Modified | per-file ≤ 120 |

> The authoritative file set is the Step 01.1 audit output (~30 files as of the 2026-06-07 audit; e.g. `ReceiveShareActivity.kt`, `BrowseCameraCaptureManager.kt`, `LinkAutoDownloadResultPresenter.kt`, `S0200AuthStateWipe.kt`, `GoogleDriveCredentialsManager.kt`, `PhoneWearListenerService.kt`, plus the rest). The fail-closed gate in Step 01.3 forbids leaving any flagged file unrewritten.
> Each rewrite is message-text only - control flow, structured values, and exception objects stay. No single file approaches 500 lines from a message rewrite, so no backup step is needed.

---

## Steps

### Step 01.1 - Add permanent-log ticket audit

**Files:** `scripts/quality/assert-no-ticket-logs.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a PowerShell audit that scans `app_v2/**` and `wear/**` for `Sxxxx` inside `Timber.i`, `Timber.w`, `Timber.e`, and long-lived `Timber.d` messages (include flavor source sets, not just `src/main`). Resolve the ticket status through `PLAN/spec-catalog.jsonl`, allow only `Timber.d("Sxxxx: ..")` probes whose ticket status is `BlockNeedUserTest`, and report every forbidden message with file/line plus an `expected:` / `actual:` count. In this step the audit only reports (does not yet gate); it produces the authoritative file list for Step 01.2.

**Verification:**

- `Glob` - `scripts/quality/assert-no-ticket-logs.ps1` exists.
- `Grep` - `PLAN/spec-catalog.jsonl` appears in `scripts/quality/assert-no-ticket-logs.ps1`.
- `Grep` - `BlockNeedUserTest` appears in `scripts/quality/assert-no-ticket-logs.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Created `scripts/quality/assert-no-ticket-logs.ps1` (report+`-Gate` modes; resolves probe status via `PLAN/spec-catalog.jsonl`; identifier-boundary regex so class names like `MigrateS0059UseCase` are not false-flagged). Audit run: `expected: 0 | actual: 95` forbidden, 49 allowed BlockNeedUserTest probes. Dev log recorded.

---

### Step 01.2 - Rewrite all flagged ticket-bearing log messages

**Files:** every file in the Step 01.1 audit output (`app_v2/**` + `wear/**`)
**Depends on:** Step 01.1

**Prompt for developer:**

> Rewrite every stale `Sxxxx` log string flagged by Step 01.1 into a plain-English operational message that names the subject without the ticket id. Cover the full flagged set, not a sample. Keep control flow unchanged; preserve structured values and exception objects. Do not touch `Timber.d("Sxxxx: ..")` probes whose ticket is still `BlockNeedUserTest` - those are exempt by the CLAUDE.md invariant.

**Verification:**

- `Grep` - `Timber\.(i|w|e)\("S\d{4}` returns zero hits across `app_v2/**` and `wear/**`.
- Run - `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` reports zero forbidden permanent-log messages (`expected: 0 | actual: 0`).
- `Grep` - the count of `Timber\.d\("S\d{4}` matches exactly the set of tickets currently in `BlockNeedUserTest` (probes preserved, nothing else).

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Rewrote 94 forbidden permanent-log lines across 38 files (app_v2 + wear) via a disposable `temp/fix_ticket_logs.ps1` operating only on audit-flagged lines (probe lines and comment lines skipped, EOL preserved). Post-fix audit: `expected: 0 | actual: 0`. `Timber.(i|w|e)("S\d{4}` zero hits (the 3 residual `MigrateS0059UseCase:` lines are class-name scope, not provenance tags). 130 `Timber.d("Sxxxx:")` probe lines preserved across 49 BlockNeedUserTest tickets. Note: the only `S0224` occurrence was a code comment, not a live probe - strategic §1.1 point 6 corrected. Dev logs recorded.

---

### Step 01.3 - Gate shared validation on the new audit

**Files:** `scripts/post-change.ps1`, `scripts/quality/assert-no-ticket-logs.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Hook the new audit into the shared post-change or validation path used by this repository's hygiene workflow. Make the command callable without editing source files by hand and keep the gate fail-closed for future stale `Sxxxx` permanent logs.

**Verification:**

- `Grep` - `assert-no-ticket-logs.ps1` appears in `scripts/post-change.ps1`.
- `Grep` - `post-change` remains present as the command entrypoint inside `scripts/post-change.ps1`.
- `Grep` - `Log\.d\(` returns zero hits in `scripts/post-change.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Added a fail-closed `ticket-log-audit` step to `scripts/post-change.ps1` (runs `assert-no-ticket-logs.ps1 -Gate -Quiet` for ChangeType Kotlin/Mixed). Confirmed `assert-no-ticket-logs.ps1` referenced (line 166), `post-change` entrypoint intact, zero `Log.d(`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 db` → BUILD SUCCESSFUL (standardDebug, 1m07s). Surrogate for noLegal/wear: 3 string-only log edits, equivalent risk.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (38 source files + 2 scripts).
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Shared validation can now fail on stale permanent `Sxxxx` logs before more cleanup work starts.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
