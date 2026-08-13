# Стратегическая спецификация: S1147 - защита от дубликата URL при добавлении трансляции

**Ticket:** S1147
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - авто-захват из исследования S1145 (2026-07-22)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-22 (auto-parked by /spec-all during S1145 research, CLAUDE.md §3.1)

**Симптом:**

Ручное добавление трансляции с URL, который уже существует (MANUAL / IMPORTED / CATALOG), может уронить приложение необработанным `SQLiteConstraintException`.

**Доказательства:**

- `AddStreamSourceUseCase.invoke` не проверяет наличие строки с таким же URL перед `repository.add()` -> `dao.upsert()`. Файл: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCase.kt:15-33`.
- Room `@Upsert` разрешает конфликт только по первичному ключу; коллизия по отдельному уникальному индексу `index_stream_sources_url` (`StreamSourceEntity.kt:16-18`) не покрывается этим `ON CONFLICT`.
- `AddResult.Duplicate` объявлен, но помечен как недостижимый («a single upserting add cannot detect it here», `AddStreamSourceUseCase.kt:41-47`).
- Вызов идёт в `viewModelScope.launch` без обработчика исключений (`StreamsViewModel.onAdd`), поэтому исключение всплывает как краш.

**Связь:** тот же корень, что и High-риск в пути редактирования (закрыт в S1145 для Edit-пути). Здесь - отдельный код-путь Add.

**Объём:** нужен собственный дизайн проверки дубликата (переиспользовать `GetStreamSourceByUrlUseCase`) + понятное сообщение вместо краха + модульный тест. Не тривиально - отдельный тикет.

---

## 1. Проблема

Ручное добавление трансляции с уже существующим URL (MANUAL / IMPORTED / CATALOG) роняет приложение необработанным `SQLiteConstraintException`: `AddStreamSourceUseCase` вызывает `repository.add()` -> `dao.upsert()`, а `@Upsert` разрешает конфликт только по первичному ключу, не по уникальному индексу `index_stream_sources_url`. Исключение всплывает из `onAdd` (`viewModelScope.launch` без обработчика) как краш.

## 2. Цели

- Проверять дубликат URL перед вставкой в пути добавления, симметрично Edit-пути (S1145).
- Показывать понятное одноразовое сообщение вместо краха.
- Сделать `AddResult.Duplicate` достижимым и покрыть модульным тестом.

## 3. Пожелания и ограничения

- Переиспользовать `repository.getByUrl` (тот же контракт, что задействовал S1145), без новых DAO-запросов.
- Никаких изменений схемы Room и Hilt-графа.
- Сообщение о дубликате - существующая строка `streams_error_duplicate_url` (создана в S1145), без новых ресурсов.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1145 (Edit-путь того же дефекта), S0660 (базовые действия карточки трансляции)

## 4. Контекст текущей архитектуры

- `AddStreamSourceUseCase.invoke(url, title)` - вход пути добавления; сейчас валидирует схему и сразу вставляет.
- `StreamSourceRepository.getByUrl(url)` - готовый резолвер строки по URL (S0581).
- `StreamsViewModel.onAdd` - обрабатывает `AddResult.InvalidUrl`; ветка `Duplicate` отсутствует.
- Edit-путь (`UpdateStreamSourceUseCase` + `onEdit`) уже реализует ровно этот guard - образец для симметрии.

## 5. Предлагаемый подход

1. В `AddStreamSourceUseCase.invoke` после проверки схемы: `repository.getByUrl(trimmedUrl)` != null -> вернуть `AddResult.Duplicate` (self-id исключать не нужно - строка новая). Обновить KDoc `Duplicate` (перестал быть недостижимым).
2. В `StreamsViewModel.onAdd` добавить ветку `AddResult.Duplicate -> _events.send(Message(R.string.streams_error_duplicate_url))`, зеркально `onEdit`.
3. Модульный тест `AddStreamSourceUseCaseTest`: `getByUrl` возвращает строку -> `Duplicate`, вставка не вызвана; `getByUrl` null -> `Success`, вставка вызвана.

## 6. Открытые вопросы / Research items

Нет - дизайн выведен из готового Edit-пути (S1145).

## 7. Риски

- Низкий: дополнительное чтение `getByUrl` на каждое добавление - один индексный lookup, путь не горячий (ручное действие).

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений showcase-уровня: это защита от краха (FIX), не новая витринная возможность. Запись capability - `CHANGE` к существующей области Streams в `docs/ALL_FEATURES.jsonl`.

## 9. Архитектурные решения (ADR)

- **ADR-1:** guard живёт в use-case, а не в репозитории/DAO. Причина: симметрия с `UpdateStreamSourceUseCase` и явный доменный результат (`Duplicate`) вместо перехвата `SQLiteConstraintException` на слое данных.

## 10. Связи с другими спеками

- S1145 - Edit-путь того же дефекта (образец guard'а и источник строки `streams_error_duplicate_url`).

## 11. Критерии готовности (strategic-level)

- Добавление существующего URL показывает сообщение о дубликате и не роняет приложение.
- `AddStreamSourceUseCase` возвращает `Duplicate` при коллизии URL, `Success` при новом URL.
- Модульный тест покрывает обе ветки и проходит.

## Last Audit

2026-07-23, `/spec-sweep` on-device (emulator-5554, standard DEBUG v2.60.7230.145, API 35). Result: **Verified** (evidence `temp/scratch/spec-sweep_20260723_0148/s1147b.log`, `s1147c.log`).

- Duplicate URL: added a manual stream whose URL equals an existing channel (`https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`). Probe `S1147: onAdd url=..` fired; a Toast (duplicate-URL message) was shown (`NotificationService: Toast ... pkg=com.sza.fastmediasorter.debug`); no crash; stream not duplicated; StreamsActivity intact. Expected duplicate message + no crash - confirmed.
- New URL: added `https://newtest.example.com/sweep_s1147.m3u8`; probe `S1147: onAdd` fired and the new row appeared in the list ("newtest.example.com - без превью"); no crash. Expected normal add - confirmed.
- Debug probe `S1147:` removed on this Verified transition.
