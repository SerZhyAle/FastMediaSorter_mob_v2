# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0264_panel-stereo-crop-fix.md`](../S0264_panel-stereo-crop-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Close the ticket mechanically: sync progress artifacts, refresh catalog if needed, and leave the spec ready for `/spec-check`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] All build validations from previous phases passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0264_panel-stereo-crop-fix.md` | Modified | ≤ 500 |
| `PLAN/S0264_panel-stereo-crop-fix/INDEX.md` | Modified | ≤ 400 |
| `dev/CHANGELOG.md` | Modified via script | n/a |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Mark tactical progress in spec artifacts

**Files:** `PLAN/S0264_panel-stereo-crop-fix.md`, `PLAN/S0264_panel-stereo-crop-fix/INDEX.md`
**Depends on:** Phase 01, Phase 02

**Prompt for developer:**

> Update tactical progress markers, keep strategic/tactical status aligned, and ensure the next operator can run `/spec-check S0264` without reconstructing state from the transcript.

**Verification:**

- `Grep` - `PLAN/S0264_panel-stereo-crop-fix/INDEX.md` contains `Completion Gate`
- `Grep` - `PLAN/S0264_panel-stereo-crop-fix.md` contains `## Last Audit`
- `Grep` - both files return zero hits for stale placeholders like `[ ]` in completed sections that were meant to be closed during this phase

**Status:** `[x]` done

---

### Step 03.2 - Run mandatory post-change sync

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run mandatory post-change rituals for every modified file from the implementation phases. If Kotlin files changed, refresh the app catalog once with the wrapper script instead of separate scan/render calls.

**Verification:**

- `PowerShell` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0 if Kotlin changed
- `PowerShell` - required `scripts/post-change.ps1` invocations exit 0
- `Grep` - `dev/CHANGELOG.md` contains `S0264` or the matching changed-file entries added by scripts

**Status:** `[x]` done

---

### Step 03.3 - Prepare audit handoff

**Files:** `PLAN/S0264_panel-stereo-crop-fix.md`, `PLAN/S0264_panel-stereo-crop-fix/INDEX.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Verify that no blocker remains, then hand the ticket off to `/spec-check`. Do not claim completion without the actual audit result.

**Verification:**

- `Grep` - `PLAN/S0264_panel-stereo-crop-fix/INDEX.md` returns zero unchecked items under `Pre-Implementation Blockers`
- `Grep` - `PLAN/S0264_panel-stereo-crop-fix.md` returns zero hits for `Status:** Approved`
- `Grep` - `PLAN/S0264_panel-stereo-crop-fix.md` contains `**Status:** Tactical` before `/spec-dev` and later advances only through the audit flow

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed beyond the bugfix itself.
