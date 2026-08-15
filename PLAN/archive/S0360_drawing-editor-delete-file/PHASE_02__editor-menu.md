# Phase 02 - editor-menu

**Strategic spec:** [`../S0360_drawing-editor-delete-file.md`](../S0360_drawing-editor-delete-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Add a "Delete file" item to the drawing-editor overflow menu that confirms, deletes the current file via the Phase 01 path, and returns to browse.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`deleteCurrentFileAndFinish()` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_drawing.xml` (+ `values-ru/`, `values-uk/`) | Modified | n/a |
| `app_v2/src/main/res/menu/menu_draw_overflow.xml` | Modified | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt` | Modified | ≤ 540 |

> `ImageDrawOverlayManager.kt` (684) and `PlayerDrawingSaveHelper.kt` (498, projected >500 after edit) are >500 lines after change - create timestamped backups in `temp/` before editing each.
>
> `menu_draw_overflow.xml` lives in `res/menu/` (no orientation variants); no `layout-land` counterpart applies.

---

## Steps

### Step 02.1 - Add the "Delete file" string in EN / RU / UK

**Files:** `app_v2/src/main/res/values/strings_drawing.xml`, `values-ru/strings_drawing.xml`, `values-uk/strings_drawing.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new string key `draw_overflow_delete_file` across all three locales in one lockstep call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Module app_v2 -Key draw_overflow_delete_file -En "Delete file" -Ru "Удалить файл" -Uk "Видалити файл"`.
> This is an action label; verify it against `docs/COMMUNICATION_POLICY.md` §2 (action/label) and §6 tone checklist before proceeding.

**Verification:**

- `Grep` - `name="draw_overflow_delete_file"` matches once in `values/strings_drawing.xml`.
- `Grep` - `name="draw_overflow_delete_file"` matches once in `values-ru/strings_drawing.xml`.
- `Grep` - `name="draw_overflow_delete_file"` matches once in `values-uk/strings_drawing.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Added via `set-android-string -Action add`, then `move`d into `strings_drawing.xml` to group with sibling `draw_overflow_*` keys. Verification 3/3 PASS (key in EN/RU/UK `strings_drawing.xml`, correct UTF-8). check_strings_localized exit 0. COMMUNICATION_POLICY §6 PASS.

---

### Step 02.2 - Add the menu item to the overflow menu

**Files:** `app_v2/src/main/res/menu/menu_draw_overflow.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `menu_draw_overflow.xml`, append a new `<item android:id="@+id/draw_overflow_delete_file" android:title="@string/draw_overflow_delete_file" />` as the last item in the menu (after `draw_overflow_keep`).

**Verification:**

- `Grep` - `@+id/draw_overflow_delete_file` matches once in `menu_draw_overflow.xml`.
- `Grep` - `@string/draw_overflow_delete_file` present in `menu_draw_overflow.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. Files: menu_draw_overflow.xml (+3 LOC). Dev log recorded.

---

### Step 02.3 - Add the callback method and implement the delete flow

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `ImageDrawOverlayManager`, add `fun onDeleteRequested()` to the `DrawOverlayActionCallback` interface (alongside `onCancelRequested()`). In `PlayerDrawingSaveHelper.setupDrawOverlayActionCallbacks()`, implement `onDeleteRequested()` in the anonymous `DrawOverlayActionCallback` object by calling a new private method `confirmAndDeleteCurrentFile()` and add that method to the helper:
> read `activity.viewModel.state.value.resource`; if `resource?.isReadOnly == true` show a short toast `R.string.error_read_only` and return; read `activity.viewModel.state.value.currentFile`; if null show a short toast `R.string.msg_no_file_to_delete` and return; otherwise show a mandatory `MaterialAlertDialogBuilder` confirmation with title `R.string.confirm_delete_title` and message `getString(R.string.confirm_delete_message, 1)`, positive button `R.string.delete` invoking `activity.viewModel.deleteCurrentFileAndFinish()`, negative button `R.string.cancel`. Guard against a finishing/destroyed activity before showing the dialog (mirror `FileOperationsHandler.deleteCurrentFile`). Use `Timber` only; do not exit draw mode here (success finishes the activity, failure keeps the editor open). Verify the confirmation strings against `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Grep` - `fun onDeleteRequested()` matches once in `ImageDrawOverlayManager.kt` (interface declaration).
- `Grep` - `override fun onDeleteRequested()` matches once in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `confirmAndDeleteCurrentFile` matches in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `deleteCurrentFileAndFinish()` present in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `confirm_delete_title` present in `PlayerDrawingSaveHelper.kt`.
- `Grep -n "Log\.d\("` on both files returns zero hits.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification 6/6 PASS. Files: ImageDrawOverlayManager.kt (interface +1), PlayerDrawingSaveHelper.kt (+30 LOC). Backups in temp/. Reuses confirm_delete_title/message, error_read_only, msg_no_file_to_delete. COMMUNICATION_POLICY §6 PASS. Dev log recorded.

---

### Step 02.4 - Wire the menu branch and visibility gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In the overflow `PopupMenu` setup of `ImageDrawOverlayManager` (the `overflowBtn?.setOnClickListener` block), after the existing Keep visibility gating, add `menu.findItem(R.id.draw_overflow_delete_file)?.isVisible = currentFile != null`. In the `setOnMenuItemClickListener` `when`, add a branch `R.id.draw_overflow_delete_file -> { actionCallback?.onDeleteRequested(); true }` before the `else -> false` branch. No other change.

**Verification:**

- `Grep` - `R.id.draw_overflow_delete_file` matches twice in `ImageDrawOverlayManager.kt` (visibility gate + click branch).
- `Grep` - `actionCallback?.onDeleteRequested()` present in `ImageDrawOverlayManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-05 - Verification PASS (2 id refs: visibility gate + click branch; onDeleteRequested call present). Files: ImageDrawOverlayManager.kt (+7 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL in 5m 12s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_overflow_delete_file"` exits 0.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- The user-visible capability is complete: overflow menu -> "Delete file" -> mandatory confirm -> trash-aware delete -> back to browse.
- Phase 03 records the new capability in `docs/FEATURES*` and finalizes catalog/dev-log.

---

## Rollback Plan

Revert the phase commit(s) - removing the menu item, callback method, and string. No data migration or schema change; the Phase 01 backend path is inert without this UI wiring.
