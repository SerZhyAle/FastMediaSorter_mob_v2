# Phase 03 - Group title strings

**Strategic spec:** [`../S0364_settings-interface-group-split.md`](../S0364_settings-interface-group-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Provide the two group titles in EN/RU/UK: repurpose the existing interface category as "general interface settings" and add a new "file browser interface" category.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (layouts reference `@string/settings_category_file_browser`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | n/a |

---

## Steps

### Step 03.1 - Retitle the general interface category

**Files:** `app_v2/src/main/res/values/strings_settings.xml`, `app_v2/src/main/res/values-ru/strings_settings.xml`, `app_v2/src/main/res/values-uk/strings_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the value of existing key `settings_category_interface` (keep the key name) to the "general interface settings" wording in all three locales using `scripts/utils/set-android-string.ps1 -Action set` per locale with `-ExpectedOldValue` guards:
> - EN: `General app interface` → `General interface settings`
> - RU: `Общий интерфейс программы` → `Общие настройки интерфейса`
> - UK: `Загальний інтерфейс програми` → `Загальні налаштування інтерфейсу`
> Apply the COMMUNICATION_POLICY §6 tone checklist (short, neutral category label).

**Verification:**

- `Grep` - `<string name="settings_category_interface">General interface settings</string>` in `values/strings_settings.xml`.
- `Grep` - `<string name="settings_category_interface">Общие настройки интерфейса</string>` in `values-ru/strings_settings.xml`.
- `Grep` - `<string name="settings_category_interface">Загальні налаштування інтерфейсу</string>` in `values-uk/strings_settings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. EN=`General interface settings`, RU=`Общие настройки интерфейса`, UK=`Загальні налаштування інтерфейсу` (expected | actual match). ExpectedOldValue guards passed for all three. set-android-string.ps1 extended to honour -File for thematic split files (was hard-coded to strings.xml). Dev log recorded.

---

### Step 03.2 - Add the file-browser category in lockstep

**Files:** `app_v2/src/main/res/values/strings_settings.xml`, `app_v2/src/main/res/values-ru/strings_settings.xml`, `app_v2/src/main/res/values-uk/strings_settings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add new key `settings_category_file_browser` across EN/RU/UK in one lockstep call: `scripts/utils/set-android-string.ps1 -Action add -Key settings_category_file_browser -En "File browser interface" -Ru "Интерфейс браузера файлов" -Uk "Інтерфейс браузера файлів"`. This is the canonical "браузер файлов" term established by S0364. Apply the COMMUNICATION_POLICY §6 tone checklist.

**Verification:**

- `Grep` - `settings_category_file_browser` matches exactly once in each of the three `strings_settings.xml` files.
- `Grep` - `Интерфейс браузера файлов` present in `values-ru/strings_settings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_file_browser"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Key added in all three locales (line 217 each): EN=`File browser interface`, RU=`Интерфейс браузера файлов`, UK=`Інтерфейс браузера файлів`. check_strings_localized.ps1 -KeyPrefix settings_category_file_browser exit 0 (expected 0 | actual 0). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`. (standardDebug BUILD SUCCESSFUL)
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category"` exits 0. (all 23 keys OK)
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both group titles resolve in all three locales; the layouts (Phase 01) now display final titles. Phases 04-06 extend the "браузер файлов" term beyond this category to the rest of the strings and docs.

---

## Rollback Plan

Revert the three string edits via `set-android-string.ps1 -Action set` (restore old values) and `-Action remove` for the new key.
