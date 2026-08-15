---
ticket: S0313
status: BlockNeedUserTest
priority: 75
date: 2026-05-31
tier: 3
parent: S0311
---

# Стратегическая спецификация: S0313 - Flavor isolation diff-guard

**Ticket:** S0313
**Status:** BlockNeedUserTest
**Priority:** 75
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Parent:** S0311 (agent tooling umbrella)
**Tactical plan:** `PLAN/S0313_flavor-isolation-diff-guard/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения и критерии готовности. Конкретный механизм diff-сканирования, формат baseline и список токенов относятся к `/spec-tech`.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Decomposition of S0311 - owner approved splitting the umbrella into independent tooling tickets.
- **Goal / expected outcome:** Provided by user - сделать нарушение flavor isolation обнаруживаемым до commit/build review.
- **Scope boundaries:** Static analysis tooling only; no app behavior or flavor source-set change.
- **Autonomy rule:** Agent may finalize the contract; baseline strategy stays an open research item.

`Approved` remains blocked until the owner accepts the direction or invokes `/spec-tech S0313`.

---

## 1. Проблема

Flavor isolation (CLAUDE.md Rule 15) сейчас держится на дисциплине агента. В `src/main` уже накоплен legacy-долг flavor-гейтов (≈169 вхождений на 2026-05-14), и новые нарушения ловятся только на ручном review. Нужен механический, diff-aware guard, который блокирует именно новые нарушения, не замораживая работу из-за legacy-долга.

## 2. Цели

1. Дать статический guard для изменённого main-source кода, детектящий новые flavor-проверки там, где требуется source-set изоляция.
2. Различать legacy debt и новые/тронутые нарушения; блокирующий exit code принадлежит только новым/тронутым.
3. Репортить нарушения с file, line, matched token и recommended remediation category.
4. Опираться на diff или явно переданные пути, а не на полный repo-scan по умолчанию.

**Non-goals:**

- Рефакторинг существующего legacy-долга в рамках этого тикета.
- Введение новых `BuildConfig`-гейтов или изменение flavor source sets.
- Изменение продуктового поведения.

## 3. Пожелания и ограничения

- Guard должен снижать риск нарушений flavor isolation до review.
- Шум на legacy-долге недопустим - иначе проверку начнут игнорировать.
- PowerShell: `-NoProfile`-safe.
- Artifacts (отчёты) - под `temp/`.
- Expected vs actual для структурных проверок.

### 3.3 Owner inputs (Approval gate)

- **Goal / expected outcome:** diff-aware guard, блокирующий только новые/тронутые main-source flavor-нарушения.
- **Scope boundaries:** static-analysis tooling only; no app behavior or flavor source-set change.
- **Delegated execution latitude:** agent finalizes the script contract; baseline strategy stays an open research item.
- **Validation level:** script dry-run on a seeded diff with exit-code assertion.
- **Feature docs:** no `docs/FEATURES*.md` update - internal tooling.
- **Related tickets:** S0311.

## 4. Контекст текущей архитектуры

Источник истины по flavor-правилам - `dev/FLAVOR_DEVELOPMENT_RULES.md` и CLAUDE.md Rule 15. Запрещены `BuildConfig.SUPPORT_*` / `ENABLE_*` / `IS_*` flavor-гейты в `src/main/java/**` для нового кода; flavor-логика живёт в `src/<flavor>/java/`. Существующий долг - технический, не прецедент.

## 5. Предлагаемый подход

- Diff-aware статический скан изменённого main-source кода или явно переданных путей.
- Детект newly introduced flavor checks, где нужна source-set изоляция.
- Отчёт: file, line, matched token, recommended remediation category.
- Legacy-долг - в summary-форме, без блокирующего exit code; блокировка только на новых/тронутых нарушениях.

## 6. Открытые вопросы / Research items

1. **Flavor guard baseline**
   - **Вопрос:** как забейзлайнить legacy main-source flavor-долг, чтобы guard блокировал только новые нарушения?
   - **Варианты:** diff-only scan; generated baseline file; explicit allowlist with expiry; full-scan warning + diff blocking.
   - **Нужно выяснить:** оправдан ли baseline-артефакт или достаточно diff-only.
   - **Статус:** Open

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Guard шумит на legacy debt | Высокая | Проверку начнут игнорировать | Блокировать только новые/тронутые нарушения; legacy summary non-blocking |
| Ложные срабатывания на легитимных паттернах | Средняя | Агенты обходят guard | Сужать матч до flavor-гейтов в `src/main/java/**`; documented override с записанной причиной |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений: внутренний static-analysis инструмент.

## 9. Архитектурные решения (ADR)

**ADR-1: Diff-aware flavor enforcement**

- **Решение:** guard блокирует новые/тронутые нарушения и репортит legacy-долг отдельно.
- **Альтернативы:** fail на полном repo-scan; игнорировать legacy и полагаться на review.
- **Почему:** fail-all заморозит несвязанную работу, а review-only не предотвращает новый долг.

## 10. Связи с другими спеками

- **S0311** - parent umbrella; общий shared script contract.
- **Related rules:** CLAUDE.md Rule 15; `dev/FLAVOR_DEVELOPMENT_RULES.md`.

## 11. Критерии готовности (strategic-level)

1. Guard возвращает блокирующий fail только для newly introduced или тронутых main-source flavor-нарушений.
2. Legacy-долг репортится в non-blocking summary-форме.
3. Каждое нарушение содержит file, line, matched token и remediation category.
4. Скан работает по diff или явным путям, а не по полному repo по умолчанию.
5. Скрипт `-NoProfile`-safe, со стабильными exit codes, artifacts под `temp/`.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0313`.

## Revision History

- **2026-05-31** - by `/spec-update` (`Claude Opus 4.8`, decomposition of S0311)
  - Applied: created as a focused tooling ticket carved out of S0311 §5.3 + research item 3 + ADR-3.
