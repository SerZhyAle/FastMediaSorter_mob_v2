---
name: measure-throughput-before-calling-a-plan-infeasible
description: Never claim a release package is too big for its date by intuition - this repo closes ~16-20 tickets/day, so measure from the catalog first
metadata:
  type: feedback
---

Never say a package "won't fit" its date from gut feel. Measure the closure rate from
`PLAN/spec-catalog.jsonl` first, then state the number.

**Why:** on 2026-08-16 I told the owner that 54 tickets in five days "won't happen". He pushed back
("я смотрю ты у нас историк.. посмотри просто по нумерации сколько мы в среднем в месяц выполняем")
and the measurement flatly contradicted me: trailing 30 days 594 closed = 19.8/day, 60 days 19.1/day,
90 days 15.9/day. Even the most conservative window gives ~80 tickets in five days. The package was
~2.7 days of work, not an overload. An invented capacity estimate is exactly the "should / probably"
class of claim that CLAUDE.md section 12 forbids - it just wears a schedule costume.

**How to apply:** when about to judge scope against a deadline, run the count. Every closed record
carries status `Archived` (nothing rests at Implemented/Verified), so pull
`select.ps1 -Status Archived -Format tsv -IncludeArchived` and group by the `updated` date.

Two traps in that data:
- `updated` on an archived ticket is the archive-SWEEP date, not the day the work finished. The daily
  series is therefore lumpy (166, 112, 110 on sweep days against 2-3 on others). Never quote a single
  day. Windows of 30+ days smooth it out and agree with each other, so quote those.
- Compare against the ISSUE rate too (`created` field): 20.5 new ids/day over 30 days, 23.6 over 7.
  Creation currently outruns closure, so a package keeps growing while it is being worked. That, not
  raw size, is the real schedule risk - along with tickets blocked on owner answers or hardware, which
  throughput does not move at all.

Related: [[feedback_argue_then_obey]].
