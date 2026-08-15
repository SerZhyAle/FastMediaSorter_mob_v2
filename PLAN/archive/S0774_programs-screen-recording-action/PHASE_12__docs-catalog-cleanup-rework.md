# Phase 12 - Docs & catalog cleanup (rework)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 09, Phase 10, Phase 11 (all rework phases done)
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Close the rework the same way Phase 08 closed the original plan: regenerate the catalog for the two new public classes, log every touched file, and confirm the delivered-capability record reflects pause support.

---

## Prerequisites

- [ ] Phases 09, 10, 11 are ✅ Done.
- [ ] `.\a.ps1 fc` green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | - |
| `docs/ALL_FEATURES.jsonl` | Modified (amend existing S0774 record, or note if unchanged) | - |

---

## Steps

### Step 12.1 - Regenerate the catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (+ `.md`)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Confirm `RecordingElapsedTimer` and `RecordingIndicatorOverlayManager` both appear with `role`/`status` set via `dev/CATALOG/scripts/set.ps1` if the sync leaves them unset - `RecordingElapsedTimer` role `util`, `RecordingIndicatorOverlayManager` role `ui` (mirrors the other `ui/main/helpers/Main*Manager` entries).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "RecordingElapsedTimer"` returns 1 record.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "RecordingIndicatorOverlayManager"` returns 1 record.

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` -> 2102 records. Both new classes present; set `status=new` (pending device verification, not `tested`) via `set.ps1` - `RecordingElapsedTimer` role `util`, `RecordingIndicatorOverlayManager` role `ui` (already inferred correctly by the scan, only status needed setting).

---

### Step 12.2 - Review the ALL_FEATURES record for the pause addition

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Find the existing S0774 entry (screen video recording). If its wording already generically covers "record/stop" without excluding pause, leave it - do not create a duplicate record for the same scenario. If the wording is start/stop-specific enough that pause reads as a gap, amend the single existing record via `scripts/all_features/add.ps1` (or its update path, if present) to mention pause. Either outcome is a valid step result; record which one in the dev log line.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - exactly one S0774-related record in `docs/ALL_FEATURES.jsonl` (no duplicate).

**Status:** `[x] done`

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. The S0774 record's old wording ("stop from the notification or an in-app timer card") was specific enough to read as a gap once pause + the compact indicator shipped - amended in place via `add.ps1` upsert-by-id (no duplicate created). Also amended the S0523 quick-capture record (voice capture also gained pause/resume/discard in this same rework) for the same reason. `validate.ps1` -> PASS: 486 records (unchanged count, confirms upsert not insert).

---

## Finalization (all phases done)

Per `/spec-dev`'s mandatory closing step for the last phase in scope: inserted 5 `Timber.d("S0774: ...")` debug tags at the changed-flow entry points (screen recording pause/resume in `ScreenVideoRecordingService`, voice capture pause/resume in `MainVoiceCaptureManager`, compact indicator shown in `RecordingIndicatorOverlayManager` - shared by both) before the final `.\a.ps1 fk` + `.\a.ps1 fkn` build (both `BUILD SUCCESSFUL`). Did not re-tag the unchanged `start()`/`startRecording()` flows - their own logic did not change, only the UI wrapped around them, which the shared indicator tag already covers. Flipped journal status `In Progress -> BlockNeedUserTest` via `close-and-log.ps1` with a device-test `StatusNote` covering both recording types' indicator/pause/resume/discard/safe-bounds/TalkBack. `assert-no-ticket-logs` -> PASS after the flip (was FAIL immediately after tag insertion but before the status flip - expected, the invariant requires both to land together).

---

## Phase Done Criteria

- [x] Both steps `[x]`.
- [ ] `/spec-check S0774` returns `Verified` - not yet: status intentionally flipped to `BlockNeedUserTest` (see below) pending the next device-test cycle, matching how this ticket was gated before the rework.
- [x] Dev log entry added for the catalog sync and the ALL_FEATURES review.

---

## Handoff Notes to Next Phase

- Final phase - see INDEX.md Completion Gate. Next lifecycle step is a device retest (`BlockNeedUserTest` again) confirming the compact indicator and pause/resume on a real device, then `/spec-check S0774`.

---

## Rollback Plan

Revert the phase commit - catalog is gitignored/regenerable, ALL_FEATURES amendment (if any) is a single-line JSONL change reversible from history.
