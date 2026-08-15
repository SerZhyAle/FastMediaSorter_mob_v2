# Phase 02 - Selection on release

**Strategic spec:** [`../S1210_edge-gesture-cancel-target-menu.md`](../S1210_edge-gesture-cancel-target-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Turn the edge band's touch handler into a selection loop: the finger position picks cancel or one action, the pick is shown live, and it is committed on `ACTION_UP` only.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` free (`scripts/utils/lock-status.ps1 -Name Code`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 700 |

> File exceeds 500 LOC - Step 02.1 takes the mandated backup first.
>
> `src/screenCapture/` is the flavor-mounted source set shared by `noLegal` and by `standard` under the edge-overlay build flag; no `src/main/` guard is introduced.

---

## Steps

### Step 02.1 - Back up the manager before editing

**Files:** `temp/S1210/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `ScreenGestureOverlayManager.kt` to `temp/S1210/` with a timestamped name before any edit (CLAUDE.md Rule 5 - file is over 500 LOC).

**Verification:**

- `Glob` - `temp/S1210/ScreenGestureOverlayManager_*.kt` matches at least once.

**Status:** `[x] done`

---

### Step 02.2 - Introduce the selection state

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the `gestureTriggered` / `gestureCancelled` / `maxInward` trio with a single per-touch selection value: cancel, or one direction. Reset it on `ACTION_DOWN`. The cancel state is the initial value - a touch that never moves inward is a cancel, not a special case.

**Verification:**

- `Grep` - `gestureTriggered` returns zero hits in the file.
- `Grep` - `gestureCancelled` returns zero hits in the file.
- `Grep` - the new selection field is declared exactly once.

**Status:** `[x] done`

---

### Step 02.3 - Map the finger onto cancel or a direction

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> On `ACTION_MOVE`, resolve the selection from the drag: inward travel shorter than the hint's `cancelTargetExtentPx` (Phase 01) selects cancel; beyond it, the existing angle classifier picks the direction, and an angle outside every window also selects cancel. Push the resolved selection into the hint so exactly one item is lit. Delete the separate trigger distance - the cancel target's depth is now the only boundary. Do not fire anything here.

**Verification:**

- `Grep` - `GESTURE_DISTANCE_PX` returns zero hits in the file.
- `Grep` - `cancelTargetExtentPx` is read in the move branch - matches at least once.
- `Grep` - `onGestureMatched` does not appear inside the `ACTION_MOVE` branch.

**Status:** `[x] done`

---

### Step 02.4 - Commit on lift, drop on system cancel

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> On `ACTION_UP`, dispatch the selected direction through `onGestureMatched` and report the touch as consumed; a cancel selection dispatches nothing. On `ACTION_CANCEL`, dispatch nothing regardless of the selection (strategic ADR-3). Both take the hint down and reset the selection. Keep the existing `performClick` call on the dispatch path so accessibility services still see the activation.

**Verification:**

- `Grep` - `onGestureMatched` appears exactly once in the file, inside the up branch.
- `Grep` - `MotionEvent.ACTION_CANCEL` is handled in a branch that does not call `onGestureMatched`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 02.5 - Refresh the debug probes

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Remove the `S1162:` probes that describe the deleted return-to-edge rule and add one `Timber.d("S1210: ..")` probe at the commit point reporting the touch-end action and the selection. One probe per changed flow entry, under 120 characters, `Sxxxx:` prefix only on these temporary lines.

**Verification:**

- `Grep` - `S1162: gesture cancelled` returns zero hits in the file.
- `Grep` - `Timber.d("S1210:` matches exactly once in the file.
- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` exits 0.

**Status:** `[x] done`

---

## Step Log

- 2026-07-27 - Steps 02.1-02.5 executed in order. Backup: `temp/S1210/ScreenGestureOverlayManager_20260727_153858.kt`. `gestureTriggered` / `gestureCancelled` / `maxInward` / `GESTURE_DISTANCE_PX` and the whole return-to-edge rule are gone; `selection` (null = cancel) replaces them, `resolveSelection` maps the drag, `ACTION_UP` dispatches, `ACTION_CANCEL` drops. `.\a.ps1 fk` BUILD SUCCESSFUL; `post-change.ps1 -ScopeToFile` PASS.
- 2026-07-27 - PREDICATE-FIX 02.4: the plan asked for `onGestureMatched` to match exactly once, but the constructor parameter carries the same name - the predicate is read as one *call site*, and there is exactly one, inside the lift branch.
- 2026-07-27 - 02.5: `assert-no-ticket-logs.ps1` reports the new `S1210` probe as stale while the ticket is `In Progress`; it turns legal on the Phase 03 flip to `BlockNeedUserTest`, and the gate is re-run there.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on the touched file returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gesture no longer fires mid-drag. A touch on a band always ends in exactly one outcome: the selected action, or nothing. The S1162 probes tied to the old cancellation rule are gone.

---

## Rollback Plan

Restore the Step 02.1 backup and revert the phase commit - no persisted state or user data is involved.
