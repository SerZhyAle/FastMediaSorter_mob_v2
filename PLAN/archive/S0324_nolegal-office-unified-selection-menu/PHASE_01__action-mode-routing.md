# Phase 01 - Action Mode Routing

**Strategic spec:** [`../S0324_nolegal-office-unified-selection-menu.md`](../S0324_nolegal-office-unified-selection-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Introduce the shared ActionMode wrapper and route WebView floating selection menus through EPUB or active Office providers.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is on `DEBUG-v011`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerHost.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 970 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 790 |

> Files above 500 lines require timestamped backups in `temp/` before edit.

---

## Steps

### Step 01.1 - Add shared selection ActionMode seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionActionModeCallback.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerHost.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a reusable `DocumentSelectionActionModeAugmentingCallback` that wraps a platform `ActionMode.Callback` and delegates menu creation/clicks to `DocumentSelectionActionModeCallback` before preserving the original callback. Extend `OfficeDocumentViewerHost` with `getSelectionActionModeCallback(): DocumentSelectionActionModeCallback?`, returning null in `NoOpOfficeDocumentViewerHost`, and add `onTranslateSelection(text: String)` to `OfficeDocumentViewerHost.Callback`.

**Verification:**

- `Grep` - `class DocumentSelectionActionModeAugmentingCallback` matches exactly once in `DocumentSelectionActionModeCallback.kt`.
- `Grep` - `fun getSelectionActionModeCallback(): DocumentSelectionActionModeCallback?` exists in `OfficeDocumentViewerHost.kt`.
- `Grep` - `fun onTranslateSelection(text: String)` exists in `OfficeDocumentViewerHost.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 3/3 PASS. Expected wrapper declarations: 1, actual: 1. Expected host selection seam: >=1, actual: 2. Expected translation callback: 1, actual: 1. Files: `DocumentSelectionActionModeCallback.kt`, `OfficeDocumentViewerHost.kt`. Dev log recorded.

---

### Step 01.2 - Route floating WebView selection in player activities

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `PlayerActivity.startActionMode`, wrap `ActionMode.TYPE_FLOATING` callbacks with the shared augmenter when an active Office host or visible EPUB WebView supplies a document selection callback. In `StandalonePlayerActivity.startActionMode`, replace the EPUB-only check with `StandaloneViewManager.getDocumentSelectionActionModeCallback()`. Add that method to `StandaloneViewManager`, prioritizing active Office over visible EPUB. Wire `OfficeDocumentViewerHost.Callback.onTranslateSelection` in `PlayerViewerFactory` and `StandaloneViewManager`.

**Verification:**

- `Grep` - `override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode?` exists in `PlayerActivity.kt`.
- `Grep` - `getDocumentSelectionActionModeCallback()` exists in `StandalonePlayerActivity.kt`.
- `Grep` - `fun getDocumentSelectionActionModeCallback(): DocumentSelectionActionModeCallback?` exists in `StandaloneViewManager.kt`.
- `Grep` - `override fun onTranslateSelection(text: String)` exists in `PlayerViewerFactory.kt`.
- `Grep` - `override fun onTranslateSelection(text: String)` exists in `StandaloneViewManager.kt`.
- `Grep` - `EpubAugmentedActionModeCallback` returns zero hits in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 6/6 PASS. Expected `PlayerActivity.startActionMode`: 1, actual: 1. Expected standalone document callback call: >=1, actual: 1. Expected standalone document callback method: 1, actual: 1. Expected player translation override: 1, actual: 1. Expected standalone translation override: 1, actual: 1. Expected old EPUB-only wrapper hits: 0, actual: 0. Files: `PlayerActivity.kt`, `PlayerViewerFactory.kt`, `StandalonePlayerActivity.kt`, `StandaloneViewManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Kotlin catalog sync passes via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `rg -n "Log\.d\(" <touched kotlin files>` returns zero hits.

---

## Handoff Notes to Next Phase

The main player and standalone player can now ask any document viewer host for a floating selection menu callback.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent state change.
