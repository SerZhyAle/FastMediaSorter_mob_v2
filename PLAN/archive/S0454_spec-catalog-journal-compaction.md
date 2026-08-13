# Стратегическая спецификация: S0454 - Компактизация журнала spec-catalog

**Ticket:** S0454
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-16 (находка при чистке проекта)
**Tactical spec:** `PLAN/S0454_spec-catalog-journal-compaction/` (будет создан через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-06-16 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-16

**Текст:**

spec-catalog-journal-compaction — split Archived entries (422 of 452) into a separate spec-catalog-archive.jsonl so select.ps1/search.ps1 scan only ~30 active tickets instead of the full journal every call. Symptom: every catalog read scans 452 lines, 93% of which are soft-deleted Archived tickets, slowing routine select/search across all Sxxxx work. Evidence: grep status distribution shows 422 Archived / 452 total (2026-06-16). Goal: keep active-journal scans proportional to active ticket count; archive.ps1 moves entries to the archive file; select/search transparently fall back to archive only when an id is not found in the active journal. Out of scope of disk cleanup task; needs tooling-design research.

**Вложения:**

Вложений нет.

**Захвачено во время:** чистка проекта (disk/Claude hygiene), без активного Sxxxx.

---

## 1. Проблема

Журнал `PLAN/spec-catalog.jsonl` содержит 463 строки, из них 431 (93%) - мягко удалённые тикеты в статусе `Archived`. Каждый вызов `select.ps1`/`search.ps1`/`preview.ps1` и любого мутатора читает и парсит весь журнал целиком через `Read-Catalog`, то есть платит за 431 неактивную запись при работе с ~30 активными. Эффект - постоянное замедление рутинных операций каталога во всех потоках работы со спеками (включая `/spec-next`), линейно растущее по мере накопления архива.

---

## 2. Цели

1. Сканирование активного журнала пропорционально числу активных тикетов, а не общему числу когда-либо созданных.
2. Архивные записи вынесены в отдельный файл, не читаемый на «горячем» пути.
3. Резолюция по id остаётся прозрачной: запрос архивного тикета по-прежнему находит запись (fallback в архивный файл при промахе в активном).
4. Архивирование тикета физически перемещает запись из активного журнала в архивный.

**Non-goals:**

- Изменение формата самой JSONL-записи (схема полей неизменна).
- Удаление архивных записей или сжатие их содержимого.
- Изменение поведения disk-cleanup задачи (вне объёма).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимальное изменение публичного контракта скриптов: существующие вызовы `select.ps1`/`search.ps1`/мутаторов работают без правок у потребителей.
2. Полные обзоры (включая архив) остаются доступны по явному флагу.

### 3.2 Жёсткие ограничения

- **Flavor:** не затрагивает сборку приложения - только tooling в `scripts/spec_catalog/`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** цель - «горячий» путь читает только активный журнал.
- **Совместимость данных:** одноразовая миграция переносит существующие `Archived` записи; обратная совместимость - при отсутствии архивного файла поведение прежнее.
- **Локализация:** не применимо (внутренний tooling, без user-facing строк).
- **Доступность:** не применимо.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Все скрипты каталога разделяют `scripts/spec_catalog/_lib.ps1`, который инкапсулирует чтение (`Read-Catalog`), запись (`Write-Catalog`) и поиск (`Find-Record`) единственного журнала `PLAN/spec-catalog.jsonl`. `archive.ps1` помечает запись `Archived` прямо в этом журнале (status-флип + priority→0), оставляя её среди активных строк. Проблему §1 нельзя решить точечно у потребителей, потому что замедление встроено в общий слой чтения - правка нужна в `_lib.ps1`, а не в отдельных командах.

---

## 5. Предлагаемый подход

Расщепить хранилище на два файла за единым библиотечным слоем, сохранив публичный контракт команд.

### 5.1 Основные столпы / модули

- Активный журнал - только не-`Archived` записи; читается по умолчанию.
- Архивный журнал - только `Archived` записи; читается лишь по явному запросу или при fallback-резолюции id.
- Библиотечный слой - инкапсулирует выбор файла(ов) для чтения/записи; точка истины для «горячего» против «полного» доступа.

### 5.2 Потоки данных и событий

- Горячий путь (ранжирование/выбор/превью) → читает только активный журнал.
- Резолюция по id → активный журнал, при промахе → архивный (прозрачно).
- Полный обзор (audit/stats) → активный + архивный по явному флагу.
- Архивирование → перенос записи из активного журнала в архивный.

### 5.3 Точки расширяемости

- Возможная будущая ротация/сегментация архивного файла без правок потребителей.
- Флаг включения архива переиспользуем любыми обзорными командами.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Мутатор теряет архивную запись при read-modify-write активного журнала | Средняя | Потеря данных архива | Запись активного слоя пишет только активный файл; архив трогается лишь `archive.ps1` и миграцией |
| Резолюция архивного id ломается | Средняя | `select`/`preview` не находят старые тикеты | `Find-Record` с fallback в архив + покрывающая проверка |
| Частичная миграция при сбое | Низкая | Дубликаты/расхождение между файлами | Атомарная запись (temp + Move-Item), миграция идемпотентна |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (централизация в `_lib.ps1`, атомарная запись через temp + `Move-Item`).

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Активный журнал содержит только не-`Archived` записи; архивные вынесены в отдельный файл.
2. `select.ps1`/`search.ps1`/`preview.ps1` по умолчанию читают только активный журнал.
3. Запрос по id архивного тикета по-прежнему возвращает запись.
4. `archive.ps1` перемещает запись в архивный файл (не оставляет в активном).
5. Полный обзор с архивом доступен по явному флагу.
6. Все существующие мутаторы каталога работают без потери архивных данных.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0454` - создаст `PLAN/S0454_spec-catalog-journal-compaction/` с фазами.

---

## Last Audit

**Date:** 2026-06-16
**Mode:** full (inline, /spec-all)
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0

> Pure tooling change in `scripts/spec_catalog/`; no app build, no device test. All six strategic done-criteria verified mechanically via `validate.ps1` (9 OK / 0 FAIL incl. the new `ArchiveSplit` invariant) and a full routing round-trip.

### Done-criteria verification

1. Active journal holds only non-`Archived` records - `validate.ps1` ArchiveSplit: 32 active / 431 archived; 0 stray, 0 misfiled.
2. `select`/`search`/`preview` default to active only - `search.ps1` default returns 32, `-IncludeArchived` returns 463.
3. Archived id still resolves - `select.ps1 -Id S0001` returns the archived record via `Find-Record` fallback.
4. `archive.ps1` relocates the record - round-trip: id leaves `spec-catalog.jsonl`, enters `spec-catalog-archive.jsonl`, stays resolvable; re-archive is a no-op (no duplicate row).
5. Full review available by flag - `Read-Catalog -IncludeArchived` / `stats.ps1` / `validate.ps1` see all 463.
6. No mutator data loss - all four transitions verified (active↔active, active→archive via update/close/delete/bulk, archive→active revive via update); `validate.ps1` Schema + Uniqueness clean, 0 duplicate ids, 463 total preserved.

### Notes

- Backward compatible: archive journal absent → single-journal behaviour.
- One-time migration `migrate-archive-split.ps1` is idempotent (second run moves 0).
- Reviving an archived id in bulk is out of scope (`update.ps1` covers single-id revive).
