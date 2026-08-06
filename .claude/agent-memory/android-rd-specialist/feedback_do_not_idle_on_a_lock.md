---
name: do-not-idle-on-a-lock
description: Owner interrupts with "не жди" when a turn is spent waiting on CODE.LOCK/BUILD.LOCK - keep the ticket moving on lock-free work instead of blocking
metadata:
  type: feedback
---

When a lock queue blocks the next edit, never spend the turn waiting on it. Do every lock-free part of the remaining work first - builds, docs, spec files, catalog, status transitions, verification of what is already written - and only then take the lock for the smallest possible edit. If the queue is still busy at the end, hand back with the one open item named, rather than idling.

**Why:** on 2026-08-06, during S1436, a sibling session held `CODE.LOCK` for ~20 minutes across two of my steps. I started a background waiter and reported "жду блокировку"; the owner cut in with "не жди". Waiting reads as stalling even when the queue contract (Rule 23) is being obeyed correctly - and it usually is avoidable, because most of what remains at that point (the debug build, the phase files, the privacy-policy rewrite, the closure gates) needs no lock at all.

**How to apply:** on `enter-code-lock.ps1` exit 4, start `wait-for-lock-turn.ps1` in the background as the contract requires, then immediately switch to the lock-free remainder rather than polling `lock-status.ps1`. Re-attempt the lock opportunistically between other steps - a head that has sat 19 minutes is near the 20-minute Code ceiling and the next reader evicts it. See [[no-concurrent-gradle-invocations]] for the build-side half of the same rule.
