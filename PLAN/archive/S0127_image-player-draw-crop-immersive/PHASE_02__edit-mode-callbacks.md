# Phase 02 — Edit Mode Callbacks

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Wire the existing `ImageDrawOverlayManager` and `ImageCropManager` so their enter/exit transitions report the active mode to `PlayerViewModel.setImageEditMode(...)`. No UI behavior change yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 920 |

> `ImageCropManager.kt` is 495 LOC. After this phase it may exceed 500 — backup required before edit (timestamped copy in `temp/`).
> `PlayerActivity.kt` is 1210 LOC — backup required before edit.

---

## Steps

### Step 02.1 — Add `editModeCallback` to `ImageDrawOverlayManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `ImageDrawOverlayManager.kt`, add a new public property after `var saveCallback: DrawOverlaySaveCallback? = null`:
> ```kotlin
> var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null
> ```
> Then in `enterDrawMode()`, immediately after the existing `Timber.d("S0107: enterDrawMode")` line, add:
> ```kotlin
> editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.DRAW)
> ```
> In `cleanupCanvas()`, immediately after `toolbarRoot?.visibility = View.GONE`, add:
> ```kotlin
> editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
> ```
> Do not modify other code in those methods.

**Verification:**

- `Grep` — `var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null` matches once.
- `Grep` — `editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.DRAW)` matches once.
- `Grep` — `editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt (+3 LOC). Dev log recorded.

---

### Step 02.2 — Add `editModeCallback` to `ImageCropManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `ImageCropManager.kt`, add a new public property immediately after the `private var activeCallback: Callback? = null` line:
> ```kotlin
> var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null
> ```
> In `enterCropMode(...)`, immediately after the existing `Timber.d("S0106: enterCropMode mode=$mode file=${currentFile.name}")` line, add:
> ```kotlin
> editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.CROP)
> ```
> In `exitCropMode()`, immediately before `currentMode = null`, add:
> ```kotlin
> editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
> ```
> Do not modify other code in those methods.

**Verification:**

- `Grep` — `var editModeCallback: ((com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit)? = null` matches once in this file.
- `Grep` — `editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.CROP)` matches once.
- `Grep` — `editModeCallback?.invoke(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)` matches once in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageCropManager.kt (+3 LOC; backup at temp/ImageCropManager_20260509_132315.kt.backup). Dev log recorded.

---

### Step 02.3 — Wire callbacks from `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `PlayerActivity.kt`, locate the existing `setupDrawOverlaySaveCallback()` method. Immediately after that method, add a new `private fun setupEditModeCallbacks()`:
> ```kotlin
> private fun setupEditModeCallbacks() {
>     Timber.d("S0127: setupEditModeCallbacks wiring Draw + Crop → ViewModel")
>     val cb: (com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit = { mode ->
>         viewModel.setImageEditMode(mode)
>     }
>     imageDrawOverlayManager.editModeCallback = cb
>     imageCropManager.editModeCallback = cb
> }
> ```
> Then locate the existing `setupDrawOverlaySaveCallback()` invocation site (search for `setupDrawOverlaySaveCallback()` outside the method definition). Immediately after that call, add `setupEditModeCallbacks()`.

**Verification:**

- `Grep` — `private fun setupEditModeCallbacks()` matches once.
- `Grep` — `imageDrawOverlayManager.editModeCallback = cb` matches once.
- `Grep` — `imageCropManager.editModeCallback = cb` matches once.
- `Grep` — `setupEditModeCallbacks()` matches at least twice (definition + invocation).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt (+9 LOC), app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt (+1 LOC). Note: invocation site lives in PlayerManagerInitializer, not PlayerActivity — Files Touched updated accordingly. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 31s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 03 introduces `PlayerImmersiveModeManager` that observes `state.imageEditMode` and toggles system bars + command panels.

---

## Rollback Plan

Revert the phase commit(s); the new callback property defaults to `null`, so removing the wiring is safe.
