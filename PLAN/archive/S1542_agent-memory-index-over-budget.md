# Стратегическая спецификация: S1542 - Индекс агентской памяти вышел за бюджет

**Ticket:** S1542
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-09
**Tier:** -1
**Roadmap entry:** Ad-hoc - находка при S1453, 2026-08-09

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-09

**Захвачено во время:** S1453

**Текст:**

`assert-memory-budget.ps1 -Gate` fails: `.claude/agent-memory/android-rd-specialist/MEMORY.md` is 17346 B against the 16595 B ceiling, over by 751 B. This is the only red gate in the whole `assert-fast-gates.ps1` batch as of 2026-08-09, so the batch exits 1 for every caller that runs it strictly, and a red batch that is red for a reason nobody is working on trains the operator to read the summary as noise - the exact failure mode S1338 recorded at a 42% FAIL rate.

The gate is a ratchet on one file's length and the file is billed on EVERY turn of EVERY session, so this is a running cost, not a tidiness issue. S1338's own note on this gate records that MEMORY.md was manually compacted twice and both compactions were undone within a week at about 1.1 KB/day of regrowth - which is why the budget was made mechanical in the first place. The current overage is the third regrowth.

The gate also prints two advisory lists worth reading while fixing: memory files whose `[[wiki-link]]` targets do not resolve to an existing memory name, and memory files naming a spec id in their body. Both are noise multipliers - a dangling link sends a future session looking for a memory that was never written, and a spec id inside a memory goes stale the moment that ticket closes.

Research needed: whether the fix is a merge pass over the pointer lines (several sections carry three or four near-duplicate one-liners that could become one), a raise of the ceiling with a stated reason, or a structural change so that rarely-relevant sections are not billed every turn. Raising the ceiling without a reason is the one option the gate exists to prevent.

---

## 1. Проблема

Индекс агентской памяти перерос потолок и держал единственный красный гейт во всём быстром батче. Цена не косметическая: файл оплачивается на каждом ходу каждой сессии, а красный батч, который никто не чинит, приучает читать сводку как шум.

---

## 2. Цели

1. Индекс укладывается в потолок без поднятия потолка.
2. Ни один указатель не теряется - память, до которой нельзя дойти из индекса, всё равно что стёрта.
3. Побочные советы гейта (висячая ссылка, несуществующий путь) закрыты, а не оставлены накапливаться.

**Non-goals:**

- Не поднимается потолок: ради этого гейт и заводился.
- Не удаляются сами файлы памяти.
- Не трогается список «памяти со ссылками только на закрытые тикеты» - их 116, это отдельная работа со своим решением, а не побочный эффект бюджета.

---

## 3. Исправление

Выбран третий вариант из трёх, названных в захвате, - структурный, а не «подрезать хвосты».

- Раздел «Streams / VR / players» (10 указателей) вынесен во второй уровень: `INDEX_streams_vr.md`. В верхнем индексе он заменён одной строкой с условием открытия. Раздел выбран не по размеру, а по применимости: это единственная группа, которая не нужна ни задаче по лаунчеру, ни настройкам, ни сборке - то есть подавляющему большинству ходов.
- Почему не подрезание хвостов: хвост указателя это то, ради чего указатель вообще читают. Экономия в 900 B на хвостах стоила бы разборчивости всего индекса, а вынос одного раздела даёт 1.26 KB и не теряет ни одной строки.
- Висячая wiki-ссылка `[[feedback-persistent-logs-no-ticket-id]]` удалена из `reference_ticket_log_gate.md` - памяти с таким именем никогда не было.
- Совет «память называет несуществующий путь» оказался дефектом самого гейта: в `project_streams_device_test_gate.md` он видел `dev/x36xhzz/x36xhzz.m3u8` внутри URL `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` - хост кончается на `.dev`, и регулярка принимала хвост URL за путь в дереве. Исправлено в `assert-memory-budget.ps1`: совпадение внутри токена с `://` больше не считается утверждением о дереве.

---

## 4. Проверка

- До: `memory index: 17479 B | ceiling 16595 B` - OVER by 884 B.
- После: `memory index: 16216 B | ceiling 16595 B` - within the ceiling; `assert-memory-budget.ps1 -Gate` PASS.
- Совет про висячую ссылку: был 1, стало 0.
- Совет про несуществующий путь: был 1 (ложный), стало 0 - после починки предиката, а не после правки памяти.
- Оба вынесенных уровня проверены на полноту: 10 строк из верхнего индекса перенесены дословно, ни одна ссылка не потеряна.

---

## 5. Связи

- S1453 - откуда находка.
- S1338 - записал этот же гейт и историю двух откатившихся ручных компактизаций (~1.1 KB/день прироста), из-за которой бюджет и стал механическим.
