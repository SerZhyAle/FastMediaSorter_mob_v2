---
name: ide-open-spec-may-finalize-midtask
description: An IDE-open Draft spec can be finalized by /spec-all concurrently while you implement - re-read it before committing to a design
type: feedback
metadata:
  type: feedback
---

When the task targets an `Sxxxx` spec that is open in the IDE and still `Draft`, its content/status can change under you mid-task (owner runs `/spec-all`, which auto-approves and writes a full tactical plan). Do not lock onto the §0 inbox / your own design - re-read the spec file (or watch for the Edit tool's "file modified since read") and reconcile to the authoritative plan before finishing.

**Why:** On S0593 I implemented Option A from the Draft §0 (reuse `lastPlayedAt`, single green badge, CSV `last_online` script), then found the file had been finalized to `Tactical` with a different richer design (two new Room columns + migration 34->35, 3-state OK/FAIL/unknown bullet, CSV stamping an explicit non-goal). Had to revert the off-spec work and redo to match the spec - a full rework.

**How to apply:** Resolve the spec via `select.ps1` at the start AND re-read the spec file right before writing code if it is IDE-open or was recently touched. The committed `Tactical` plan wins over the captured §0 idea. Treat "file modified since read" on a spec as a signal to re-read fully, not just re-Read-and-continue.
