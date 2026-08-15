# Phase 03 — Progress Dialog Cleanup

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — parallel with Phase 01/02
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Remove design-time placeholder text (`file.jpg`, `0 / 10`, `1.5 МБ/с`) from production string resources, move them to `tools:text` in layout XML so developers still see them in the editor preview, and add an explicit "preparing" initial state to `FileOperationProgressDialog`.

---

## Prerequisites

- [ ] Working tree clean or on `DEBUG-v004`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/res/layout/dialog_file_operation_progress.xml` | Modified | — |
| `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt` | Modified | ≤ 250 |

> Landscape parity satisfied: `layout-land/dialog_file_operation_progress.xml` listed.

---

## Steps

### Step 03.1 — Add new "preparing" string resource (trilingual)

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Add a new resource key `file_operation_progress_preparing` to all three locale files. EN: `Preparing..`. RU: `Подготовка..`. UK: `Підготовка..`. Use `..` (two dots), not `...`. Place each entry next to the other `dialog_file_operation_progress_*` keys. Check `docs/COMMUNICATION_POLICY.md` §6 — short status, no exclamation, lowercase past "Preparing", neutral tone. Strings pass COMMUNICATION_POLICY §6 checklist.

**Verification:**

- `Grep` — `file_operation_progress_preparing` matches exactly 3 times across the three string files.
- `Grep` — `Preparing\\.\\.` in `values/strings.xml`.
- `Grep` — `Подготовка\\.\\.` in `values-ru/strings.xml`.
- `Grep` — `Підготовка\\.\\.` in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 4/4 PASS. Files: values/strings.xml + values-ru + values-uk (+1 key each via set-android-string.ps1).

---

### Step 03.2 — Delete placeholder strings (trilingual)

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 03.1

**Prompt for developer:**

> Delete these three keys from each of the three locale files: `dialog_file_operation_progress_tvCurrentFile_text`, `dialog_file_operation_progress_tvProgressText_text`, `dialog_file_operation_progress_tvSpeed_text`. Keep `dialog_file_operation_progress_btnCancel_text` and `dialog_file_operation_progress_tvProgressTitle_text` — those are real user-facing strings. Keep `tvProgressTitle_text` because the dialog title is real text used at runtime. Do NOT delete `dialog_file_operation_progress_tvProgressTitle_text`.

**Verification:**

- `Grep` — `dialog_file_operation_progress_tvCurrentFile_text` returns zero hits across all three string files.
- `Grep` — `dialog_file_operation_progress_tvProgressText_text` returns zero hits.
- `Grep` — `dialog_file_operation_progress_tvSpeed_text` returns zero hits.
- `Grep` — `dialog_file_operation_progress_tvProgressTitle_text` still matches 3 times (one per locale).
- `Bash` — `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "dialog_file_operation_progress"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 5/5 PASS (3 deleted keys absent from all string files; ProgressTitle preserved x3; locale audit OK).

---

### Step 03.3 — Replace `android:text` with `tools:text` in portrait layout

**Files:** `app_v2/src/main/res/layout/dialog_file_operation_progress.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `tvCurrentFile`, `tvProgressText`, `tvSpeed`: replace `android:text="@string/dialog_file_operation_progress_tv*_text"` with `tools:text="file.jpg"` (for tvCurrentFile), `tools:text="0 / 10"` (for tvProgressText), `tools:text="1.5 MB/s"` (for tvSpeed). Add `xmlns:tools="http://schemas.android.com/tools"` to the root `LinearLayout` if not already present. Leave `tvProgressTitle` untouched — it still uses `android:text="@string/..."`.

**Verification:**

- `Grep` — `xmlns:tools="http://schemas.android.com/tools"` present in the file.
- `Grep` — `tools:text="file.jpg"` present.
- `Grep` — `tools:text="0 / 10"` present.
- `Grep` — `tools:text="1.5 MB/s"` present.
- `Grep` — `dialog_file_operation_progress_tvCurrentFile_text` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 5/5 PASS. Files: layout/dialog_file_operation_progress.xml (xmlns:tools + 3× tools:text).

---

### Step 03.4 — Replace `android:text` with `tools:text` in landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Apply the identical change as Step 03.3 to `layout-land/dialog_file_operation_progress.xml`. Same three replacements, same `xmlns:tools` declaration.

**Verification:**

- `Grep` — `xmlns:tools="http://schemas.android.com/tools"` present in landscape file.
- `Grep` — `tools:text="file.jpg"` present.
- `Grep` — `tools:text="0 / 10"` present.
- `Grep` — `tools:text="1.5 MB/s"` present.
- `Grep` — `dialog_file_operation_progress_tvCurrentFile_text` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 5/5 PASS. Files: layout-land/dialog_file_operation_progress.xml (xmlns:tools + 3× tools:text).

---

### Step 03.5 — Initialise dialog fields to "Preparing.." on `onCreate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`
**Depends on:** Step 03.1, Step 03.3, Step 03.4

**Prompt for developer:**

> In `FileOperationProgressDialog.onCreate(savedInstanceState)`, immediately after the `findViewById` block, set `tvCurrentFile.text = context.getString(R.string.file_operation_progress_preparing)`, `tvProgress.text = ""`, `tvSpeed.text = ""`, `tvOverallPercent.text = ""`, `tvEta.text = ""`. This guarantees that if the dialog appears before the first `Processing` callback (e.g. during a 30-second cloud download with no events), the user sees "Preparing.." not the layout-tool placeholder. Compile gate at the end: `./a.ps1 dq` exits 0.

**Verification:**

- `Grep` — `tvCurrentFile.text = context.getString(R.string.file_operation_progress_preparing)` present in `FileOperationProgressDialog.kt`.
- `Grep` — `R.string.file_operation_progress_preparing` matches exactly once in the file.
- `Grep` — `R.string.file_operation_progress_preparing` matches exactly once in the file.
- `Bash` — `./a.ps1 dq` exits 0.
- `Grep` — `BUILD SUCCESSFUL` in build output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 4/4 PASS. Files: `FileOperationProgressDialog.kt` (+8 LOC initial state). BUILD SUCCESSFUL in 39s, version 2.60.5201.227.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "dialog_file_operation_progress"` exits 0 — only `_btnCancel_text` and `_tvProgressTitle_text` remain, both present in all three locales.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "file_operation_progress_preparing"` exits 0.
- [x] `./a.ps1 dq` exits 0.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Production locales no longer carry design-time placeholder values.
- Initial dialog state is "Preparing..", which is independent of when (or whether) cloud download emits progress.

---

## Rollback Plan

Revert Phase 03 commit(s). Layout edits and string deletions are independent of other phases.
