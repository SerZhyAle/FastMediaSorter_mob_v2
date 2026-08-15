# Стратегическая спецификация: S0650 - Переименование группы "Camera, microphone and other functions"

**Ticket:** S0650
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical spec:** будет создан через `/spec-tech`

> **Scope:** STRATEGIC. Draft-инбокс.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

группу настроек "Камера, микрофон и другие функции" переименовать в "Фото, Видео, Диктофон"

**Ключевые требования из запроса:**

- Экран: Settings -> Operations.
- Переименовать существующую группу настроек "Camera, microphone and other functions".
- Новое имя группы: "Фото, Видео, Диктофон".

**Что ожидается позже при доработке:**

- Найти все locale strings и layout references, связанные с заголовком группы.
- Привести название к новому варианту без рассинхрона между portrait/landscape и search/docs, если они завязаны на этот label.

**Вложения:** нет.

---

## 1. Реализация

- Переименован ключ `settings_category_other_features` (заголовок группы `headerOtherFeatures` на экране Settings -> Operations).
- EN: `Photo, Video, Voice recorder`.
- RU: `Фото, Видео, Диктофон`.
- UK: `Фото, Відео, Диктофон`.
- Перегенерирован `docs/settings/settings-manifest.json` (gradle `-Dsettings.manifest.generate=true`).
- Обновлена аннотация `headerOtherFeatures` в `docs/settings/settings-annotations.json` под новый смысл группы.
- Перерендерены `docs/SETTINGS_REFERENCE*.md` (включая noLegal).
- Ссылок на старое имя группы в `docs/HOW_TO*`/прочих доках нет - S0558 gate не затронут.

## 2. Проверка на устройстве

- Открыть Settings -> Operations, найти группу-заголовок капчер-настроек (камера/видео/микрофон).
- Заголовок читается как новое имя в текущей локали (EN/RU/UK).
- Logcat-тег `S0650:` появляется при открытии экрана.

## Last Audit

**Date:** 2026-06-24
**Mode:** strategic
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1 (FEATURES: rename of an existing group header, no user-facing capability change)

Device sweep (emulator-5554, API 37) confirmed the Operations capture group header renders the renamed string on-device in EN (`Photo, Video, Voice recorder`) and RU (`Фото, Видео, Диктофон`); UK (`Фото, Відео, Диктофон`) static-verified. The `S0650:` probe fired on screen open (2 hits). Debug tag removed from `OperationsSettingsFragment.kt` on this Verified flip (the co-located `S0651:` probe was preserved - that ticket is still BlockNeedUserTest).

### Manual / on-device

- [x] Settings -> Operations capture group header reads new name in RU (`Фото, Видео, Диктофон`) - verified on-device 2026-06-24
- [x] Settings -> Operations capture group header reads new name in EN (`Photo, Video, Voice recorder`) - verified on-device 2026-06-24
- [x] UK string `settings_category_other_features` resolves to `Фото, Відео, Диктофон` - verified by static fallback (locale not driven on-device) 2026-06-24
- [x] Logcat tag `S0650:` (debug) fires on Operations screen open - verified on-device 2026-06-24 (2 hits: RU + EN screen opens, `D/OperationsSettingsFragment`)

## Revision History

- **2026-06-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android API)
  - Scenario: temp/S0650_mobile_test_scenario_20260624_1346.md · PASS/FAIL/SKIPPED 4/0/0 · Errors in log: 0 (app-side)
