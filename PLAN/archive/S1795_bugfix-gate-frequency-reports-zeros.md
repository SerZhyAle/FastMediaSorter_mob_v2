# Спецификация (compact bugfix): S1795 - Замер частоты гейтов печатает нули как измерение

**Ticket:** S1795
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-18
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18

**Текст:**

Найдено при проверке реализации S1786 (шаг 03.3). `scripts/quality/measure-gate-frequency.ps1` печатает заголовок «Telemetry source: temp/metrics/transcript_metrics.json» и таблицу на 71 строку, в которой **у каждого гейта** `Invocations 0, Failures 0, FailureRatePct 0.00`.

Причина - скрипт читает ключи, которых в файле нет:

```powershell
if ($telemetry.PSObject.Properties.Name -contains 'top_commands' ...)
if ($telemetry.PSObject.Properties.Name -contains 'top_failing_commands' ...)
```

Реальные ключи `temp/metrics/transcript_metrics.json`: `sessions`, `requests`, `tiers`, `compaction`, `totals`, `tool_counts`, `tool_fail`, `tool_soft_fail`, `failure_rates`, `tool_result_chars`, **`top_bash`**, **`top_bash_fail`**, `skills`, `agents`, `models`, `top_reads`, `big_results`, `corrections`, `daily`, `per_session`. Проверка на наличие ключа тихая, поэтому вместо предупреждения печатается таблица нулей, выглядящая как данные.

Вторая, более глубокая проблема: частоты срабатывания гейтов в этом корпусе нет в принципе. Гейты запускаются **внутри** `post-change.ps1` и `assert-fast-gates.ps1`, а не отдельными Bash-вызовами, и `top_bash` покрывает ~120 форм команд из 15 746 вызовов. Переименование ключей даст лишь нижнюю границу по трём-четырём гейтам, которые изредка вызывают руками.

Исходная задача (июльский аудит, пункт B4: 11 гейтов не сработали ни разу и съели 33% времени гейтов) требует другого источника: писать исход каждого гейта - имя, время, число находок - в машинный журнал прямо из `post-change.ps1` / `assert-fast-gates.ps1`, и считать по нему.

---

## 1. Проблема / симптом

<см. §0 - эвиденс захвачен>

---

## 2. Корневая причина

Чтение несуществующих ключей телеметрии плюс попытка получить частоту гейтов из корпуса, где её нет.

---

## 3. Исправление

<реализовать: инструментировать post-change.ps1 и assert-fast-gates.ps1 записью per-gate исхода в машинный журнал под temp/; measure-gate-frequency.ps1 считать по нему; при отсутствии источника печатать честный отказ, а не нули>

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1786 (шаг 03.3, в рамках которого скрипт создан)

---

## 4. Проверка

`measure-gate-frequency.ps1` после серии из N закрытий показывает ненулевые прогоны как минимум у гейтов, которые в этих закрытиях реально запускались; при отсутствии журнала - exit не 0 и внятное сообщение вместо таблицы нулей.

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- No manual or device verification is required for repository tooling.
