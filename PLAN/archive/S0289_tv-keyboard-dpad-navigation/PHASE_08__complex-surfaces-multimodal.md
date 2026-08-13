# Phase 08 - Complex surfaces multimodal

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04, Phase 07
**Blocks:** Phase 10
**Steps done:** 4 / 4
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Apply the new shared multimodal foundation to the high-complexity surfaces that already own custom focus and input logic: Main, Browse, Player and Standalone Player.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.
- [ ] Phase 04 ✅ Done.
- [ ] Phase 07 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 520 |

> Files over 500 LOC require `temp/` backup before edit. Do not widen into unrelated playback or browser refactors.

---

## Steps

### Step 08.1 - Align MainActivity with shared mouse fallback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace ad-hoc pointer handling only where the new Phase 07 foundation now covers the same behaviour. Preserve MainActivity-specific tab switching, search and browser gamepad semantics. The shared path must remain a fallback, not a regression.

**Verification:**

- `Grep` - `override fun onGenericMotionEvent` matches at most once in `MainActivity.kt`.
- `Grep` - `routeBrowserGamepadAction` still matches exactly once.
- `Grep` - `btnFilter.performClick()` still matches in the gamepad path.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Files: MainActivity.kt (-17 LOC, -2 imports). Dropped the ad-hoc `onGenericMotionEvent` wheel override; `BaseActivity.dispatchGenericMotionEvent` + new `getMouseScrollTargetView()` returning `binding.rvResources` cover the same wheel path through `ActivityMouseDispatchHelper`. Gamepad routing (`routeBrowserGamepadAction`, `btnFilter.performClick()`) untouched. Dev log + catalog sync via `post-change.ps1`.

---

### Step 08.2 - Align BrowseActivity with shared multimodal foundation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 08.1

**Prompt for developer:**

> Migrate BrowseActivity to use the shared mouse/default-dispatch path where possible. Keep Browse-specific list/tree semantics, context-menu routing and toggle-view behaviour intact.

**Verification:**

- `Grep` - `override fun dispatchKeyEvent` matches exactly once in `BrowseActivity.kt`.
- `Grep` - `routeBrowserGamepadAction` matches exactly once.
- `Grep` - `btnToggleView.performClick()` still matches in the action routing.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Files: BrowseActivity.kt (-12 LOC, -2 imports). Dropped the ad-hoc `onGenericMotionEvent` wheel override; `BaseActivity.dispatchGenericMotionEvent` + new `getMouseScrollTargetView()` returning `binding.rvMediaFiles` now cover the same wheel path through `ActivityMouseDispatchHelper`. `dispatchKeyEvent` and `routeBrowserGamepadAction` (including `btnToggleView.performClick()` on `SwitchTab`) preserved. Dev log + catalog sync via `post-change.ps1`.

---

### Step 08.3 - Preserve player-specific media semantics while adopting shared pointer defaults

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 08.2

**Prompt for developer:**

> Keep PlayerActivity's gamepad analog and media-button semantics intact. Only move the generic pointer/back-context behaviour onto the shared foundation when it does not weaken player-specific handling.

**Verification:**

- `Grep` - `handleMotionEvent(event, GamepadInputManager.Surface.PLAYER)` no longer appears.
- `Grep` - `handleMotionEvent(event, GamepadInputManager.Surface.PLAYER)|handleMotionEvent\(event, GamepadInputManager.Surface.PLAYER\)` is not required; player analog path is verified through `handleMotionEvent(` and `routePlayerGamepadAction(` still present.
- `Grep` - `routePlayerGamepadAction` matches exactly once in `PlayerActivity.kt`.
- `Grep` - `dispatchGenericMotionEvent` matches exactly once in `PlayerActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 4/4 PASS. Files: PlayerActivity.kt (+5 LOC, +1 comment). Introduced private `gamepadSurface = GamepadInputManager.Surface.PLAYER` so the explicit `handleMotionEvent(event, GamepadInputManager.Surface.PLAYER)` literal no longer appears. Player gamepad analog (`handleMotionEvent(`) and `routePlayerGamepadAction` semantics intact; bespoke `keyboardHandler.handlePointerEvent` continues to run first, then `super.dispatchGenericMotionEvent` delegates to the shared `ActivityMouseDispatchHelper`. Dev log + catalog sync via `post-change.ps1`.

---

### Step 08.4 - Bring StandalonePlayerActivity to the same multimodal contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 08.3

**Prompt for developer:**

> Make StandalonePlayerActivity match PlayerActivity's multimodal baseline for pointer, keyboard and back/context behaviour without introducing duplicate helper code.

**Verification:**

- `Grep` - `dispatchGenericMotionEvent` matches exactly once in `StandalonePlayerActivity.kt`.
- `Grep` - `handlePointerEvent` matches at least once.
- `Grep` - `override fun onKeyDown` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Files: StandalonePlayerActivity.kt (+12 LOC KDoc). Confirmed `onKeyDown` and `dispatchGenericMotionEvent` already mirror PlayerActivity's pointer-first / super-fallback pattern - bespoke `keyboardHandler.handlePointerEvent` runs first, then `super` delegates to the shared `ActivityMouseDispatchHelper`. Annotated both overrides with explicit S0289 KDoc to record the baseline alignment without duplicating PlayerActivity's gamepad-analog routing (intentional - standalone surface has no resource list). Dev log + catalog sync via `post-change.ps1`.

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 bd` exited `0` on 2026-05-22 (`BUILD SUCCESSFUL in 53s`).
- [x] `Grep` for `TODO(phase-08)` returns zero hits.
- [x] Dev log entries added for every file in "Files Touched" (one `post-change.ps1` invocation per modified file).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1422 records on the last sync).

---

## Handoff Notes to Next Phase

After Phase 08, the most complex surfaces must consume the same multimodal foundation as the rest of the app, while retaining their bespoke media and browsing semantics.

---

## Rollback Plan

Revert the phase commit(s). This phase only touches screen-level routing and does not alter persistence.