# Phase 03 — Immersive Controller

**Strategic spec:** [`../S0127_image-player-draw-crop-immersive.md`](../S0127_image-player-draw-crop-immersive.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 4 / 4
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Create `PlayerImmersiveModeManager` and wire observation of `state.imageEditMode` so that entry into Draw or Crop hides the top command panel, copy/move panels, filename overlay, and system bars. Exit restores them and forces the command panel back on.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt` | Modified | ≤ 200 |

> `PlayerActivity.kt` is 1210 LOC — backup required before edit (timestamped copy in `temp/`).

---

## Steps

### Step 03.1 — Create `PlayerImmersiveModeManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file at `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt`. Package: `com.sza.fastmediasorter.ui.player.helpers`. Class signature:
> ```kotlin
> class PlayerImmersiveModeManager(
>     private val activity: com.sza.fastmediasorter.ui.player.PlayerActivity,
>     private val safeViews: PlayerBindingSafeViews
> )
> ```
> Public method `fun apply(mode: com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode)` — branches on `mode == PlayerImageEditMode.NONE` to call private `exit()`; otherwise calls private `enter()`.
> `enter()` does:
>   - `Timber.d("S0127: PlayerImmersiveModeManager.enter")`
>   - sets `activity.activityBinding.topCommandPanel.isVisible = false`
>   - sets `activity.activityBinding.tvFileNameOverlay.isVisible = false`
>   - sets `safeViews.copyToPanel.isVisible = false`
>   - sets `safeViews.moveToPanel.isVisible = false`
>   - calls `hideSystemBars()`
> `exit()` does:
>   - `Timber.d("S0127: PlayerImmersiveModeManager.exit")`
>   - calls `showSystemBars()`
>   - calls `activity.viewModel.enterCommandPanelMode()`
>   - calls `activity.updatePanelVisibility(activity.viewModel.state.value.showCommandPanel)`
> `hideSystemBars()` (private) uses `WindowInsetsControllerCompat.getInsetsController(activity.window, activity.window.decorView)` to call `hide(WindowInsetsCompat.Type.systemBars())` and set `systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. Wrap in try/catch logging via `Timber.w(e, "S0127: hideSystemBars failed")`.
> `showSystemBars()` (private) calls `show(WindowInsetsCompat.Type.systemBars())` on the same controller; same try/catch.
> Use `androidx.core.view.WindowCompat`, `androidx.core.view.WindowInsetsCompat`, `androidx.core.view.WindowInsetsControllerCompat` imports.
> Use `androidx.core.view.isVisible` import for property setters.
> Add KDoc one-liner above the class: `Hides system bars and command panels while imageEditMode != NONE (S0127).`

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt` exists.
- `Grep` — `class PlayerImmersiveModeManager(` matches once.
- `Grep` — `fun apply(mode: com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode)` matches once.
- `Grep` — `WindowInsetsCompat.Type.systemBars()` matches at least twice in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt (+62 LOC, new). Dev log recorded.

---

### Step 03.2 — Add lateinit `immersiveModeManager` to `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `PlayerActivity.kt`, locate the existing line:
> ```kotlin
> internal lateinit var cropDelegate: com.sza.fastmediasorter.ui.player.helpers.PlayerCropDelegate
> ```
> Immediately after that property declaration, add:
> ```kotlin
> internal lateinit var immersiveModeManager: com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager
> ```
> Then locate the existing call to `setupEditModeCallbacks()` introduced in Phase 02. Immediately before that call, add:
> ```kotlin
> immersiveModeManager = com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager(
>     activity = this,
>     safeViews = com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews(activityBinding)
> )
> ```
> Do not modify any other line.

**Verification:**

- `Grep` — `internal lateinit var immersiveModeManager: com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager` matches once.
- `Grep` — `immersiveModeManager = com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager(` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt (+2 LOC), app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt (+4 LOC). Note: instantiation lives in PlayerManagerInitializer; Files Touched updated. Dev log recorded.

---

### Step 03.3 — Observe `imageEditMode` in `PlayerObserverManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `PlayerObserverManager.kt`, inside `startObserving()`, locate the inner `repeatOnLifecycle(Lifecycle.State.STARTED)` block. Immediately after the existing `launch { combine(... favorites ...) }` block (the one that ends with `activity.activityBinding.btnFavorite.isVisible = shouldShow`), add a new `launch` block:
> ```kotlin
> launch {
>     viewModel.state
>         .distinctUntilChangedBy { it.imageEditMode }
>         .collect { state ->
>             Timber.d("S0127: PlayerObserverManager observed imageEditMode=${state.imageEditMode}")
>             activity.immersiveModeManager.apply(state.imageEditMode)
>         }
> }
> ```
> Do not modify the surrounding observer blocks.

**Verification:**

- `Grep` — `distinctUntilChangedBy { it.imageEditMode }` matches once.
- `Grep` — `activity.immersiveModeManager.apply(state.imageEditMode)` matches once.
- `Grep` — `Timber.d("S0127: PlayerObserverManager observed imageEditMode=` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt (+9 LOC). Dev log recorded.

---

### Step 03.4 — Reset stale `imageEditMode` on activity create

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `PlayerActivity.kt`, locate the previously-added `setupEditModeCallbacks()` invocation. Immediately after it, add:
> ```kotlin
> viewModel.setImageEditMode(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
> ```
> This guarantees stale state from a prior process death cannot leave the UI in an immersive state with no live overlay.

**Verification:**

- `Grep` — `viewModel.setImageEditMode(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)` matches once in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt (+1 LOC, inside setupEditModeCallbacks()). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` BUILD SUCCESSFUL in 33s.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, panels and system bars hide automatically when entering Draw or Crop and restore on exit. Phase 04 disables crop-to-fullscreen scale type while in Crop. Phase 05 wires pinch-to-zoom passthrough.

---

## Rollback Plan

Revert the phase commit(s); the manager is a leaf class with no external coupling beyond the observer block.
