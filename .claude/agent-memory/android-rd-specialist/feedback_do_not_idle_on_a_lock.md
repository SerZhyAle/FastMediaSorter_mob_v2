---
name: do-not-idle-on-a-lock
description: Owner interrupts with "не жди" when a turn is spent waiting on CODE.LOCK/BUILD.LOCK - keep the ticket moving on lock-free work instead of blocking
metadata:
  type: feedback
---

When a lock queue blocks the next edit, never spend the turn waiting on it. Do every lock-free part of the remaining work first - builds, docs, spec files, catalog, status transitions, verification of what is already written - and only then take the lock for the smallest possible edit. If the queue is still busy at the end, hand back with the one open item named, rather than idling.

**Why:** on 2026-08-06, during S1436, a sibling session held `CODE.LOCK` for ~20 minutes across two of my steps. I started a background waiter and reported "жду блокировку"; the owner cut in with "не жди". Waiting reads as stalling even when the queue contract (Rule 23) is being obeyed correctly - and it usually is avoidable, because most of what remains at that point (the debug build, the phase files, the privacy-policy rewrite, the closure gates) needs no lock at all.

**How to apply:** on `enter-code-lock.ps1` exit 4, start `wait-for-lock-turn.ps1` in the background as the contract requires, then immediately switch to the lock-free remainder rather than polling `lock-status.ps1`. Re-attempt the lock opportunistically between other steps - a head that has sat 19 minutes is near the 20-minute Code ceiling and the next reader evicts it. See [[no-concurrent-gradle-invocations]] for the build-side half of the same rule.

---

**A CODE.LOCK held by a session the owner already closed still blocks you for up to 15 minutes, and the PID in the lock file cannot tell you otherwise.** On 2026-08-07 the owner said "у меня нет ни одного процесса про код" while `lock-status.ps1` reported the lock HELD with `processAlive: True`. Both were right: Code-lock liveness is judged by the owning session's **transcript write time** against `SessionStaleMinutes = 15` (`agent-lock.ps1` `$Script:AgentLockTimings`), not by the process; and the recorded `pid` had been recycled by Windows to an unrelated `cmd`. Only the `procStart` field distinguishes them, and the Code branch does not consult it - the Build branch does.

**How to apply:** when the owner says no session is running, do not argue from `processAlive`. Read the holder's `sessionId` out of `temp/CODE.LOCK` and check the mtime of `~/.claude/projects/<project>/<sessionId>.jsonl` - a transcript frozen more than a couple of minutes ago with the owner denying the session means it is dead and merely not yet stale. Then `clear-agent-lock.ps1 -Name Code -Force`, which drops the lock **and the whole queue** (including your own ticket, so any background waiter you started exits 3 - expected, not an error) and re-enter the lock normally. Waiting out the 15 minutes instead is pure idle time.
