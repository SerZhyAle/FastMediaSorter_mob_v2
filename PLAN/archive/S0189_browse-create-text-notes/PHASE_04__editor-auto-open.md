# Phase 04 — Editor auto-open in edit mode after create

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

After successful `CreateTextNoteUseCase` invocation, immediately open the new file in `PlayerActivity` with edit mode pre-activated. Apply to both LOCAL (real target path) and network/cloud (local staging path from Phase 03). After this phase the user sees the text editor (cursor blinking, keyboard visible) without any extra tap.

---

## Prerequisites

- [ ] Phase 02 Done.
- [ ] Phase 03 Done (so network resources have a local path the editor can open).
- [ ] `PlayerActivity` already accepts the file path via intent extra `EXTRA_MEDIA_FILE_PATH` (or similar) — verify by grepping `EXTRA_` keys in `PlayerActivity.kt` and record actual key name in the implementation log.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | reuse existing entry path; ≤ +30 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | reuse `enterEditMode()`; ≤ +20 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | wire `notifyCreatedForOpen` lambda |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt` | Modified | new public helper `openTextNoteInEditor(path)` |

---

## Steps

### Step 04.1 — Add `EXTRA_TEXT_EDIT_MODE_ON_OPEN` to `PlayerActivity`

**Files:** `PlayerActivity.kt`, `TextViewerManager.kt`

**Prompt for developer:**

> 1. In `PlayerActivity.kt`:
>    - Add a public companion constant `const val EXTRA_TEXT_EDIT_MODE_ON_OPEN = "s0189_edit_mode_on_open"`.
>    - In the activity's `onCreate` / wherever text-file content load completes, read this extra (`intent.getBooleanExtra(EXTRA_TEXT_EDIT_MODE_ON_OPEN, false)`). If true, after the text content is loaded into the viewer, call `textViewerManager.enterEditMode()`. Use the existing post-load callback site (search for `safeViews.tvTextContent.text = ` or similar finish-of-load hook).
> 2. In `TextViewerManager.kt`:
>    - Ensure `enterEditMode()` is callable from outside the manager (already invoked via `binding.btnEditTextCmd` click). If currently `private`, make it `internal` or `public` with a clear KDoc.
>    - Add `Timber.d("S0189: TextViewerManager.enterEditMode invoked autoOpen=$autoOpen")` at the entry of `enterEditMode()`. Introduce a parameter `autoOpen: Boolean = false` so callers from S0189 path can flag the intent (purely for diagnostics; does not change behaviour).

**Verification:**

- Grep — `EXTRA_TEXT_EDIT_MODE_ON_OPEN` matches once in `PlayerActivity.kt` (declaration) and at least once in usage.
- Grep — `enterEditMode(autoOpen` (with parameter) present.
- Grep — `Timber.d("S0189: TextViewerManager.enterEditMode` present.
- Build: `assembleStandardDebug` compiles.

**Status:** `[ ]` not done

---

### Step 04.2 — Add `openTextNoteInEditor` in `BrowseFileOpenManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOpenManager.kt`

**Prompt for developer:**

> Add public method `fun openTextNoteInEditor(path: String, resourceId: Long)`. Body: build an Intent for `PlayerActivity` reusing the existing intent-creation helper in this class (search for how regular file-open builds its intent — copy the pattern). Set:
> - The standard file-path extra used by PlayerActivity (record the actual key name).
> - `EXTRA_TEXT_EDIT_MODE_ON_OPEN = true`.
> - Resource id extra if PlayerActivity requires it for navigation.
> Then `context.startActivity(intent)`. Add `Timber.d("S0189: BrowseFileOpenManager.openTextNoteInEditor path=$path resource=$resourceId")`.

**Verification:**

- Grep — `fun openTextNoteInEditor(` matches once in `BrowseFileOpenManager.kt`.
- Grep — `EXTRA_TEXT_EDIT_MODE_ON_OPEN` referenced once in this file.
- Grep — `Timber.d("S0189: BrowseFileOpenManager.openTextNoteInEditor` present.

**Status:** `[ ]` not done

---

### Step 04.3 — Wire the `notifyCreatedForOpen` lambda

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`

**Prompt for developer:**

> When constructing `BrowseTextNoteCreateManager` (Step 02.5), pass:
> ```
> notifyCreatedForOpen = { createdPath ->
>     val resourceId = viewModel.state.value.resource?.id ?: return@let
>     browseFileOpenManager.openTextNoteInEditor(createdPath, resourceId)
> }
> ```
> Make sure the lambda runs on Main dispatcher (the manager already withContexts to Main before calling it per Phase 02). Add `Timber.d("S0189: BrowseManagerInitializer.notifyCreatedForOpen path=$createdPath")` inside the lambda.

**Verification:**

- Grep — `browseFileOpenManager.openTextNoteInEditor` matches once in `BrowseManagerInitializer.kt`.
- Grep — `Timber.d("S0189: BrowseManagerInitializer.notifyCreatedForOpen` present.
- Manual smoke (LOCAL): create note → success toast → editor opens with the new (empty) file in edit mode, keyboard visible.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes.
- [ ] Manual smoke recorded (`expected: editor opens in edit mode | actual: ...`).
- [ ] `add_to_dev_log.ps1` invoked for each touched file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2`.

---

## Handoff Notes to Next Phase

- The editor is now reachable via the create flow. Phase 05 redesigns the editor's action panel (Save, Save & Close, Save & Send, Send to Keep, Cancel) and introduces the dirty-state indicator.

---

## Rollback Plan

- Revert phase commits. `EXTRA_TEXT_EDIT_MODE_ON_OPEN` is opt-in — without it, `PlayerActivity` behaves identically to today. No data migration.
