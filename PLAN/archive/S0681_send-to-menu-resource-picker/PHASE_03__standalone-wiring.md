# Phase 03 - Standalone wiring

**Strategic spec:** [`../S0681_send-to-menu-resource-picker.md`](../S0681_send-to-menu-resource-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Make the pinned «Select resource..» entry appear and work in every standalone player by giving the shared standalone file-ops handler a copy-to-resource dialog entry point and passing `onPickResource` from its send-to call sites.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1095 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 630 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ 770 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1095 |

> All six files exceed 500 LOC - Step 03.1 backs each up to `temp/` before editing (CLAUDE.md Rule 5).
> Standalone hosts share `StandaloneFileOperationsHandler`. The four typed activities already inject `fileOperationUseCase` + `getDestinationsUseCase` and own a SAF `customPathPickerLauncher` for the «..» path. The unified host `StandalonePlayerActivity` (5th construction site, found during execution) had `fileOperationUseCase` but no `getDestinationsUseCase`/SAF picker - Step 03.4 adds both so it gets the entry too.

---

## Steps

### Step 03.1 - Back up the >500-LOC files

**Files:** the five files in "Files Touched"
**Depends on:** - start of phase

**Prompt for developer:**

> Copy each of the five files to `temp/` with a timestamp suffix before editing (CLAUDE.md Rule 5). Example: `pwsh -NoProfile -Command "$ts=Get-Date -Format yyyyMMdd_HHmmss; Copy-Item <file> temp/<name>.$ts.bak"` for each file.

**Verification:**

- `Glob` - `temp/StandaloneFileOperationsHandler.kt.*.bak` exists.
- `Glob` - `temp/*StandaloneActivity.kt.*.bak` returns four entries.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - PASS. Backed up handler + 4 typed activities (and later `StandalonePlayerActivity`) to `temp/*.bak`.

---

### Step 03.2 - Add copy-to-resource dialog entry point to the shared handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend the constructor with `getDestinationsUseCase: GetDestinationsUseCase` and `onPickCustomFolderForCopy: () -> Unit` (the host launches its SAF tree picker for the «..» path). Add `fun showCopyDialog()` that builds a `FileOperationDestinationDialog` in `FileOperationType.COPY` for the current file from `getCurrentMediaFile()`: `sourceFiles = listOf(createNetworkAwareFile(file.path, file.name))`, `currentResourceId = -1L` (no resource context in standalone - full recipient list per strategic §6.2), `sourceFolderName =` the current file's parent directory name derived from `file.path` (no new string key needed), `sourceCredentialsId = null`, `currentBrowsePath =` the file's parent path, `fileOperationUseCase = fileOperationUseCase` (guard: if null, log `Timber.w` and return), `getDestinationsUseCase = getDestinationsUseCase`, `overwriteFiles = settings.overwriteOnCopy`, `showDetailedErrors = settings.showDetailedErrors`, `onComplete = { /* copy keeps the viewer open - no finish() */ }`, `onSelectFolderClicked = { _, _, _ -> onPickCustomFolderForCopy() }`. Load `settings` via the existing `getCurrentSettings()`; show the dialog on the main thread. Pass `onPickResource = { showCopyDialog() }` to BOTH existing `sendToMenuManager.show(activity, content, settings)` call sites in this file. Do not reimplement the copy itself - the dialog runs `FileOperationUseCase` internally; the «..» path reuses the activity's `customPathPickerLauncher` -> `copyCurrentFileToPath`.

> Execution note: new handler params are nullable-defaulted (`getDestinationsUseCase: GetDestinationsUseCase? = null`, `onPickCustomFolderForCopy: () -> Unit = {}`) because a 5th construction site (`StandalonePlayerActivity`) exists; `shareCurrentFile()` gates `onPickResource` on `fileOperationUseCase != null && getDestinationsUseCase != null` so an unwired host shows no dead entry. The pinned entry is wired only into the unified `shareCurrentFile()`, not the niche `shareLocalCopy()` crop-and-share path (which shares a derived bitmap, not the current file).

**Verification:**

- `Grep` - `fun showCopyDialog` matches once in `StandaloneFileOperationsHandler.kt`.
- `Grep` - `getDestinationsUseCase` matches in `StandaloneFileOperationsHandler.kt` (constructor + dialog).
- `Grep` - `onPickCustomFolderForCopy` matches in `StandaloneFileOperationsHandler.kt`.
- `Grep` - `{ showCopyDialog() }` matches once in `StandaloneFileOperationsHandler.kt` (gated in `shareCurrentFile`).
- `Grep -n "Log\.d\("` returns zero hits in `StandaloneFileOperationsHandler.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 5/5 PASS. Added FileOperationType/GetDestinationsUseCase/FileOperationDestinationDialog imports, two nullable constructor params, `showCopyDialog()` (COPY, resourceId -1, «..» -> `onPickCustomFolderForCopy`), gated `onPickResource` in `shareCurrentFile`.

---

### Step 03.3 - Wire the four standalone activities to the new handler params

**Files:** `PhotoVideoStandaloneActivity.kt`, `AudioStandaloneActivity.kt`, `TextStandaloneActivity.kt`, `DocumentStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In each activity's `StandaloneFileOperationsHandler(..)` construction (the call that already passes `sendToMenuManager = ..` and `fileOperationUseCase = ..`), add `getDestinationsUseCase = getDestinationsUseCase` (already injected in each activity) and `onPickCustomFolderForCopy = { pendingCustomPathOp = com.sza.fastmediasorter.domain.model.FileOperationType.COPY; customPathPickerLauncher.launch(null) }` (reuses each activity's existing custom-path launcher + pending-op field). Do not introduce a new launcher.

**Verification:**

- `Grep` - `onPickCustomFolderForCopy =` matches once in each of the four `*StandaloneActivity.kt` files (unique new token proving the handler construction was extended).
- `.\a.ps1 fc` compiles - confirms each construction passes the new args.
- `Grep -n "Log\.d\("` returns zero NEW hits introduced by this step in the four files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS. PhotoVideo/Audio/Text/Document each pass `getDestinationsUseCase` + `onPickCustomFolderForCopy` (sets `pendingCustomPathOp = FileOperationType.COPY`, launches existing `customPathPickerLauncher`).

---

### Step 03.4 - Wire the unified standalone host (`StandalonePlayerActivity`)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> The unified standalone player (`ActivityPlayerUnifiedBinding`) is a 5th independent host that constructs the handler but lacked the copy-to-resource wiring. Inject `getDestinationsUseCase`, add a `customPathPickerLauncher` (`OpenDocumentTree`) + `pendingCopyToCustomFolder` flag whose result calls `fileOperations.copyCurrentFileToPath(uri, label)`, and pass `getDestinationsUseCase` + `onPickCustomFolderForCopy = { pendingCopyToCustomFolder = true; customPathPickerLauncher.launch(null) }` into the handler construction.

**Verification:**

- `Grep` - `getDestinationsUseCase` matches in `StandalonePlayerActivity.kt`.
- `Grep` - `customPathPickerLauncher` matches in `StandalonePlayerActivity.kt`.
- `Grep` - `onPickCustomFolderForCopy =` matches once in `StandalonePlayerActivity.kt`.
- `.\a.ps1 fc` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 4/4 PASS. Injected `getDestinationsUseCase`, added SAF `customPathPickerLauncher` + `pendingCopyToCustomFolder`, passed both new handler args. `.\a.ps1 fc` BUILD SUCCESSFUL (37s), neuroslop delta 0.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the changed files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (handler public surface changed) - done in Phase 04.

---

## Handoff Notes to Next Phase

Every standalone player now shows the pinned «Select resource..» entry and opens a COPY recipient dialog (recipient buttons + «..») for its current file. Public constructor of `StandaloneFileOperationsHandler` changed - catalog regen due in Phase 04.

---

## Rollback Plan

Revert phase commit(s) and restore from the `temp/` backups if needed - additive constructor params + one new method; no data migration or default behavior change.
