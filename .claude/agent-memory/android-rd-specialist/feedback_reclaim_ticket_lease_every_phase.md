---
name: reclaim-ticket-lease-every-phase
description: Re-claim the Sxxxx ticket lease at every phase boundary - it expires during long runs and a sibling session takes the ticket mid-flight
metadata:
  type: feedback
---

Re-claim the ticket lease (`ticket-lease.ps1 -Verb Claim -Id Sxxxx`) at **every phase boundary**, not
once at the start. The lease expires on liveness, and a long single-ticket run outlives it.

**Why:** observed twice in one session on 2026-08-18. Driving S1781 through five phases took long
enough that my lease lapsed; a sibling `/spec-do` claimed S1781 at 21:56:57 while I was mid-Phase-08,
so two sessions were on one ticket and I had to hand over the terminal transition instead of finishing
it. The same thing had already happened to the session working S1802: it still held `CODE.LOCK` with
reason `/spec-do S1802 phase 03` while its ticket lease was gone, so preflight offered S1802 to me as
free. `/spec-all` says this in one line - "Re-claim the lease at long-running phase boundaries to
refresh its heartbeat" - and it is easy to read as optional. It is not.

**How to apply:**

- Claim again right after each `plan-tick ... -State Done` that closes a phase. It is one cheap call
  and re-claiming a lease you already hold prints `already held by this session`.
- Before claiming a ticket preflight offered, check `lock-status.ps1 -Name Code` first. A free lease
  plus a `CODE.LOCK` naming that ticket means a live session is working it with a lapsed lease -
  skip it and take the next candidate rather than repeating the collision.
- If your own lease is gone and a sibling holds it, do not race: land what is done through
  `post-change.ps1`, write the remaining work into the tactical `INDEX.md` as a handover, and record
  the round as `advanced`.
