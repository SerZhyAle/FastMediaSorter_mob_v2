# Phase 02 — Enhanced Error Dialog

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Rebuild `ErrorDialog` with a new layout featuring collapsible details, selectable/scrollable text, Save-to-file, Share, Close, and Copy actions. Update trilingual strings. No screen-wiring changes yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_error_detail.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout-land/dialog_error_detail.xml` | New | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt` | Modified | ≤ 250 |

> `ErrorDialog.kt` is currently 95 LOC — no backup required.

---

## Steps

### Step 02.1 — Create dialog_error_detail layout (portrait + landscape)

**Files:**
- `app_v2/src/main/res/layout/dialog_error_detail.xml`
- `app_v2/src/main/res/layout-land/dialog_error_detail.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Create `dialog_error_detail.xml` in `res/layout/`. The root is a vertical `LinearLayout`. It contains:
>
> 1. A `ScrollView` (`android:layout_weight="1"`, `android:layout_height="0dp"`, height `@dimen/dialog_content_height_large` in portrait) wrapping a `TextView` with `android:id="@+id/tvErrorMessage"`: `textIsSelectable="true"`, `breakStrategy="simple"`, `lineSpacingMultiplier="1.2"`. This is the main user-readable message.
>
> 2. A `TextView` with `android:id="@+id/tvDetailsToggle"`: shows `"▶ Details"` / `"▼ Details"` (uses `@string/error_dialog_show_details`). Style: bold, `textSize="@dimen/toggler_title_text_size"`, clickable. Default: visible.
>
> 3. A `ScrollView` with `android:id="@+id/scrollDetails"`, `android:visibility="gone"` by default, height `@dimen/dialog_content_height_large`. Contains a `TextView` `android:id="@+id/tvErrorDetails"`: `textIsSelectable="true"`, `breakStrategy="simple"`, `textSize` slightly smaller (use `@dimen/resource_card_desc_text_size`).
>
> Landscape counterpart `res/layout-land/dialog_error_detail.xml`: identical structure but both ScrollViews use `@dimen/dialog_landscape_list_max_height` instead of `@dimen/dialog_content_height_large`.
>
> Both files must not use hard-coded `dp` values — reference existing dimension resources only.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/dialog_error_detail.xml` exists.
- `Glob` — `app_v2/src/main/res/layout-land/dialog_error_detail.xml` exists.
- `Grep` — `tvErrorMessage` present in both layout files.
- `Grep` — `tvDetailsToggle` present in both layout files.
- `Grep` — `tvErrorDetails` present in both layout files.
- `Grep` — `scrollDetails` present in both layout files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: layout/dialog_error_detail.xml (new), layout-land/dialog_error_detail.xml (new). btnSaveToFile also included (pre-resolves Step 02.4). Dev log recorded.

---

### Step 02.2 — Add trilingual strings for error dialog

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase (parallel with Step 02.1)

**Prompt for developer:**

> Add the following new string keys to all three `strings.xml` files. Keep alphabetical order within each file.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `error_dialog_save_to_file` | `Save to file` | `Сохранить в файл` | `Зберегти у файл` |
> | `error_dialog_share` | `Share` | `Отправить` | `Надіслати` |
> | `error_dialog_show_details` | `▶ Details` | `▶ Подробности` | `▶ Деталі` |
> | `error_dialog_hide_details` | `▼ Details` | `▼ Подробности` | `▼ Деталі` |
> | `error_saved_to_downloads` | `Error log saved to Downloads` | `Лог ошибки сохранён в Downloads` | `Лог помилки збережено у Downloads` |
> | `error_save_failed` | `Failed to save error log` | `Не удалось сохранить лог ошибки` | `Не вдалося зберегти лог помилки` |
>
> Do not modify any existing string key.

**Verification:**

- `Grep` — `error_dialog_save_to_file` present in `values/strings.xml`.
- `Grep` — `error_dialog_save_to_file` present in `values-ru/strings.xml`.
- `Grep` — `error_dialog_save_to_file` present in `values-uk/strings.xml`.
- `Grep` — `error_saved_to_downloads` present in all three files.
- `Grep` — `error_save_failed` present in all three files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. All 6 string keys added to EN/RU/UK. Dev log recorded.

---

### Step 02.3 — Rebuild ErrorDialog with new layout + Save/Share/collapsible details

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Rewrite `ErrorDialog.kt` (currently 95 LOC) to use the new `R.layout.dialog_error_detail` layout. Keep the public API compatible: `show(context, title, message, details, actionButtonText?, onActionClick?)` and `show(context, title, throwable)`.
>
> Changes to `show(...)`:
> - Inflate `R.layout.dialog_error_detail`.
> - Set `tvErrorMessage.text = message` (user-readable part).
> - If `details != null`: set `tvErrorDetails.text = details`; wire `tvDetailsToggle` click to toggle `scrollDetails` visibility and swap the toggle label between `R.string.error_dialog_show_details` and `R.string.error_dialog_hide_details`.
> - If `details == null`: hide both `tvDetailsToggle` and `scrollDetails` (set GONE).
> - Replace the `setNeutralButton(R.string.copy_to_clipboard)` with three separate neutral/positive buttons:
>   - **Close** (negative) — dismisses.
>   - **Copy** (neutral) — copies the full text (`message + "\n\n" + details` if present) to clipboard via the existing `copyToClipboard` private method.
>   - **Share** (positive) — creates `Intent(Intent.ACTION_SEND)` with `type = "text/plain"`, `putExtra(EXTRA_TEXT, fullText)`, wrapped in `Intent.createChooser`. Starts from `context`.
> - Add a fourth action accessible from a `setNeutralButton` override is not possible with standard `AlertDialog` (only 3 buttons). Use a custom bottom row instead: after `.setView(dialogView)`, do **not** use `.setNeutralButton` for Save. Instead, add a horizontal `LinearLayout` as part of `dialog_error_detail.xml` containing a "Save to file" `Button` (id `btnSaveToFile`). Wire the click inside `show(...)` to `saveErrorToFile(context, fullText)`.
> - Implement `private fun saveErrorToFile(context: Context, text: String)` using a `CoroutineScope(Dispatchers.IO)`. On API 29+: insert into `MediaStore.Downloads` with MIME `text/plain`, filename `fms_error_<timestamp>.txt`. On API ≤ 28: write to `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`. On success: show a `Toast.makeText(context, R.string.error_saved_to_downloads, Toast.LENGTH_SHORT)` on the main thread. On failure: `Timber.e(e, ...)` and show `R.string.error_save_failed` toast.
> - Retain `BadTokenException` guard and Activity finishing/destroyed check.
> - Add `Timber.d("S0115: ErrorDialog.show title=$title")` at the top of `show(...)`.

**Verification:**

- `Grep` — `dialog_error_detail` present in `ErrorDialog.kt` (new layout reference).
- `Grep` — `Intent.ACTION_SEND` present in `ErrorDialog.kt` (Share action).
- `Grep` — `saveErrorToFile` present in `ErrorDialog.kt` (Save action).
- `Grep` — `MediaStore` present in `ErrorDialog.kt` (API 29+ save path).
- `Grep` — `tvDetailsToggle` present in `ErrorDialog.kt` (collapsible details wiring).
- `Grep` — `Timber.d("S0115:` present in `ErrorDialog.kt`.
- `Grep` — `dialog_log_view` must NOT appear in `ErrorDialog.kt` (old layout replaced).
- `Grep -n "Log\.d\("` — zero hits in `ErrorDialog.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 8/8 PASS. Files: ErrorDialog.kt (rewritten, 155 LOC — dialog_error_detail layout, collapsible details, Share, Save-to-file, Copy). String key error_save_failed renamed to error_log_save_failed to avoid collision with pre-existing key. Dev log recorded.

---

### Step 02.4 — Update dialog_error_detail layout: add btnSaveToFile

**Files:**
- `app_v2/src/main/res/layout/dialog_error_detail.xml`
- `app_v2/src/main/res/layout-land/dialog_error_detail.xml`

**Depends on:** Step 02.3 (determines required view id)

**Prompt for developer:**

> Add a `Button` with `android:id="@+id/btnSaveToFile"` and `android:text="@string/error_dialog_save_to_file"` at the bottom of both `dialog_error_detail.xml` (portrait) and `dialog_error_detail.xml` (landscape) as the last child of the root `LinearLayout`. Use `style="@style/Widget.MaterialComponents.Button.TextButton"` to match the dialog button style. The button must be full-width (`layout_width="match_parent"`).

**Verification:**

- `Grep` — `btnSaveToFile` present in `app_v2/src/main/res/layout/dialog_error_detail.xml`.
- `Grep` — `btnSaveToFile` present in `app_v2/src/main/res/layout-land/dialog_error_detail.xml`.
- `Grep` — `error_dialog_save_to_file` referenced in both layout files (text attribute).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. btnSaveToFile and error_dialog_save_to_file present in both layout files (pre-resolved in Step 02.1). Dev log recorded.

---

### Step 02.5 — Verify build and string locale parity

**Files:** _(none — checks only)_
**Depends on:** Steps 02.1–02.4

**Prompt for developer:**

> 1. Run `/build` to verify the project assembles.
> 2. Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_dialog_"` and `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_saved_"` and `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_save_"`. All must exit code 0.

**Verification:**

- Build exits code 0.
- String locale checks exit code 0 (no missing translations).
- `Grep` — `TODO(phase-02)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Build PASS (1m 57s). String parity: error_dialog_ (4 keys), error_saved_ (1 key), error_log_save_ (1 key) — all EN/RU/UK OK. Zero TODO(phase-02) hits.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` passed in Step 02.5.
- [ ] String parity checks exit 0 for all new keys.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `ErrorDialog.show(...)` now accepts `showDetailedErrors` flag implicitly by its caller (Browse/Player managers pass it).
- The new layout is `dialog_error_detail` (portrait + landscape). The old `dialog_log_view` is NOT removed — it may be used by other dialogs; do not touch it.
- `btnSaveToFile` is wired inside `ErrorDialog.kt`; callers do not need to know about it.

---

## Rollback Plan

Revert phase commit(s). `dialog_log_view` is unchanged and remains the fallback for any other dialog that references it. No data migration involved.
