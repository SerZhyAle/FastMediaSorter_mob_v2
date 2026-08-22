---
name: no-kotlin-ticket-cannot-end-at-blockneedusertest
description: A ticket that changes no .kt cannot be parked in BlockNeedUserTest - the debug-tag invariant demands a Timber probe and there is no flow entry to host one; capture device evidence and close at Implemented instead.
metadata:
  type: feedback
---

A ticket whose whole change is strings, layouts, resources or documentation - no `.kt` touched - must not be
routed to `BlockNeedUserTest`, even when a human plainly ought to look at the result.

**Why:** the debug-tag invariant is two-directional. `/spec-check` scores `BlockNeedUserTest` only with at least
one `Timber.d("Sxxxx:` in `.kt`, and explicitly says that a spec in that status carrying none must gain one
"at the changed flow entry" before the flip, or the ticket-log gate refuses the close. A ticket that changed no
Kotlin has no changed flow entry, so the only way to satisfy the gate is to edit a Kotlin file purely to host a
probe - touching code the ticket had no reason to touch, to satisfy a gate about code it did not change.
Hit on S1919 (RU/UK wording + a settings-row icon + regenerated docs; zero `.kt`).

**How to apply:** when such a ticket needs a human to see the result, do not park it. Capture the evidence
yourself - `scripts/devtest/adb.ps1 install/launch/tap-id/uidump/shot` on the ticket's own build - write the
verdict into the spec, and close through `Implemented` -> `/spec-check` -> `Verified`. The S1338 UI-evidence
refusal is satisfied by the artifact, not by the status.

Two traps when doing that:

- **Cite durable paths.** `check-evidence-durable.ps1` runs inside the closing gates and refuses a spec that
  cites `temp/` - it is disposable under Rule 1. Copy the artifact into `PLAN/Sxxxx_<slug>/evidence/`
  (64 KB per file cap, so a UI tree usually fits and a PNG usually does not) or replace it with a reproducing
  command plus its expected result.
- **An emulator may not be able to rotate.** A sibling session can leave an `Override size` set
  (`wm size`), which pins the display shape; `settings put system user_rotation` then does nothing. Check
  `wm size` before claiming an orientation was tested, say which orientation the evidence actually shows, and
  do not reset another session's device configuration to get the other one.

See also [[reference_ticket_log_gate]] and [[feedback_a_pass_that_observed_nothing]].
