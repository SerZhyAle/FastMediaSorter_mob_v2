---
ticket: S0312
status: BlockNeedUserTest
priority: 75
date: 2026-05-31
tier: 3
parent: S0311
---

# Стратегическая спецификация: S0312 - Build failure digest

**Ticket:** S0312
**Status:** BlockNeedUserTest
**Priority:** 75
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Parent:** S0311 (agent tooling umbrella)
**Tactical plan:** `PLAN/S0312_build-failure-digest/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения и критерии готовности. Конкретные пути скриптов, параметры команд и формат отчёта относятся к `/spec-tech`.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Decomposition of S0311 - owner approved splitting the umbrella into independent tooling tickets.
- **Goal / expected outcome:** Provided by user - сократить время обратной связи по build/lint failures за счёт структурированного отчёта.
- **Scope boundaries:** Internal DX tooling only; no app behavior, UI or feature-doc change.
- **Autonomy rule:** Agent may finalize the contract; trigger-model choice stays an open research item.

`Approved` remains blocked until the owner accepts the direction or invokes `/spec-tech S0312`.

---

## 1. Проблема

Чтение build- и lint-логов вручную медленно и ненадёжно: блок `FAILURE:` может находиться в середине лога, поэтому `tail -N` пропускает реальную причину. Агент тратит время на разбор консольного шума вместо чтения первого actionable failure.

## 2. Цели

1. Дать caller-started режим, который выдаёт компактный структурированный отчёт о compiler- и high-priority lint-падениях.
2. Приоритизировать первый actionable failure с указанием модуля/flavor, file anchor (когда доступен), exit code и пути к полному raw-логу.
3. Не прятать падение за цветным консольным выводом.
4. При невозможности завершить build/lint run репортить blocker, а не stale success.
5. Зафиксировать явный lifecycle: start, stop, timeout, output - без скрытого always-on daemon.

**Non-goals:**

- Замена Gradle или существующего `a.ps1` build-feedback пути.
- Always-on фоновый watcher, стартующий без явного действия.
- Изменение продуктового поведения приложения.

## 3. Пожелания и ограничения

- Отчёт должен читаться агентом без ручной интерпретации raw-лога.
- Инструмент должен расширять существующий build-feedback путь, а не конкурировать с ним.
- One-shot режим остаётся каноническим; watcher (если будет) - явный и с timeout.
- PowerShell: `-NoProfile`-safe, без зависимости от профиля.
- Artifacts (raw-логи, digest) - под `temp/`.

### 3.3 Owner inputs (Approval gate)

- **Goal / expected outcome:** структурированный build/lint failure digest, читаемый агентом без разбора raw-лога.
- **Scope boundaries:** internal DX tooling only; no app behavior, UI or feature-doc change.
- **Delegated execution latitude:** agent finalizes the script contract; trigger model (one-shot vs watcher) stays an open research item.
- **Validation level:** script dry-run with exit-0 closure; no app build required for the tool itself.
- **Feature docs:** no `docs/FEATURES*.md` update - internal tooling.
- **Related tickets:** S0311.

## 4. Контекст текущей архитектуры

Build-инспекция уже доступна через `a.ps1` (build-fail режим). Чего не хватает - стабильной machine-readable формы первого actionable failure: модуль/flavor, file anchor, exit code и ссылка на raw-лог в едином JSON.

## 5. Предлагаемый подход

- Explicit, caller-started build feedback mode поверх существующего build-fail инспектора.
- Компактный structured summary: команда, exit code, первый actionable failure, affected module/flavor, file anchors, путь к raw-логу.
- JSON для агента + краткий human-summary для владельца.
- При невозможности завершить run - blocker-отчёт с категорией и raw-путём.

## 6. Открытые вопросы / Research items

1. **Build feedback trigger model**
   - **Вопрос:** one-shot команда, watcher с timeout, или оба?
   - **Варианты:** one-shot digest only; explicit watcher with timeout; IDE task integration.
   - **Нужно выяснить:** приемлемая стоимость по времени и не конфликтует ли непрерывный watch с harness-правилами.
   - **Статус:** Open

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Watcher превращается в неуправляемый долгий процесс | Средняя | Harness/session cleanup становится ненадёжным | Explicit start/stop/timeout; one-shot режим канонический; без скрытого daemon |
| Digest прячет реальное падение | Низкая | Агент чинит не ту ошибку | Всегда выводить первый actionable failure + путь к raw-логу |
| Stale success при оборванном run | Средняя | Ложное «build OK» | При незавершённом run репортить blocker, а не успех |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений: внутренний DX-инструмент, не пользовательская функция.

## 9. Архитектурные решения (ADR)

**ADR-1: Explicit watcher lifecycle**

- **Решение:** build feedback - явная команда с timeout и artifact-выводом, не always-on daemon.
- **Альтернативы:** фоновый shell-workaround; только ручные сборки.
- **Почему:** явный lifecycle уважает PowerShell-efficiency правила и держит session cleanup предсказуемым.

## 10. Связи с другими спеками

- **S0311** - parent umbrella; общий shared script contract (JSON + human summary, `-NoProfile`, stable exit codes, artifacts под `temp/`).
- **Related rules:** CLAUDE.md запрет на `tail -N` по build-логам; существующий `a.ps1` build-fail режим.

## 11. Критерии готовности (strategic-level)

1. Build feedback tooling выдаёт структурированный failure-digest с командой, exit code, первым actionable failure и путём к raw-логу.
2. One-shot режим документирован как канонический; watcher (если есть) имеет explicit start/stop/timeout.
3. При незавершённом build/lint run инструмент репортит blocker вместо stale success.
4. Скрипт `-NoProfile`-safe, со стабильными exit codes, JSON + human-выводом, artifacts под `temp/`.
5. Существующий build-feedback путь переиспользуется или сознательно консолидируется, а не дублируется.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0312`.

## Revision History

- **2026-05-31** - by `/spec-update` (`Claude Opus 4.8`, decomposition of S0311)
  - Applied: created as a focused tooling ticket carved out of S0311 §5.2 + research item 2 + ADR-4.
