# Phase 01 - UI Terminology Alignment

**Strategic spec:** [`../S0302_file-manager-mode.md`](../S0302_file-manager-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02 - Manifest Integration
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Align all user-facing terminology in local/network resource creation & editing screens from "All Files" to "File Manager Mode" across English, Russian, and Ukrainian localizations.

---

## Prerequisites

- [ ] Strategic §6 research items are resolved (done).
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/layout/fragment_resource_editor.xml` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 - Update Localized Strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** start of phase

**Prompt for developer:**

> Replace all user-facing English, Russian, and Ukrainian UI labels referring to "All Files" preset, mode, and checkboxes to "File Manager Mode".
>
> English:
> - `label_all_files` -> `"File Manager Mode (browse and manage all files)"`
> - `all_files` -> `"File Manager"`
> - `tooltip_all_files_title` -> `"File Manager Mode"`
> - `tooltip_all_files_message` -> `"Show and manage ALL file types in resources (not just media). When enabled, automatically supports images, video, audio, text, documents, archives, and binaries."`
> - `all_files_resource_info` -> `"File Manager Mode is enabled for this resource. All file types are supported."`
> - `label_profile_all_files` -> `"Quick Setup: File Manager Mode"`
>
> Russian:
> - `label_all_files` -> `"Режим файлового менеджера (показ и управление всеми файлами)"`
> - `all_files` -> `"Файловый менеджер"`
> - `tooltip_all_files_title` -> `"Режим файлового менеджера"`
> - `tooltip_all_files_message` -> `"Показывать и управлять ВСЕМИ типами файлов в ресурсах (не только медиа). При включении автоматически поддерживаются изображения, видео, аудио, текст, документы, архивы и бинарные файлы."`
> - `all_files_resource_info` -> `"Режим файлового менеджера включён для этого ресурса. Все типы файлов поддерживаются."`
> - `label_profile_all_files` -> `"Быстрая настройка: Файловый менеджер"`
>
> Ukrainian:
> - `label_all_files` -> `"Режим файлового менеджера (показ та керування всіма файлами)"`
> - `all_files` -> `"Файловий менеджер"`
> - `tooltip_all_files_title` -> `"Режим файлового менеджера"`
> - `tooltip_all_files_message` -> `"Показувати та керувати ВСІМА типами файлів у ресурсах (не лише медіа). При увімкненні автоматично підтримуються зображення, відео, аудіо, текст, документи, архіви та бінарні файли."`
> - `all_files_resource_info` -> `"Режим файлового менеджера увімкнено для цього ресурсу. Усі типи файлів підтримуються."`
> - `label_profile_all_files` -> `"Швидке налаштування: Файловий менеджер"`
>
> Double check that all changes pass the `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Verification:**

- `Grep` - `<string name="label_all_files">File Manager Mode` exists in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `<string name="label_all_files">Режим файлового менеджера` exists in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `<string name="label_all_files">Режим файлового менеджера` exists in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Updated all localized string files using set-android-string.ps1. Dev logs recorded.

---

### Step 01.2 - Adjust Resource Editor Layout

**Files:** `app_v2/src/main/res/layout/fragment_resource_editor.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Check if `cbAllFiles` checkbox text uses `@string/label_all_files`. If not, update it to use `@string/label_all_files` to ensure consistent naming across localizations. If landscape layout exists under `res/layout-land/fragment_resource_editor.xml`, make sure it is aligned as well.

**Verification:**

- `Grep` - `android:text="@string/label_all_files"` exists in `app_v2/src/main/res/layout/fragment_resource_editor.xml` inside `MaterialCheckBox` with id `cbAllFiles`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 1/1 PASS. Layout fragment_resource_editor.xml successfully verified to use @string/label_all_files. Dev logs recorded.

---

### Step 01.3 - Verify AddResourceFormManager Kotlin References

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Inspect `AddResourceFormManager.kt` and confirm it references the localized string resources (like `R.string.label_profile_all_files`) rather than hardcoded text for profiles. Since we updated the string resources, verify compilation works correctly without any reference breaks.

**Verification:**

- `Grep` - `ResourceProfile.ALL_FILES -> R.string.label_profile_all_files` matches.
- Project compiles successfully.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. AddResourceFormManager.kt compiles and successfully references R.string.label_profile_all_files. Dev logs recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entries added for modified files.

---

## Handoff Notes to Next Phase

Phase 01 completed. Naming changed in UI layers and localizations to "File Manager Mode". Ready for Manifest Integration.

---

## Rollback Plan

Revert git commits of Phase 01. No DB schema or storage changes.
