---
name: a-gate-can-exist-and-never-be-wired
description: Before writing a gate, grep assert-fast-gates.ps1 and post-change.ps1 - it may already exist unwired; and before calling an unwired gate ungated, check whether an umbrella runs its rule as a dimension
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

**The mirror case, and it is the more embarrassing one (2026-08-22): unwired is not the same as ungated.**
An audit found ten `assert-*.ps1` that no `.ps1` runner invokes and reported an enforcement hole, naming
`assert-deprecated-pm-flags.ps1` and CLAUDE.md Rule 21, which pointed at that file "(in post-change.ps1)".
Both halves were wrong. Since S1338 the lexical rules live once in `lib/source-matchers.ps1` and run as
named **dimensions** of `assert-source-gates.ps1`; `assert-neuroslop.ps1` forwards to it **with no -Only
filter**, so every closure judges all two dozen dimensions under the single label `neuroslop-gate`. The
seven "dead" scripts turned out to be 36-line forwarders (`$forward = @{ Only = 'globalscope' }`) kept as
named hand-run entry points. Nothing was ungated and nothing was worth deleting.

- The tell was in the output, not the wiring: running `assert-neuroslop.ps1 -Gate -ChangedFiles <f>` prints
  `assert-source-gates: PASS`, which says outright who does the work. Run the gate before theorising about it.
- A gate label disappearing from the transcripts (deprecated-pm-flags-gate, public-mutable-flow-gate and
  flavor-flag-gate all stop on 2026-08-14) means the **invocation** was consolidated, not that the rule
  stopped being judged. Check `assert-source-gates.ps1 -List` for the dimension name before concluding.
- Sequence that would have saved the whole detour: `-List` the umbrella, then grep the dimension name, then
  read the suspect script's first 30 lines. Three minutes against forty.
