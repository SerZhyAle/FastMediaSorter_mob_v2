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

**Concurrent status-flip variant (2026-07-15, S1051/S1054):** the owner also flips a freshly-created ticket's *status* mid-loop, not just its content. On S1054 (owner-created 10:49, Approved) I found the fix already in code (`StreamsViewModel`, ADR-1) with an `Sxxxx:` probe present but status still `Approved` (invariant violated) - a strong "owner is finalizing right now" tell. My Last Audit append triggered "file modified since read" even though I had only *read* the spec (not mutated it via tooling), meaning an external process (owner) was live-editing. `select.ps1` then showed the owner had already flipped it to `BlockNeedUserTest` + written a StatusNote. Rule: before running `update.ps1 -Status` on a ticket the owner is actively curating, re-check the authoritative status via `select.ps1`; if the owner already set it, do nothing (drift-review Last Audit is additive/safe, a status flip could race the owner's `.md` header). A recently-created spec (minutes-old `updated`) + a probe-without-BlockNeedUserTest + "modified on disk" on a read-only spec all point to active owner work - defer the status change to them.
