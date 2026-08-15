# Phase 03 - MainActivity wiring

**Strategic spec:** [`../S0542_download-link-from-clipboard.md`](../S0542_download-link-from-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Wire the new menu entry into the main-window dropdown: resolve its visibility from the existing link auto-download setting, instantiate the managers, add the item to the popup, route its tap, and include it in the dropdown visibility count.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (both managers compile).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified (currently 1219 LOC) | +~25 |

> File >500 LOC → backup step required (Step 03.1). File <1500 LOC → no split needed.

---

## Steps

### Step 03.1 - Backup MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `MainActivity.kt` is 1219 LOC (>500). Before editing, copy it to `temp/` with a timestamped name (e.g. `temp/MainActivity_<yyyyMMdd_HHmmss>.kt.bak`) per CLAUDE.md Rule 5.

**Verification:**

- `Glob` - a `temp/MainActivity_*.kt.bak` file exists.

**Status:** `[x]` done

---

### Step 03.2 - Resolve link-download visibility flag and instantiate managers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `private var isLinkDownloadEnabled = false` field next to `isCalculatorEnabled` / `isCameraOcrEnabled`. Populate it from `AppSettings.linkAutoDownloadEnabled` in the same settings-observation path that already sets the other dropdown flags (find where `isCalculatorEnabled` / `isEmbeddedGameEnabled` are assigned from the settings flow and set `isLinkDownloadEnabled` alongside). Instantiate the managers where the other menu managers are created (next to `quickCaptureMenuManager = MainQuickCaptureMenuManager(...)`): a `MainLinkDownloadManager(this)` field and `linkDownloadMenuManager = MainLinkDownloadMenuManager(onLinkDownload = { linkDownloadManager.show() })`. After assigning `isLinkDownloadEnabled`, refresh the dropdown visibility (call the existing `refreshMainWindowDropdownMenuVisibility()` so the button appears/disappears when the setting changes at runtime).

**Verification:**

- `Grep` - `isLinkDownloadEnabled` present in `MainActivity.kt`.
- `Grep` - `linkAutoDownloadEnabled` referenced in `MainActivity.kt`.
- `Grep` - `MainLinkDownloadMenuManager(` and `MainLinkDownloadManager(` both present.

**Status:** `[x]` done

---

### Step 03.3 - Add the item to the popup and route its tap

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `populateMainWindowDropdownMenu(popup)`, call `linkDownloadMenuManager.populate(popup, isLinkDownloadEnabled, <next startOrder>)` after the existing quick-capture populate call, and fold its return into the running item count. In `showMainWindowDropdownMenu()`'s `setOnMenuItemClickListener`, add `|| linkDownloadMenuManager.handleMenuItem(item.itemId)` to the existing delegation chain (alongside `miniGameMenuManager` / `quickCaptureMenuManager`), so the tap dismisses the popup and opens the dialog.

**Verification:**

- `Grep` - `linkDownloadMenuManager.populate(` present in `MainActivity.kt`.
- `Grep` - `linkDownloadMenuManager.handleMenuItem(` present in `MainActivity.kt`.

**Status:** `[x]` done

---

### Step 03.4 - Include the item in dropdown visibility count

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> The dropdown button hides when the total item count is zero. Wherever the total dropdown item count is computed (the path feeding `refreshMainWindowDropdownMenuVisibility()` / alongside `getMiniGameMenuItemCount()`), add `linkDownloadMenuManager.itemCount(isLinkDownloadEnabled)` to the sum so the button shows when only this item is enabled and hides when nothing is enabled.

**Verification:**

- `Grep` - `linkDownloadMenuManager.itemCount(` present in `MainActivity.kt`.
- Project compiles - run `/build`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `MainActivity.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The feature is functionally complete end-to-end: menu item visible iff `linkAutoDownloadEnabled`, tap → clipboard dialog → confirm → existing receiver download. Phase 04 records the capability and syncs catalog/docs.

---

## Rollback Plan

Restore `MainActivity.kt` from the `temp/` backup; delete the Phase 02 files. No data migration or persisted state changed.
