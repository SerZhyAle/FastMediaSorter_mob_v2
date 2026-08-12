---
name: silent-subagent-is-not-stuck
description: Before force-killing a long-running background agent, check its artifact directory mtimes - chat silence is not evidence of a stall
metadata:
  type: feedback
---

A background subagent that has said nothing for twenty minutes is not necessarily stuck. Judge its
liveness by the files it is writing, not by its silence: `ls -la --time-style=+%H:%M:%S temp/<Sxxxx>/`
and compare against the clock. Kill it only when the artifacts have actually stopped moving.

**Why:** On 2026-08-11 the owner asked to force-reset stuck sessions during a device sweep. Every
mechanism that can genuinely stall a run was clean - both locks absent, both queues empty, no stale
ticket lease - and the one background agent had written a new evidence file eleven seconds before
the check. Killing it would have discarded a run that had already built and proved its test fixture.
What it actually needed was one missing fact, delivered by SendMessage; it converged within minutes.
A device agent is legitimately silent for long stretches because taps, screenshots and logcat
harvests happen between tool calls, not in chat.

**How to apply:** When asked to reset stuck work, or when tempted to kill a slow agent, run the
diagnosis in this order: `lock-status.ps1 -Name Build|Code -Queue`, `ticket-lease.ps1 -Verb List`
and `-Verb Sweep`, then artifact mtimes. If everything is clean and the artifacts are moving,
nothing is stuck - say so with the evidence, and correct the agent with SendMessage instead of
restarting it. Note that the `.output` file of a `local_agent` task is a symlink to its full
transcript and often reads as zero bytes: it is not a progress signal in either direction.
Related: [[feedback_background_task_exit_code_is_echo]], [[feedback_lock_code_lock_pid_is_always_dead]].
