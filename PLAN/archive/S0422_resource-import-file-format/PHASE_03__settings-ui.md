# Phase 03 - Settings UI

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-15
**Completed:** 2026-06-15

**Step Log:**

- 2026-06-15 - Steps 03.1-03.4 done. 11 strings EN/RU/UK (parity OK). VM states + exportAllResources/previewResourceImport/confirmResourceImport. Fragment launchers + preview/result dialogs. Layout 2 cards. `a.ps1 fc` PASS. Neuroslop delta 0.

---

## Objective

Add user-facing «Import resources from file» and «Export all resources to file» controls to the Backup & Restore settings screen, with a credential warning, an import preview/confirm dialog, and a result summary.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_backup_restore.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/BackupRestoreUiState.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt` | Modified | ≤ 460 |

> No `res/layout-land/fragment_settings_backup_restore.xml` exists - landscape variant absent, not needed.
> If `BackupRestoreUiState.kt` is not the file declaring `FavoritesImportUiState`, locate that file by `Grep "sealed interface FavoritesImportUiState"` and edit it instead.

---

## Steps

### Step 03.1 - Add strings (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these keys in lockstep across EN/RU/UK with `scripts/utils/set-android-string.ps1 -Action add`: `resource_share_export_title`, `resource_share_import_title`, `resource_share_credentials_warning` (text: the file will contain access passwords - share it only with people you trust), `resource_share_import_preview_message` (uses `%1$d` create / `%2$d` overwrite counts), `resource_share_import_action`, `resource_share_export_success` (`%1$d` exported, `%2$d` skipped), `resource_share_import_success` (`%1$d` created, `%2$d` updated, `%3$d` skipped), `resource_share_invalid_file`. Verify each message against `docs/COMMUNICATION_POLICY.md` §2 (message formula for confirmation/result/warning) and §6 (tone checklist).

**Verification:**

- `Grep` - `resource_share_credentials_warning` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_share_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 03.2 - Add import/export UI states

**Files:** `ui/settings/BackupRestoreUiState.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror the existing favorites import/export state model for resources: add `sealed interface ResourceShareExportUiState` (`Idle`, `Loading`, `Success(exported, skipped)`, `Error(message)`) and `sealed interface ResourceShareImportUiState` (`Idle`, `LoadingPreview`, `Preview(toCreate, toUpdate, containsCredentials, uri)`, `Importing`, `Success(created, updated, skipped)`, `Error(message)`).

**Verification:**

- `Grep` - `ResourceShareExportUiState` present.
- `Grep` - `ResourceShareImportUiState` present.
- `Grep` - `Preview(` with `containsCredentials` present.

**Status:** `[ ]` not done

---

### Step 03.3 - Wire ViewModel actions

**Files:** `ui/settings/BackupRestoreViewModel.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inject `ExportResourcesToFileUseCase` and `SzaResourcesImporter`. Add `StateFlow`s for the two new states and methods: `exportAllResources(target: Uri)` (gather all non-virtual resource ids, call the export use case, emit Success/Error); `previewResourceImport(uri: Uri)` (call `importer.preview`, emit `Preview`/`Error`); `confirmResourceImport(uri: Uri)` (call `importer.importFromUri`, emit Success/Error); plus `reset*` methods. Keep business logic in the use case/importer; the VM only orchestrates and maps to state.

**Verification:**

- `Grep` - `fun exportAllResources(` present.
- `Grep` - `fun previewResourceImport(` present.
- `Grep` - `fun confirmResourceImport(` present.
- `Grep` - `ExportResourcesToFileUseCase` injected (constructor param).

**Status:** `[ ]` not done

---

### Step 03.4 - Add buttons, launchers and dialogs to the fragment

**Files:** `ui/settings/fragments/BackupRestoreFragment.kt`, `res/layout/fragment_settings_backup_restore.xml`
**Depends on:** Step 03.1, Step 03.3

**Prompt for developer:**

> Add two buttons to the layout (`btnExportResources`, `btnImportResources`) using existing button styles and theme attributes (no hardcoded hex). In the fragment register `ActivityResultContracts.CreateDocument(ResourceShareFormat.MIME_TYPE)` for export and `ActivityResultContracts.OpenDocument()` for import. Export: confirm the credential warning (`resource_share_credentials_warning`) before launching CreateDocument with a default name like `fms_resources.${ResourceShareFormat.EXTENSION}`, then on result call `viewModel.exportAllResources(uri)`. Import: launch OpenDocument with `arrayOf(ResourceShareFormat.MIME_TYPE, "application/xml", "text/xml", "*/*")`, then `viewModel.previewResourceImport(uri)`; observe `Preview` and show a `MaterialAlertDialogBuilder` summarizing create/overwrite counts plus the credential note, confirming via `viewModel.confirmResourceImport(uri)`. Observe states with `collectOnLifecycle`. Show results/errors via Snackbar or dialog mirroring the favorites flow. Guard the picker launches with the same `ActivityNotFoundException` catch the favorites import already uses.

**Verification:**

- `Grep` - `btnImportResources` present in fragment and layout.
- `Grep` - `CreateDocument(` and `OpenDocument(` present in the fragment.
- `Grep` - `previewResourceImport` and `confirmResourceImport` called in the fragment.
- `Grep` - `collectOnLifecycle` used for the new states (no bare `lifecycleScope.launch { ... collect }`).
- `Grep -n "#" app_v2/src/main/res/layout/fragment_settings_backup_restore.xml` shows no hardcoded color literals added.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_share_"` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Settings now owns the reliable import/export path. The CreateDocument/OpenDocument pattern and the preview dialog are the reference for the per-resource export (Phase 04) and the file-association receiver (Phase 05).

---

## Rollback Plan

Revert phase commit(s) - additive UI, strings, and VM state; no migration.
