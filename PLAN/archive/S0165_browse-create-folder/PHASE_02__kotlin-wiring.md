# Phase 02 — Kotlin Wiring

**Strategic spec:** [`../S0165_browse-create-folder.md`](../S0165_browse-create-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-13

---

## Objective

Wire `btnCreateFolder` through the existing manager chain so that the button appears/disappears reactively from `BrowseState` and invokes `ResourceOpsMenuManager.showCreateFolderDialog()` on click — without duplicating any existing dialog or keyboard-shortcut handler.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`btnCreateFolder` exists in both layouts; `ic_create_new_folder_24.xml` exists).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 700 |

> `BrowseManagerInitializer.kt` is 658 lines — **backup required before editing**. Create `temp/BrowseManagerInitializer_<timestamp>.kt.bak` before touching the file.

---

## Steps

### Step 02.1 — Extend `BrowseButtonSetupHelper` with create-folder callback and handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> In `BrowseButtonSetupHelper.kt`:
>
> 1. Add `fun onCreateFolderClicked()` to the `ButtonCallbacks` interface (after `onStopScanClicked`).
> 2. In `setupAllButtons()`, add a click handler immediately before the `setupScrollButtons()` call:
>    ```kotlin
>    binding.btnCreateFolder?.setOnClickListener {
>        UserActionLogger.logButtonClick("CreateFolder", "BrowseActivity")
>        callbacks.onCreateFolderClicked()
>    }
>    ```
> 3. In `updateToolbarButtonLabels()`:
>    - Inside the `isLandscape` branch, add: `binding.btnCreateFolder?.text = ctx.getString(R.string.action_create_folder)`
>    - Inside the `else` branch, add: `binding.btnCreateFolder?.text = null`
>
> Use `?.` for all `binding.btnCreateFolder` accesses (view is `gone` by default, not absent from binding).
> Do not add `Timber.d` calls — `UserActionLogger.logButtonClick` is sufficient here (existing pattern).

**Verification:**

- `Grep` — `fun onCreateFolderClicked()` present in `BrowseButtonSetupHelper.kt`.
- `Grep` — `binding.btnCreateFolder?.setOnClickListener` present in `BrowseButtonSetupHelper.kt`.
- `Grep` — `binding.btnCreateFolder?.text = ctx.getString(R.string.action_create_folder)` present.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseButtonSetupHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Files: BrowseButtonSetupHelper.kt (modified). Dev log recorded.

---

### Step 02.2 — Add `updateCreateFolderButtonVisibility` to `BrowseStateUiUpdater`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `BrowseStateUiUpdater.kt`:
>
> 1. Add a call to `updateCreateFolderButtonVisibility(state)` inside `onStateChanged()`, after the `onUpdateBreadcrumb(state)` line.
> 2. Implement the private function:
>    ```kotlin
>    private fun updateCreateFolderButtonVisibility(state: BrowseState) {
>        val resource = state.resource
>        val canCreateFolder = resource != null
>                && resource.showSubfoldersAsItems
>                && !resource.isReadOnly
>                && !VirtualPathUtils.isVirtualPath(resource.path)
>        binding.btnCreateFolder?.isVisible = canCreateFolder
>    }
>    ```
>
> `VirtualPathUtils` is already imported. `isVisible` is from `androidx.core.view.isVisible` which is already imported via `import androidx.core.view.isVisible`. Do not import anything new unless the compiler requires it.

**Verification:**

- `Grep` — `updateCreateFolderButtonVisibility(state)` called inside `onStateChanged` in `BrowseStateUiUpdater.kt`.
- `Grep` — `fun updateCreateFolderButtonVisibility` present as private function in same file.
- `Grep` — `resource.showSubfoldersAsItems` present in `BrowseStateUiUpdater.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseStateUiUpdater.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Files: BrowseStateUiUpdater.kt (modified). Dev log recorded.

---

### Step 02.3 — Backup `BrowseManagerInitializer.kt` before editing

**Files:** `temp/` (backup only)
**Depends on:** Step 02.2

**Prompt for developer:**

> `BrowseManagerInitializer.kt` is 658 lines (>500) — a timestamped backup is required before editing. Run:
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" `
>           "temp/BrowseManagerInitializer_$ts.kt.bak"
> ```

**Verification:**

- `Glob` — `temp/BrowseManagerInitializer_*.kt.bak` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 1/1 PASS. Backup: temp/BrowseManagerInitializer_20260513_144207.kt.bak.

---

### Step 02.4 — Wire `onCreateFolderClicked` callback in `BrowseManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Steps 02.1, 02.3

**Prompt for developer:**

> In `BrowseManagerInitializer.kt`, find the `BrowseButtonSetupHelper.ButtonCallbacks` anonymous implementation passed to `buttonSetupHelper.setupAllButtons(...)`. Add the override immediately after the `onStopScanClicked` override:
>
> ```kotlin
> override fun onCreateFolderClicked() =
>     resourceOpsMenuManager.showCreateFolderDialog(viewModel)
> ```
>
> Do not add a new `Timber.d` call here — the existing keyboard-shortcut path (`KeyboardNavigationCallbacks.showCreateFolderDialog`) already routes to the same `resourceOpsMenuManager.showCreateFolderDialog(viewModel)` call in this class (line ~273). The button uses the same entry point.
>
> Also insert the S0165 debug verification tag at the entry of `onCreateFolderClicked()` (the spec is about to move to `BlockNeedUserTest`):
>
> ```kotlin
> override fun onCreateFolderClicked() {
>     Timber.d("S0165: btnCreateFolder clicked → showCreateFolderDialog")
>     resourceOpsMenuManager.showCreateFolderDialog(viewModel)
> }
> ```
>
> Ensure `timber.log.Timber` is already imported (it is in this file).

**Verification:**

- `Grep` — `override fun onCreateFolderClicked()` present in `BrowseManagerInitializer.kt`.
- `Grep` — `Timber.d("S0165:` present in `BrowseManagerInitializer.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseManagerInitializer.kt`.
- Build passes — run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS + build PASS. Files: BrowseManagerInitializer.kt (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Manual smoke test:
  - Open Browse on a resource with `showSubfoldersAsItems = true` and writable path → `btnCreateFolder` appears.
  - Open Browse on a resource with `showSubfoldersAsItems = false` → `btnCreateFolder` hidden.
  - Tap `btnCreateFolder` → Create Folder dialog opens.
  - Confirm folder name → folder created, list refreshed, toast appears.
  - Verify `Timber.d("S0165:` fires in logcat on button tap.

---

## Handoff Notes to Next Phase

Phase 02 establishes: `btnCreateFolder` is live — appears reactively for eligible resources and invokes the existing Create Folder dialog. The existing `...` popup entry and keyboard shortcut (F7/Ctrl+Shift+N) are unchanged. Debug tag `S0165` is present in logcat path.

---

## Rollback Plan

Revert phase commit(s). No data migration. Backup at `temp/BrowseManagerInitializer_<timestamp>.kt.bak` for manual restore if needed.
