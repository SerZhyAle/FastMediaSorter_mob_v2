---
name: ticket-busyness-is-a-lease-not-a-status
description: "Who is working a ticket right now" is the lease store, never a catalog status - owner ruled 2026-08-08, ticket S1518
metadata:
  type: feedback
---

Never propose a new lifecycle status to mean "a session is already driving this ticket". Runtime ownership belongs to the ticket-lease store (`scripts/spec_catalog/ticket-lease.ps1`), the catalog status stays the resume point.

**Why:** owner asked for exactly such a status on 2026-08-08 ("другой разработчик может взять draft"), then accepted the counter-argument and parked S1518 instead. A status lives in a git-tracked journal, cannot expire, and carries no owner identity or heartbeat - a crashed session would leave it stuck forever. `/spec-all` also routes its stages off the status (Draft -> F1, Approved -> F2, Tactical -> F3), so overwriting it destroys where to resume. The lease store already does the job properly: atomic claim, owner-checked release, liveness plus a 480-minute ceiling, self-sweeping on read.

**How to apply:** when a ticket looks free but might be taken, read `ticket-lease.ps1 -Verb Status` - not the status column. When the coordination gap is that a *command* fails to claim (S1518: `/spec-all` and `/spec-dev` claim nothing; only `/spec-next`, `/spec-do` and the queue driver do), fix the claim coverage, not the status enum. Related: [[spec_all_queue_driver_stage0_silence]].
