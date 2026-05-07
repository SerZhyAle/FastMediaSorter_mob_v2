# Phase 01 — Foundations

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Add the three vector drawable icons, trilingual string resources, and overflow menu item IDs required by all later phases. No Kotlin changes in this phase.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_crop.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_crop_to_file.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_compress.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3000 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | ≤ 300 |

---

## Steps

### Step 1.1 — Create vector drawable icons for the three crop commands

**Files:** `app_v2/src/main/res/drawable/ic_crop.xml`, `ic_crop_to_file.xml`, `ic_compress.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create three 24dp vector drawables styled to match the project's existing icon set (monochrome, `?attr/colorControlNormal`-tinted). Use the Material crop symbol for `ic_crop.xml`, a crop+save symbol for `ic_crop_to_file.xml`, and a compression/resize symbol (e.g. shrinking arrows) for `ic_compress.xml`. Keep each file under 30 lines.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ic_crop.xml` exists.
- `Glob` — `app_v2/src/main/res/drawable/ic_crop_to_file.xml` exists.
- `Glob` — `app_v2/src/main/res/drawable/ic_compress.xml` exists.
- `Grep` — `<vector` present in each file.

**Status:** `[x] done`
**Step Log:** Created ic_crop.xml (Material crop L-handles), ic_crop_to_file.xml (crop+save), ic_compress.xml (shrinking arrows). All verified.

---

### Step 1.2 — Add EN string keys for crop commands and dialogs

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Append the following six string entries to `values/strings.xml` in the player-commands section (near `menu_save_frame`):
>
> ```xml
> <!-- S0106: Image crop commands -->
> <string name="menu_crop">Crop</string>
> <string name="menu_crop_to_file">Crop to file</string>
> <string name="menu_compress_copy">Compressed copy</string>
> <string name="dialog_crop_filename_hint">File name</string>
> <string name="crop_save_to_downloads_note">Read-only source — file will be saved to Downloads</string>
> <string name="crop_file_created">File %s created</string>
> ```

**Verification:**

- `Grep` — `name="menu_crop"` matches exactly once in `values/strings.xml`.
- `Grep` — `name="menu_crop_to_file"` matches exactly once in `values/strings.xml`.
- `Grep` — `name="menu_compress_copy"` matches exactly once in `values/strings.xml`.
- `Grep` — `name="dialog_crop_filename_hint"` matches exactly once in `values/strings.xml`.
- `Grep` — `name="crop_save_to_downloads_note"` matches exactly once in `values/strings.xml`.
- `Grep` — `name="crop_file_created"` matches exactly once in `values/strings.xml`.

**Status:** `[x] done`
**Step Log:** Six keys added to values/strings.xml after save_frame block. All verified.

---

### Step 1.3 — Add RU string translations

**Files:** `app_v2/src/main/res/values-ru/strings.xml`
**Depends on:** Step 1.2

**Prompt for developer:**

> Append the same five keys to `values-ru/strings.xml` with Russian translations:
>
> ```xml
> <!-- S0106: Image crop commands -->
> <string name="menu_crop">Вырезать</string>
> <string name="menu_crop_to_file">Вырезать в файл</string>
> <string name="menu_compress_copy">Сжатая копия</string>
> <string name="dialog_crop_filename_hint">Имя файла</string>
> <string name="crop_save_to_downloads_note">Источник только для чтения — файл будет сохранён в Загрузки</string>
> <string name="crop_file_created">Файл %s создан</string>
> ```

**Verification:**

- `Grep` — `name="menu_crop"` matches exactly once in `values-ru/strings.xml`.
- `Grep` — `name="menu_compress_copy"` matches exactly once in `values-ru/strings.xml`.
- `Grep` — `name="crop_save_to_downloads_note"` matches exactly once in `values-ru/strings.xml`.

**Status:** `[x] done`
**Step Log:** Six RU keys added at end of values-ru/strings.xml. All verified.

---

### Step 1.4 — Add UK string translations

**Files:** `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 1.2

**Prompt for developer:**

> Append the same five keys to `values-uk/strings.xml` with Ukrainian translations:
>
> ```xml
> <!-- S0106: Image crop commands -->
> <string name="menu_crop">Обрізати</string>
> <string name="menu_crop_to_file">Обрізати у файл</string>
> <string name="menu_compress_copy">Стиснута копія</string>
> <string name="dialog_crop_filename_hint">Ім\'я файлу</string>
> <string name="crop_save_to_downloads_note">Джерело лише для читання — файл буде збережено у Завантаження</string>
> <string name="crop_file_created">Файл %s створено</string>
> ```

**Verification:**

- `Grep` — `name="menu_crop"` matches exactly once in `values-uk/strings.xml`.
- `Grep` — `name="menu_compress_copy"` matches exactly once in `values-uk/strings.xml`.
- `Grep` — `name="crop_save_to_downloads_note"` matches exactly once in `values-uk/strings.xml`.

**Status:** `[x] done`
**Step Log:** Six UK keys added at end of values-uk/strings.xml. All verified.

---

### Step 1.5 — Add three menu items to overflow_menu_player.xml

**Files:** `app_v2/src/main/res/menu/overflow_menu_player.xml`
**Depends on:** Step 1.1, Step 1.2

**Prompt for developer:**

> Add three new `<item>` entries to `overflow_menu_player.xml` after the `menu_open_in_separate_window` item (keep the S0106 comment):
>
> ```xml
> <!-- S0106: Image crop & compress (IMAGE only; Crop requires write access) -->
> <item
>     android:id="@+id/menu_crop"
>     android:icon="@drawable/ic_crop"
>     android:title="@string/menu_crop"
>     app:showAsAction="never" />
>
> <item
>     android:id="@+id/menu_crop_to_file"
>     android:icon="@drawable/ic_crop_to_file"
>     android:title="@string/menu_crop_to_file"
>     app:showAsAction="never" />
>
> <item
>     android:id="@+id/menu_compress_copy"
>     android:icon="@drawable/ic_compress"
>     android:title="@string/menu_compress_copy"
>     app:showAsAction="never" />
> ```

**Verification:**

- `Grep` — `@+id/menu_crop"` matches exactly once in `overflow_menu_player.xml` (the new item, not `menu_crop_to_file`).
- `Grep` — `@+id/menu_crop_to_file"` matches exactly once in `overflow_menu_player.xml`.
- `Grep` — `@+id/menu_compress_copy"` matches exactly once in `overflow_menu_player.xml`.
- `Grep` — `S0106` comment present in `overflow_menu_player.xml`.

**Status:** `[x] done`
**Step Log:** Three menu items added to overflow_menu_player.xml after menu_open_in_separate_window. All verified.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] String locale parity verified: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_crop"` exits 0.

---

## Handoff Notes to Next Phase

- Icons `ic_crop.xml`, `ic_crop_to_file.xml`, `ic_compress.xml` exist and compile.
- String keys `menu_crop`, `menu_crop_to_file`, `menu_compress_copy`, `dialog_crop_filename_hint`, `crop_save_to_downloads_note` present in EN/RU/UK.
- Menu item IDs `R.id.menu_crop`, `R.id.menu_crop_to_file`, `R.id.menu_compress_copy` are available to Phase 02+ Kotlin code.

---

## Rollback Plan

Revert phase commit(s) — no Kotlin or data migration changes.
