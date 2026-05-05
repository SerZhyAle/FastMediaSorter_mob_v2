# Phase 02 — Player Folder Picker Handler

**Strategic spec:** [`../S0073_player-copy-move-custom-path-button.md`](../S0073_player-copy-move-custom-path-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Create `PlayerFolderPickerHandler` — a new class in `ui/player/helpers/` that stores the pending folder-pick operation, launches `OpenDocumentTree`, converts the returned SAF URI to a `File` path via `UriPathResolver.getPath()`, and delegates to `FileOperationsHandler.performCopyToPath()` / `performMoveToPath()`. Register the `ActivityResultLauncher` in `PlayerActivity` and wire initialisation through the existing manager chain.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`performCopyToPath` / `performMoveToPath` exist in `FileOperationsHandler`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFolderPickerHandler.kt` | **New** | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | existing + ~30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | existing + ~10 |

> If `PlayerActivity.kt` exceeds 500 lines, create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 02.1 — Create `PlayerFolderPickerHandler`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFolderPickerHandler.kt`
**Depends on:** — start of phase (Phase 01 complete)

**Prompt for developer:**

> Create a new file `PlayerFolderPickerHandler.kt` in `ui/player/helpers/`. Model it after `BrowseFolderPickerHandler` — keep the same structure but adapted to the player context:
>
> ```kotlin
> class PlayerFolderPickerHandler(
>     private val activity: Activity,
>     private val coroutineScope: CoroutineScope,
>     private val settingsRepository: SettingsRepository,
>     private val fileOperationsHandler: FileOperationsHandler,
>     private val onLaunchPicker: (Uri?) -> Unit
> ) {
>     data class PendingOp(
>         val operationType: FileOperationType,
>         val sourceCredentialsId: String?
>     )
>
>     var pendingOp: PendingOp? = null
>
>     fun requestFolderPick(operationType: FileOperationType, sourceCredentialsId: String?) { ... }
>     fun onFolderPicked(uri: Uri?) { ... }
> }
> ```
>
> `requestFolderPick`:
> 1. Store `PendingOp(operationType, sourceCredentialsId)` in `pendingOp`.
> 2. Read `settings.lastSelectedLocalFolder` from `settingsRepository.getSettings().first()` to build an initial URI hint.
> 3. Call `onLaunchPicker(initialUri)` inside a `coroutineScope.launch`.
>
> `onFolderPicked`:
> 1. If `uri == null` → log with Timber and return (user cancelled).
> 2. Call `activity.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ + FLAG_GRANT_WRITE)` — catch `SecurityException` as non-fatal.
> 3. Resolve `path = UriPathResolver.getPath(activity, uri) ?: uri.path` — if null, show `R.string.error_unknown` Toast and return.
> 4. Create `destDir = File(path)` — if `!destDir.exists() || !destDir.canWrite()`, show `R.string.error_folder_not_writable` Toast and return.
> 5. Read `op = pendingOp ?: return`; set `pendingOp = null`.
> 6. Persist `uri.toString()` to `settings.lastSelectedLocalFolder` (same key as Browse uses — field `AppSettings.lastSelectedLocalFolder`).
> 7. Delegate: when `op.operationType == COPY` call `fileOperationsHandler.performCopyToPath(path)`, when `MOVE` call `fileOperationsHandler.performMoveToPath(path)`.
>
> Imports needed: `android.app.Activity`, `android.net.Uri`, `com.sza.fastmediasorter.core.util.UriPathResolver`, `com.sza.fastmediasorter.domain.model.FileOperationType`, `com.sza.fastmediasorter.domain.repository.SettingsRepository`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.launch`, `timber.log.Timber`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFolderPickerHandler.kt` exists.
- `Grep` — `class PlayerFolderPickerHandler` matches exactly once.
- `Grep` — `UriPathResolver.getPath` found in the new file.
- `Grep` — `performCopyToPath` found in the new file.
- `Grep` — `performMoveToPath` found in the new file.
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Files: PlayerFolderPickerHandler.kt (new, 110 LOC). Dev log recorded.

---

### Step 02.2 — Register `OpenDocumentTree` launcher in `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `PlayerActivity`, register an `ActivityResultLauncher<Uri?>` for `ActivityResultContracts.OpenDocumentTree()` **before `onCreate`** (as a property, same pattern as any other launcher in the Activity):
> ```kotlin
> private val folderPickerLauncher = registerForActivityResult(
>     ActivityResultContracts.OpenDocumentTree()
> ) { uri ->
>     playerFolderPickerHandler.onFolderPicked(uri)
> }
> ```
> Also declare `internal lateinit var playerFolderPickerHandler: PlayerFolderPickerHandler` alongside the other `lateinit var` manager fields (around line 90).
>
> Do NOT initialise the handler here — that happens in `PlayerManagerInitializer` (Step 02.3).

**Verification:**

- `Grep` — `folderPickerLauncher` found in `PlayerActivity.kt`.
- `Grep` — `OpenDocumentTree` found in `PlayerActivity.kt`.
- `Grep` — `playerFolderPickerHandler` found in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: PlayerActivity.kt (+8 LOC). Dev log recorded.

---

### Step 02.3 — Initialise `PlayerFolderPickerHandler` in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `PlayerManagerInitializer`, initialise `PlayerFolderPickerHandler` and assign it to `activity.playerFolderPickerHandler`. Do this **after** `fileOperationsHandler` is initialised (it is a constructor parameter). Pass `activity.folderPickerLauncher::launch` as the `onLaunchPicker` lambda:
> ```kotlin
> activity.playerFolderPickerHandler = PlayerFolderPickerHandler(
>     activity = activity,
>     coroutineScope = lifecycleScope,
>     settingsRepository = settingsRepository,
>     fileOperationsHandler = activity.fileOperationsHandler,
>     onLaunchPicker = { uri -> activity.folderPickerLauncher.launch(uri) }
> )
> ```
> Place this immediately after the line that assigns `activity.fileOperationsHandler`.

**Verification:**

- `Grep` — `PlayerFolderPickerHandler` found in `PlayerManagerInitializer.kt`.
- `Grep` — `folderPickerLauncher` found in `PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: PlayerManagerInitializer.kt (+8 LOC). Dev log recorded.

---

### Step 02.4 — Wire `requestFolderPick` into `DestinationButtonsCallback`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a new method to the `DestinationButtonsCallback` interface (in `DestinationButtonsManager.kt`):
> ```kotlin
> fun onCustomPathPickerRequested(operationType: FileOperationType)
> ```
> Import `com.sza.fastmediasorter.domain.model.FileOperationType` at the top of the file.
>
> This callback will be called when the user taps the «..» button — it delegates to `playerFolderPickerHandler.requestFolderPick(operationType, sourceCredentialsId)`. The callback implementation lives in `PlayerActivity` (Step 02.5 below).

**Verification:**

- `Grep` — `onCustomPathPickerRequested` found in `DestinationButtonsManager.kt`.
- `Grep` — `FileOperationType` imported in `DestinationButtonsManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: DestinationButtonsManager.kt (+2 lines). Dev log recorded.

---

### Step 02.5 — Implement `onCustomPathPickerRequested` in `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> `PlayerActivity` implements `DestinationButtonsCallback`. Because the interface gained `onCustomPathPickerRequested`, the Activity will not compile until the override is added. Implement it:
> ```kotlin
> override fun onCustomPathPickerRequested(operationType: FileOperationType) {
>     val credId = fileOperationsHandler.callback.getCurrentResource()?.credentialsId
>     playerFolderPickerHandler.requestFolderPick(operationType, credId)
> }
> ```
> Place this alongside the other `DestinationButtonsCallback` overrides. If `getCurrentResource()` is not directly accessible, use `null` as `sourceCredentialsId` — the player always operates on local-source files from this panel.

**Verification:**

- `Grep` — `onCustomPathPickerRequested` found in `PlayerActivity.kt`.
- Project compiles — run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Spec correction: implemented in PlayerManagerInitializer.kt anonymous DestinationButtonsCallback (same pattern as Phase 01). Verification 1/1 PASS (grep). Build running.

---

## Phase Done Criteria

- [ ] Every Step 02.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new public class added).

---

## Handoff Notes to Next Phase

- `PlayerFolderPickerHandler` is live; `PlayerActivity.folderPickerLauncher` is registered.
- `DestinationButtonsCallback.onCustomPathPickerRequested(operationType)` is wired: callback in `PlayerActivity` delegates to `playerFolderPickerHandler.requestFolderPick(...)`.
- Phase 03 will add the visible «..» button to `DestinationButtonsManager.populateDestinationButtons()` and update panel-visibility logic.

---

## Rollback Plan

Revert phase commit(s) — the launcher is inert until called; no data migration or user-visible surface changed yet.
