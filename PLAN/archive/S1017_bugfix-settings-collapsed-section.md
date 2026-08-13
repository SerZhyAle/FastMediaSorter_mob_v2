# Спецификация (compact bugfix): S1017 - Maestro settings_toggle_sweep не находит rowEnableFavorites в свёрнутой секции

**Ticket:** S1017
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-12
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

Maestro flow settings_toggle_sweep.yaml fails reproducibly (both smoke and full suite, /spec-prerelease sweep 2026-07-12): scrollUntilVisible cannot find id=rowEnableFavorites within 8000ms timeout on the General settings tab. Root cause per workflow evidence: the row lives inside a CollapsibleSectionHeader-gated container "containerMainWindowInterface" which is collapsed by default, so the element is absent from the view tree until that section is expanded - the flow does not expand the section first. Unclear whether this is a Maestro flow authoring bug (flow needs to tap the section header to expand before scrollUntilVisible) or a product regression (the section used to be expanded by default / this row used to live outside it). Needs investigation of maestro/smoke/settings_toggle_sweep.yaml against the current Settings screen layout to determine which. Evidence: maestro run log P:\ANDROID\FastMediaSorter_mob_v2\temp\settings_toggle_sweep_maestro_20260712_204811.log; full-suite results temp/S0484/maestro_suite_20260712_193755.json (17 flows, 16 pass, this one fails); smoke suite showed identical failure with retry also failing (not a flake). Verified NOT the known phantom-offline-device infra issue (adb devices showed exactly one online emulator-5554 throughout).

---

## 1. Проблема / симптом

Maestro-флоу `maestro/smoke/settings_toggle_sweep.yaml` стабильно (не флап) падает и в smoke-, и в full-сьюте `/spec-prerelease` (2026-07-12): `scrollUntilVisible` не находит `id=rowEnableFavorites` на вкладке General за 8000мс. Элемент физически лежит внутри контейнера `containerMainWindowInterface`, управляемого `CollapsibleSectionHeader` и свёрнутого по умолчанию - поэтому элемента нет в дереве вида, пока секция не развёрнута, а флоу её не разворачивает.

---

## 2. Корневая причина

Гипотеза (а) - **баг авторства Maestro-флоу**. Расследование против текущей разметки:

- `rowEnableFavorites` (и `rowAllowSeparateWindow`) лежат в контейнере `containerInterface` под заголовком `headerInterface` секции "Interface" (`fragment_settings_general.xml`, стр.24-113), а не в `containerMainWindowInterface` - имя контейнера в §0-evidence перепутано, но структурная причина верна.
- `GeneralSettingsFragment` (стр.323-327) регистрирует все секции через `sectionsManager.register(..., defaultExpanded = false)` - секция "Interface" свёрнута по умолчанию.
- `CollapsibleSectionsManager.register` (стр.43-47) при `defaultExpanded=false` и пустом сторе ставит `container.isVisible = false` - ряды физически отсутствуют в дереве вида, `scrollUntilVisible` не найдёт их никаким скроллом.
- `/spec-prerelease` делает clean install -> `CollapsibleSectionStore` пуст -> секция гарантированно свёрнута на старте прогона.

Это не продуктовая регрессия: свёрнутое по умолчанию состояние секций - намеренное поведение (S0911), а ряд перемещён в секцию Interface осознанно (коммент `Moved from Sleep/Favorites container`, стр.82). Флоу просто не разворачивает секцию перед `scrollUntilVisible`.

---

## 3. Исправление

Доправить `maestro/smoke/settings_toggle_sweep.yaml`: перед первым `scrollUntilVisible` на `rowEnableFavorites` добавить разворот секции Interface по устоявшемуся в репозитории идиому (ср. `maestro/features/resource/resource_edit_toggles.yaml`, стр.24-31):

- `scrollUntilVisible` -> `headerInterface`
- `tapOn` -> `headerInterface`
- `waitForAnimationToEnd`

Один разворот покрывает оба ряда (`rowEnableFavorites`, `rowAllowSeparateWindow`) - они в одном контейнере. Продуктовый код не меняется.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Статически: YAML содержит tap по `headerInterface` до `scrollUntilVisible` на `rowEnableFavorites`.
- На устройстве: `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke\settings_toggle_sweep.yaml -DeviceId <id> -Json` возвращает `pass=true` (в этой сессии устройство не подключено - проверка отложена).
