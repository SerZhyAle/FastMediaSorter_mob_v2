# Phase 06 — Save flow with rename + conflict suffix + network upload

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 03, Phase 05
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Wire the actual save-commit. On Save / Save & Close / Save & Send the user gets a single dialog to confirm or change the filename (default = current filename, or `yy-MM-dd_HH-mm.txt` if the file is freshly-staged with that default and has not been renamed yet). On commit: if the filename collides with an existing file at the target destination, the conflict resolver silently appends `-ss` and writes the new file. For network-staged notes (Phase 03), commit triggers the upload of the local staging copy to the remote resource; on success the local copy is deleted, on failure the local copy stays and the user sees an error message.

---

## Prerequisites

- [ ] Phase 03 Done (staging registry available).
- [ ] Phase 05 Done (`onSave()` callback exists in `TextEditorActionPanelManager`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveTextNoteUseCase.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextNoteSaveDialog.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorSaveFlow.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | wire into `onSave*` callbacks |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +10 |

---

## Steps

### Step 06.1 — Add strings (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add keys, apply COMMUNICATION_POLICY §6 tone checklist:
>
> English:
> - `text_note_save_dialog_title` → `Save note`
> - `text_note_save_dialog_hint` → `File name`
> - `text_note_save_dialog_ok` → `Save`
> - `text_note_save_renamed_suffix` → `Saved as %1$s (name was taken)`
> - `text_note_save_error_network` → `Couldn't reach the folder. Your note stayed on the device — try again when you're back online.`
> - `text_note_save_success_local` → `Saved.`
> - `text_note_save_success_remote` → `Saved to %1$s.`
>
> Russian:
> - `text_note_save_dialog_title` → `Сохранить заметку`
> - `text_note_save_dialog_hint` → `Имя файла`
> - `text_note_save_dialog_ok` → `Сохранить`
> - `text_note_save_renamed_suffix` → `Сохранено как %1$s (имя было занято)`
> - `text_note_save_error_network` → `Не удалось добраться до папки. Заметка осталась на устройстве — повторим, когда снова будет связь.`
> - `text_note_save_success_local` → `Сохранено.`
> - `text_note_save_success_remote` → `Сохранено в %1$s.`
>
> Ukrainian:
> - `text_note_save_dialog_title` → `Зберегти нотатку`
> - `text_note_save_dialog_hint` → `Імʼя файлу`
> - `text_note_save_dialog_ok` → `Зберегти`
> - `text_note_save_renamed_suffix` → `Збережено як %1$s (імʼя було зайняте)`
> - `text_note_save_error_network` → `Не вийшло достукатися до теки. Нотатка залишилась на пристрої — повторимо, коли зʼявиться звʼязок.`
> - `text_note_save_success_local` → `Збережено.`
> - `text_note_save_success_remote` → `Збережено в %1$s.`

**Verification:**

- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "text_note_save_"` → exit 0.
- Tone checklist pass.

**Status:** `[ ]` not done

---

### Step 06.2 — Add `SaveTextNoteUseCase` (uses existing `FileOperationUseCase` / `FileOperation.Copy`)

**Files:** `SaveTextNoteUseCase.kt`, `UnifiedFileOperationHandler.kt`

**Prompt for developer:**

> Architecture note: do NOT introduce a new `uploadFile()` method on `UnifiedFileOperationHandler` or a new `exists()` per-strategy method. Instead, reuse the existing `FileOperation.Copy` pathway (consumed by `FileOperationUseCase` and `BrowseShareOperationsHelper`) and the `network-aware File` wrapper pattern from `BrowseShareOperationsHelper.createNetworkAwareFile`. This keeps Phase 06 strictly additive on the data side.
>
> 1. Create `SaveTextNoteUseCase`. `@Inject constructor` with:
>    - `private val fileOperationUseCase: FileOperationUseCase`
>    - `private val stagingRegistry: TextNoteStagingRegistry`
>    (Conflict resolution and name util are referenced as objects: `TextNoteNameConflictResolver`.)
>    Data class `SaveOutcome(val finalName: String, val renamedDueToConflict: Boolean, val isLocalSaveOnly: Boolean, val remoteFailureMessage: String? = null, val finalPath: String)`.
>    Method `suspend operator fun invoke(currentLocalFile: java.io.File, intendedName: String, content: String): Result<SaveOutcome>`. Algorithm:
>    1. Resolve via `stagingRegistry.lookup(currentLocalFile)`. If absent → this is a pure-LOCAL save (currentLocalFile is the real target). Otherwise it's a network-staged save.
>    2. Compute `finalName = intendedName`. If a sibling file with `finalName` already exists at the target — call `TextNoteNameConflictResolver.applySecondsSuffix(intendedName)` and re-check. Record `renamedDueToConflict = true` if the name changed.
>       - LOCAL existence check: `File(targetParent, finalName).exists()`.
>       - Network/cloud existence check: reuse the existing list-directory of the target resource via `FileOperationUseCase` (call `FileOperation.List(parentPath, resourceType)` or whichever existing list operation already exists; if no such list op exists, OMIT the network pre-check and rely on the remote write to fail-and-fallback — record the chosen branch in the implementation log).
>    3. Write the file:
>       - **LOCAL only:** open `File(targetParent, finalName)` and `writeText(content, Charsets.UTF_8)`. If `currentLocalFile.name != finalName`, delete `currentLocalFile`.
>       - **Network-staged:** first rewrite the local staging file in `Downloads/FastMediaSorter/notes/` with the new content and (if renamed) new name; build a `FileOperation.Copy` with:
>         - `sources = listOf(stagedLocalFileAsCopySource)` — wrap in the network-aware `File` if needed.
>         - `destination = File(staged.targetParentPath)`  // protocol-specific path; same convention as `BrowseShareOperationsHelper`.
>         - `overwrite = false` (collision was already resolved in step 2).
>         - `sourceCredentialsId = null` (local source).
>         - `destinationCredentialsId = <staged resource's credentials id, looked up via repository if available>`.
>         Then `fileOperationUseCase.execute(copyOp)`. On `FileOperationResult.Success`: delete the local staging file, `stagingRegistry.unregister(currentLocalFile)`. On `Failure` / `AuthenticationRequired` / other: keep the local file, set `remoteFailureMessage = result.toString()`, set `isLocalSaveOnly = true`.
>    4. Return `Result.success(SaveOutcome(finalName, renamedDueToConflict, isLocalSaveOnly, remoteFailureMessage, finalPath))`.
> 2. Do NOT modify `UnifiedFileOperationHandler` or `FileOperationStrategy` in this step. (The `createTextFile` method added in Phase 01/03 stays; no new methods.)
> 3. Add `Timber.d("S0189: SaveTextNoteUseCase outcome=$outcome")` after the result is built.

**Verification:**

- Glob — `SaveTextNoteUseCase.kt` exists.
- Grep — `class SaveTextNoteUseCase @Inject constructor` matches once.
- Grep — `suspend operator fun invoke` matches once in `SaveTextNoteUseCase.kt`.
- Grep — `data class SaveOutcome` matches once.
- Grep — `fileOperationUseCase.execute(` matches at least once in `SaveTextNoteUseCase.kt`.
- Grep — `FileOperation.Copy(` matches at least once in `SaveTextNoteUseCase.kt`.
- Grep — `fun uploadFile(` returns ZERO hits across `UnifiedFileOperationHandler.kt` and `data/transfer/strategy/` (proves no new method was added).
- Grep — `Timber.d("S0189: SaveTextNoteUseCase` present.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 06.3 — Add `TextNoteSaveDialog`

**Files:** `TextNoteSaveDialog.kt`

**Prompt for developer:**

> Create `object TextNoteSaveDialog` with `fun show(context: Context, defaultName: String, onConfirm: (chosenName: String) -> Unit, onCancel: () -> Unit = {})`. Build a Material dialog with `TextInputLayout` + `TextInputEditText`. Pre-fill the field with `defaultName`. Allow the user to change name AND extension (do NOT auto-protect the dot). Validate that the trimmed name is not empty and does not contain `/ \ : * ? " < > |`. Disable OK button on invalid input. On OK → `onConfirm(text.toString().trim())`. On Cancel → `onCancel()`. Add `Timber.d("S0189: TextNoteSaveDialog.show default=$defaultName")`.

**Verification:**

- Glob — `TextNoteSaveDialog.kt` exists.
- Grep — `object TextNoteSaveDialog` matches once.
- Grep — `fun show(context: Context, defaultName: String` matches once.

**Status:** `[ ]` not done

---

### Step 06.4 — Add `TextEditorSaveFlow` and wire into `TextViewerManager` callbacks

**Files:** `TextEditorSaveFlow.kt`, `TextViewerManager.kt`

**Prompt for developer:**

> 1. Create `TextEditorSaveFlow` orchestrator (Hilt-injected). Constructor: `context: Context`, `saveTextNoteUseCase: SaveTextNoteUseCase`, `scope: CoroutineScope`, `ioDispatcher: CoroutineDispatcher`.
>    Public method `fun commit(currentLocalFile: File, currentName: String, currentContent: String, afterSave: (SaveOutcome) -> Unit, onCancel: () -> Unit)`. Flow:
>    1. `TextNoteSaveDialog.show(context, currentName, onConfirm = { chosen -> launch save }, onCancel)`.
>    2. On confirm — launch coroutine on `ioDispatcher`; call `saveTextNoteUseCase(currentLocalFile, chosen, currentContent)`.
>    3. On Main: build user-facing toast:
>       - LOCAL success: `R.string.text_note_save_success_local` (no name) OR include name if `renamedDueToConflict`.
>       - Remote success: `R.string.text_note_save_success_remote` with destination.
>       - Network failure: `R.string.text_note_save_error_network`.
>       - Renamed-due-to-conflict additional toast: `R.string.text_note_save_renamed_suffix` with the new name.
>    4. Invoke `afterSave(outcome)` only on success.
>    Add `Timber.d("S0189: TextEditorSaveFlow.commit current=$currentName chosen=...")` and `Timber.d("S0189: TextEditorSaveFlow.outcome $outcome")`.
> 2. In `TextViewerManager`: replace the Phase 05 stub `onSave()` with `saveFlow.commit(currentLocalFile, currentFileName, currentText, afterSave = { outcome -> /* refresh UI, update title bar */ }, onCancel = { /* no-op */ })`. Hook the same call into `onSaveAndClose()` (use `afterSave` to then `exitEditMode()` + close viewer) and `onSaveAndSend()` (use `afterSave` to then launch share sheet with the saved file path).

**Verification:**

- Glob — `TextEditorSaveFlow.kt` exists.
- Grep — `class TextEditorSaveFlow @Inject constructor` matches once.
- Grep — `saveTextNoteUseCase(` referenced in `TextEditorSaveFlow.kt`.
- Grep — `saveFlow.commit(` matches at least three times in `TextViewerManager.kt` (onSave, onSaveAndClose, onSaveAndSend).
- Build: `assembleStandardDebug` compiles.
- Manual smoke:
   - LOCAL: edit → Save → dialog appears with current name → confirm → toast "Сохранено." → file present, content correct.
   - LOCAL conflict: same name twice → second save toast `Сохранено как …-NN.txt (имя было занято)`.
   - SMB or SFTP (if available): edit → Save → toast `Сохранено в …` → file present on remote, local staging removed.
   - SMB/SFTP offline: simulate by disconnecting Wi-Fi → toast `Не удалось добраться до папки..` → file still present locally in Downloads.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes.
- [ ] Strings audit (`text_note_save_` prefix) clean.
- [ ] All four manual smoke scenarios passed and recorded.
- [ ] `add_to_dev_log.ps1` invoked for each touched file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2`.

---

## Handoff Notes to Next Phase

- Save flow is fully operational. Phase 07 layers the auto-fit font behaviour on top — independent of save logic.

---

## Rollback Plan

- Revert this phase. The Phase 05 stub `onSave()` (which only logs and exits) becomes the active behaviour again. No data loss — staged files remain in Downloads.
