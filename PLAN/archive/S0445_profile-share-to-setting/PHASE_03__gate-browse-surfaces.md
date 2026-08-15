# Phase 03 - Gate the browse surfaces

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Make the two browse system-Share show-points obey the `system_share` flag:

1. Single-file overflow menu - `PlayerCommand.SHARE` in the per-row extended-command builder.
2. Multi-select toolbar - the `btnShare` button (currently `isVisible = hasSelection`).

Both consumers already have `AppSettings` in hand at the decision point (the overflow manager receives `appSettings`; the selection-panel updater observes browse state with settings available), so the gate is an additive `&&` with no new flow.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt` | Modified | ≤ TBD by current size |

---

## Steps

### Step 03.1 - Gate the single-file overflow Share item

**Files:** `ui/browse/helpers/BrowseFileOverflowMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> This manager is `@ActivityScoped @Inject constructor(@ActivityContext context)`. Inject `IsShareTargetEnabledUseCase` as a second constructor parameter (Hilt provides it). In `buildExtendedCommands(...)`, the `appSettings` is already a parameter; change the unconditional `add(PlayerCommand.SHARE)` to `if (isShareTargetEnabledUseCase("system_share", appSettings)) add(PlayerCommand.SHARE)`. Leave the Telegram block (its own `firstInstalledPackage` gate) untouched - that is S0446's surface.

**Verification:**

- `Grep` - `IsShareTargetEnabledUseCase` referenced in `BrowseFileOverflowMenuManager.kt`.
- `Grep` - `"system_share"` literal referenced.
- `Grep` - no remaining unconditional `add(PlayerCommand.SHARE)` in this file (it must be inside the flag guard).
- Compiles via Step 03.3 (shared with the toolbar step build).

**Status:** `[ ]` not done

---

### Step 03.2 - Gate the multi-select toolbar Share button

**Files:** `ui/browse/managers/BrowseStateUiUpdater.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the selection-panel update (where `binding.btnShare.isVisible = hasSelection` is set), additionally require the `system_share` flag. The updater has access to `viewModel` / browse state; obtain the current `AppSettings` the same way the surrounding code already reads other settings-driven visibility (do not add a new lifecycle-unsafe collector - reuse the state/settings already available to this method). Change to `binding.btnShare.isVisible = hasSelection && isShareTargetEnabledUseCase("system_share", settings)`. If `BrowseStateUiUpdater` has no settings handle in this method, prefer routing the gate through the existing `setCommandEligibility` / `latestSettings` seam in `BrowseManagerInitializer` rather than widening this class - pick the seam that reads settings already present, and record the choice in the Step Log.

**Verification:**

- `Grep` - `"system_share"` literal referenced in the browse selection-panel gating (either `BrowseStateUiUpdater.kt` or the chosen seam file).
- `Grep` - `btnShare.isVisible` is no longer assigned `hasSelection` alone (the flag is ANDed in).
- Compiles via Step 03.3.

**Status:** `[ ]` not done

---

### Step 03.3 - Compile the browse gating

**Files:** - (build only)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Run `.\a.ps1 fk`. With `system_share` OFF, the overflow "Share" item disappears for every file row and the multi-select toolbar Share button hides even with a selection; with it ON, both return.

**Verification:**

- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` exits 0.
- [ ] Telegram overflow item gate unchanged.

---

## Handoff Notes to Next Phase

- Browse done. Phase 04 covers the five standalone hosts - the last family of show-points before the completeness sweep.

---

## Rollback Plan

Revert the two-file change. No data migration.
