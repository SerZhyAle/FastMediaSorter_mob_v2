---
name: never-batch-code-lock-with-the-edit
description: Never put enter-code-lock.ps1 and the edits it guards in the same message - a queued refusal still lets the edits land
metadata:
  type: feedback
---

`enter-code-lock.ps1` and the edits it protects go in **separate turns**. Acquire, read the verdict, then edit.

**Why:** on 2026-08-15 (S1696) I sent `enter-code-lock.ps1` and two `Edit` calls in one message. The lock refused with **exit 4** - "CODE.LOCK is free, but this session is not the queue head - queued at position 2", the head being a sibling running `/spec-dev S0494`. Parallel tool calls in one message do not see each other's results, so the edit applied anyway, unguarded, while a `build-debug.PS1` was live on BUILD.LOCK and could have compiled a half-written file. The lock did its job; the batching defeated it.

**How to apply:** the lock call is its own turn, always. Exit 0 means go; **exit 4 means queued - do not edit**, launch `scripts/utils/wait-for-lock-turn.ps1 -Name Code` as a *background* task and do lock-free work (specs, docs, catalog, research, memory) until its completion notification arrives. The same rule covers any acquire-then-act pair: the acquire is a gate, and a gate you did not wait for is not a gate.

Related: [[code-lock-release-ownership]] - the mirror-image mistake on the release side.
