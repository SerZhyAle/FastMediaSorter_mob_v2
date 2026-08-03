---
name: schedulewakeup-loop-mode-only
description: ScheduleWakeup tool is scoped to /loop dynamic mode - don't call it to wait on a background task inside /spec-do or any other non-loop session.
type: feedback
---
`ScheduleWakeup` exists to self-pace `/loop` (no-interval) dynamic-mode iterations. It is not
a generic "wait for my background task" primitive, even though its own description discusses
fallback-heartbeat patterns that sound applicable anywhere. A background task started via `Bash
run_in_background:true` (or an `Agent`/`Workflow` call) is already harness-tracked and delivers its
own `<task-notification>` on completion - no wakeup needed to catch that.

**Why:** during an S1349 fix-verification (`/spec-do` session, not `/loop`), called `ScheduleWakeup`
with the `<<autonomous-loop-dynamic>>` sentinel purely to avoid polling a background gate-verification
run. That sentinel is meaningless outside an active `/loop` - the session had none, so the call was a
category error, only caught because the background task's own notification arrived first and made the
scheduled wakeup redundant (cancelled via `stop:true` before it could fire).

**How to apply:** if already waiting on a `run_in_background` task, self, `Agent`, or `Workflow` call,
never call `ScheduleWakeup` for it - just stop making tool calls and let the notification arrive.
Reserve `ScheduleWakeup` for literal `/loop` sessions (dynamic mode, no fixed interval) where the
skill instructions actually call for self-pacing between iterations that are NOT already tracked by
the harness (e.g. "check back on an external cron/deploy in N minutes").
