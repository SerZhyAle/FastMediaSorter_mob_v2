# Спецификация (compact bugfix): S1368 - assert-exit-contract считает штатный выход «без причины»

**Ticket:** S1368
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

assert-exit-contract flags a normal control-flow exit as "reasonless"

`scripts/quality/assert-exit-contract.ps1` reports `scripts\spec_catalog\spec-next-session.ps1:207  exit 3 with no reason printed` on every run (1 reasonless exit, baseline 0, gate still PASS - advisory only).

It is a false positive. Line 207 reads `if ($result.crossed) { exit 3 } else { exit 0 }` and is the `CheckContext` verb's normal control-flow signal for "context threshold crossed", which `/spec-next` Stage 5b parses. The reason IS printed - lines 202-206 emit a compact JSON object carrying `tokens`, `threshold` and `crossed` immediately before the exit. The gate's heuristic only recognises an adjacent `Write-Error`/`Write-Host` and does not see a `ConvertTo-Json` pipeline as a printed reason.

Why it matters: the advisory line is permanent, it appears in `.\a.ps1 fg` and in every `post-change.ps1` closure that runs the exit-contract gate, and the obvious "fix" is actively wrong - adding a `Write-Error` before that exit would print an error for an expected outcome and would pollute the JSON contract Stage 5b reads. This nearly happened during S1343 phase 03 (2026-08-02), and S1343 phase 01's step log had already mis-classified the same finding as a trivial one-line fix.

Candidate direction (not decided): teach the gate to accept any output statement immediately preceding an `exit N` as the printed reason, not only `Write-Error`/`Write-Host` - or let a script opt a specific exit site out with a documented marker.

Dedup: `search.ps1 -Query "exit-contract"` returns only S1192 (gate misses catalog scripts - coverage gap, different cause); `-Query "reasonless"` returns nothing.

**Поправка к тексту выше (2026-08-02, тот же день, S1343 фаза 04).** Утверждение «gate still PASS - advisory only» неверно, и это ровно тот класс ошибки, про который §7 S1343 и память `documented-invariant-is-a-claim`. Без `-Gate` скрипт печатает PASS и выходит 0 - так он и был замерен при захвате. Но `assert-fast-gates.ps1` вызывает каждый гейт именно с `-Gate`, и тогда он выходит 1:

```
  assert-exit-contract.ps1                 FAIL (773 ms)
assert-fast-gates: FAIL (2 gate(s)).
```

То есть один ложноположительный срабатывает не как советующая строка, а как красный `.\a.ps1 fg` на чистом дереве. Приоритет поднят 35 -> 60. Вторая поломка в том же прогоне (`assert-memory-budget`, MEMORY.md на 280 Б выше потолка) - чужой незакоммиченный WIP в `.claude/agent-memory/`, не дефект, отдельным тикетом не парковалась.

---

## 1. Проблема / симптом

`assert-exit-contract.ps1` считал штатный выход по контрольному потоку выходом «без объяснения» и валил гейт на чистом дереве.

- Воспроизведено при захвате тикета и повторно 2026-08-03: `assert-exit-contract.ps1 -Gate` - `1 reasonless exit(s) (baseline 0)`, exit 1.
- Единственная находка - `scripts/spec_catalog/spec-next-session.ps1:207`, строка `if ($result.crossed) { exit 3 } else { exit 0 }`.
- Через `.\a.ps1 fg` это красный гейт, а не советующая строка: `assert-fast-gates.ps1` вызывает каждый гейт с `-Gate`.

---

## 2. Корневая причина

Эвристика «причина напечатана» знала только один способ печатать.

- Правило C ищет назад от `exit N` не дальше четырёх строк и признаёт причиной вызов `Write-*` с аргументом, прямую запись в `[Console]::Error/Out` или `throw`.
- `CheckContext` печатает причину иначе: строки 202-206 отдают объект в `ConvertTo-Json -Compress`, то есть пишут в success-поток конвейером. Ни одного `Write-*` в окне поиска нет.
- Причина при этом печатается и является контрактом: `/spec-next` Stage 5b разбирает именно этот JSON, а код 3 - его машинный дубль.
- Опаснее самой находки был бы «очевидный» ремонт: `Write-Error` перед этим выходом напечатал бы ошибку для ожидаемого исхода и испортил бы JSON, который читает вызывающий.

---

## 3. Исправление

- Правило C дополнено третьим признаком причины: конвейер, чей хвост рендерит в success-поток - `ConvertTo-Json|Csv|Xml`, `Format-Table|List|Wide`, `Out-Host|Default|String`.
- Список хвостов закрытый и намеренно не содержит `Out-Null`, `Out-File`, `Set-Content`: они гасят вывод или уводят его в файл, то есть причину не показывают. Иначе признак выродился бы в «есть любой конвейер».
- Заголовок скрипта (описание правила C) обновлён - иначе документированный инвариант разошёлся бы с кодом.
- Правка в одном месте эвристики; окно поиска, ратчет и baseline не трогаются.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1192 (тот же гейт, другая причина - пробел в покрытии, не ложное срабатывание)

---

## 4. Проверка

- Симптом снят: `assert-exit-contract.ps1 -Gate` - `0 reasonless exit(s) (baseline 0)`, exit 0 (было 1 и exit 1).
- Регрессионный набор `assert-exit-contract.tests/Run-Tests.ps1` - 12 из 12, exit 0. Два случая добавлены под эту правку: J1 - конвейер с `ConvertTo-Json` признаётся причиной; J2 - хвост `Out-Null` причиной не признаётся, гейт по-прежнему краснеет.
- Обе стороны проверены намеренно: гейт, ослабленный так, что перестал ловить исходный дефект, выглядит точно так же зелено.
- `.\a.ps1 fg`: `assert-exit-contract.ps1 PASS`. Батч в целом остаётся красным из-за `assert-memory-budget` (MEMORY.md на 280 Б выше потолка) - это состояние описано в §0 и к этому тикету не относится.

---

## Last Audit

**Date:** 2026-08-03
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

- Правило C принимает новый признак: `assert-exit-contract.ps1 -Gate` - 0 reasonless, exit 0.
- Гейт не ослаблен: J2 доказывает, что хвост `Out-Null` по-прежнему краснеет; весь набор 12/12.
- Закрытие: `post-change.ps1 -ScopeToFile` - `post-change: PASS`.
- Заголовок скрипта описывает правило C так, как оно теперь работает - документированный инвариант не разошёлся с кодом.
- Отладочных тегов `Timber.d("S1368:` нет: тикет скриптовый, `.kt` не затронут.
