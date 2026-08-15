---
ticket: S0315
status: BlockNeedUserTest
priority: 60
date: 2026-05-31
tier: 3
parent: S0311
---

# Стратегическая спецификация: S0315 - Rule and prompt drift audit

**Ticket:** S0315
**Status:** BlockNeedUserTest
**Priority:** 60
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Parent:** S0311 (agent tooling umbrella)
**Tactical spec:** `PLAN/S0315_rule-prompt-drift-audit/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0315_rule-prompt-drift-audit/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения и критерии готовности. Конкретные canonical-источники, правила сравнения и формат отчёта относятся к `/spec-tech`.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Decomposition of S0311 - owner approved splitting the umbrella into independent tooling tickets.
- **Goal / expected outcome:** Provided by user - обнаруживать drift между agent rules, prompt files, workflow docs и реальными скриптами до того, как он станет execution-ошибкой.
- **Scope boundaries:** Audit tooling only; no rule or skill content change as part of this ticket.
- **Autonomy rule:** Agent may finalize the contract; the canonical-source baseline stays an open research item.

`Approved` remains blocked until the owner accepts the direction or invokes `/spec-tech S0315`.

---

## 1. Проблема

Repo rules (`CLAUDE.md`), prompt skills, agent profiles, workflow docs и реализованные скрипты расходятся со временем. Из-за рассинхрона разные агенты выполняют одну задачу по-разному. Живой пример: S0311 §3.3 ссылалась на несуществующие Maestro-wrappers и несуществующее поле каталога `hasTests` - drift в правилах, который никто не ловил до ручной проверки.

## 2. Цели

1. Дать проверку рассинхрона между repo rules, prompt skills, agent profiles, workflow docs и реализованными скриптами.
2. Находить: conflicting route names, stale command examples, missing `-NoProfile`, ссылки на abolished audit-файлы, outdated validation rules, скрипты, задокументированные но отсутствующие на диске.
3. Подсвечивать executable mismatch, а не стилистический drift.

**Non-goals:**

- Замена человеческого review правил.
- Style-checker для прозы правил.
- Автоматическая правка правил или skill-файлов в рамках этого тикета.

## 3. Пожелания и ограничения

- Audit должен быть сухим и точным - иначе его начнут игнорировать как шумный linter.
- Репортить только executable conflicts, stale commands и route mismatches.
- PowerShell: `-NoProfile`-safe.
- Artifacts (отчёт) - под `temp/`.

### 3.3 Owner inputs (Approval gate)

- **Goal / expected outcome:** audit находит executable mismatch между правилами, промптами и реальными скриптами.
- **Scope boundaries:** audit tooling only; no rule or skill content change in this ticket.
- **Delegated execution latitude:** agent finalizes the contract; canonical-source baseline stays an open research item.
- **Validation level:** audit dry-run producing a structured report with exit-code semantics.
- **Feature docs:** no `docs/FEATURES*.md` update - internal tooling.
- **Related tickets:** S0311, S0278, S0279.

## 4. Контекст текущей архитектуры

В репозитории уже есть точечные drift-проверки: `scripts/spec_catalog/drift-check.ps1`, `scripts/doc-drift/`, `scripts/check-doc-vs-gradle.ps1`, `scripts/check_docs_freshness.ps1`. Эти инструменты надо оценить и при возможности расширить, а не вводить конкурирующий entrypoint.

## 5. Предлагаемый подход

- Сравнивать canonical rule-источники с prompt-файлами, workflow-доками и фактическими скриптами на диске.
- Находить executable mismatch: несуществующие команды/скрипты, конфликтующие route names, missing `-NoProfile`, ссылки на abolished артефакты.
- Style-only drift оставить вне scope.
- Переиспользовать или консолидировать существующие drift-скрипты.

## 6. Открытые вопросы / Research items

1. **Rule drift baseline**
   - **Вопрос:** какие rule-источники считать canonical для drift audit?
   - **Варианты:** `CLAUDE.md` first; `AGENTS.md` import graph; prompt files; generated route index; all with conflict reporting.
   - **Нужно выяснить:** как не превратить audit в шумный style checker.
   - **Статус:** Open

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Drift audit становится шумным | Средняя | Агенты игнорируют audit | Репортить только executable conflicts, stale commands и route mismatches; style-drift вне scope |
| Дублирование существующих drift-скриптов | Средняя | Конкурирующие отчёты | Сначала оценить `drift-check.ps1`, `doc-drift/`, `check-doc-vs-gradle.ps1`; расширять, а не дублировать |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений: внутренний audit-инструмент.

## 9. Архитектурные решения (ADR)

**ADR-1: Audit reports executable conflicts only**

- **Решение:** drift audit репортит несуществующие скрипты, конфликтующие routes, missing `-NoProfile` и устаревшие команды, но не стилистику.
- **Альтернативы:** общий style/linter по прозе правил.
- **Почему:** шумный audit игнорируется; ценность - в ловле mismatch, ведущего к execution-ошибке.

## 10. Связи с другими спеками

- **S0311** - parent umbrella; общий shared script contract.
- **Related tooling:** `scripts/spec_catalog/drift-check.ps1`; `scripts/doc-drift/`; `scripts/check-doc-vs-gradle.ps1`; `scripts/check_docs_freshness.ps1`.

## 11. Критерии готовности (strategic-level)

1. Audit репортит stale commands, conflicting route names, missing scripts и obsolete workflow references.
2. Audit не репортит style-only drift.
3. Существующие drift-проверки оценены и переиспользованы либо сознательно консолидированы.
4. Скрипт `-NoProfile`-safe, с JSON + human-выводом, artifacts под `temp/`.

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0315`.

## Revision History

- **2026-05-31** - by `/spec-update` (`Claude Opus 4.8`, decomposition of S0311)
  - Applied: created as a focused tooling ticket carved out of S0311 §5.12 + research item 11. Promoted from the cut governance layer because drift audit is mechanical DX tooling, not coordination process.
