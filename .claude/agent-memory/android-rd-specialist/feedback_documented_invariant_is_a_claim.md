---
name: documented-invariant-is-a-claim
description: A phase-log audit line or an ARCHITECTURE.md invariant is a claim someone wrote, not a verified fact - re-check it in code before trusting or repeating it
metadata:
  type: feedback
---

A written invariant - a phase file's "Phase-boundary audit run, no P0/P1", an `## Last Audit`
verdict, a sentence in `docs/ARCHITECTURE.md` - records what someone believed at the time. Verify
it against the code before relying on it, and especially before repeating it in a new document.

**Why:** S1225 (2026-07-31) found a P1 where two independent records asserted the same false
invariant. `docs/ARCHITECTURE.md` said folder-walk progress was "rate-limited through the same
`TransferProgressReporter` the file path uses", and S1325's own phase-04 audit said "a tree of many
small files cannot flood the notification channel". The call site passed `forcePublish = true`
alongside its interval argument, so the gate was never armed and the guard below it was unreachable.
Both documents had been written by someone who read the call and saw the reporter's name in it.

**How to apply:** When a doc or phase log states an invariant that matters to the change in hand,
open the code path and confirm it. Two smells that this specific class of bug leaves behind: a
throttle/interval argument passed next to a `force`/`skip` flag that overrides it, and a guard whose
condition can never be true. When the audit ran and the ticket still shipped the defect, treat the
phase's `Files Touched` as unaudited and redo it - see [[feedback-phase-boundary-audit]].
