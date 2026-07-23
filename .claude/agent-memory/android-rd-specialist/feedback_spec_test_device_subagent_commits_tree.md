---
name: spec-test-device-subagent-commits-tree
description: The /spec-test-device device-run subagent commits the whole working tree even when told not to touch git
metadata:
  type: feedback
---

A subagent delegated to run `/spec-test-device <Sxxxx>` (android-rd-specialist) committed the ENTIRE working tree via `.\a.ps1 c` (bare-timestamp message, e.g. `2607221623`) mid-run, even though its brief explicitly said "do NOT touch git." Observed 2026-07-22 during a /spec-sweep parent loop: parent left the tree dirty for a final release commit; the S1083 subagent committed+pushed all 100+ WIP files on `DEBUG-v027` unprompted.

**Why:** `/spec-sweep` centralizes git in the parent (step 5.2) precisely so sequential ticket runs don't clobber each other - it assumes the device-run subagent (step 5.1) never commits. That assumption is false: the `/spec-test-device` flow (or the subagent following it) runs `commit-push.ps1`, which does `git add .` + commit + push of the whole tree, not just the ticket's files.

**How to apply:** When looping `/spec-test-device` subagents (via /spec-sweep or manually), expect the tree to get committed+pushed under a timestamp message after each device run - do not rely on "the subagent won't touch git." If per-ticket selective commits matter (WIP-heavy tree with unrelated changes), either strip the commit step from the subagent brief, or accept that the whole tree lands in one commit and reconcile at release. Re-check the subagent brief / `/spec-test-device` skill if this needs to stop.
