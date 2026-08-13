# Стратегическая спецификация: S0259 — Миграция general + destinations на SettingsToggleRow

**Ticket:** S0259
**Status:** Verified
<!-- discovered by /spec-all - 2026-05-19 (carve-out from S0258) -->
**Priority:** 50
**Date:** 2026-05-19
**Tier:** 3 — Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc — продолжение S0258
**Parent spec:** [`S0258_settings-toggle-row-template.md`](S0258_settings-toggle-row-template.md)

> **Scope:** STRATEGIC. Завершить миграцию toggle-строк для general и destinations настроек, оставшихся вне зоны S0258.

---

## 1. Проблема

S0258 ввёл общий компонент `SettingsToggleRow` и провёл миграцию пилотного экрана (documents), четырёх простых фрагментов (video/audio/images/other) и большого playback. Оставшиеся два экрана — `fragment_settings_general` и `fragment_settings_destinations` — не были мигрированы в рамках S0258, потому что их миграция каскадирует в несколько helper-классов (`GeneralSettingsObserversHelper`, `GeneralSettingsViewSetupHelper`, `GeneralSettingsSectionsHelper`) и в `OperationsSettingsFragment` (использует destinations binding). Кроме того, на ветке DEBUG-v004 идёт параллельная работа S0254 (revised settings refactor), которая удалила `fragment_settings_revised_*.xml` слои и оставила в `SettingsSearchIndex.kt` 6 dead-ref entries (switchAllowDelete, switchGridMode, etIconSize, switchHideGridActionButtons, switchFileOpsOverflowMenu, switchDisableCameraCapture).

Эта спека закрывает оставшийся scope: миграция general/destinations + чистка dead refs после того, как S0254 определит финальные IDs.

---

## 2. Цели

1. `fragment_settings_general.xml` и его landscape parity мигрированы на `SettingsToggleRow`.
2. `fragment_settings_destinations.xml` и его landscape parity мигрированы на `SettingsToggleRow`.
3. `GeneralSettingsFragment` + 3 helper-класса (`Observers`, `ViewSetup`, `Sections`) переведены на row API.
4. `OperationsSettingsFragment` переведён на row API для destinations rows.
5. `SettingsSearchIndex.kt` синхронизирован с финальными ID после S0254 — все 6 dead-ref entries заменены на корректные `R.id.row*` (или удалены, если соответствующая функция упразднена).
6. Все touched layouts проходят portrait/landscape parity gate.
7. Build `standardDebug` проходит без warnings новых, не относящихся к pre-existing kotlin issues.

**Non-goals:**

- Изменение поведения отдельных тогглов.
- Перерисовка любых других экранов настроек, не входящих в general/destinations.
- Изменение бизнес-логики `SettingsViewModel` или `SettingsRepository`.

---

## 3. Жёсткие ограничения

- **Flavor:** все flavor `app_v2`.
- **API level:** без API-специфичных решений.
- **Wear OS:** не затрагивается.
- **Производительность:** компонент уже инфлейтится по тому же контракту, что и existing rows.
- **Совместимость данных:** существующие ключи настроек не меняются.
- **Локализация:** EN/RU/UK обязательны для любых новых subtitle/help-строк.
- **Доступность:** keyboard / D-pad / mouse / touch — наследуется от `SettingsToggleRow`.
- **Coordination with S0254:** перед началом миграции — проверить актуальное состояние `SettingsSearchIndex` и убедиться, что S0254 либо завершён, либо его pending IDs определены явно.

---

## 4. Контекст текущей архитектуры

`SettingsToggleRow` (compound view) и его layout уже введены в проекте через S0258. `BaseSettingsFragment` уже имеет overload `bindSwitch(row: SettingsToggleRow, ...)` и `setSwitchChecked(row: SettingsToggleRow, ...)`. Migration recipe отработан и закреплён в `docs/ARCHITECTURE.md` § "UI Patterns - Trigger Row".

General settings уникален тем, что вынес setup-логику в 3 helper-класса (`GeneralSettingsViewSetupHelper`, `GeneralSettingsObserversHelper`, `GeneralSettingsSectionsHelper`). Поэтому миграция требует синхронной правки всех трёх helper'ов, а не только самого фрагмента.

Destinations layout (`fragment_settings_destinations.xml`) консумирует `OperationsSettingsFragment` (а не `DestinationsSettingsFragment` — историческое расхождение в naming). Это поведение сохраняется.

---

## 5. Предлагаемый подход

Тот же recipe, что уже отработан S0258:

1. Заменить ad-hoc `LinearLayout + SwitchMaterial + TextView (+ ImageButton help)` блоки на `<com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow>` с `app:str_title`, `app:str_subtitle`, `app:str_showHelp` + `app:str_helpTitle`/`app:str_helpMessage`.
2. Naming: `switch<X>` → `row<X>`. ID consistency между portrait и landscape — обязательна.
3. Сложные dependent-row patterns (where switch toggles visibility of sub-container) — выносим row на верхний уровень, container преобразуется в просто wrapper над dependent rows.
4. Helper-классы и фрагмент перенаправляют bindings на `binding.row<X>` через base overloads.
5. После завершения миграций — синхронизировать `SettingsSearchIndex.kt`: убрать `viewId = 0` placeholder'ы, заменить на финальные `R.id.row<X>`.

### 5.1 Этапы

1. Coordination check: подтвердить состояние S0254 (либо завершён, либо `R.id.switch<X>` для grid_mode/icon_size/hide_grid_action_buttons/file_ops_overflow_menu/disable_camera_capture определены).
2. Миграция destinations XML (portrait + landscape) + `OperationsSettingsFragment.kt`.
3. Миграция general XML (portrait + landscape) + `GeneralSettingsFragment.kt` + 3 helper'ов.
4. Sweep `SettingsSearchIndex.kt`: финальные `R.id.row<X>` для всех ранее dead-ref entries.
5. Build gate.

---

## 6. Открытые вопросы / Research items

1. **Зависимость от S0254**
   - **Вопрос:** должен ли S0259 ждать завершения S0254, или может работать параллельно через осторожный sweep?
   - **Решение:** ждать. S0254 определяет финальные ID для `grid_mode/icon_size/hide_grid_action_buttons/file_ops_overflow_menu/disable_camera_capture`. Без них S0259 не может закрыть SettingsSearchIndex.
   - **Статус:** Resolved.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| S0254 затягивается → S0259 не может закрыть SettingsSearchIndex | Средняя | Заблокирована финальная фаза | Spec помечается `BlockByOtherTask` до завершения S0254 |
| Каскад helper-зависимостей — больше файлов, чем виделось | Средняя | Build break, ручной откат | Брать общий paint-by-numbers подход из S0258; делать helper-by-helper, а не "сразу всё" |
| Dependent-row patterns в general/destinations отличаются от уже мигрированных фрагментов | Средняя | Регрессии в видимости sub-containers | Юнит-проверка каждой dependent group в тестовом сценарии перед закрытием фазы |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений. Инфраструктурное завершение унификации toggle-строк, без новой пользовательской функции.

---

## 9. Архитектурные решения (ADR)

**ADR-1: S0259 наследует ADR-1..3 родительской S0258**

- **Решение:** компонент, helper-inline pattern, фазированный rollout — все ADR S0258 применяются без изменений.
- **Альтернативы:** —.
- **Почему:** consistency и завершение начатого scope.

---

## 10. Связи с другими спеками

- **Parent:** S0258 — ввёл компонент и мигрировал пилот + 5 экранов (documents/video/audio/images/other/playback).
- **Blocking:** S0254 — revised settings refactor; должен поставить финальные ID для 6 ныне dead-ref entries в SettingsSearchIndex.

---

## 11. Критерии готовности (strategic-level)

1. `fragment_settings_general.xml`: 0 `SwitchMaterial` в portrait и landscape. `SettingsToggleRow`: portrait ≥10, landscape ≥8 — три portrait-only ряда (`rowCompactElements`, `rowEnableThumbnailPreload`, `rowThumbnailPreloadWifiOnly`) сохранены через nullable binding (см. Phase 02.1 step log).
2. `fragment_settings_destinations.xml` + landscape: 0 `SwitchMaterial`, ≥10 `SettingsToggleRow` в каждом.
3. `GeneralSettingsFragment.kt` + 3 helper-класса: 0 `binding.switch*`, использование row API через `BaseSettingsFragment` overloads.
4. `OperationsSettingsFragment.kt`: 0 `binding.switch*` для destinations rows.
5. `SettingsSearchIndex.kt`: 0 `viewId = 0  // TODO(S0254)`, все entries указывают на существующие row IDs.
6. Portrait/landscape parity сохраняется для каждого мигрированного экрана.
7. `a.ps1 dq` проходит без новых compile errors.

---

## 12. Ссылка на тактическую спецификацию

**Tactical plan:** `PLAN/S0259_settings-toggle-row-general-destinations/INDEX.md`

**Implemented date:** 2026-05-19

Следующий шаг: `/spec-dev S0259` — выполнить тактические фазы.

---

## Last Audit

**Date:** 2026-05-20
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 20 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] (none — все criteria подтверждены статикой; Phase 04.3 уже зафиксировал успешный `assembleStandardDebug`).

---

## Revision History

- **2026-05-20** — by `/spec-update` (claude-sonnet-4-5, focus: verifiability, `--force-locked`)
  - Applied: 1. Proposed (DISCUSS): 0.
  - Override reason: спека в `Partial` из-за WARN, выписанного `/spec-check` против §11.1; переформулирование критерия — это и есть фикс. После /spec-check вердикт ожидаемо станет `Verified`.
  - §11.1 переписан под реальную архитектуру: portrait ≥10, landscape ≥8 с тремя portrait-only рядами (`rowCompactElements`, `rowEnableThumbnailPreload`, `rowThumbnailPreloadWifiOnly`), задокументированными в Phase 02.1 step log.
