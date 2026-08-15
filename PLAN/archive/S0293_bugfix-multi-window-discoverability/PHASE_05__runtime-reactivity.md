# Phase 05 - Runtime Reactivity (DeX Entry/Exit)

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-05-23
**Completed:** 2026-05-23

---

## Objective

Make the multi-window UI controls react to the Activity entering / leaving DeX (desktop UI mode) without requiring a process restart. Compose the effective "is multi-window UX available" flag from two sources at UI-read time: the persistent preference (`AppSettings.allowSeparateWindow`) OR the runtime `MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)`. Subscribe to `onMultiWindowModeChanged` / `onConfigurationChanged` in the two Activities that host the affected UI so the controllers recompute visibility on transitions.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `isMultiWindowActiveNow(activity)` is available.
- [ ] Phase 02 is ✅ Done - install-time defaults already use the detector.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |

> `PlayerActivity.kt` is the largest of the four. Verify line count before edit; if it crosses 1500 LOC after this phase's small additions, do not split here - phase scope is one new override + one notification call. The split is owned by a separate refactor.

---

## Steps

### Step 05.1 - Compose effective `allowSeparateWindow` at UI read site

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In both files, locate every read site that supplies `allowSeparateWindow` (or `lastKnownAllowSeparateWindow`) into UI visibility decisions:
>
> 1. `BrowseManagerInitializer.kt:611-614` (call to `resourceOpsMenuManager.showMenu(... allowSeparateWindow = settings.allowSeparateWindow ...)` and the `openBrowseInNewWindow` lambda gate).
> 2. `BrowseManagerInitializer.kt` - the private `showPerFileOverflowMenu` introduced in Phase 03 (where it reads `appSettings.allowSeparateWindow` before passing `onOpenInNewWindow`).
> 3. `CommandPanelController.kt:344` - `val shouldAllowSeparateWindow = settings.allowSeparateWindow`.
>
> Replace each `settings.allowSeparateWindow` boolean source with the OR-composition:
>
> `settings.allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)`
>
> Both files already have an Activity reference (`activity` in `BrowseManagerInitializer` constructor; `binding.root.context as? Activity` or the existing controller's activity field in `CommandPanelController` - reuse whichever the existing code already uses for similar runtime checks). Add the import if missing. Do NOT change the persistent `settings.allowSeparateWindow` source itself - it remains the user's stored preference.

**Verification:**

- `Grep -n` - in `BrowseManagerInitializer.kt`, the substring `MultiWindowCapabilityDetector.isMultiWindowActiveNow` appears at least twice (the two call sites above).
- `Grep -n` - in `CommandPanelController.kt`, the substring `MultiWindowCapabilityDetector.isMultiWindowActiveNow` appears at least once.
- `Grep` - `import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector` is present at the top of each modified file.
- Compile check via `/build` (target: `assembleStandardDebug`) - deferred to Phase Done Criteria.

**Status:** `[x] done`

**Step Log:**

- 2026-05-23 - Verification 4/4 PASS. BrowseMI: 3 isMultiWindowActiveNow refs (showBrowseResourceOpsMenu ×2 + showPerFileOverflowMenu ×1); CPC: 1 ref. Imports present.

---

### Step 05.2 - Hook `onMultiWindowModeChanged` in BrowseActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Override `onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration)` in `BrowseActivity` (call `super` first). Inside the override, trigger a re-render of the visibility-sensitive UI parts: the toolbar's `btnResourceOps` action set and the file-row overflow buttons. The simplest mechanism that already exists is to re-emit the current settings to the relevant observers - call `viewModel.reloadFiles(syncMediaStore = false)` ONLY IF no cheaper path exists; the preferred path is to expose a `notifyMultiWindowModeChanged()` method on `BrowseManagerInitializer` (added in this step) that re-invokes the existing `buttonSetupHelper.updateToolbarButtonLabels(resources.configuration)` and forces the file adapter to invalidate (`mediaFileAdapter.notifyDataSetChanged()`).
>
> Also override `onConfigurationChanged(newConfig: Configuration)` to call `super` then invoke the same `notifyMultiWindowModeChanged()` method, since some manufacturers (Samsung One UI on DeX) deliver the UI-mode change via `onConfigurationChanged` rather than `onMultiWindowModeChanged`. Make sure the activity's `configChanges` in `AndroidManifest.xml` already includes `uiMode` and `screenLayout` - if not, do NOT add them inside this phase (it changes Activity lifecycle behavior more broadly); instead log a TODO and accept that the override only fires reliably on Activity recreation.
>
> Persistent log lines must not contain `Sxxxx` - use plain English subject in any `Timber.i/w/e` you add. The Phase 06 BlockNeedUserTest probe is the only allowed `Timber.d("S0293: ...")` line.

**Verification:**

- `Grep` - in `BrowseActivity.kt`, `override fun onMultiWindowModeChanged\(isInMultiWindowMode: Boolean, newConfig: Configuration\)` matches exactly once.
- `Grep` - the body contains `super.onMultiWindowModeChanged\(` and `notifyMultiWindowModeChanged\(\)`.
- `Grep` - in `BrowseManagerInitializer.kt`, `fun notifyMultiWindowModeChanged\(\)` matches exactly once.
- `Grep` - no `Timber.\(i\|w\|e\)\(.*S0293` lines exist in either file.
- Compile check via `/build` (target: `assembleStandardDebug`) - deferred to Phase Done Criteria.

**Status:** `[x] done`

**Step Log:**

- 2026-05-23 - Verification 4/4 PASS. BrowseActivity overrides both methods with proper Configuration import; notifyMultiWindowModeChanged exists in BMI; no S0293 in persistent log lines.

---

### Step 05.3 - Hook `onMultiWindowModeChanged` in PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Override `onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration)` in `PlayerActivity` (call `super` first). In the override, request the command-panel controller to recompute visibility by exposing a public method `notifyMultiWindowModeChanged()` on `CommandPanelController` that re-invokes `updateCommandAvailability(currentState)` using the most recently observed state.
>
> Also override `onConfigurationChanged(newConfig: Configuration)` mirroring the BrowseActivity pattern - call `super`, then forward to the controller. The controller must be safe against being called before `currentState` is initialised (early no-op return is sufficient).

**Verification:**

- `Grep` - in `PlayerActivity.kt`, `override fun onMultiWindowModeChanged\(isInMultiWindowMode: Boolean, newConfig: Configuration\)` matches exactly once.
- `Grep` - in `CommandPanelController.kt`, `fun notifyMultiWindowModeChanged\(\)` matches exactly once and is `public` (no `private` modifier).
- `Grep` - the controller method body calls `updateCommandAvailability` directly or with a safe null-check.
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-23 - Verification 4/4 PASS. PlayerActivity overrides onMultiWindowModeChanged; CommandPanelController exposes public `fun notifyMultiWindowModeChanged()` reading `cachedState`. Build PASS.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for all four files via post-change.ps1.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via post-change.ps1.

---

## Handoff Notes to Next Phase

UI controls now reflect "is multi-window UX available right now" via the OR of the stored preference and the runtime DeX/desktop-mode signal. Activities forward mode-change events to their UI controllers, which recompute visibility without a process restart. Phase 06 does the cleanup pass (catalog sync, dev log, BlockNeedUserTest probes).

---

## Rollback Plan

Revert the phase commit. The OR-composition falls back to the persistent-preference-only behavior (Phase 02 state). Activity overrides are independent and can be reverted without affecting Phase 01-04 outcomes.
