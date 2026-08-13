# Стратегическая спецификация: S0879 - Пробелы в CSV-матрице пресетов профилей устройства

**Ticket:** S0879
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-02

<!-- auto-approved by /spec-all - 2026-07-03 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Device profile preset matrix (app_v2/src/main/assets/device_profile_presets.csv) is missing rows for 6 AppSettings fields already shipped: screenRecordingEnabled, screenRecordingDestinationResourceId, screenRecordingDisclosureAccepted (S0774), resourceTypeTabCollapsed (S0781), programsPanelCollapsed (S0807), streamsPanelCollapsed (S0808). Discovered via `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` while implementing S0820 (unrelated ticket) - it reported "INCONSISTENT" with these 6 fields missing from CSV rows. Each needs a decision on per-profile preset values (or explicit empty/no-override row) before running -AddMissing and filling them in. Not fixed inline because it requires its own research into what preset value each field should carry per device profile, unrelated to S0820's scope.

---

## 1. Проблема

`scripts/check_device_profile_presets.ps1` сообщает INCONSISTENT: 187 полей AppSettings против 181 строки CSV - 6 отгруженных полей отсутствуют в матрице пресетов. Матрица дрейфует от модели настроек, а гейт шумит для несвязанных тикетов (впервые упёрся S0820). На пользователя влияния нет: отсутствующая строка и no-override-строка ведут себя одинаково.

---

## 2. Цели

1. CSV-матрица покрывает все поля AppSettings; чекер завершается с exit 0 (CONSISTENT).
2. Семантика пресетов для 6 полей не меняется: явные no-override-строки (все ячейки пустые).

**Non-goals:**

- Per-profile тюнинг значений capture- и panel-полей (если владелец захочет - отдельный тикет).
- Изменения загрузчика пресетов или чекера.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Нет - тикет порождён гейтом, не запросом.

### 3.2 Жёсткие ограничения

- **Flavor:** все (asset лежит в `src/main/assets`).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** не затрагивается (данные, не строки UI).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0774, S0781, S0807, S0808 - тикеты, добавившие поля, которых сейчас нет в CSV-матрице.

---

## 4. Контекст текущей архитектуры

Матрица пресетов применяется при выборе профиля устройства: загрузчик читает CSV из assets, пустая ячейка означает «профиль не переопределяет это поле», применение идёт через use case поверх репозитория настроек. Чекер сверяет список полей AppSettings со строками CSV и падает, пока строки не добавлены - сама логика приложения от пропуска не страдает.

---

## 5. Предлагаемый подход

Добавить 6 явных no-override-строк (все 11 колонок профилей пустые) через штатный `-AddMissing` самого чекера. Решение по значениям выводится из семейных прецедентов уже существующих строк, owner-ввод не требуется:

- `screenRecordingDisclosureAccepted` - согласие пользователя; прецедент `screenCaptureDisclosureAccepted` - no-override. Consent никогда не пресетится профилем.
- `screenRecordingDestinationResourceId` - runtime-идентификатор зарегистрированного ресурса; прецеденты `screenshotDestinationResourceId`, `videoRecordingDestinationResourceId`, `micRecordingDestinationResourceId` - все no-override.
- `screenRecordingEnabled` - capture-семья; прецеденты `gestureOverlayEnabled`, `screenshotGestureStripVisible`, `copyScreenshotToClipboard` - все no-override. Дефолт false в AppSettings сохраняется для всех профилей.
- `resourceTypeTabCollapsed`, `programsPanelCollapsed`, `streamsPanelCollapsed` - transient-состояние сворачивания UI-панелей; прецеденты `copyPanelCollapsed`, `showProgramsPanelInMainWindow`, `showStreamsPanelInMainWindow` - no-override.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет - значения решены семейными прецедентами существующих строк матрицы (см. §5).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ломаный CSV-формат после ручной правки | Низкая | Загрузчик пресетов молча игнорирует строку | Строки генерирует сам чекер (`-AddMissing`); повторный прогон подтверждает parse и консистентность |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - поведение пресетов идентично (no-override).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (семейные прецеденты матрицы).

---

## 10. Связи с другими спеками

- S0774 - добавил screenRecording*-поля.
- S0781 - добавил resourceTypeTabCollapsed.
- S0807 - добавил programsPanelCollapsed.
- S0808 - добавил streamsPanelCollapsed.
- S0820 - тикет, во время которого гейт обнаружил пропуск.

---

## 11. Критерии готовности (strategic-level)

1. `check_device_profile_presets.ps1` завершается с exit 0 (CONSISTENT, 187/187).
2. В CSV появились 6 строк с пустыми значениями во всех 11 колонках профилей.
3. Поведение применения пресетов не изменилось ни для одного профиля.

---

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- Fix: `device_profile_presets.csv` - 6 явных no-override-строк добавлены штатным `check_device_profile_presets.ps1 -AddMissing`; значения решены семейными прецедентами (§5), owner-ввод не потребовался.
- Валидация: чекер - expected: exit 0, 187/187 | actual: exit 0, 187/187 (CONSISTENT); tail CSV - expected: 6 строк по 11 пустых ячеек | actual: 6 строк по 11 пустых ячеек.
- Кода нет (asset-only), билд не требуется; поведение пресетов идентично - no-override-строка эквивалентна отсутствующей по контракту загрузчика.
