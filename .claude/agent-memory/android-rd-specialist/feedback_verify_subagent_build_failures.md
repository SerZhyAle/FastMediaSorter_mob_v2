---
name: verify-subagent-build-failures
description: Never trust a delegated sub-agent's build claim (pass OR fail) or its root-cause diagnosis - self-verify with your own `.\a.ps1 dq` / your own read of the code before acting on it.
metadata:
  type: feedback
---

When a delegated sub-agent (android-kotlin-developer) reports "BUILD FAILED" with a compile error in an unrelated pre-existing file, re-run `.\a.ps1 dq` yourself before treating it as a hard stop. Agent's incremental Gradle/kapt cache may be stale - especially after a previous Phase added many new files (Hilt graph regenerates).

**Background build outlives the sub-agent's turn (2026-07-14, S1039).** When you delegate impl and the sub-agent launches its build via `run_in_background`, the sub-agent's turn (and task-notification) can fire while the detached gradle build is STILL RUNNING. Symptoms: the sub-agent's final `<result>` is truncated mid-thought ("waiting for the build to finish.."), and `temp/BUILD.LOCK` is still HELD by a live PID (`lock-status.ps1 -Name Build` shows `processAlive: True`). Do NOT start your own build then (Rule 23 - it would refuse). Instead: (1) `lock-status.ps1 -Name Build` to see if a build is live; (2) if held, wait for release via a `run_in_background` bash `until` loop polling `lock-status.ps1` (one notification on release), never a foreground sleep; (3) once free, run your own `.\a.ps1 dq` (fast/UP-TO-DATE if nothing changed since = confirms compile) + targeted tests. Meanwhile verify the impl statically (Grep/Glob for the expected symbols + debug tags) - that needs no lock and confirms the sub-agent actually wrote everything.

**A device-test verdict's ROOT CAUSE is a hypothesis, not evidence (2026-07-26, S1115).** A `/spec-sweep` subagent
reported the standalone video host could not reach panel-hidden fullscreen "because `setupCloseButton()`
unconditionally re-shows `topCommandPanel`". Reading the code disproved it - `setupCloseButton()` runs from
`setupViews()`, well before the fullscreen-entry block in `observeData()`. The real cause was a deprecated
`SYSTEM_UI_FLAG_FULLSCREEN` check that reads as "bars visible" unconditionally on API 30+. Fixing the reported
cause would have changed nothing while looking done. Note the asymmetry: the subagent's *observation* (panel is
visible when it should be hidden) was correct and valuable; only its *explanation* was wrong.
**How to apply:** treat a delegated verdict's PASS/FAIL observation as evidence and its "because X" as a lead.
Before editing the named code, find one cheap discriminator that separates the reported cause from your own
reading (here: one log line that only exists if fullscreen was actually entered), and run that first. Never swap
one unproven diagnosis for another.

**Why:** Observed during S0242 /spec-all execution (2026-05-18). Agent reported `Unresolved reference 'onMetadataErrors'` in `SmbMediaScanner.kt:175` claiming unrelated pre-existing breakage. Reading the file confirmed `ScanProgressCallback.onMetadataErrors(...)` was indeed defined. A fresh `.\a.ps1 dq` from my own session: BUILD SUCCESSFUL in 26s. Agent had not done a clean run after the Phase 01 Hilt additions polluted incremental cache.

**How to apply:**
- If sub-agent reports a build failure in a file that is NOT in its edit scope, run `.\a.ps1 dq` myself via PowerShell before believing the block. Cost is ~30 seconds, saves potentially blocking the entire pipeline on a non-issue.
- If my re-run also FAILS with the same error → the agent was right; proceed with hard-stop / user question.
- If my re-run PASSES → mark the disputed step `[x] done` with corrected Step Log explaining the stale-cache cause, and re-delegate the remaining steps.
- Do NOT assume the agent verified its own run was deterministic - Gradle incremental build is notorious for caching across DI graph changes.
- Related: [[build-gotchas]] documents the broader "daemon stopped → retry" pattern.
