# Phase 05 - Grid Mode Wiring

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Add the toolbar list/grid toggle and a `StreamGridModeManager` that swaps adapter + layout, drives the snapshot engine over visible tiles, supports pull-to-refresh + periodic refresh, and keeps `StreamsActivity` free of the new logic.

---

## Prerequisites

- [ ] Phases 01, 03, 04 ✅ Done.
- [ ] `ic_view_grid` / `ic_view_list` drawables exist (already used by `BrowseRecyclerViewManager`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |
| `app_v2/src/main/res/menu/menu_streams.xml` | Modified | ≤ 35 |
| `app_v2/src/main/res/layout/activity_streams.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamGridModeManager.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 620 |

> Landscape parity: `activity_streams.xml` is edited together with its `layout-land` counterpart (both wrap `rvStreams` in the same `SwipeRefreshLayout`).

---

## Steps

### Step 05.1 - Add trilingual toggle strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `scripts/utils/set-android-string.ps1 -Action add` (parity-enforced EN/RU/UK):
> - `streams_view_grid` - EN: `"Grid view"`, RU: `"Сеткой"`, UK: `"Сіткою"`.
> - `streams_view_list` - EN: `"List view"`, RU: `"Списком"`, UK: `"Списком"`.
>
> These are the toggle menu title + content description (the icon already encodes the action; the string is the affordance label). Strings pass COMMUNICATION_POLICY §2 (affordance) + §6 tone checklist.

**Verification:**

- `Grep` - `streams_view_grid`, `streams_view_list` present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_view_"` exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added `streams_view_grid` (Grid view / Сеткой / Сіткою) and `streams_view_list` (List view / Списком / Списком) via set-android-string.ps1 add. `check_strings_localized.ps1 -KeyPrefix streams_view_` -> all OK (exit 0). No mojibake.

---

### Step 05.2 - Add display-mode toggle to menu_streams.xml

**Files:** `res/menu/menu_streams.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a menu item `@+id/action_stream_display_toggle` with `app:showAsAction="ifRoom"`, `android:icon="@drawable/ic_view_grid"`, `android:title="@string/streams_view_grid"`, placed before `action_stream_refresh`. The icon/title are swapped at runtime by the manager to reflect the current mode (grid icon while in list, list icon while in grid - same convention as `BrowseRecyclerViewManager.updateDisplayMode`).

**Verification:**

- `Grep` - `action_stream_display_toggle` present in `menu_streams.xml`.
- `.\a.ps1 fr` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added `@+id/action_stream_display_toggle` (icon `ic_view_grid`, title `streams_view_grid`, `showAsAction=ifRoom`) before `action_stream_refresh`. Grep present; `.\a.ps1 fr` exit 0.

---

### Step 05.3 - Wrap rvStreams in SwipeRefreshLayout (portrait + landscape)

**Files:** `res/layout/activity_streams.xml`, `res/layout-land/activity_streams.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> In both layouts wrap the existing `rvStreams` RecyclerView in an `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` (`@+id/swipeStreams`) occupying the same constraints/position the RecyclerView held; keep the `rvStreams` id and all its attributes so existing bindings/scroll buttons keep working. Do not change the scroll-button FABs or mini-control. No hardcoded hex. The SwipeRefreshLayout is disabled by default (enabled only in grid mode by the manager).

**Verification:**

- `Grep` - `@+id/swipeStreams` present in both `layout/activity_streams.xml` and `layout-land/activity_streams.xml`.
- `Grep` - `@+id/rvStreams` (or `android:id="@id/rvStreams"`) still present in both.
- `Grep -n "=\"#"` returns zero new hardcoded hex in either file.
- `.\a.ps1 fr` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Wrapped `rvStreams` in `@+id/swipeStreams` SwipeRefreshLayout in BOTH `layout/activity_streams.xml` and `layout-land/activity_streams.xml`; RecyclerView id + all attributes (incl. landscape paddingStart/End) preserved. Scroll FABs / mini-control untouched. No new hardcoded hex. `.\a.ps1 fr` exit 0.

---

### Step 05.4 - Create StreamGridModeManager

**Files:** `ui/streams/helpers/StreamGridModeManager.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Create `class StreamGridModeManager` owning all grid-mode behavior so `StreamsActivity` stays delegating (Rule 3/5). Constructor: the `RecyclerView`, the `SwipeRefreshLayout`, the list `StreamSourceAdapter`, the grid `StreamGridAdapter`, the `StreamFrameSnapshotManager`, the `StreamFrameCache`, a `LifecycleOwner`/`CoroutineScope`, and a `Resources`. Responsibilities:
> - `fun applyMode(mode: DisplayMode, currentList: List<StreamSourceEntity>)` - swap adapter (list<->grid) and layout manager: `LinearLayoutManager` for LIST, `GridLayoutManager(context, spanCount)` for GRID where `spanCount = calculateGridSpanCount(resources)` (reuse the width/density approach of `BrowseRecyclerViewManager`; target min tile width ~160dp, `coerceAtLeast(2)`). Re-submit the current list to the now-active adapter. Enable `SwipeRefreshLayout` only in GRID. Update the toggle menu icon/title via a callback. On leaving GRID, call `snapshotManager.cancelAll()` and stop the periodic timer.
> - `fun onVisibleRangeChanged()` (hooked to a scroll listener) - for visible GRID tiles that are http(s) VIDEO and not `cache.isFresh(url)`, the grid adapter already enqueues via `requestCapture`; this method only triggers a re-bind sweep when needed.
> - Pull-to-refresh: on `SwipeRefreshLayout` refresh, `cache.invalidate` the visible urls + re-request captures, then stop the spinner when the sweep is dispatched.
> - Periodic refresh: a `scope` coroutine timer (`REFRESH_INTERVAL_MS = 60_000L`) that, while in GRID and resumed, invalidates expired visible frames and re-requests; cancel on leaving GRID / on stop.
> - Route `snapshotManager.onCaptured = { url -> gridAdapter.repaintUrl(url) }`.
> - All players are muted by the engine - no audio in grid mode (strategic non-goal).
> - Timber only; no `S0675:` in any persistent log; expected fallbacks at `Timber.i`.

**Verification:**

- `Glob` - `StreamGridModeManager.kt` exists.
- `Grep` - `class StreamGridModeManager` matches exactly once.
- `Grep` - `fun applyMode(`, `calculateGridSpanCount`, `REFRESH_INTERVAL_MS` present.
- `Grep` - `cancelAll` referenced (capture teardown on mode leave).
- `Grep -n "Log\.d\("` returns zero hits.
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Created `StreamGridModeManager` (@UnstableApi). `applyMode` swaps `LinearLayoutManager`/`GridLayoutManager(calculateGridSpanCount, coerceAtLeast(2), ~160dp tile)` + adapter, re-submits the list, enables SwipeRefreshLayout only in GRID, starts/stops the periodic `REFRESH_INTERVAL_MS=60_000` timer, and `snapshotManager.cancelAll()` on leaving GRID. Pull-to-refresh + periodic tick invalidate visible frames and re-bind. `onCaptured` routes to `gridAdapter.repaintUrl`. `submitCurrentList` keeps the active adapter current; `stop()` tears down on Activity onStop. All grep predicates matched; `.\a.ps1 fk` exit 0.

> Deviation: added `submitCurrentList(currentList)` (not named in the prompt) so the Activity's existing `state.sources` observe path keeps the active adapter current without re-running `applyMode` on every list emission; `applyMode` is reserved for actual mode changes. Mirrors the existing list-adapter `submitList` flow.

---

### Step 05.5 - Wire StreamsActivity to the toggle and manager

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> In `StreamsActivity`: construct `StreamFrameCache` (inject) + `StreamFrameSnapshotManager` (built with `applicationContext`, the cache, `lifecycleScope`) + `StreamGridAdapter` (passing `onPlay = ::onPlay`, `frameProvider = cache::get`, `requestCapture = snapshotManager::request`, and the same favicon plumbing as the list adapter) + `StreamGridModeManager` (wired to `binding.swipeStreams`, both adapters, the engine, the cache). Add the `R.id.action_stream_display_toggle` branch in the toolbar `setOnMenuItemClickListener` calling `viewModel.onToggleDisplayMode()`. In `observeData`, when `state.displayMode` changes, call `gridModeManager.applyMode(state.displayMode, state.sources)` and pass the toggle-icon update through `tintToolbarMenuIcons()` so the new item is tinted too. In `onStop`, call `snapshotManager.cancelAll()` (and the manager stops its timer) so captures never run in the background. Keep the existing list-mode wiring intact; grid is purely additive.

**Verification:**

- `Grep` - `action_stream_display_toggle` referenced in `StreamsActivity.kt`.
- `Grep` - `StreamGridModeManager`, `StreamFrameSnapshotManager`, `StreamGridAdapter` all referenced.
- `Grep` - `onToggleDisplayMode` referenced.
- `Grep` - `cancelAll` referenced in `onStop`.
- `StreamsActivity.kt` ≤ 1500 LOC.
- `/build` -> `standard debug` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Wired `StreamsActivity`: injected `StreamFrameCache`; lazy `StreamFrameSnapshotManager(applicationContext, cache, lifecycleScope)` + `StreamGridAdapter` (onPlay/frameProvider=cache::get/requestCapture=snapshotManager::request + favicon plumbing) + `StreamGridModeManager`. Added `R.id.action_stream_display_toggle -> viewModel.onToggleDisplayMode()`; `observeData` calls `applyMode` on displayMode change (else keeps active adapter list current via `submitCurrentList`/list `submitList`); toggle icon/title swapped via `updateDisplayToggleIcon` + re-tint. `onStop` calls `gridModeManager.stop()` + `snapshotManager.cancelAll()`. StreamsActivity 619 LOC (<=620 budget, <1500 Rule 2). All grep predicates matched; `.\a.ps1 fc` exit 0 (authoritative full standard-debug build left to the orchestrator per task scope).

> Deviation: the `applyMode`-vs-keep-current split in `observeData` uses an `appliedDisplayMode` guard field so `applyMode` (adapter/layout swap) runs only on an actual mode change, not on every catalog/filter emission; ordinary list updates route to `submitCurrentList`/the existing list `submitList`. Scroll-button visibility is refreshed after a LIST applyMode via `binding.rvStreams.post`.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build` -> `standard debug`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Feature is functionally complete after this phase. Phase 06 regenerates the catalog, adds FEATURES sentences, and runs the doc-sync gates.

---

## Rollback Plan

Revert phase commit(s). Grid mode is additive: list mode, filter/sort/import, and stream playback are unchanged, so reverting restores the prior list-only screen. No data migration.
