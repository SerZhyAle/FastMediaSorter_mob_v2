# Phase 03 — Off-Toggle Clears HUD Label

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

When the user disables `vrShowFps` while inside an immersive session, the FPS label must vanish on the next HUD redraw. Until now `updateFps(fps: Int)` only sets a positive value; nothing clears the existing `state.fps`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt` | Modified | ≤ 100 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | Modified | ≤ 600 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1010 |

---

## Steps

### Step 03.1 — Add `clearFps()` to sink + driver

**Files:**
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`

**Depends on:** — start of phase

**Prompt for developer:**

> In `VrHudSink.kt`, add an interface method `fun clearFps() {}` next to `updateFps`, with a default no-op body. In `VrHudSceneDriver.kt`, override it: wrap in `runOnMain { state = state.copy(fps = null); requestRedraw() }`. Do not modify `VrHudIndicatorManager` (Android-view fallback already does not draw FPS).

**Verification:**

- `Grep` — `fun clearFps\(\)` matches at least once in `VrHudSink.kt`.
- `Grep` — `override fun clearFps\(\)` matches exactly once in `VrHudSceneDriver.kt`.
- `Grep` — `state\.copy\(fps = null\)` matches exactly once in `VrHudSceneDriver.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Files: `VrHudSink.kt` (+1 LOC), `VrHudSceneDriver.kt` (+6 LOC).

---

### Step 03.2 — Call `clearFps()` when flag flips off in active session

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inside `renderVrFrame`, after the FPS publish block (Phase 02), if `viewModel.settings.value.vrShowFps == false` AND `vrFpsLastValid > 0`: call `vrHudManager?.clearFps()`, then set `vrFpsLastValid = 0` so the clear fires only once per off-transition. Add a single Timber log line `Timber.d("VR_FPS: cleared HUD label after vrShowFps→false")` only inside the off-branch.

**Verification:**

- `Grep` — `vrHudManager\?\.clearFps\(\)` matches exactly once in `VrPlayerActivity.kt`.
- `Grep` — `VR_FPS: cleared HUD label after vrShowFps→false` matches exactly once.
- `Grep` — `Log\.d\(` matches zero times in `VrPlayerActivity.kt` (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Files: `VrPlayerActivity.kt` (+5 LOC). Off-branch fires `clearFps()` once and resets `vrFpsLastValid`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles — `/build` `vr debug` PASS (auto-build — PASS).
- [x] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.

---

## Handoff Notes to Next Phase

`clearFps()` is now part of the public sink contract; future overlays can ignore it. The flag-off path now produces exactly one HUD redraw and then stops touching the FPS slot.

---

## Rollback Plan

Revert phase commit. Default no-op interface method keeps non-VR backends safe under any partial revert.
