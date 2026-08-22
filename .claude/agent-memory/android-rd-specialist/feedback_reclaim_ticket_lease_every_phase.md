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

Third occurrence, 2026-08-21, and the most expensive so far: driving S1873 through three phases of
`/spec-all` - research, spec, tactical plan, then live gradle measurements - outlived the lease
without a single re-claim. Session `01a021cf` (`/spec-next`) claimed S1873 and began editing the phase
files I had just authored, marking steps done, while I still had verified work uncommitted to the
plan. Claiming again returned "already claimed by session .." and I had to hand the ticket over
mid-implementation. The tell was not a lock or an error: it was **my own phase file changing on disk
under me**. Treat an unexplained edit to a file you are the only author of as a lease check, not as a
mystery.

Fourth occurrence, 2026-08-21, with a **new trigger worth naming separately: waiting in the `CODE.LOCK`
queue burns the lease.** Driving S1897 needed the code lock four separate times, and with three sibling
sessions live each wait ran minutes. The work between waits was short and correct - no long phase, no
gradle marathon - yet the accumulated *queue* time was enough to lose the lease, and session `787173c7`
(`/spec-all`) took S1897 and completed a phase I had planned. The tell was the same as last time: a phase
file I authored showed up as ✅ Done with an audit section I had not written. Being blocked feels like
doing nothing, so it does not register as elapsed time - but the lease measures wall clock, not effort.

**How to apply:**

- Re-claim right after every `enter-code-lock` / `wait-for-lock-turn` round trip, not only after a phase.
  A queued wait is a lease-expiry event even though no work happened during it.

- Claim again right after each `plan-tick ... -State Done` that closes a phase. It is one cheap call
  and re-claiming a lease you already hold prints `already held by this session`.
- Before claiming a ticket preflight offered, check `lock-status.ps1 -Name Code` first. A free lease
  plus a `CODE.LOCK` naming that ticket means a live session is working it with a lapsed lease -
  skip it and take the next candidate rather than repeating the collision.
- If your own lease is gone and a sibling holds it, do not race: land what is done through
  `post-change.ps1`, write the remaining work into the tactical `INDEX.md` as a handover, and record
  the round as `advanced`.
- A long research or measurement step counts as a phase boundary too. Two gradle measurement runs and
  a subagent report are enough wall-clock to lose the lease, and nothing about them looks like a
  transition.
- When the lease is already gone, do not race: write a handoff into the tactical `INDEX.md` Blockers
  Log naming exactly what is already applied and verified, then stop touching that ticket. The
  incoming session cannot see your evidence any other way, and it will otherwise re-apply or revert
  work that was already proven.
