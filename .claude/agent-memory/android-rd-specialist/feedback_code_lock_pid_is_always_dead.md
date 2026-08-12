---
name: code-lock-pid-is-always-dead
description: A dead pid in CODE.LOCK proves nothing - judge its holder by the session transcript; only BUILD.LOCK has a real process behind it
type: feedback
metadata:
  type: feedback
---

**Never conclude "the lock holder died" from `Get-Process -Id <CODE.LOCK pid>` returning nothing.** That pid is always dead within seconds. Judge a `CODE.LOCK` holder by the write time of the transcript named in its `sessionId` / `transcriptPath` field.

**Why:** the pid in `temp/CODE.LOCK` belongs to the `enter-code-lock.ps1` process, which writes the file and exits immediately - a Claude Code editing turn is not one continuous OS process, nothing runs between tool calls. `temp/BUILD.LOCK` is the opposite: a real gradle process owns it for its whole life, which is why `lock-status.ps1 -Name Build` prints `processAlive:` and the `Code` output has no such line at all. On 2026-08-08 the owner read a dead pid on a `CODE.LOCK` whose session had written its transcript 6 seconds earlier and concluded the queue was jammed by a corpse; the real corpse was elsewhere, in the queue.

**How to apply:** to decide whether anything is actually stuck, run `lock-status.ps1 -Name Code -Queue` twice - both the lock queue and the lease store self-clean on *any* read, with no watchdog process, so the first read may itself be what removes the dead entry. Thresholds live in `$Script:AgentLockTimings` (Code: lock stale 10 min, session stale 15, queue ceiling 20, reservation 3) - compare against those, not against intuition. A queue entry whose transcript is silent past the session-stale window is the thing to suspect. Related: [[agent-lock-release-lies]], [[code-lock-is-per-step-not-per-ticket]], [[do-not-idle-on-a-lock]].
