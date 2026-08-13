# Phase 01 — Exit Target Redirect

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Replace the `VrTaskTransition.exitImmersiveToPanel` target from `MainActivity` (file browser) to `PlayerActivity` (flat playback panel) populated with the same file/position the user was watching in immersive. Carry context via intent-extras as resolved in strategic §6 #3.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` | Modified | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1700 |

---

## Steps

### Step 01.1 — Add `exitImmersiveToFlatPlayer(source, fileContext)` overload

**Files:** `VrTaskTransition.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new function `exitImmersiveToFlatPlayer(source: Activity, playerIntent: Intent)` next to the existing `exitImmersiveToPanel`. It must use the same `home-intent + PendingIntent` mechanism as the existing exit, but route the PendingIntent to the supplied `playerIntent` (which the caller has pre-populated with `EXTRA_RESOURCE_ID`, `EXTRA_INITIAL_FILE_PATH`, position, isPlaying, etc.). Add a Timber.i marker `"VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent file=%s"` with the resolved file path from the intent extras.

**Verification:**

- `Grep` — `fun exitImmersiveToFlatPlayer\(` matches exactly once in `VrTaskTransition.kt`.
- `Grep` — `VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent` matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 01.2 — Switch `VrPlayerActivity` exit calls to use the flat-player overload

**Files:** `VrPlayerActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `VrPlayerActivity` find every call to `VrTaskTransition.exitImmersiveToPanel(this, ...)`. For the user-driven «exit to panel» path (the one called from the overlay command, not from XR-init failure recovery), construct a `playerIntent` via `PlayerActivity.createIntent(context = this, resourceId = state.resourceId, initialFilePath = state.currentFile?.path, isPlaying = false, isSlideshowEnabled = state.isSlideshowEnabled)` and call the new `exitImmersiveToFlatPlayer(this, playerIntent)`. Leave the failure-recovery path (`launchVrFailureRecovery`) on the original `exitImmersiveToPanel(this)` — it should not carry context, it just needs to land somewhere usable.

**Verification:**

- `Grep` — `exitImmersiveToFlatPlayer\(this` matches at least 1 time in `VrPlayerActivity.kt`.
- `Grep` — `exitImmersiveToPanel\(this\)` (the no-context form) matches at most 2 times in `VrPlayerActivity.kt` (failure recovery only).

**Status:** `[x]` done

---

### Step 01.3 — Add `// recovery: not user exit` annotations on remaining `exitImmersiveToPanel` calls

**Files:** `VrPlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> For each remaining call to `exitImmersiveToPanel(this)` (failure-recovery paths), add a `// S0019 recovery: not user exit — falls back to MainActivity intentionally` comment immediately above. This makes the distinction obvious to future readers and to the audit predicate.

**Verification:**

- `Grep` — `S0019 recovery: not user exit` matches at least 1 time near every `exitImmersiveToPanel(this)` call.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `vr debug`.
- [ ] Dev log entries for `VrTaskTransition.kt` and `VrPlayerActivity.kt`.

---

## Handoff Notes to Next Phase

Phase 03 will piggy-back on the same `VrTaskTransition` infrastructure for the «return to immersive» path from the flat player; verify the new overload is reusable for that direction too.

---

## Rollback Plan

Revert phase commit. The new function is additive; reverting restores the prior single-overload behaviour.
