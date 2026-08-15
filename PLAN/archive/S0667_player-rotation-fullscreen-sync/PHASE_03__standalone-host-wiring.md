# Phase 03 - Standalone host wiring (StandalonePlayerActivity)

**Strategic spec:** [`../S0667_player-rotation-fullscreen-sync.md`](../S0667_player-rotation-fullscreen-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Make `StandalonePlayerActivity` switch between fullscreen and command-panel mode on device rotation, mirroring Phase 02 but applying through its own `StandaloneFullscreenManager` panel-aware transition without restarting playback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `PlayerOrientationModeManager` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1060 |

> File is 1032 LOC (>500) - take a timestamped backup in `temp/` before editing.

---

## Steps

### Step 03.1 - Drive display mode from orientation in onConfigurationChanged

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `StandalonePlayerActivity.onConfigurationChanged`, after the existing insets re-apply, compute the target mode via `PlayerOrientationModeManager().resolve(isLandscape, followsDevice = true, isVisualMedia)` where `isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE` and `isVisualMedia` is true only when `currentMediaType()` is `VIDEO`, `IMAGE`, or `GIF`. The standalone host has no app-level orientation lock (it follows the device by manifest), so pass `followsDevice = true`. Apply only when the current panel state differs from the target: on `FULLSCREEN`, if `binding.topCommandPanel.isVisible`, call `fullscreenManager?.enterFullscreenWithPanel(binding.topCommandPanel) { updateFullscreenButtonState(it) }`; on `COMMAND_PANEL`, if not visible, call `fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { updateFullscreenButtonState(it) }`; on `null` do nothing. Reusing the panel-aware transitions preserves playback and image zoom/pan (research 03). Hold a single `PlayerOrientationModeManager` instance as a property. Add the `S0667` debug tag at this entry (see Phase Done note).

**Verification:**

- `Grep` - `PlayerOrientationModeManager` referenced in `StandalonePlayerActivity.kt`.
- `Grep` - both `enterFullscreenWithPanel` and `exitFullscreenWithPanel` invoked in `StandalonePlayerActivity.kt`.
- `Grep` - `PlayerDisplayMode.FULLSCREEN` and `PlayerDisplayMode.COMMAND_PANEL` referenced.
- `Grep` - `currentMediaType()` used in the rotation branch.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 5/5 PASS. Files: StandalonePlayerActivity.kt (+~28 LOC). S0667 debug tag deferred to finalization (after BlockNeedUserTest flip, per ticket-log gate).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] One `Timber.d("S0667: ..")` tag present at the mode-switch entry in `onConfigurationChanged`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both player hosts now follow orientation through the shared resolver. Phase 04 records the capability and regenerates the catalog.

---

## Rollback Plan

Revert phase commit(s) - only `onConfigurationChanged` behaviour added; no data migration. Mode reverts to manual-only toggling.
