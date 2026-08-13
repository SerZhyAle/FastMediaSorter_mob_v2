# Phase 03 - Scheduled-op local-folder picker

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Steps 03.1-03.5 verification PASS. New: `ScheduledOperationDraft`, `ResolveLocalFolderResourceUseCase` (dedup visible-only + create hidden via FTS-safe `addResource`), `CheckLocalFolderWritableUseCase`. Dialog: "Local folder" atop both dropdowns, staged folder + `onLocalFolderPicked`, draft on Save. Manager hosts SAF `OpenDocumentTree` launcher, writability gate (reject non-writable receiver, read-only source -> COPY), edit-case augments visible lists with the resolved hidden FK. VM `saveOperation` resolves staged folders on Save. Build: BUILD SUCCESSFUL (1m5s) after fixing a recursive-type-inference on the field-init launcher (explicit type). Audit fix: null `currentDialog` on dismiss (P3 retention).

---

## Objective

Add a "Local folder" first option to both the sender and receiver pickers of the scheduled-operation dialog. Selecting it opens the system SAF `OpenDocumentTree()` picker (hosted by the settings Fragment, not the dialog). On Save the chosen folder becomes the operation's FK: reuse a matching VISIBLE local resource if the path already exists, otherwise persist a new HIDDEN local resource. Receiver folders must be writable; sender folders may be read-only (operation forced to COPY).

---

## Prerequisites

- [ ] Phase 01 ✅ (`isHidden` exists) and Phase 02 ✅ (hidden resources are filtered from the dropdowns, so a created hidden resource stays invisible).
- [ ] Reused strings already exist (no new strings): `R.string.local_folder` (EN/RU/UK) and `R.string.error_folder_not_writable` (EN/RU/UK) - verified present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsScheduledManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveLocalFolderResourceUseCase.kt` | New | ≤ 120 |

> No new repository method: dedup reuses the existing `ResourceRepository.getLocalResourceByPath(path)` and creation reuses the existing FTS-safe `ResourceRepository.addResource(resource): Long` (which routes through the transactional `ResourceDao.insert` = `insertResource` + `insertFts`). No layout edit: the picked folder path is shown in the existing `actvSource` / `actvTarget` `AutoCompleteTextView` text, so no `res/layout` / `res/layout-land` change and no landscape-parity step. `AddResourceVirtualCoordinator` is `internal` in a different package and stages through `AddResourceBridge` - do NOT reuse the class; mirror only its LOCAL `MediaResource` field-population pattern (strategic ADR §9).

---

## Steps

### Step 03.1 - Host the SAF folder picker in the settings Fragment

**Files:** `ui/settings/fragments/OperationsSettingsFragment.kt`, `ui/settings/helpers/OperationsScheduledManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `ScheduledOperationDialog` has no ActivityResult host; the Fragment does. In `OperationsSettingsFragment`, register `folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> scheduledManager.onFolderPicked(uri) }` at field-init time (mirror the existing `notificationsPermissionLauncher` pattern and its "Must be created at field-init time" comment; contract precedent: `AddResourceActivity.folderPickerLauncher`). Pass the launcher into `OperationsScheduledManager` (new ctor param) and add `OperationsScheduledManager.onFolderPicked(uri: Uri?)` plus a `pendingPickSide: SchedOpPickSide` field (SOURCE / TARGET) so the result routes back to the side that requested it. `onFolderPicked` calls `takePersistableUriPermission(uri, READ|WRITE)` before use.

**Verification:**

- `Grep` - `ActivityResultContracts.OpenDocumentTree()` present in `OperationsSettingsFragment.kt`.
- `Grep` - `fun onFolderPicked(` present in `OperationsScheduledManager.kt`.
- `Grep` - `takePersistableUriPermission` present in `OperationsScheduledManager.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Add "Local folder" as the first item in both dropdowns

**Files:** `ui/dialog/ScheduledOperationDialog.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `setupDropdowns()`, prepend `getString(R.string.local_folder)` as index 0 of both the `actvSource` and `actvTarget` adapter lists (before the resource names). When the user selects that item, invoke a callback into the host (`onPickLocalFolder(side)`) that sets `pendingPickSide` and launches the SAF picker via the Fragment launcher. Hold staged state in the dialog: `stagedSourceFolderUri: Uri?` and `stagedTargetFolderUri: Uri?`. When a folder is returned, set the corresponding staged Uri and display its human path/name in the matching `AutoCompleteTextView` text. Keep keyboard/D-pad focus order correct - the new item is a normal dropdown row.

**Verification:**

- `Grep` - `R.string.local_folder` referenced in `ScheduledOperationDialog.kt`.
- `Grep` - `stagedSourceFolderUri` and `stagedTargetFolderUri` declared.
- `Grep` - a callback (e.g. `onPickLocalFolder`) wired from the dropdown item selection.

**Status:** `[x]` done

---

### Step 03.3 - Writability gate: reject non-writable receiver, allow read-only sender

**Files:** `ui/settings/helpers/OperationsScheduledManager.kt`, `ui/dialog/ScheduledOperationDialog.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `onFolderPicked`, after taking permission, check `localMediaScanner.isWritable(path, credentialsId = null)` inside `withTimeout(5000)` (mirror `AddResourceVirtualCoordinator`). If `pendingPickSide == TARGET` and the folder is not writable, show `Toast` with `R.string.error_folder_not_writable` and do NOT stage it. If `pendingPickSide == SOURCE`, always stage; a read-only source is allowed and the dialog's existing `applyReadOnlySourceConstraint` forces the operation to COPY (compute `isReadOnly` from `!isWritable` for the staged source so the constraint fires). Inject `LocalMediaScanner` (or its `isWritable` seam) where the manager can reach it.

**Verification:**

- `Grep` - `isWritable` referenced in `OperationsScheduledManager.kt`.
- `Grep` - `R.string.error_folder_not_writable` referenced (receiver rejection).
- `Grep` - `applyReadOnlySourceConstraint` still invoked for a staged read-only source.

**Status:** `[x]` done

---

### Step 03.4 - Use-case: dedup-or-create a hidden local resource

**Files:** `domain/usecase/ResolveLocalFolderResourceUseCase.kt` (New)
**Depends on:** - independent of 03.1-03.3 (foundation for 03.5); consumes existing repo methods + `isHidden` from Phase 01

**Prompt for developer:**

> Create `ResolveLocalFolderResourceUseCase` (`@Inject constructor(resourceRepository)`). `suspend operator fun invoke(folderPath: String, folderName: String, isWritable: Boolean): Long`: (1) dedup - call the existing `resourceRepository.getLocalResourceByPath(folderPath)` (backed by the DAO `WHERE type='LOCAL' AND path=:path` query). Reuse its id ONLY when the match is VISIBLE (`!it.isHidden`) - owner dedup reuses a visible resource as the FK target. A match that is itself HIDDEN must be treated as no-match (it belongs 1:1 to another operation; reusing it would break orphan-cleanup) - fall through to create. (2) no visible match - build a LOCAL `MediaResource` mirroring `AddResourceVirtualCoordinator.addManualFolder`'s field population, but with `isHidden = true`, and persist it via the existing `resourceRepository.addResource(resource): Long` - which already routes through the FTS-safe transactional `ResourceDao.insert` (`insertResource` + `insertFts`), so NO new repository method is needed and no FTS row is skipped (contrast the `deleteResource` bug, [[S1159]]). `addResource` returns the new id.

**Verification:**

- `Glob` - `ResolveLocalFolderResourceUseCase.kt` exists.
- `Grep` - `getLocalResourceByPath` referenced in the use-case (dedup).
- `Grep` - the dedup reuse is guarded on `!` `isHidden` (visible-only reuse).
- `Grep` - `addResource(` referenced in the use-case (create) with `isHidden = true` on the constructed resource.

**Status:** `[x]` done

---

### Step 03.5 - Resolve staged folders on Save; preserve an existing hidden FK on edit

**Files:** `ui/dialog/ScheduledOperationDialog.kt`, `ui/settings/helpers/OperationsScheduledManager.kt`, `ui/settings/ScheduledOperationsViewModel.kt`
**Depends on:** Step 03.2, Step 03.3, Step 03.4

**Prompt for developer:**

> Save flow: `ScheduledOperationDialog.trySave()` must carry the staged source/target Uris out with the draft (extend the `onSave` payload, e.g. pass `stagedSourceFolderUri` / `stagedTargetFolderUri` alongside the `ScheduledOperation`). In `ScheduledOperationsViewModel`, inject `ResolveLocalFolderResourceUseCase`; before upserting, for each side with a staged folder resolve it to an id via the use-case and set the FK (`sourceResourceId` / `targetResourceId`). EDIT case: when `OperationsScheduledManager.showScheduledOperationDialog(existing)` opens an operation whose `sourceResourceId`/`targetResourceId` points to a HIDDEN resource, resolve that resource by id (unfiltered `getResourceById`), pre-stage it as the selected local folder (display its path), and if the user does not re-pick, keep the existing id on Save - never lose it via the visible-list `indexOfFirst` lookup (the hidden row is not in the filtered dropdown list from Phase 02).

**Verification:**

- `Grep` - `ResolveLocalFolderResourceUseCase` injected/used in `ScheduledOperationsViewModel.kt`.
- `Grep` - `trySave` (or `onSave` payload) carries the staged folder Uri(s).
- `Grep` - the edit path resolves an existing hidden FK via `getResourceById` and pre-stages it.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] New use-case wired through Hilt (`@Inject` constructor, no new `@Module` needed - constructor injection); reuses existing `getLocalResourceByPath` + `addResource`, adds no repository method.
- [ ] Dev log entry added for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `ResolveLocalFolderResourceUseCase`).
- [ ] Phase-boundary audit - verify: receiver rejects non-writable folder; read-only sender forces COPY; Save reuses a visible match and otherwise creates exactly one hidden resource; editing an op with a hidden FK preserves its id.

---

## Handoff Notes to Next Phase

An operation can now reference a hidden local resource created on Save. Each hidden resource is strictly 1:1 with the operation FK that created it (dedup only ever reuses VISIBLE resources, never hidden ones). Phase 04 must delete that hidden resource when the operation is deleted, when its source/target is re-pointed to a different resource, and when all operations are cleared - but only when the row is `isHidden` (a reused visible resource is never deleted).

---

## Rollback Plan

Revert the phase commit(s). The new use-case, repo method, and dialog option are additive; a rolled-back build simply lacks the "Local folder" option. Any hidden resource already persisted stays FK-valid and invisible; it becomes an orphan only if Phase 04 is also absent - acceptable for a rollback window.
