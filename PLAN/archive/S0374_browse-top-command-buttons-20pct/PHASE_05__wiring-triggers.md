# PHASE_05 - Wiring + recompute triggers + focus-chain repair

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending
**Depends on:** PHASE_02, PHASE_03, PHASE_04

## Goal

Construct the overflow manager, recompute it after every event that changes the candidate set or available width, and repair the focus chain after each overflow flip.

## Steps

### Step 5.1 - Construct + hold the manager

In `app_v2/.../ui/browse/managers/BrowseManagerInitializer.kt`:
- Add `lateinit var commandOverflowManager: BrowseCommandOverflowManager` alongside the other manager fields.
- Instantiate in `initialize()` near `smallControlsManager = BrowseSmallControlsManager(binding)`: `commandOverflowManager = BrowseCommandOverflowManager(binding)`.
- Set `commandOverflowManager.onOverflowChanged = { activity.restitchBrowseControlChain() }` (expose the activity hook - Step 5.4).
- In `showBrowseResourceOpsMenu(anchor)` pass `isOverflowed = { id -> commandOverflowManager.isOverflowed(id) }` plus callbacks to `showMenu(...)` (PHASE_04 Step 4.3).

### Step 5.2 - Recompute after state-driven visibility

In `app_v2/.../ui/browse/managers/BrowseStateUiUpdater.kt` `onStateChanged()`:
- As the FINAL line (after small-controls, create-folder/text/drawing visibility, play-random, breadcrumb), call `onRecomputeOverflow?.invoke()`.
- Add `var onRecomputeOverflow: (() -> Unit)? = null`; wire it in `BrowseManagerInitializer` to `commandOverflowManager::recompute`.

### Step 5.3 - Recompute after mic + orientation

- Mic collector in `BrowseActivity.observeData()` (sets `btnMicRecord.isVisible`): after the assignment, call `initializer.commandOverflowManager.recompute()`.
- `BrowseActivity.onLayoutConfigurationChanged(newConfig)`: after `buttonSetupHelper.updateToolbarButtonLabels(newConfig)`, call `initializer.commandOverflowManager.recompute()` (labels change button widths → re-measure).

### Step 5.4 - Make focus-chain repair re-invokable

In `app_v2/.../ui/browse/BrowseActivity.kt`:
- Change `restitchBrowseControlChain()` visibility from `private` to internal/public so the overflow manager callback can invoke it. Keep its existing body (it already filters `visibility == VISIBLE`, so overflowed-GONE buttons drop out of the chain automatically).

### Step 5.5 - Build gate

This phase makes the feature compile end-to-end (manager referenced, HSV gone, menu push-model live).

**Verification:**
- `Grep` `commandOverflowManager` in `BrowseManagerInitializer.kt` → expected: ≥3 | actual: record.
- `Grep` `onRecomputeOverflow` in `BrowseStateUiUpdater.kt` → expected: ≥2 | actual: record.
- `Grep` `recompute()` call-sites (mic + orientation) in `BrowseActivity.kt` → expected: ≥2 | actual: record.
- `Grep` `private fun restitchBrowseControlChain` in `BrowseActivity.kt` → expected: 0 (no longer private) | actual: record.
- `.\a.ps1 dq` (standardDebug assemble) → expected: BUILD SUCCESSFUL | actual: record. On FAIL use `.\a.ps1 bf`.

## Phase Done Criteria

- [ ] Manager constructed in `BrowseManagerInitializer`, `onOverflowChanged` → `restitchBrowseControlChain`.
- [ ] Recompute called from: end of `onStateChanged`, mic collector, `onLayoutConfigurationChanged`.
- [ ] `restitchBrowseControlChain` callable from the manager (not `private`).
- [ ] `standardDebug` assembles green.
- [ ] Catalog sync run after `.kt` edits.
