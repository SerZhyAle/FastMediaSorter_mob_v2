# Phase 01 - strings-and-resources

**Strategic spec:** [`../S0442_settings-pages-rename-regroup.md`](../S0442_settings-pages-rename-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Update all string resources for the renamed tabs and the renamed/new reset buttons across EN, RU, and UK locales.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | ≤ 5 lines changed |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | ≤ 5 lines changed |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | ≤ 5 lines changed |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 12 lines changed |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 12 lines changed |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 12 lines changed |

---

## Steps

### Step 1.1 - Rename tab label strings in all three locales

**Files:**
- `app_v2/src/main/res/values/strings_settings.xml`
- `app_v2/src/main/res/values-ru/strings_settings.xml`
- `app_v2/src/main/res/values-uk/strings_settings.xml`

**Depends on:** start of phase

**Prompt for developer:**

> Hand-edit `strings_settings.xml` in all three locale variants (EN, RU, UK). Change `settings_tab_playback`: EN `"Playback"` → `"Player"`, RU `"Воспроизведение"` → `"Плеер"`, UK `"Відтворення"` → `"Плеєр"`. Change `settings_tab_operations`: EN `"Operations"` → `"Management"`, RU `"Операции"` → `"Управление"`, UK `"Операції"` → `"Управління"`. These two keys are defined at lines 121–122 in each locale file. Check `docs/COMMUNICATION_POLICY.md` §6 tone checklist: tab labels are capitalized nouns, no punctuation — confirm before saving.

**Verification:**

- `Grep` - `settings_tab_playback` in `values/strings_settings.xml` matches `"Player"`.
- `Grep` - `settings_tab_playback` in `values-ru/strings_settings.xml` matches `"Плеер"`.
- `Grep` - `settings_tab_playback` in `values-uk/strings_settings.xml` matches `"Плеєр"`.
- `Grep` - `settings_tab_operations` in `values/strings_settings.xml` matches `"Management"`.
- `Grep` - `settings_tab_operations` in `values-ru/strings_settings.xml` matches `"Управление"`.
- `Grep` - `settings_tab_operations` in `values-uk/strings_settings.xml` matches `"Управління"`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 6/6 PASS. Files: values/strings_settings.xml, values-ru/strings_settings.xml, values-uk/strings_settings.xml. Tab labels updated in all three locales.

---

### Step 1.2 - Update four reset_playback_section* strings to reference "Player"

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 1.1

**Prompt for developer:**

> Use `scripts/utils/set-android-string.ps1 -Action set` to update each of the four keys below. Run one call per key. Guard each call with `-ExpectedOldValue` matching the current value. All EN, RU, and UK variants must be updated in a single pass per key (the tool writes all locales automatically when a `values/strings.xml` is the target — verify that; otherwise set each locale file individually).
>
> New values (EN / RU / UK):
> - `reset_playback_section` → `"Reset Player settings"` / `"Сбросить настройки Плеера"` / `"Скинути налаштування Плеєра"`
> - `reset_playback_section_title` → `"Reset Player settings"` / `"Сброс настроек Плеера"` / `"Скидання налаштувань Плеєра"`
> - `reset_playback_section_message` → `"Reset only Player settings to default values?"` / `"Сбросить только настройки страницы «Плеер» к значениям по умолчанию?"` / `"Скинути тільки налаштування сторінки «Плеєр» до значень за замовчуванням?"`
> - `reset_playback_section_success` → `"Player settings reset completed"` / `"Настройки Плеера сброшены"` / `"Налаштування Плеєра скинуто"`
>
> After editing: run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix reset_playback_section`. Check `docs/COMMUNICATION_POLICY.md` §6 tone checklist: dialog text is clear, direct, no ellipsis in button labels.

**Verification:**

- `Grep` - `reset_playback_section` in `values/strings.xml` matches `"Reset Player settings"`.
- `Grep` - `reset_playback_section` in `values-ru/strings.xml` matches `"Сбросить настройки Плеера"`.
- `Grep` - `reset_playback_section_title` in `values/strings.xml` matches `"Reset Player settings"`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix reset_playback_section` → exit 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 checklist (verified manually before commit).

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 4/4 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. check_strings_localized.ps1 exit 0.

---

### Step 1.3 - Add four reset_operations_section* strings in all three locales

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 1.2

**Prompt for developer:**

> Use `scripts/utils/set-android-string.ps1 -Action add` to create each of the four keys below. Each `-Action add` call adds the key to EN/RU/UK in lockstep — pass `-En`, `-Ru`, `-Uk` parameters together.
>
> Keys and values:
> - `reset_operations_section`: EN `"Reset Management settings"` / RU `"Сбросить настройки управления"` / UK `"Скинути налаштування управління"`
> - `reset_operations_section_title`: EN `"Reset Management settings"` / RU `"Сброс настроек управления"` / UK `"Скидання налаштувань управління"`
> - `reset_operations_section_message`: EN `"Reset only Management tab settings to default values?"` / RU `"Сбросить только настройки страницы «Управление» к значениям по умолчанию?"` / UK `"Скинути тільки налаштування сторінки «Управління» до значень за замовчуванням?"`
> - `reset_operations_section_success`: EN `"Management settings reset completed"` / RU `"Настройки управления сброшены"` / UK `"Налаштування управління скинуто"`
>
> After adding: run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix reset_operations_section`. Strings pass `COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - `reset_operations_section` in `values/strings.xml` → matches `"Reset Management settings"`.
- `Grep` - `reset_operations_section` in `values-ru/strings.xml` → matches `"Сбросить настройки управления"`.
- `Grep` - `reset_operations_section` in `values-uk/strings.xml` → matches `"Скинути налаштування управління"`.
- `Grep` - `reset_operations_section_title` exists in all three locales.
- `Grep` - `reset_operations_section_message` exists in all three locales.
- `Grep` - `reset_operations_section_success` exists in all three locales.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix reset_operations_section` → exit 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 checklist (verified manually before commit).

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 7/7 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. check_strings_localized.ps1 exit 0. Communication policy: button labels are nouns, dialog text is clear and direct.

---

### Step 1.4 - Compile-check resources

**Files:** _(no source change - validation only)_
**Depends on:** Step 1.3

**Prompt for developer:**

> Run `.\a.ps1 fr` to validate all string resources and manifests compile without errors. Confirm `settings_tab_playback` and `settings_tab_operations` now resolve to the new values at build time.

**Verification:**

- Run `.\a.ps1 fr` → exit 0 (resource + manifest compilation passes).
- `Grep` - no occurrence of `"Playback"` in `values/strings_settings.xml` for key `settings_tab_playback`.
- `Grep` - no occurrence of `"Operations"` in `values/strings_settings.xml` for key `settings_tab_operations`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 3/3 PASS. .\a.ps1 fr → BUILD SUCCESSFUL in 7s. settings_tab_playback=Player, settings_tab_operations=Management confirmed.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] `.\a.ps1 fr` → exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Tab label strings are updated in all three locales. All four `reset_playback_section*` strings reference "Player". All four `reset_operations_section*` strings exist in EN/RU/UK. Phase 02 (ViewModel) and Phase 03 (Operations layout + code) may now begin in parallel since neither depends on the other.

---

## Rollback Plan

Revert the six `strings*.xml` edits — no data migration or schema change involved.
