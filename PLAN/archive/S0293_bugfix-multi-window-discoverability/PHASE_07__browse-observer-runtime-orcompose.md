# Phase 07 - Browse observer runtime OR-compose

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Status:** ✅ Done
**Depends on:** Phase 05 (runtime-reactivity hooks)

> Retroactive entry: code shipped between 2026-05-22 and 2026-05-25 without a tactical record. This file documents the delivered scope so the audit's "tactical drift" warning closes cleanly.

## Goal

Wire the per-row `⋮` overflow button visibility through the same OR-composition that Phase 05 introduced for `allowSeparateWindow` in the dropdown menus and player. Without this step, entering a DeX / desktop container on a phone fired `onMultiWindowModeChanged` but the per-row `⋮` button stayed hidden because its gate read only the persisted `fileOpsInOverflowMenu` preference.

## Files Touched

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`

## Steps

### 07.1 OR-compose the overflow-menu visibility feed

- Add `activity: Activity` parameter to `BrowseObserverManager`.
- Introduce a `MutableStateFlow<Int>` ticker (`multiWindowTick`) on the observer.
- Rewrite `observeFileOpsOverflowMenu()` to `combine(settingsRepository.getSettings(), multiWindowTick)` and emit `settings.fileOpsInOverflowMenu || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)` to `adapter.setFileOpsInOverflowMenu(...)`.
- Expose `fun notifyMultiWindowModeChanged()` that bumps the ticker.
- Pipe `BrowseManagerInitializer.notifyMultiWindowModeChanged()` to call `observerManager.notifyMultiWindowModeChanged()` first, then `mediaFileAdapter.notifyDataSetChanged()`.

**Verification:**
- `Grep` `BrowseObserverManager.kt` for `multiWindowTick` - hit count ≥ 2 (declaration + use in `combine`).
- `Grep` `BrowseObserverManager.kt` for `MultiWindowCapabilityDetector.isMultiWindowActiveNow` - exact match present.
- `Grep` `BrowseManagerInitializer.kt` for `observerManager.notifyMultiWindowModeChanged()` - present.
- Build `standardDebug` PASS.

## Phase Done Criteria

- Adapter receives the OR-composed value on every settings emission and on every multi-window mode transition.
- No regression to the existing pre-Phase-05 menu OR-composition (`showPerFileOverflowMenu`) - both paths now consistent.
- Build passes.
