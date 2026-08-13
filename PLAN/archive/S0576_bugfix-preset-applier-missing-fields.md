# Стратегическая спецификация: S0576 - Preset applier silently skips CSV fields

**Ticket:** S0576
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-21
**Tactical spec:** `PLAN/S0576_bugfix-preset-applier-missing-fields/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-21

**Захвачено во время:** S0575 research (streams toggle / per-profile default)

**Текст:**

DeviceProfilePresetApplier silently skips device-profile preset fields that have rows in device_profile_presets.csv but no matching `when` branch in applyOverride(). Symptom: a non-empty CSV cell for such a field is dropped via the fall-through `else -> skip(field, raw, settings)` (DeviceProfilePresetApplier.kt:225) instead of being applied to AppSettings. Confirmed missing cases for CSV rows: smbEnabled, sftpEnabled, ftpEnabled, googleDriveEnabled, gestureOverlayEnabled, enabledShareTargets, disabledShareTargets, playerFollowSystemRotation (and likely others - full audit needed). Currently benign because those CSV rows are empty, so no value is lost today; but any future per-profile default for these fields would silently no-op with only a Timber.w, making the matrix appear authoritative when it is not. Evidence: applier `when` block has no branch for the listed fields (grep returned only `else ->` lines); CSV has explicit rows for all of them. Fix scope: audit every CSV row against applier branches, add the missing cases (with correct type coercion bool/enum/csv-list), and extend ApplyProfilePresetUseCaseTest. Discovered during S0575 research (streams toggle / per-profile default), out of scope for that ticket.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Применитель пресетов устройства молча пропускал часть полей, у которых есть строка в матрице
device-profile CSV, но нет ветки в `when`-блоке: непустая ячейка падала в `else -> skip` с одним лишь
`Timber.w`, вместо записи в `AppSettings`. Сегодня это безвредно (ячейки пусты), но любой будущий
per-profile default по этим полям тихо превратился бы в no-op, делая матрицу мнимо авторитетной.

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S0575 (streams toggle - находка обнаружена при его исследовании; не зависимость)

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» Если фича новая - одно предложение для FEATURES + _RU + _UK.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

1. Каждая строка CSV, соответствующая реальному пользовательскому полю настроек, применяется
   применителем с корректной коэрцией типа (bool/enum/csv-list/string).
2. State/credential/pointer-поля (defaultUser, defaultPassword, `*ResourceId`, lastUsedResourceId,
   scheduledOperationsPaused, enableStatistics и т. п.) по-прежнему не применяются.
3. Тесты применителя покрывают новые типы коэрции и подтверждают пропуск state-полей и неизвестных
   enum-значений.

## Implementation note (2026-06-21)

Добавлены ветки в `DeviceProfilePresetApplier.applyOverride`:

- bool: smbEnabled, sftpEnabled, ftpEnabled, googleDriveEnabled, oneDriveEnabled, dropboxEnabled,
  gestureOverlayEnabled, copyScreenshotToClipboard, playerFollowSystemRotation.
- enum (ScreenshotGestureAction, skip-on-unknown): screenshotGestureActionDown/Right/Up.
- string-set (разделители `,` `;` `|`): enabledShareTargets, disabledShareTargets.
- string verbatim: videoSnapshotFormat.

Намеренно остались без веток (падают в `else -> skip`): defaultUser, defaultPassword, `*ResourceId`,
slideshowMusicUri, lastUsedResourceId, lastSelectedLocalFolder, scheduledOperationsPaused,
enableStatistics.

Проверка: `ApplyProfilePresetUseCaseTest` (6 тестов, 0 failures), `check_device_profile_presets.ps1`
(exit 0). Поведения для пользователя не меняет - целевые CSV-ячейки пусты; это hardening матрицы.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0576` - создаст `PLAN/S0576_bugfix-preset-applier-missing-fields/` с фазами.

---

## Last Audit

**Date:** 2026-06-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 18 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

All 12 newly-covered applier branches present (bool: smbEnabled, sftpEnabled, ftpEnabled, googleDriveEnabled, oneDriveEnabled, dropboxEnabled, gestureOverlayEnabled, playerFollowSystemRotation; enum: screenshotGestureActionDown/Right/Up; string-set: enabledShareTargets, disabledShareTargets; string: videoSnapshotFormat). State/credential fields (defaultUser, defaultPassword, scheduledOperationsPaused, enableStatistics, `*ResourceId`) correctly retain no branch (fall through `else -> skip`). `ApplyProfilePresetUseCaseTest` carries both the newly-covered-fields test and the state-fields-skip test; `check_device_profile_presets.ps1` exit 0 (171 fields = 171 CSV rows).

### Manual / on-device

- [ ] None required - pure matrix-applier hardening; first-run behaviour unchanged because the targeted CSV cells are still empty. Validated by unit test + preset-consistency gate, no device step.
