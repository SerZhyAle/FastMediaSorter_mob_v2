---
ticket: S0314
status: BlockNeedUserTest
priority: 55
date: 2026-05-31
tier: 3
parent: S0311
---

# Стратегическая спецификация: S0314 - Catalog dependency and test enrichment

**Ticket:** S0314
**Status:** BlockNeedUserTest
**Priority:** 55
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Parent:** S0311 (agent tooling umbrella)
**Tactical plan:** `PLAN/S0314_catalog-dependency-test-enrichment/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения и критерии готовности. Конкретные имена полей, правила извлечения зависимостей и формат query относятся к `/spec-tech`.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Decomposition of S0311 - owner approved splitting the umbrella into independent tooling tickets.
- **Goal / expected outcome:** Provided by user - агент видит тестовое покрытие и ключевые зависимости класса без широкого grep.
- **Scope boundaries:** Catalog tooling only; no app source change. App и Wear records остаются раздельными.
- **Autonomy rule:** Agent may finalize the contract; dependency field naming stays an open research item.

`Approved` remains blocked until the owner accepts the direction or invokes `/spec-tech S0314`.

---

## 1. Проблема

Каталог классов не отвечает на практические вопросы навигации без широкого grep: какие domain-классы без тестов, какие UseCase-подобные записи зависят от repository или data source, какие классы получили side effects. Авто-поле `hasTests` уже есть, но его извлечение узкое: `Test-HasTests` мапит только `src\main\` по конвенции `<ClassName>Test.kt`, поэтому классы из flavor source roots не получают тестовый матч. Dependency-метаданных (помимо `injected` от `@Inject constructor`) в каталоге нет вовсе.

## 2. Цели

1. Укрепить существующее извлечение `hasTests`, чтобы тестовое покрытие резолвилось от source root каждого файла, а не только от `src\main\`.
2. Добавить dependency-метаданные стабильными camelCase-полями, append-only, без слома существующих имён полей.
3. Дать query ответ на практические вопросы: untested domain classes; UseCase-подобные записи, зависящие от repository/data source; классы с новыми side effects; записи, требующие ручной чистки role/status.
4. Сохранять ручные записи (`role`, `status`) при пересканировании.

**Non-goals:**

- Изменение Kotlin/Java исходников приложения.
- Слияние app и Wear records.
- Переименование существующих полей каталога.

## 3. Пожелания и ограничения

- Расширять существующую инфраструктуру каталога, а не вводить параллельный индекс.
- Wear: enrichment держит app и Wear records раздельными.
- Производительность: scan остаётся пригодным для рутинного запуска после каждой `.kt`-правки.
- PowerShell: `-NoProfile`-safe.
- Совместимость: существующие query-consumers не должны ломаться.

### 3.3 Owner inputs (Approval gate)

- **Goal / expected outcome:** каталог отвечает на untested- и dependency-вопросы без global grep; поле тестового покрытия создано.
- **Scope boundaries:** catalog tooling only; no app source change; app and Wear records stay separate.
- **Delegated execution latitude:** agent finalizes the schema; dependency field naming stays an open research item.
- **Validation level:** scan+query dry-run on app_v2 with field-presence assertion; manual records preserved.
- **Feature docs:** no `docs/FEATURES*.md` update - internal tooling.
- **Related tickets:** S0311.

## 4. Контекст текущей архитектуры

Каталог сканируется и рендерится скриптами `dev/CATALOG/scripts/{scan,query,set,render}.ps1`; ритуал scan+render обёрнут в `scripts/catalog_sync.ps1`. Индексы `dev/CATALOG/<module>.jsonl` и `.md` локальны и gitignored - регенерируются, не коммитятся.

## 5. Предлагаемый подход

- Расширить scan, чтобы он вычислял и писал поле тестового покрытия и dependency-метаданные.
- Новые поля - стабильные camelCase, append-only; существующие имена сохранены.
- Query отвечает на untested- и dependency-вопросы без global grep.
- Ручные записи сохраняются при скане.

## 6. Открытые вопросы / Research items

1. **Catalog dependency schema**
   - **Вопрос:** какие имена полей и правила извлечения достаточно стабильны для JSONL-consumers?
   - **Варианты:** `dependsOn`, `repositoryDeps`, `constructorDeps`, либо reuse `injected` где достаточно.
   - **Нужно выяснить:** включать ли imports, constructor parameters, injected types или всё сразу с source-тегами.
   - **Статус:** Open

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Catalog schema drift | Средняя | Query-consumers ломаются или читают несогласованные поля | Сохранять текущие поля; новые поля append-only; документировать схему в catalog README |
| Потеря ручных записей при скане | Средняя | Стираются role/status | Сохранять manual-поля при пересканировании; покрыть кейс в проверке |
| Неточное извлечение зависимостей | Средняя | Query вводит в заблуждение | Зафиксировать source-теги (import vs constructor vs injected); документировать правила |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений: внутренний catalog-инструмент.

## 9. Архитектурные решения (ADR)

**ADR-1: Append-only catalog schema**

- **Решение:** test и dependency поля добавляются как новые camelCase-поля; существующие имена не трогаются.
- **Альтернативы:** переименовать/реструктурировать существующую схему.
- **Почему:** существующие query-consumers не должны ломаться при обогащении.

## 10. Связи с другими спеками

- **S0311** - parent umbrella; общий shared script contract.
- **Related tooling:** `dev/CATALOG/scripts/{scan,query,set,render}.ps1`; `scripts/catalog_sync.ps1`.

## 11. Критерии готовности (strategic-level)

1. Scan укрепляет `hasTests`: тестовое покрытие резолвится от source root каждого файла, а не только от `src\main\`.
2. Query отвечает на untested-class и dependency вопросы без global grep.
3. Существующие имена полей сохранены; новые поля append-only.
4. Ручные записи (`role`, `status`) переживают пересканирование.
5. App и Wear records остаются раздельными; скрипт `-NoProfile`-safe.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0314`.

## Revision History

- **2026-05-31** - by `/spec-update` (`Claude Opus 4.8`, decomposition of S0311)
  - Applied: created as a focused tooling ticket carved out of S0311 §5.4 + research item 4.
- **2026-05-31** - by `/spec-tech` (`Claude Opus 4.8`, premise correction)
  - Corrected: the `hasTests` field **does** already exist (narrow `src\main\`-only auto extraction, documented in `dev/CATALOG/README.md`). S0314 hardens it for flavor source roots and adds genuinely-new dependency metadata. The earlier "field must be created" premise was a regression - a `bash rg` over the gitignored `dev/CATALOG` zone silently missed the existing `Test-HasTests`.
