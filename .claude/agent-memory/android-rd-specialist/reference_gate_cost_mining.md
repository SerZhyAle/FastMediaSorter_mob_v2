---
name: gate-cost-mining
description: How to measure what the quality gates actually cost from transcripts, and the two traps that make the naive answer wrong
metadata:
  type: reference
---

Gate cost is recoverable from the session transcripts: `post-change.ps1` prints every step as
`  [<label>] <PASS|FAIL|SKIP> (<N> ms)`, so a regex over `~/.claude/projects/**/*.jsonl` yields runs,
failures and wall time per gate. Measured 2026-08-07 over three weeks: **8,562 runs, 555 failures,
2,803 minutes** of wall time.

**Trap 1 - do not count tool invocations by grepping for a script name.** A first pass counted
occurrences of `spec_catalog/`, `post-change.ps1`, `document_registry` and reported ~45,500 "tooling
invocations". Those are *mentions*: rule text, command drivers and prose in the transcript all contain
the strings. The verdict lines are the only records that mean a run happened - `document-registry`
really ran 86 times, not 6,188.

**Trap 2 - the printed ms includes waiting for `BUILD.LOCK`, not just the work.** `Invoke-Step` starts
its stopwatch before the child process, and a gradle-backed gate queues on the lock inside that window.
detekt averaged 152 s across 947 recorded runs, but a directly measured run on a warm daemon with the
configuration cache reused was **25 s**. Both numbers are true and they answer different questions:
scheduling cost versus compute cost. Say which one you mean.

**Why:** the conclusion flipped twice under these traps. The distribution is extremely skewed - detekt
alone was 86% of all gate wall time (2,409 of 2,803 minutes), and the thirteen gates that never fired
once in three weeks cost 133 minutes between them, 4.7%. "Delete the gates that never catch anything"
looks obvious and buys almost nothing while removing insurance.

**How to apply:** before proposing any gate be removed, weakened or reordered, produce the per-gate
table first and check where the mass actually is. Optimise the head of the distribution (caching,
skipping work whose inputs did not change, not queueing for a lock you do not need); leave the cheap
never-firing gates alone. See [[post-change-dev-log-first-file-only]] and
[[transcript-cost-mining]] for the dedup rules that apply to any transcript-derived count.
