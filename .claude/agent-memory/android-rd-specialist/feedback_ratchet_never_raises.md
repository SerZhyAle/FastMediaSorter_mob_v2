---
name: ratchet-never-raises
description: A red count-ratchet gate (class-architecture-naming, listener-symmetry, neuroslop..) cannot be absorbed - -UpdateBaseline only lowers, so the only exits are fixing the code or fixing a false positive in the counter
metadata:
  type: feedback
---

When a count-ratchet gate in `scripts/quality/` reports `actual > baseline`, do not reach for
`-UpdateBaseline`. Every one of these gates refuses to RAISE its baseline and only ever ratchets it
down on a green run. There are exactly two honest exits:

1. Fix the code so the count returns to the baseline.
2. Prove the growth is a false positive and fix the COUNTER, then let the baseline ratchet down.

**Why:** the baselines are debt caps, so a gate that could be widened would stop being a cap at all.
Measured 2026-08-19: `class-architecture-naming` was +6 and `listener-symmetry` +2 before a
pre-release build, and the first instinct - absorb both into the baseline - is refused outright by
the scripts (`assert-source-gates.ps1` prints `refusing to RAISE baseline`).

**How to apply:** read the counter before assuming the finding is real. Exit 2 is legitimate and was
the right answer for listener-symmetry, whose library stripped `import` lines but still counted an
`addObserver` written inside a KDoc - four phantom registrations in one file. Prove it by measuring
the whole-repo count before and after the counter fix, and by naming every file the fix moves in the
adverse direction; if exactly one file grows, read that file and confirm what it means before
shipping the change. Related: [[detekt-scoped-gate-surfaces-untouched-debt]].
