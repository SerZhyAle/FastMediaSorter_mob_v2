---
description: "Use to run /spec-next without the context-threshold stop - the unbounded escape hatch. Triggers: 'spec-do', 'run unbounded', 'don't stop for context'."
---

# Spec-Do - Unbounded Autonomous Loop

**This is the unbounded variant of `/spec-next`. Context will grow without limit until the backlog itself is exhausted or a genuine human-gated blocker is hit.** Use it only when you have deliberately decided to spend the tokens; the bounded default (`/spec-next`) exists precisely so this is not the accidental habit.

Full process: [.claude/commands/spec-next.md](.claude/commands/spec-next.md) - every stage, every hard rule, every eligibility rule is identical. Read it; do not re-derive it here. The differences are exactly these three.

## Differences from `/spec-next`

1. **Stage 5b never stops the loop.** `-Verb CheckContext` still runs after every `-Verb Record` (identical call, same script). Its `tokens`/`threshold` JSON is printed in the round verdict every round, even under threshold - the operator watches the cost accumulate. Exit 3 (crossed) does **not** trigger `-Verb Handoff` and does **not** stop the loop here - log `[unbounded] context <tokens>k / threshold <n>k - continuing` and proceed straight to the next Stage 1 call.
2. **Loud start banner.** Before Stage 0, print exactly: `/spec-do: UNBOUNDED - context will grow without limit until the backlog is exhausted or a genuine blocker is hit. Use /spec-next for the bounded default.` Not optional, not skippable - an escape hatch that looks like the default is a trap.
3. **Usage forms.** `/spec-do` (fresh session, `-Verb Init`), `/spec-do --resume` (`-Verb Resume` - works whether the prior session was started by `/spec-next` or `/spec-do`, same state file), `/spec-do --once` (one round, still unbounded within it), `/spec-do --dry`, `/spec-do --plan` (both behave exactly as in `/spec-next` - they never reach Stage 5b), `/spec-do --threshold <n>` (accepted and still reported, it just never halts anything here).

**Parallel sessions (S1437).** Two or three `/spec-do` and `/spec-next` sessions may run at once against one working tree: each keeps its own round state and claims its ticket before working it, so they take different tickets rather than duplicating each other. The claim and release calls live in the stage text this command inherits from `spec-next.md` (Stages 3.5 and 5) - they are not repeated here, because a second copy is a second place to update.

Everything else - Stage 1 through Stage 6, Stage 5.5 device drain, the Hard rules, the Forbidden list - is `/spec-next`, verbatim. `/spec-do` stops only on the conditions that are about work rather than cost: nothing left the machine can advance alone (Stage 6), or a genuine human-gated blocker.

The unverified-backlog ceiling (S1338 package I) is a correctness limit, not a cost limit, and is out of scope here - once landed, `-Verb CheckContext` gains a second stop reason there, not yet active. Do not add a "just this once" flag to `/spec-next` that duplicates this command - one named, loud command is the whole point.
