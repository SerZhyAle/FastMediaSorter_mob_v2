---
name: a-gate-can-exist-and-never-be-wired
description: Before writing a gate for a recurring finding, grep assert-fast-gates.ps1 and post-change.ps1 - the gate may already exist and simply never have been wired into either
metadata:
  type: feedback
---

A rule can be gated and still never enforced, because writing the gate and wiring it into the batch are
two separate acts and only the first one feels like finishing. Before building a gate for a recurring
finding, check whether one already exists **and** whether anything runs it.

**Why:** measured 2026-08-21. Five tickets - S1186, S1198, S1247, S1269, S1311 - were written about one
mechanism in five different files: a detekt baseline entry is keyed by the full signature of what it
suppresses, so any edit invalidates the entry and hands the whole accumulated finding to the next person
as new debt, while re-freezing the baseline makes the file look clean without touching the debt. Two gates
for exactly that already existed - `assert-detekt-baseline-absorption.ps1` (S1356, refuses a baseline that
absorbed a finding) and `audit-detekt-baseline-drift.ps1` (S1334, classifies entries that went dead). Both
were correct, both passed when run by hand, and `grep` for either in `assert-fast-gates.ps1` returned
**zero**. Nobody had run them since they were written. Wiring the first one in took one line and 3.1 s of
batch time.

**How to apply:**

- Two greps before writing any gate: `ls scripts/quality/ | grep <topic>` for existence, then
  `grep <name> scripts/quality/assert-fast-gates.ps1 scripts/post-change.ps1` for wiring. A hit on the
  first and a miss on the second means the work is one line, not one ticket.
- When a ticket's own section proposes "a sweep or a report would prevent the third instance", check that
  proposal against the scripts directory before planning it - the third instance may exist because the
  tool was built and shelved.
- Wiring is not free: the batch's uniform call passes `-Gate` and often `-Quiet`, and a gate that does not
  declare those parameters fails with "A parameter cannot be found", which reads as a gate failure rather
  than a wiring error. Read the gate's `param()` block before adding its row.
- The same question is worth asking of a rule with no gate at all: CLAUDE.md Rule 2's 1500-line ceiling had
  been stated for months and was measured by nothing until S1270 - detekt carried `LongMethod` but no
  `FileLength`, and never sees a `.cpp`.
