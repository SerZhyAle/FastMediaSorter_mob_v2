# Phase 02 - Migrate ad-hoc action/resource dialogs (A-F)

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-06-24
**Completed:** 2026-06-24

> Step Log (batched): all six ad-hoc dialogs migrated to `ListSelectionDialog<T>` / `SimpleValueChoiceDialog`. Grep-verified zero `setItems`/`setSingleChoiceItems` across the six files; explicit type params added to the four `ListSelectionDialog<T>` sites. Compiled in the consolidated clean build.

---

## Objective

Replace every ad-hoc `AlertDialog.Builder` / `MaterialAlertDialogBuilder` `setItems` / `setSingleChoiceItems` value picker in settings (sites A-F) with the canonical `ListSelectionDialog<T>` (string-keyed sites via `SimpleValueChoiceDialog`). No XML changes - these are already dialogs; this removes the appcompat-vs-Material split and unifies the list look.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`SimpleValueChoiceDialog` available for D, F).
- [ ] `ListSelectionDialog` / `ListSelectionConfig` present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/ScreenshotGestureActionPickerManager.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsDestinationsManager.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsImportExportHelper.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsWidgetHelper.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt` | Modified | ≤ 460 |

> No new user-visible strings: every site reuses its existing title and label resources. No layout files - all six sites already render as dialogs.

---

## Steps

### Step 02.1 - Migrate screenshot gesture action picker (A)

**Files:** `ScreenshotGestureActionPickerManager.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `showPicker`, replace the `MaterialAlertDialogBuilder.setSingleChoiceItems` with a `ListSelectionDialog<ScreenshotGestureAction>`. Add a `lifecycleOwner: LifecycleOwner` parameter to `showPicker` (callers pass `viewLifecycleOwner`) and update the call site that invokes it.
> Config: `loader = { availableActions() }`, formatter `getDisplayName = { labelFor(context, it) }`, `hasSelection = true`, `isSelected = { it == current }`, `allowClear = false`, `onSelected = { it?.let(onPicked) }`, title `R.string.setting_screenshot_gesture_action_dialog_title`. Preserve the OCR-translate capability filter (already inside `availableActions()`).
> Then `.show()` the dialog.

**Verification:**

- `Grep -n "setSingleChoiceItems"` in `ScreenshotGestureActionPickerManager.kt` returns zero hits.
- `Grep` - `ListSelectionDialog<ScreenshotGestureAction>` present.
- `Grep` - `availableActions()` still referenced (capability filter intact).

**Status:** `[x] done`

---

### Step 02.2 - Migrate add-destination picker (B)

**Files:** `OperationsDestinationsManager.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `showAddDestinationDialog`, replace the appcompat `AlertDialog.Builder.setItems` with a `ListSelectionDialog<MediaResource>` whose `loader = { viewModel.getWritableNonDestinationResources() }` (the loader is `suspend`, so the use of the suspend getter is fine - drop the outer `lifecycleScope.launch`).
> Formatter `getDisplayName = { "${it.name} (${it.path})" }`; `hasSelection = false`; `isSelected = { false }`; `allowClear = false`; `emptyMessageRes = R.string.no_writable_resources_destinations`; `onSelected = { it?.let { res -> viewModel.addDestination(res); /* keep the "destination added" toast */ } }`. Use `fragment.viewLifecycleOwner` as the lifecycle owner. Keep the post-add toast behavior.

**Verification:**

- `Grep -n "setItems"` in `OperationsDestinationsManager.kt` returns zero hits.
- `Grep` - `ListSelectionDialog<MediaResource>` present.
- `Grep` - `R.string.no_writable_resources_destinations` still referenced (empty-state preserved).
- `Grep` - `destination_added` still referenced (post-add toast preserved).

**Status:** `[x] done`

---

### Step 02.3 - Migrate operations destination picker (C)

**Files:** `OperationsSettingsFragment.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `showDestinationPicker`, replace the appcompat `AlertDialog.Builder.setSingleChoiceItems` (with its leading manual "(clear)" entry) with a `ListSelectionDialog<MediaResource>` using `allowClear = true` instead of the manual clear row.
> Config: `loader = { destinationsManager.currentDestinations }`, formatter `getDisplayName = { it.name }`, `hasSelection = currentResourceId != null`, `isSelected = { it.id == currentResourceId }`, `emptyMessageRes = R.string.no_resources_available`, `onSelected = onPicked`, title `R.string.setting_select_destination`. Keep the up-front empty-list guard toast if the list is read before construction, or rely on the dialog's `emptyMessageRes`; do not both guard and pass an empty list.

**Verification:**

- `Grep -n "setSingleChoiceItems"` in `OperationsSettingsFragment.kt` returns zero hits.
- `Grep` - `ListSelectionDialog<MediaResource>` present in the file.
- `Grep` - `allowClear = true` present (clear path preserved via the canonical Clear button).

**Status:** `[x] done`

---

### Step 02.4 - Migrate import-method picker (D)

**Files:** `GeneralSettingsImportExportHelper.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `importSettings`, replace the appcompat `AlertDialog.Builder.setItems(arrayOf(import_auto, import_browse))` with a `SimpleValueChoiceDialog`.
> Options: `Option("auto", getString(R.string.import_auto))`, `Option("browse", getString(R.string.import_browse))`; `currentKey = null`; `allowClear = false`; title `R.string.import_method_title`; lifecycle owner `fragment.viewLifecycleOwner`. In `onSelected`, branch on the key: `"auto" -> importSettingsAuto()`, `"browse" -> launch file picker` (preserve the existing `ActivityNotFoundException` toast fallback).

**Verification:**

- `Grep -n "setItems"` in `GeneralSettingsImportExportHelper.kt` returns zero hits.
- `Grep` - `SimpleValueChoiceDialog` present.
- `Grep` - `ActivityNotFoundException` still referenced (file-picker fallback preserved).

**Status:** `[x] done`

---

### Step 02.5 - Migrate widget-type picker (E)

**Files:** `GeneralSettingsWidgetHelper.kt`
**Depends on:** start of phase

**Prompt for developer:**

> In `showPickerDialog`, replace the `MaterialAlertDialogBuilder.setItems` with a `ListSelectionDialog<HomeWidgetEntry>`. Config: `loader = { entries }`, formatter `getDisplayName = { context.getString(it.labelRes) }`, `hasSelection = false`, `isSelected = { false }`, `allowClear = false`, title `R.string.widget_picker_dialog_title`, lifecycle owner `fragment.viewLifecycleOwner`. In `onSelected`, run the existing `pinner.requestPin(entry.component(context), null)` flow and keep the unsupported-launcher toast.

**Verification:**

- `Grep -n "setItems"` in `GeneralSettingsWidgetHelper.kt` returns zero hits.
- `Grep` - `ListSelectionDialog<HomeWidgetEntry>` present.
- `Grep` - `requestPin` still referenced (pin flow preserved).

**Status:** `[x] done`

---

### Step 02.6 - Migrate default-document-type picker (F)

**Files:** `DefaultPlayerHelper.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> In `showSetDefaultDocumentDialog`, keep the `options.size == 1` shortcut unchanged. Replace the multi-option `MaterialAlertDialogBuilder.setItems(labels)` branch with a `SimpleValueChoiceDialog` whose `Option.key` is the MIME type and `Option.label` is `context.getString(labelRes)` for each option pair.
> `currentKey = null`; `allowClear = false`; title `R.string.settings_default_document_type_title`; lifecycle owner `fragment.viewLifecycleOwner`. In `onSelected`, call `showSetDefaultDialogForType(fragment, key)` with the selected MIME type. Preserve the flavor-driven option assembly (PDF/TEXT always, EPUB when `includeEpub`, Office when `defaultOfficeMimeType()` non-null).

**Verification:**

- `Grep -n "setItems"` in `DefaultPlayerHelper.kt` returns zero hits.
- `Grep` - `SimpleValueChoiceDialog` present.
- `Grep` - `defaultOfficeMimeType()` still referenced (flavor option assembly preserved).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` -> `standard debug`.
- [ ] `Grep` across the six files for `setSingleChoiceItems` / `setItems` returns zero hits (confirmation/destructive dialogs that legitimately use `setMessage`+buttons are untouched and contain neither call).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added (batched, one ticket-level entry) via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All six already-dialog sites now render through `ListSelectionDialog`. Phases 03/04 handle the harder inline-control -> trigger-row conversions that also require XML edits.

---

## Rollback Plan

Revert phase commit(s) - per-site dialog swaps, no data migration, no schema change. Each step is independently revertible.
