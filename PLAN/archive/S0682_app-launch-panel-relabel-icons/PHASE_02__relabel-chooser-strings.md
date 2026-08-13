# Phase 02 - Relabel Add-Chooser and Picker Strings

**Strategic spec:** [`../S0682_app-launch-panel-relabel-icons.md`](../S0682_app-launch-panel-relabel-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Rename the two misleading add-chooser items and the matching picker title across EN/RU/UK so labels reflect the real action: app selection and Android OS settings.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a (3 keys) |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a (3 keys) |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a (3 keys) |

> No Kotlin change: `EditAppLaunchPanelActivity` reads these labels from string resources; renaming the values is sufficient. Keys are unchanged, so no code references break.

---

## Steps

### Step 02.1 - Relabel external-app chooser item (`app_launch_panel_path_external_app`)

**Files:** `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> The item currently reads "Функция Android" (RU) / "Функція Android" (UK) but actually opens the device app picker. Per owner decision (strategic §6.1) set RU value to `Приложение` and UK value to `Застосунок`. Leave the EN value `Android app` unchanged - it is already correct and is the reference meaning. Use `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set` once per locale with `-ExpectedOldValue` guarding the prior text (byte-preserving edit).

**Verification:**

- `Grep` - `name="app_launch_panel_path_external_app">Приложение<` in `values-ru/strings.xml`.
- `Grep` - `name="app_launch_panel_path_external_app">Застосунок<` in `values-uk/strings.xml`.
- `Grep` - `name="app_launch_panel_path_external_app">Android app<` still in `values/strings.xml` (unchanged).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification PASS. RU "Приложение", UK "Застосунок"; EN "Android app" unchanged.

---

### Step 02.2 - Relabel OS-settings chooser item (`app_launch_panel_path_os_part`)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> The item currently reads "System shortcut" (EN) / "Часть системы" (RU) / "Частина системи" (UK). Owner wants it to read as Android OS settings. Set EN = `Android OS settings`, RU = `Настройки ОС Андроид`, UK = `Налаштування ОС Андроїд`. Use `set-android-string.ps1 -Action set` once per locale with `-ExpectedOldValue`.

**Verification:**

- `Grep` - `name="app_launch_panel_path_os_part">Android OS settings<` in `values/strings.xml`.
- `Grep` - `name="app_launch_panel_path_os_part">Настройки ОС Андроид<` in `values-ru/strings.xml`.
- `Grep` - `name="app_launch_panel_path_os_part">Налаштування ОС Андроїд<` in `values-uk/strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS. EN "Android OS settings", RU "Настройки ОС Андроид", UK "Налаштування ОС Андроїд".

---

### Step 02.3 - Align the OS picker title (`app_launch_panel_picker_os_title`)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> The picker dialog title currently reads "Choose a system shortcut" (EN) / "Выбор части системы" (RU) / "Вибір частини системи" (UK). Align it with the renamed item: EN = `Choose Android OS setting`, RU = `Выбор настройки ОС Андроид`, UK = `Вибір налаштування ОС Андроїд`. Use `set-android-string.ps1 -Action set` once per locale with `-ExpectedOldValue`.

**Verification:**

- `Grep` - `name="app_launch_panel_picker_os_title">Choose Android OS setting<` in `values/strings.xml`.
- `Grep` - `name="app_launch_panel_picker_os_title">Выбор настройки ОС Андроид<` in `values-ru/strings.xml`.
- `Grep` - `name="app_launch_panel_picker_os_title">Вибір налаштування ОС Андроїд<` in `values-uk/strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS. EN "Choose Android OS setting", RU "Выбор настройки ОС Андроид", UK "Вибір налаштування ОС Андроїд".

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_path_"` exits 0.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_picker_"` exits 0.
- [ ] Dev log entry added for the string change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All three relabeled keys are parity-complete EN/RU/UK. Phase 03 runs the localization audit and catalog/dev-log closure.

---

## Rollback Plan

Revert phase commit(s) - string values only; keys unchanged, no code or data affected.
