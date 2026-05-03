# Phase 03 — Black Screen Overlay

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Implement `BlackScreenOverlayManager` — a helper that adds a full-screen black `View` to `PlayerActivity`'s DecorView window. The overlay intercepts touch events (dismiss on any tap), passes volume-key events through to the system without closing, and auto-dismisses when the player transitions to a non-audio/video file. Replace the Phase 02 stubs.

> **Architecture note:** a View-based overlay inside the same `Activity` window is used (not a separate `Activity`) to guarantee ExoPlayer's `onPause` is never triggered. This satisfies strategic ADR-1 ("не вмешивается в жизненный цикл воспроизведения").

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`TODO(phase-03)` stubs exist in `PlayerControlsSetupManager.kt`).
- [ ] Understand how `PlayerActivity` observes `PlayerState` (state collector / `collectOnLifecycle`).
- [ ] Read `PlayerActivity.kt` `dispatchKeyEvent()` override (if any) to understand key-event routing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BlackScreenOverlayManager.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt` | Modified | ≤ 600 |

> If `PlayerActivity.kt` is already ≥ 500 lines, create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 3.1 — Create BlackScreenOverlayManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BlackScreenOverlayManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `BlackScreenOverlayManager` as a plain class (no Hilt injection, constructed directly in `PlayerActivity`). It receives a `WeakReference<Activity>` in the constructor.
>
> Responsibilities:
> - `val isVisible: Boolean` — whether the overlay is currently showing.
> - `fun show()` — if not already visible, create a `View` with `setBackgroundColor(Color.BLACK)`, `layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)`, add it to `activity.window.decorView` as the topmost child, set `isVisible = true`. The view must cover the full screen including status bar (`fitSystemWindows = false`).
> - `fun hide()` — if visible, remove the overlay view from the decorView, set `isVisible = false`.
> - `fun onFileTypeChanged(isAudioOrVideo: Boolean)` — calls `hide()` if `!isAudioOrVideo && isVisible`.
>
> Touch handling on the overlay view's `setOnTouchListener`: on `MotionEvent.ACTION_DOWN` → call `hide()`; return `true` (consume event). This dismisses the overlay on any screen tap.
>
> No animation — `show()` and `hide()` are instant (no `animate()` calls, no alpha transitions).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BlackScreenOverlayManager.kt` exists.
- `Grep` — `class BlackScreenOverlayManager` in that file.
- `Grep` — `fun show()` in `BlackScreenOverlayManager.kt`.
- `Grep` — `fun hide()` in `BlackScreenOverlayManager.kt`.
- `Grep` — `fun onFileTypeChanged` in `BlackScreenOverlayManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. Files: BlackScreenOverlayManager.kt (new, 54 LOC). Dev log recorded.

---

### Step 3.2 — Wire BlackScreenOverlayManager into PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `PlayerActivity`:
> 1. Instantiate `BlackScreenOverlayManager(WeakReference(this))` as a property (created in `onCreate`, after `setContentView`).
> 2. Pass the instance to `PlayerControlsSetupManager` (add it as a constructor parameter).
> 3. In the `PlayerState` observer (`collectOnLifecycle` or equivalent), after receiving a new state with a changed `currentFile`, call:
>    ```kotlin
>    val isAudioOrVideo = state.currentFile?.type?.let {
>        it == MediaType.AUDIO || it == MediaType.VIDEO
>    } ?: false
>    blackScreenOverlayManager.onFileTypeChanged(isAudioOrVideo)
>    ```

**Verification:**

- `Grep` — `BlackScreenOverlayManager` in `PlayerActivity.kt` (at least 2 hits).
- `Grep` — `onFileTypeChanged` in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: PlayerActivity.kt (+12 LOC: property, instantiation, observeData collector). Dev log recorded.

---

### Step 3.3 — Replace Phase 02 stubs with real calls

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> Replace both `TODO(phase-03)` stubs (button click and overflow menu item) with real calls to `blackScreenOverlayManager.show()`. The `BlackScreenOverlayManager` reference was added to the constructor in Step 3.2.

**Verification:**

- `Grep` — `TODO(phase-03)` across all player files returns **zero** hits.
- `Grep` — `blackScreenOverlayManager.show()` in `PlayerControlsSetupManager.kt` (1 hit: bar button).
- `Grep` — `blackScreenOverlayManager.show()` in `PlayerCommandPanelCallbackImpl.kt` (1 hit: overflow callback via onBlackScreenClicked).

> _Spec patch: overflow dispatch lives in `PlayerCommandPanelCallbackImpl.kt` (Phase 02 architecture), not a second hit in `PlayerControlsSetupManager.kt`._

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS (patched predicates). Files: PlayerControlsSetupManager.kt (+1 param, stub→show()), PlayerManagerInitializer.kt (+1 arg), PlayerCommandPanelCallbackImpl.kt (stub→show()). Dev log recorded.

---

### Step 3.4 — Pass volume-key events through overlay

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> Override (or extend the existing) `dispatchKeyEvent(event: KeyEvent): Boolean` in `PlayerActivity`. When `blackScreenOverlayManager.isVisible` is `true` and `event.keyCode` is `KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`, or `KEYCODE_VOLUME_MUTE`, call `super.dispatchKeyEvent(event)` and return its result **without** calling `blackScreenOverlayManager.hide()`. For all other key events while the overlay is visible, call `blackScreenOverlayManager.hide()` first, then dispatch normally.
>
> Media keys (`KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_STOP`) must also pass through without closing the overlay, so include them in the no-dismiss list alongside volume keys.

**Verification:**

- `Grep` — `KEYCODE_VOLUME_UP` in `PlayerActivity.kt`.
- `Grep` — `KEYCODE_MEDIA_NEXT` in `PlayerActivity.kt`.
- `Grep` — `isVisible` in `PlayerActivity.kt` (referencing `blackScreenOverlayManager.isVisible`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: PlayerActivity.kt (dispatchKeyEvent extended +14 LOC). Dev log recorded.

---

### Step 3.5 — Expose show() for keybinding dispatch (Phase 04 hook)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 3.3

**Prompt for developer:**

> Add a single public method to `PlayerActivity` that toggles the overlay:
> ```kotlin
> fun toggleBlackScreenOverlay() {
>     if (blackScreenOverlayManager.isVisible) blackScreenOverlayManager.hide()
>     else blackScreenOverlayManager.show()
> }
> ```
> This method will be called by the keybinding dispatcher in Phase 04.

**Verification:**

- `Grep` — `fun toggleBlackScreenOverlay` in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS. Files: PlayerActivity.kt (+4 LOC: toggleBlackScreenOverlay()). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 3.* above is `[x] done`.
- [x] Project compiles — run `/build`. (auto-build — PASS)
- [x] `Grep` for `TODO(phase-03)` across all files returns **zero** hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `BlackScreenOverlayManager` is fully functional: show/hide, touch-dismiss, file-type-dismiss, volume/media key pass-through.
- `PlayerActivity.toggleBlackScreenOverlay()` is the single entry point for Phase 04 keybinding dispatch.

---

## Rollback Plan

Revert phase commit(s). `BlackScreenOverlayManager` is a new file; all `PlayerActivity` changes are additive (new method, one observer call, dispatchKeyEvent extension). No data migration.
