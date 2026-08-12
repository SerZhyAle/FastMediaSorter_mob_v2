---
name: long-gap-invalidates-round-state
description: After a multi-hour suspension a /spec-next or /spec-do round loses its lease, its temp round-state file, and its picture of the tactical INDEX - re-verify all three before continuing.
metadata:
  type: feedback
---

A `/spec-next` / `/spec-do` round that resumes after hours of wall-clock is not the round that started. Before continuing, re-check three things rather than trusting the session's own memory of them:

- **The round state file.** `temp/spec-next-session.<id>.json` can be swept while the session is suspended, and `-Verb Record` then fails with `no session state` (exit 1). Re-`Init`, re-set `-Verb Device`, then record - the work itself is already durable in `dev/CHANGELOG.md` and the phase files, so only the loop's tally is at risk.
- **The ticket lease.** It expires with session liveness, so `ticket-lease.ps1 -Verb Status -Id Sxxxx` can come back empty for a ticket this very session claimed. A sibling may have picked the ticket up in the gap - and the shared skip cache may name *your* session as the sibling that owns it.
- **The tactical INDEX.** Phases you did not touch can have moved from `Not started` to `✅ Done`, and your own phase row can already be flipped by whoever ran `/spec-dev` on the ticket meanwhile. Read the INDEX and the phase Step Logs before deciding what the next step is; a detailed Step Log with paths, LOC and gate verdicts is real work, not scaffolding.

**Why:** on 2026-08-09 a `/spec-do` round on S1433 stalled ~4 h waiting on `CODE.LOCK`. It came back to a swept state file, a lapsed lease, phases 06 and 07 finished by another session, and its own phase 05 row already flipped - while the shared skip cache carried an entry describing this session as the live owner.

**How to apply:** on any resumed round, run the three checks before the next edit. If a sibling now owns the ticket's forward edge, close out only what you can prove is yours, record the outcome, release the lease and hand the ticket back rather than racing into the next phase. See [[ticket-busyness-is-a-lease-not-a-status]].
