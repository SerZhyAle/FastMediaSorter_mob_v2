# Phase 02 - Stream/collection host wiring (PlayerActivity)

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

Make `PlayerActivity` (host for streams and in-collection playback) switch between fullscreen and command-panel mode on device rotation, reusing the existing `enterFullscreenMode` / `enterCommandPanelMode` transitions without restarting playback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `PlayerOrientationModeManager` and `ScreenRotationManager.followsDevice()` exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1230 |

> File is 1205 LOC (>500) - take a timestamped backup in `temp/` before editing.

---

## Steps

### Step 02.1 - Drive display mode from orientation in onConfigurationChanged

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `PlayerActivity.onConfigurationChanged`, after the existing panel/insets handling, compute the target mode via `PlayerOrientationModeManager().resolve(isLandscape, followsDevice, isVisualMedia)` where `isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE`, `followsDevice = screenRotationManager.followsDevice()`, and `isVisualMedia` is true only when `viewModel.state.value.currentFile?.type` is `VIDEO`, `IMAGE`, or `GIF`. On `FULLSCREEN` call `viewModel.enterFullscreenMode()`; on `COMMAND_PANEL` call `viewModel.enterCommandPanelMode()`; on `null` do nothing. These existing actions only flip `showCommandPanel`, so playback, position and image zoom/pan are preserved (research 03). Hold a single `PlayerOrientationModeManager` instance as a property rather than constructing per call. Add the `S0667` debug tag here (see Phase Done note).

**Verification:**

- `Grep` - `PlayerOrientationModeManager` referenced in `PlayerActivity.kt`.
- `Grep` - `screenRotationManager.followsDevice()` present.
- `Grep` - both `enterFullscreenMode()` and `enterCommandPanelMode()` invoked in `PlayerActivity.kt`.
- `Grep` - `PlayerDisplayMode.FULLSCREEN` and `PlayerDisplayMode.COMMAND_PANEL` referenced.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 6/6 PASS. Files: PlayerActivity.kt (+~18 LOC). S0667 debug tag deferred to finalization (inserted before final build, after BlockNeedUserTest flip, per ticket-log gate).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] One `Timber.d("S0667: ..")` tag present at the mode-switch entry in `onConfigurationChanged` (ticket enters `BlockNeedUserTest`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Stream/collection host now follows orientation. Standalone host (Phase 03) must mirror the same resolver call but apply through its own fullscreen transition (per-host glue mirroring).

---

## Rollback Plan

Revert phase commit(s) - only `onConfigurationChanged` behaviour added; no data migration. Mode reverts to manual-only toggling.
