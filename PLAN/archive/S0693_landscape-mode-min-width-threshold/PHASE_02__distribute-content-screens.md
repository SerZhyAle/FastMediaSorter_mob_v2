# Phase 02 - Distribute to Content Screens

**Strategic spec:** [`../S0693_landscape-mode-min-width-threshold.md`](../S0693_landscape-mode-min-width-threshold.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Route the primary content screens (Main, Browse, Streams) through the shared node, replacing their per-site orientation / `screenWidthDp >= 600` checks with `isWideLayout()`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`isWideLayout` extensions exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt` | Modified | edit only |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamGridModeManager.kt` | Modified | edit only |

> No `res/layout/*.xml` edited in this phase - landscape-parity rule not triggered. Resource-layer alignment is Phase 04.
>
> **Out of scope (do NOT migrate):** `MainResourceTabsManager` (`screenWidthDp < 480` tab-mode) and player-family `DestinationButtonsManager` (`screenWidthDp < 500` button height) are orthogonal width-class decisions, not landscape-style switches. Leave them untouched.

---

## Steps

### Step 02.1 - MainActivity: route grid column decision through the node

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> At the `screenWidthDp >= 600` check (~line 800) that selects the resource-list `GridLayoutManager` column count, replace the raw width comparison with `resources.configuration.isWideLayout()`. Import `com.sza.fastmediasorter.core.orientation.isWideLayout`. Keep the surrounding column-count math unchanged.

**Verification:**

- `Grep` - `isWideLayout()` present in `MainActivity.kt`.
- `Grep` - `screenWidthDp >= 600` no longer present in `MainActivity.kt`.

**Status:** `[x]` done

---

### Step 02.2 - MainLayoutChromeManager: toolbar labels + list layout-manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the `config.orientation == Configuration.ORIENTATION_LANDSCAPE` check (~line 30, toolbar button labels) and the `screenWidthDp >= 600` check (~line 59, `isWideScreen` for `LinearLayoutManager` vs `GridLayoutManager`) with `config.isWideLayout()`. Import the extension. Keep the existing `Timber.d` debug lines but update their interpolated values to read the new boolean.

**Verification:**

- `Grep` - `isWideLayout()` matches at least twice in `MainLayoutChromeManager.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `MainLayoutChromeManager.kt`.
- `Grep` - `screenWidthDp >= 600` no longer present in `MainLayoutChromeManager.kt`.

**Status:** `[x]` done

---

### Step 02.3 - BrowseRecyclerViewManager: list min-cell-width signal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> At ~line 98, the `isLandscape` flag (currently `resources.configuration.orientation == ..ORIENTATION_LANDSCAPE`) is passed into `calculateListSpanCount(screenWidthDp, isLandscape)` to pick the landscape vs portrait minimum cell width. Replace its derivation with `resources.configuration.isWideLayout()` and rename the local to `isWide` (update the single call site and the `calculateListSpanCount` parameter name accordingly). Leave the `screenWidthDp >= 600f` grid base-span math at ~line 128 unchanged - that is already width-based and correct. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `BrowseRecyclerViewManager.kt`.
- `Grep` - `val isLandscape =` no longer present in `BrowseRecyclerViewManager.kt`.
- `Grep` - `calculateListSpanCount(` still present (signature retained, param renamed).

**Status:** `[x]` done

---

### Step 02.4 - BrowseButtonSetupHelper: toolbar labels

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the `config.orientation == Configuration.ORIENTATION_LANDSCAPE` check (~line 237, toolbar button labels) with `config.isWideLayout()`. Import the extension.

**Verification:**

- `Grep` - `isWideLayout()` present in `BrowseButtonSetupHelper.kt`.
- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` no longer present in `BrowseButtonSetupHelper.kt`.

**Status:** `[x]` done

---

### Step 02.5 - StreamGridModeManager: list single-column gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamGridModeManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> At ~line 175, replace `if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return 1` with `if (!resources.configuration.isWideLayout()) return 1`. Leave the existing `screenWidthDp / MIN_LIST_COLUMN_WIDTH_DP` span math below it unchanged. Import the extension. Coordinate with S0692 (stream-list landscape multi-column) - this site overlaps; if S0692 landed first, fold its change into the node call rather than duplicating the decision.

**Verification:**

- `Grep` - `isWideLayout()` present in `StreamGridModeManager.kt`.
- `Grep` - `orientation != Configuration.ORIENTATION_LANDSCAPE` no longer present in `StreamGridModeManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Content screens now read the node. The toolbar/grid behavior for a wide-portrait device matches a landscape phone. The XML structure of these screens is still selected by the `-land` qualifier - Phase 04 aligns it.

---

## Rollback Plan

Revert the phase commit(s). Each site reverts to its prior independent check; no data or user-facing surface migrated.
