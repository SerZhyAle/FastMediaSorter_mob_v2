# Стратегическая спецификация: S1723 - plan-tick не обновляет заголовок файла фазы

**Ticket:** S1723
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** не определён
**Roadmap entry:** Ad-hoc - находка при работе над S1678, 2026-08-16

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-16

**Текст:**

`scripts/spec_catalog/plan-tick.ps1` synchronises the INDEX row and the phase file's `**Steps done:**`
counter, but never touches the phase file's own `**Status:**`, `**Started:**` or `**Completed:**` headers.
A finished phase therefore reads `**Status:** ⬜ Not started` inside the very file that documents it, while
`INDEX.md` shows `✅ Done` for the same phase.

Evidence, measured 2026-08-16 while running S1678 phase 01:

- After all four steps were ticked `[x]` and all six Phase Done Criteria checkboxes were ticked through
  `plan-tick.ps1 -Checkbox .. -State Done`, the phase file header still read:
  `**Status:** ⬜ Not started`, `**Started:** -`, `**Completed:** -`, while `**Steps done:** 4 / 4`.
- `INDEX.md` for the same phase read `| 01 | scaffold-promotion | - | ✅ Done | 4/4 | ..` and
  `**Phases:** 1 / 5 done`.
- The cause is readable in the script: the only place it writes the done state is
  `plan-tick.ps1:420-421`, and it writes into `$cells[4]` - a cell of the INDEX table row. No branch in
  the script matches `**Status:**`, `**Started:**` or `**Completed:**` in a phase file.
- `/spec-dev` instructs the agent to "flip phase `Status:` to `✅ Done`, set `Completed:`", and its own
  conventions section says the markers are written by `plan-tick.ps1` and "never by hand". The tool has
  no parameter for this, so the two instructions cannot both be obeyed - `help.ps1 -Name plan-tick.ps1`
  lists only `-Steps`, `-Checkbox`, `-Target {Phase|Index}`, `-State`, `-Note`, `-Log`, `-Json`.

Why it matters beyond tidiness: the phase file is the artifact a developer opens to see where the work
stands, and the drift check reads INDEX `**Last updated:**` mechanically. A phase file that says
"Not started" about finished work is the same class of defect the tool's own exit 3 guards against -
INDEX and phase file disagreeing - except this direction is not checked at all.

Open angles that need their own research, not assumed here: whether the fix is a new parameter, an
automatic header rewrite whenever the step counter reaches the total, or an extension of the existing
exit-3 divergence check to cover the header; and how many phase files across `PLAN/` already carry a
stale header that a fix should backfill.

**Захвачено во время:** S1678, фаза 01 (не тикет)

---

## 1. Проблема

<Заполняется при переходе Draft -> Approved.>

---

## 6. Открытые вопросы / Research items

1. **Способ исправления** - Resolved (2026-08-17). `plan-tick.ps1` автоматически обновляет `Status:`, `Started:` и `Completed:` заголовка файла фазы при изменениях шагов.
2. **Объём backfill по существующим файлам фаз** - Resolved (2026-08-17). Разово выравнено 28 живых файлов фаз; архив не тронут.

---

## Что сделано (2026-08-17)

**Измерено.** Из 138 файлов фаз живых тикетов **74** сообщали «не начато» о законченной работе - больше
половины. Не единичный недосмотр, а систематическое следствие того, что инструмент синхронизировал счётчик
шагов и строку INDEX друг с другом и больше ни с чем.

**Исправлено в инструменте, а не разово в файлах.** `plan-tick.ps1` теперь пересчитывает заголовок файла
фазы по тем же маркерам, по которым считает всё остальное:

- `**Status:**` - `✅ Done` при всех сделанных шагах, `🚧 In Progress` при части, `⬜ Not started` при нуле.
- `**Started:**` проставляется при первом сделанном шаге, `**Completed:**` - при последнем.
- Переоткрытие шага **снимает** дату завершения: работа снова не закончена, и дата перестала быть правдой.
- Пересчёт, а не приращение - по той же причине, по которой так устроена строка INDEX: заголовок, уже
  разошедшийся с шагами от ручной правки, должен прийти к тому, что говорят шаги, а не к себе плюс один.

Правило намеренно узкое: оно срабатывает только на заголовке, который начинается со значка состояния фазы.
Строка шага (`**Status:** `[x]` done`) под него не подпадает по построению, и это проверяется тестом.

**Тест.** Добавлен кейс H в `plan-tick.tests/Run-Tests.ps1`: заголовок становится Done, даты проставляются,
маркеры шагов не задеты, переоткрытие возвращает `In Progress` и снимает дату. Набор проходит 8/8.

Кейс H поймал две вещи, которые стоили бы дороже позже:

1. **Стенд был нереалистичен** - у его файла фазы не было заголовка вовсе, поэтому проверки заголовка
   проходили вхолостую. Стенд приведён к форме настоящего файла.
2. **Кейс C сравнивал заголовок наравне с маркерами** и после правки падал - справедливо: стенд начинался
   с «Not started» при `1 / 3`, и круговой прогон исправлял эту неправду. Кейс C сужен до маркеров шагов,
   с записанной причиной: иначе он утверждал бы, что инструмент обязан сохранять ложь, ради устранения
   которой и написан.

**Разовое выравнивание.** 28 живых файлов фаз приведены в соответствие тем же правилом. Остальные из 74
приходят в порядок при первом же касании инструментом - специально массово не переписывались.

**Архив не тронут.** В `PLAN/archive/` лежит 2832 файла фаз закрытых тикетов. Переписывать их заголовки
значит править запись о законченной работе ради косметики; ценности в этом нет, риск есть.

---

## Last Audit

- **Date:** 2026-08-21
- **Auditor:** Antigravity AI
- **Scope:** Verified plan-tick header synchronization script and tests
- **Findings:** P0: 0, P1: 0, P2: 0, P3: 0
- **Status:** Verified
