# Phase 02 - Bar Path Button

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Replace the growing `BreadcrumbView` ribbon in all three `activity_browse` layouts with a single fixed-width `btnPath` button that opens the segment `PopupMenu`, and re-point the runtime wiring and the D-pad focus chain at it.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Pre-Implementation Blocker in `INDEX.md` resolved - the owner has fixed the button content.
- [x] `scripts/utils/enter-code-lock.ps1 -Reason "S1316 phase 02"` acquired; `scripts/utils/lock-status.ps1 -Name Build` reports no live build.
- [x] Backup taken: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` (992 LOC) and `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` (844 LOC) copied to `temp/S1316/` with a timestamped name.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_browse.xml` | Modified | ≤ 161 |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified | ≤ 159 |
| `app_v2/src/main/res/layout-w600dp/activity_browse.xml` | Modified | ≤ 159 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 995 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 846 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Landscape parity.** All three `activity_browse` variants exist and carry byte-identical breadcrumb markup at line 13 and `spaceAfterBack` at line 16; every one of them is edited in Step 02.1. No `-sw*dp` or `-w*dp` variant other than `layout-w600dp` exists for this file.

---

## Steps

### Step 02.1 - Swap the breadcrumb for `btnPath` in all three layouts

**Files:** `app_v2/src/main/res/layout/activity_browse.xml`, `app_v2/src/main/res/layout-land/activity_browse.xml`, `app_v2/src/main/res/layout-w600dp/activity_browse.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In each of the three files, delete the `com.sza.fastmediasorter.ui.common.BreadcrumbView` element (currently line 13) together with its `<!-- Breadcrumb path .. -->` comment, and delete the `<Space android:id="@+id/spaceAfterBack" .. />` element (currently line 16) together with its comment - that Space declares `layout_width="@dimen/match_constraint"` (`0dp`) with no `layout_weight`, so it measures to zero pixels and exists only as the breadcrumb's companion.
> In the freed slot, directly after `btnBack`, insert a `com.google.android.material.button.MaterialButton` with `android:id="@+id/btnPath"`, `android:visibility="gone"`, `app:icon="@drawable/ic_folder_24"`, `app:iconTint="?attr/colorControlNormal"`, `android:contentDescription="@string/current_path"` and otherwise the exact attribute set of the sibling `btnRefresh` in that same file - including `style="?attr/materialIconButtonStyle"` in `layout/`, `style="?attr/materialIconButtonStyle"` in `layout-land/` and `layout-w600dp/` as well (NOT `@style/Widget.FastMediaSorter.Button.Text`, which is the labelled variant and would reintroduce a width that varies with locale), `android:focusable="true"`, `android:clickable="true"`, `android:foreground="@drawable/focus_button_background"`, `android:nextFocusDown="@id/rvMediaFiles"`, and the `browse_cmd_*` size/inset/padding dimensions.
> Add no hex colour literal and no new dimension resource. `@string/current_path` already exists in `values`, `values-ru` and `values-uk`, so no strings work is needed.

**Verification:**

- `Grep` - `BreadcrumbView` returns zero hits across `app_v2/src/main/res/layout*/activity_browse.xml`.
- `Grep` - `spaceAfterBack` returns zero hits across `app_v2/src/main/res/layout*/activity_browse.xml`.
- `Grep` - `android:id="@+id/btnPath"` returns exactly one hit in each of the three files (three hits total).
- `Grep` - `@drawable/ic_folder_24` returns exactly one hit in each of the three files.
- `Grep` - `@string/current_path` returns exactly one hit in each of the three files.
- `Grep` - `Widget.FastMediaSorter.Button.Text` does not appear on any line that also contains `btnPath`.
- `Grep` - `="#` returns zero hits in the three files (Rule 19: no hardcoded hex in layouts).

**Status:** `[x]` done

---

### Step 02.2 - Re-point the runtime wiring from breadcrumb to `btnPath`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Rename the private method `updateBreadcrumb(state: BrowseState)` (currently at line 976) to `updatePathButton(state: BrowseState)` and update the `onUpdateBreadcrumb = { state -> updateBreadcrumb(state) }` lambda at line 236 to `onUpdateBreadcrumb = { state -> updatePathButton(state) }`; leave the `BrowseStateUiUpdater` constructor parameter name `onUpdateBreadcrumb` alone so this stays a single-file change.
> Inside the renamed method keep the existing `sub` predicate (`state.isSubfolderMode && state.currentPath != null`), set `binding.btnPath.visibility` from it, delete both `binding.breadcrumbView.*` calls and the `binding.spaceAfterBack?.visibility` line, and when `sub` is true set the click listener to open the menu:
> `binding.btnPath.setOnClickListener { val (name, folders) = viewModel.getBreadcrumbParts(); pathMenuManager.showPathMenu(binding.btnPath, name, folders) { depth -> viewModel.navigateToDepth(depth) } }`.
> Add a `pathMenuManager` field initialised next to `commandOverflowManager` (line 225) as `BrowsePathMenuManager(activity)`. `getBreadcrumbParts()` and `navigateToDepth(depth)` already exist on `BrowseViewModel` (lines 948-949) and must keep being the only navigation entry points - S0917 reconstructs the target from the path/name stacks by index, so any string parsing of `currentPath` here is wrong.
> Add no comment restating what a line does; the only comment worth writing is why the click listener is (re)assigned inside the `sub` branch.

**Verification:**

- `Grep` - `breadcrumbView` returns zero hits under `app_v2/src/main/java/`.
- `Grep` - `spaceAfterBack` returns zero hits under `app_v2/src/main/java/`.
- `Grep` - `private fun updatePathButton(` matches exactly once in `BrowseManagerInitializer.kt`.
- `Grep` - `updateBreadcrumb` returns zero hits in `BrowseManagerInitializer.kt`.
- `Grep` - `pathMenuManager.showPathMenu(` matches exactly once in `BrowseManagerInitializer.kt`.
- `Grep` - `viewModel.navigateToDepth(` matches exactly once in `BrowseManagerInitializer.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `BrowseManagerInitializer.kt`.

**Status:** `[x]` done

---

### Step 02.3 - Put `btnPath` in the D-pad focus chain

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `restitchBrowseControlChain()` (line 353) insert `binding.btnPath,` into the `candidates` list immediately after `binding.btnBack,` so the horizontal chain follows the on-screen order. The existing `.filter { it.visibility == View.VISIBLE }` already drops it at resource root, where the button is `GONE`. Change nothing else in that method. Do not add `btnPath` to `BrowseButtonSetupHelper.updateToolbarButtonLabels` - a text label there would make the button's width depend on locale, which is the defect this ticket removes.

**Verification:**

- `Grep` - `binding.btnPath` matches exactly once in `BrowseActivity.kt`.
- `Grep -A 2` - the line `binding.btnBack,` inside `restitchBrowseControlChain` is immediately followed by `binding.btnPath,`.
- `Grep` - `btnPath` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt`.

**Status:** `[x]` done

---

### Step 02.4 - Add the S1316 device probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add exactly one probe line at the entry of the changed flow, as the first statement inside `updatePathButton`, immediately after `sub` is computed:
> `Timber.d("S1316: path button sub=$sub depth=${state.folderNameStack.size}")`
> This is the only `S1316:` tag in the tree; it stays until the ticket leaves `BlockNeedUserTest`. Keep the line ≤ 120 chars.

**Verification:**

- `Grep` - `Timber.d("S1316:` matches exactly once across `app_v2/src/**/*.kt`.
- `Grep` - that match is inside `BrowseManagerInitializer.kt`.
- `Grep` - `S1316` returns zero hits in any `Timber.i(`, `Timber.w(` or `Timber.e(` call.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Listener symmetry: `btnPath.setOnClickListener` is reassigned per state emission, not accumulated - confirm no second registration site.

---

## Handoff Notes to Next Phase

`@id/btnPath` exists in all three layouts and generates a non-null `binding.btnPath`. The command bar no longer contains any view whose width grows with path depth. Phase 03 makes the overflow allocator account for the button's fixed width; Phase 04 deletes the now-orphaned `BreadcrumbView`.

---

## Rollback Plan

Revert phase commit(s) - layout and wiring only, no data migration and no persisted state touched.
