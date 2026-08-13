# S0479 - Декомпозиция раздела операций (внутренняя, группа B к S0474)

**Ticket:** S0479
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** группа B спецификации S0474, вынесена в отдельный черновик по решению владельца 2026-06-17
**Tactical plan:** `PLAN/S0479_settings-operations-section-decomposition/INDEX.md`

> **Scope:** структурный трек по настройкам, отделённый от безопасных оптимизаций загрузки (S0474 = группа A, выполнена и архивирована). Здесь - декомпозиция переросшего `OperationsSettingsFragment` без изменения навигации и видимого UX.

---

## 0. Raw capture / контекст

Выделено из S0474 («Работоспособность экрана настроек»). S0474 покрывает группу A - невидимые пользователю оптимизации загрузки. Эта спека - группа B - структурная переработка раздела операций.

**Решения владельца (2026-06-17), зафиксированы как входные ограничения:**
- Список назначений (destinations) остаётся встроенным в раздел операций - не выносить (центральная концепция, discoverability). Его ускорение - в рамках S0474, не здесь.

**Evidence (из research S0474, архив `temp/done/S0474_settings-activity-perf-research/research/`):**
- `03__embedded-tables-inventory.md` - каталог встроенных списков (T1 destinations, T2 scheduled, T3 send commands).
- `04__improvement-options.md` - группа B, варианты B1/B2.

---

## 2. Цели

- Снизить `OperationsSettingsFragment` (~1165 LOC) до сопровождаемого размера с запасом под лимит CLAUDE.md §2 (1500 LOC), без потери функциональности.
- Вынести бизнес-логику фрагмента в `*Manager`-хелперы согласно слоистой архитектуре (UI не несёт логики).
- Сохранить текущее поведение: видимый UX, навигация, флейвор-гейты, глобальный поиск по настройкам - без регресса.

## 3. Ограничения и охват

### 3.2 Технические ограничения

- Destinations (T1) и Scheduled Operations (T2) остаются встроенными в Operations-таб - без выноса в отдельный экран/диалог.
- Декомпозиция чисто внутренняя: извлечение логики во вспомогательные классы, без новых экранов, фрагментов и navigation-назначений.
- Глобальный поиск по настройкам (`SettingsSearchTabMapping`, `SettingsSearchDestination`, `ensureSectionExpanded`) должен продолжать находить и подсвечивать секции операций после перекомпоновки.
- Флейвор-гейты сохраняются: Scheduled скрыт при `ENABLE_SCHEDULED_OPERATIONS == false` (lite/photos) и доступен только в noLegal/vr.
- Без изменений Room-схемы, DI-графа поведения, публичных контрактов ViewModel.
- Миграция на androidx `PreferenceFragmentCompat` (вариант B2) отклонена для этой итерации (research §04: риск/выгода неблагоприятны).

### 3.3 Owner inputs (Approval gate)

- **Scheduled Operations:** оставить встроенным; декомпозиция только внутренняя (Manager-хелперы), без изменения навигации. (quiz 2026-06-19)
- **Destinations:** остаётся встроенным (решение владельца §0).
- **Видимый UX:** без изменений - чистый рефакторинг.
- **Related tickets:** S0474 (родитель, группа A, выполнена и архивирована).

## 5. Подход

### 5.1 Опорные точки (pillars)

- **P1. Извлечение Destinations:** логика списка назначений (`DestinationsAdapter`, наблюдение `viewModel.destinations`, adaptive layout, кнопки reorder/delete) → `OperationsDestinationsManager`.
- **P2. Извлечение Scheduled:** логика запланированных операций (`ScheduledOperationsAdapter`, наблюдение `ScheduledOperationsViewModel`, флейвор-гейт) → `OperationsScheduledManager`.
- **P3. Извлечение expandable-секций:** управление раскрытием/состоянием секций и чтение prefs → `OperationsSectionsManager`.
- **P5. Извлечение Capture:** строки съёмки (камера-фото, видеозапись, микрофон) и их селекторы назначений → `OperationsCaptureManager`.
- **P6. Извлечение Gestures:** строки overlay жестов-скриншотов (noLegal-only через инъецированный набор контроллеров) → `OperationsGesturesManager`.
- **P4. Худой фрагмент:** `OperationsSettingsFragment` сводится к проводке хелперов и lifecycle; целевой размер < 800 LOC. Достигается как сумма P1-P3, P5, P6, без отдельной фазы.

> Capture и Gestures - самостоятельные подгруппы того же переросшего фрагмента; их вынос - часть цели §2 (снижение размера, вынос бизнес-логики), а не изменение размещения/UX. Они runtime-гейтятся (`mediaCapabilities` / `Set<ScreenGestureOverlayController>`) и остаются в `src/main` - флейвор-сорссеты не требуются.

## 6. Открытые research-вопросы

- Нет открытых вопросов. Вынос Destinations/Scheduled в отдельные экраны (research §6.6 S0474) закрыт решением владельца: оба остаются встроенными.

## 9. Архитектурные решения (ADR)

- Декомпозиция через паттерн `NounVerbManager` (CLAUDE.md §10.6), хелперы под `ui/settings/helpers/` (рядом с существующими `GeneralSettings*Helper` / `*SettingsManager`). Фрагмент удерживает ссылки и делегирует; хелперы не держат Activity-контекст дольше lifecycle фрагмента.

## 11. Критерии приёмки

- `OperationsSettingsFragment` < 800 LOC после рефакторинга.
- Каждый извлечённый `*Manager` (Sections, Destinations, Scheduled, Capture, Gestures) - самостоятельный класс; во фрагменте не остаётся UI-бизнес-логики этих подгрупп.
- Видимое поведение Operations-таба идентично до/после: destinations, scheduled, capture, gestures, expandable-секции, флейвор-гейты.
- Глобальный поиск находит и раскрывает секции операций.
- Сборка `standard` и `noLegal` проходит; юнит-тесты затронутой области зелёные.

---

### Quiz decisions (2026-06-19)
- Что делать со Scheduled Operations при декомпозиции? → Оставить встроенным, декомпозиция только внутренняя (флейвор-гейтнутый список менее централен, но вынос за кнопку добавляет навигационный слой и риск; внутренняя декомпозиция через Manager-хелперы снимает объём фрагмента без UX-изменений).

## Last Audit

**Date:** 2026-06-19
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

### Manual / on-device

- [x] Operations tab renders all sections after decomposition - verified on-device 2026-06-19
- [x] All sections expand/collapse correctly; state persists across Settings close/reopen - verified on-device 2026-06-19
- [x] Destinations section: add button + list with reorder/delete controls visible - verified on-device 2026-06-19
- [x] Scheduled section: enable toggle + Add/Log/Clear action buttons present - verified on-device 2026-06-19
- [x] Capture section: camera/video/mic toggle rows render - verified on-device 2026-06-19
- [x] Global settings search finds Operations section entries - verified on-device 2026-06-19
- [x] No crashes throughout; 8/8 S0479: debug tags fired - verified on-device 2026-06-19
- [x] OperationsSettingsFragment: 519 LOC (< 800 target) - PASS
- [x] All 5 Manager classes declared and wired - PASS
- [x] 20 dev-log entries for S0479 in CHANGELOG - PASS
- [ ] Color picker interaction for destinations - not driven (emulator constraint, cosmetic)
- [ ] Scheduled op create/edit/delete dialog - not driven (bottom-sheet tap constraint on emulator)
- EXEMPT: FEATURES trilingual - pure internal refactor, §8 has no user-visible change

**Run:** 2026-06-19 · emulator-5554 (Pixel 4, API 35) · build 2.60.6191.257-DEBUG
**Scenario:** temp/S0479_device_test_20260619_1257/S0479_mobile_test_scenario_20260619_1257.md

## Revision History

- **2026-06-19** - by `/spec-test-device` (`emulator-5554`, Pixel 4 API 35, standard-debug 2.60.6191.257)
  - Scenario: temp/S0479_device_test_20260619_1257/S0479_mobile_test_scenario_20260619_1257.md · PASS/FAIL/SKIPPED 9/0/1 · log errors 0
  - All decomposed sections render + expand/collapse; state persists across reopen; search finds Operations entries; 8/8 S0479: debug tags fired; 0 crashes.
- **2026-06-19** - by `/spec-test-device` (`R5CY9070WNB`, Samsung, standard-debug 2.60.6190.149)
  - Scenario: temp/S0479_mobile_test_scenario_20260619_0149.md · PASS/PARTIAL/SKIPPED 6/1/1 · crashes 0 · app errors 0
  - Operations tab + all decomposed sections render and expand; 4/4 standard `S0479:` debug tags fired (sections/destinations/scheduled/capture); gestures tag noLegal-only. No app exception/crash.
