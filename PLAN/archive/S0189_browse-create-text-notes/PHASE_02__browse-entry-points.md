# Phase 02 — Browse entry points (toolbar + overflow + keyboard)

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 7
**Started:** —
**Completed:** —

---

## Objective

Surface the create-text-note action in three places — toolbar button, resource-ops overflow popup, keyboard shortcut — and route it through a new `BrowseTextNoteCreateManager` into `CreateTextNoteUseCase`. Visibility predicate mirrors the existing folder predicate but is more permissive (no `showSubfoldersAsItems` requirement). No editor changes yet; success of this phase = a `.txt` file actually appears in the current LOCAL path after the user confirms the dialog.

---

## Prerequisites

- [ ] Phase 01 Done.
- [ ] EN/RU/UK string files are in clean state (no stray duplicate keys).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/menu_resource_ops.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +20 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +20 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +20 lines |
| `app_v2/src/main/res/layout/activity_browse.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/drawable/ic_create_text_file.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseTextNoteCreateManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 1100 (backup mandatory if existing >500) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/KeyboardNavigationManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt` | Modified | ≤ 150 |
| `app_v2/src/main/assets/input/default_bindings.json` | Modified | ≤ +10 lines |

---

## Steps

### Step 02.1 — Add strings (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add the following keys to all three files (English source first, then RU + UK translations following the `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist). Maintain alphabetical ordering inside each `strings.xml` is NOT required — append at the bottom of the same section as `action_create_folder`. Keys to add (English values):
>
> - `action_create_text_file` → `Create text note`
> - `create_text_file_title` → `New text note`
> - `create_text_file_hint` → `File name`
> - `error_text_note_create_failed` → `Couldn't create the note here. Please try a different folder.`
> - `msg_text_note_created` → `Note created: %1$s`
> - `error_invalid_text_note_name` → `Use letters, digits, dashes; avoid / \ : * ? \" &lt; &gt; |`
>
> Russian — use `ё`/`Ё` where grammatically correct and `..` not `...`:
>
> - `action_create_text_file` → `Создать заметку`
> - `create_text_file_title` → `Новая заметка`
> - `create_text_file_hint` → `Имя файла`
> - `error_text_note_create_failed` → `Не получилось создать заметку здесь. Попробуйте другую папку.`
> - `msg_text_note_created` → `Заметка создана: %1$s`
> - `error_invalid_text_note_name` → `Используйте буквы, цифры, дефис; без / \ : * ? \" &lt; &gt; |`
>
> Ukrainian:
>
> - `action_create_text_file` → `Створити нотатку`
> - `create_text_file_title` → `Нова нотатка`
> - `create_text_file_hint` → `Імʼя файлу`
> - `error_text_note_create_failed` → `Не вдалося створити нотатку тут. Спробуйте іншу теку.`
> - `msg_text_note_created` → `Нотатку створено: %1$s`
> - `error_invalid_text_note_name` → `Літери, цифри, дефіс; без / \ : * ? \" &lt; &gt; |`
>
> Apply communication-policy tone checklist before commit.

**Verification:**

- Grep — each of 6 keys present exactly once in each of the 3 files (18 hits total).
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "action_create_text_file"` → exit 0.
- Run the same for `create_text_file_title`, `error_text_note_create_failed`, `msg_text_note_created`, `error_invalid_text_note_name` → all exit 0.
- Manual: strings pass COMMUNICATION_POLICY §6 checklist (recorded as `expected: pass | actual: pass`).

**Status:** `[ ]` not done

---

### Step 02.2 — Add menu entry and toolbar drawable

**Files:** `menu_resource_ops.xml`, `drawable/ic_create_text_file.xml`

**Prompt for developer:**

> 1. In `menu_resource_ops.xml`, append an `<item>` after `action_create_folder` (id: `action_create_text_file`, title: `@string/action_create_text_file`).
> 2. Create `app_v2/src/main/res/drawable/ic_create_text_file.xml` as a 24dp vector drawable showing a document-plus glyph (Material `note_add` icon paths or equivalent simple "page + plus" pictogram, white fill on `null` tint). Width/height = 24dp, viewport = 24×24.

**Verification:**

- Grep — `android:id="@+id/action_create_text_file"` matches once in `menu_resource_ops.xml`.
- Glob — `app_v2/src/main/res/drawable/ic_create_text_file.xml` exists.
- Grep — `<vector` and `viewportWidth="24"` present in the new drawable.

**Status:** `[ ]` not done

---

### Step 02.3 — Add `BrowseTextNoteCreateManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseTextNoteCreateManager.kt`

**Prompt for developer:**

> Create `BrowseTextNoteCreateManager` modelled on `BrowseDirectoryOpsManager`. Constructor params:
> - `context: Context`
> - `createTextNoteUseCase: CreateTextNoteUseCase`
> - `scope: CoroutineScope`
> - `ioDispatcher: CoroutineDispatcher`
> - `stateFlow: StateFlow<BrowseState>`
> - `sendEvent: (BrowseEvent) -> Unit`
> - `reloadResource: () -> Unit`
> - `notifyCreatedForOpen: (createdPath: String) -> Unit`  // hook for Phase 04 auto-open
>
> Public method `fun createTextNote(name: String)`: launch on `ioDispatcher`, resolve `resource` and `currentPath` from state, call `createTextNoteUseCase(resource, currentPath, name)`. On `success(path)`: switch to Main, send `BrowseEvent.ShowMessage(getString(R.string.msg_text_note_created, name))`, call `reloadResource()`, then `notifyCreatedForOpen(path)`. On failure: send `BrowseEvent.ShowError(getString(R.string.error_text_note_create_failed))`. Add `Timber.d("S0189: BrowseTextNoteCreateManager.createTextNote name=$name path=$currentPath")` at entry. Keep file ≤ 120 LOC.

**Verification:**

- Glob — `BrowseTextNoteCreateManager.kt` exists.
- Grep — `class BrowseTextNoteCreateManager` matches once.
- Grep — `fun createTextNote(name: String)` matches once.
- Grep — `Timber.d("S0189: BrowseTextNoteCreateManager` present.

**Status:** `[ ]` not done

---

### Step 02.4 — Wire ViewModel event and `BrowseEvent`

**Files:** `BrowseViewModel.kt`, `BrowseEvent.kt`

**Prompt for developer:**

> 1. In `BrowseEvent.kt`, add no new event — re-use existing `ShowMessage`/`ShowError`. (Recorded as expected: 0 new events | actual: 0.)
> 2. In `BrowseViewModel.kt`:
>    - Inject `private val textNoteCreateManager: BrowseTextNoteCreateManager` (constructor parameter — provided via Hilt; the manager is instantiated by `BrowseManagerInitializer`, so VM receives the instance through the manager-initialiser pattern already used for `directoryOpsManager`). If VM does not currently receive `BrowseDirectoryOpsManager` directly, follow the exact same indirection (lateinit + assigned by initialiser) — do not invent a new pattern.
>    - Add public method `fun createTextNote(name: String) { textNoteCreateManager.createTextNote(name) }`.
>    - File projection: if `BrowseViewModel.kt` is already ≥500 LOC, create timestamped backup in `temp/` BEFORE the edit (CLAUDE.md Rule 5).
>    - Add `Timber.d("S0189: BrowseViewModel.createTextNote name=$name")` at the entry of the new method.

**Verification:**

- Grep — `fun createTextNote(name: String)` matches once in `BrowseViewModel.kt`.
- Grep — `textNoteCreateManager.createTextNote(name)` matches once.
- Grep — `Timber.d("S0189: BrowseViewModel.createTextNote` present.
- If `BrowseViewModel.kt` >500 LOC: Glob — `temp/BrowseViewModel.kt.*.bak` exists (any timestamp).

**Status:** `[ ]` not done

---

### Step 02.5 — Wire popup menu, dialog, and overflow visibility

**Files:** `ResourceOpsMenuManager.kt`, `BrowseManagerInitializer.kt`

**Prompt for developer:**

> 1. In `ResourceOpsMenuManager.kt`:
>    - Add visibility logic for `R.id.action_create_text_file`. Predicate `canCreateTextNote`:
>      ```
>      resource != null &&
>          !resource.isReadOnly &&
>          !VirtualPathUtils.isVirtualPath(resource.path) &&
>          resource.supportsDocuments()
>      ```
>      `MediaResource.supportsDocuments()` returns true when `allFiles == true` or `supportedMediaTypes` contains any of `TEXT`/`PDF`/`EPUB`. NO `showSubfoldersAsItems` requirement. Hidden for audio/video/photo-only libraries (refined 2026-05-17 — strategic §6.3).
>    - Add `R.id.action_create_text_file -> { showCreateTextNoteDialog(viewModel); true }` to the `setOnMenuItemClickListener` switch.
>    - Add new method `fun showCreateTextNoteDialog(viewModel: BrowseViewModel)`. Build the dialog with `TextInputLayout` + `TextInputEditText` (mirror `showCreateFolderDialog`). Pre-fill the field with `TextNoteFileNameProvider.defaultName()`. Validate against the same `forbiddenChars` set. Positive button → `viewModel.createTextNote(inputEdit.text.toString().trim())`.
>    - Add `Timber.d("S0189: ResourceOpsMenuManager.showCreateTextNoteDialog default=$default")` at dialog construction.
> 2. In `BrowseManagerInitializer.kt`:
>    - Construct `BrowseTextNoteCreateManager` (mirror how `directoryOpsManager` is built — same constructor injection + scope wiring).
>    - Add a click handler for the toolbar button (see Step 02.6) and for the keyboard action (see Step 02.7) routing through `resourceOpsMenuManager.showCreateTextNoteDialog(viewModel)`.
>    - Expose a `showCreateTextNoteDialog()` callback to keyboard nav, matching the existing `showCreateFolderDialog()` callback at line 322 of the current file.

**Verification:**

- Grep — `R.id.action_create_text_file` matches at least twice in `ResourceOpsMenuManager.kt` (visibility + click handler).
- Grep — `fun showCreateTextNoteDialog` matches once.
- Grep — `TextNoteFileNameProvider.defaultName()` present in dialog code.
- Grep — `showCreateTextNoteDialog` matches in `BrowseManagerInitializer.kt`.

**Status:** `[ ]` not done

---

### Step 02.6 — Add toolbar button (portrait + landscape) and wire setup

**Files:** `res/layout/activity_browse.xml`, `res/layout-land/activity_browse.xml`, `BrowseButtonSetupHelper.kt`, `BrowseStateUiUpdater.kt`

**Prompt for developer:**

> 1. In **both** `res/layout/activity_browse.xml` and `res/layout-land/activity_browse.xml`: add an `ImageButton` or `MaterialButton` with `android:id="@+id/btnCreateTextFile"`, `android:icon="@drawable/ic_create_text_file"` (or `android:src=` for ImageButton), `android:contentDescription="@string/action_create_text_file"`, placed in the toolbar row directly AFTER `btnCreateFolder`. Use exactly the same widget type, dimensions, padding, and tint as `btnCreateFolder` — copy the surrounding XML attributes. Landscape variant must mirror portrait exactly except for orientation-specific positioning attributes that already differ for `btnCreateFolder`. **Landscape parity is mandatory (CLAUDE.md Rule 12).** Record expected widget type vs actual: `expected: same as btnCreateFolder | actual: <observed>`.
> 2. In `BrowseButtonSetupHelper.kt`:
>    - Add a click handler analogous to `btnCreateFolder`:
>      ```
>      binding.btnCreateTextFile?.setOnClickListener {
>          UserActionLogger.logButtonClick("CreateTextNote", "BrowseActivity")
>          callbacks.onCreateTextNoteClicked()
>      }
>      ```
>    - In `updateToolbarButtonLabels`, add the same landscape-label / portrait-null pattern as `btnCreateFolder` (lines 237 / 247 of current file).
>    - Add `onCreateTextNoteClicked()` to the `Callbacks` interface inside this file.
> 3. In `BrowseStateUiUpdater.kt`:
>    - Compute `canCreateTextNote` (same predicate as Step 02.5).
>    - `binding.btnCreateTextFile?.isVisible = canCreateTextNote`.

**Verification:**

- Grep — `@+id/btnCreateTextFile` matches once in `res/layout/activity_browse.xml`.
- Grep — `@+id/btnCreateTextFile` matches once in `res/layout-land/activity_browse.xml`.
- Grep — `binding.btnCreateTextFile?.setOnClickListener` matches once in `BrowseButtonSetupHelper.kt`.
- Grep — `binding.btnCreateTextFile?.isVisible` matches once in `BrowseStateUiUpdater.kt`.
- Build: `assembleStandardDebug` compiles (view binding regenerates correctly).

**Status:** `[ ]` not done

---

### Step 02.7 — Add keyboard shortcut

**Files:** `KeyboardNavigationManager.kt`, `CommandId.kt`, `default_bindings.json`

**Prompt for developer:**

> 1. In `CommandId.kt`: add enum constant `CreateTextNote` directly below `CreateFolder` (preserve serialisation order).
> 2. In `KeyboardNavigationManager.kt`:
>    - Add `fun showCreateTextNoteDialog()` to the `Callbacks` interface (mirror line 38).
>    - Add `InputAction.CreateTextNote -> { callbacks.showCreateTextNoteDialog(); true }` to the action dispatch block (mirror line 86 for `CreateFolder`).
> 3. In `default_bindings.json`: add a default binding for `CreateTextNote`. Choose `Ctrl+Alt+N` (Ctrl+Alt+T is reserved on Linux desktops for terminal; the device user will rarely conflict, but `N` for "Note" is mnemonic). Match the JSON shape of the existing `CreateFolder` entry exactly.

**Verification:**

- Grep — `CreateTextNote` matches once in `CommandId.kt`.
- Grep — `fun showCreateTextNoteDialog()` matches once in `KeyboardNavigationManager.kt`.
- Grep — `"CreateTextNote"` matches once in `assets/input/default_bindings.json`.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes via `/build`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "action_create_text_file"` exits 0.
- [ ] Manual smoke (LOCAL resource): toolbar button → dialog → confirm → success toast → file appears in the list.
- [ ] `add_to_dev_log.ps1` invoked for each modified file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2` re-run; commit catalog diff together with code.

---

## Handoff Notes to Next Phase

- The newly-created file's path is propagated to `BrowseTextNoteCreateManager.notifyCreatedForOpen` — Phase 04 will hook into this lambda to launch the editor.
- Network/cloud resources currently fail with `UnsupportedOperationException("S0189: createTextFile not yet wired for ...")` — Phase 03 replaces this with the Downloads-staging flow.

---

## Rollback Plan

- Revert phase commit(s). No data migration. View binding regenerates without `btnCreateTextFile` automatically once layout reverts.
