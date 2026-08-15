# Phase 04 - Per-resource export

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

**Step Log:**

- 2026-06-15 - Steps 04.1-04.3 done. Menu item action_export_resource (+ string). ResourceAdapter onExportClick + both popup handlers + virtual-hide. MainViewModel.exportResourceForShare + MainEvent.ShareResourceFile; MainEventHandler ACTION_SEND via FileProvider (.fileprovider). MainActivity warning dialog. `a.ps1 fc` PASS. Neuroslop delta 0.

---

## Objective

Add an «Export» action to the main resource-list item context menu so a user can share a single configured resource as a file via the system share sheet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/resource_item_actions.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 60 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 80 added |

> `ResourceAdapter` has two `PopupMenu` blocks (list + grid view holders) - both must gain the new item handling. Strings reuse Phase 03 keys plus one new `resource_menu_export` key (added here).

---

## Steps

### Step 04.1 - Add the menu item and string

**Files:** `res/menu/resource_item_actions.xml`, `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `<item android:id="@+id/action_export_resource" android:title="@string/resource_menu_export" android:icon="@drawable/ic_share" />` to `resource_item_actions.xml` (reuse an existing share/export drawable; pick one present in `res/drawable`). Add the `resource_menu_export` string across EN/RU/UK with `set-android-string.ps1 -Action add`.

**Verification:**

- `Grep` - `action_export_resource` present in `resource_item_actions.xml`.
- `Grep` - `resource_menu_export` present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_menu_export"` exits 0.

**Status:** `[ ]` not done

---

### Step 04.2 - Route the menu item in the adapter

**Files:** `ui/main/ResourceAdapter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a constructor callback `onExportClick: (MediaResource) -> Unit` to `ResourceAdapter`. In BOTH `PopupMenu.setOnMenuItemClickListener` blocks (list and grid view holders) add `R.id.action_export_resource -> { onExportClick(resource); true }`. Hide the item for predefined virtual resources the same way `action_copy` is hidden (`findItem(R.id.action_export_resource)?.isVisible = !isPredefinedVirtualResource`).

**Verification:**

- `Grep -c` - `action_export_resource ->` appears twice (both view holders).
- `Grep` - `onExportClick` declared as a constructor parameter.
- `Grep` - `findItem(R.id.action_export_resource)` present.

**Status:** `[ ]` not done

---

### Step 04.3 - Wire export + share in MainActivity

**Files:** `ui/main/MainActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Pass an `onExportClick` lambda when constructing `ResourceAdapter`. The lambda calls `ExportResourcesToFileUseCase` (via the activity's ViewModel - add a thin VM method if the main ViewModel owns resource ops; otherwise inject the use case into the activity through an existing entry point) to write the single resource to a cache file named `<resourceName>.${ResourceShareFormat.EXTENSION}`, then shares it with `ACTION_SEND`, `type = ResourceShareFormat.MIME_TYPE`, `EXTRA_STREAM` from `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)` and `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in `Intent.createChooser`. Show the credential warning (`resource_share_credentials_warning`) in a confirm dialog before sharing. Verify the cache path is covered by `res/xml/file_provider_paths.xml`; add a `<cache-path>` entry if missing.

**Verification:**

- `Grep` - `onExportClick` passed to `ResourceAdapter(` construction.
- `Grep` - `ResourceShareFormat.MIME_TYPE` referenced in `MainActivity.kt`.
- `Grep` - `ACTION_SEND` and `FileProvider.getUriForFile` present in the export path.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_menu_export"` exits 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

A single resource can now be shared as a `.fmsr` file with the vendor MIME, which is exactly what the file-association receiver (Phase 05) consumes on the recipient device.

---

## Rollback Plan

Revert phase commit(s) - menu item + adapter callback + activity wiring; no migration.
