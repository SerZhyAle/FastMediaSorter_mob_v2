---
name: verify-subagent-build-failures
description: When a sub-agent reports a build failure that blocks spec work, re-run the build yourself before accepting it as a hard stop - agent's incremental kapt cache may be stale.
metadata:
  type: feedback
---

When a delegated sub-agent (android-kotlin-developer) reports "BUILD FAILED" with a compile error in an unrelated pre-existing file, re-run `.\a.ps1 dq` yourself before treating it as a hard stop. Agent's incremental Gradle/kapt cache may be stale - especially after a previous Phase added many new files (Hilt graph regenerates).

**Why:** Observed during S0242 /spec-all execution (2026-05-18). Agent reported `Unresolved reference 'onMetadataErrors'` in `SmbMediaScanner.kt:175` claiming unrelated pre-existing breakage. Reading the file confirmed `ScanProgressCallback.onMetadataErrors(...)` was indeed defined. A fresh `.\a.ps1 dq` from my own session: BUILD SUCCESSFUL in 26s. Agent had not done a clean run after the Phase 01 Hilt additions polluted incremental cache.

**How to apply:**
- If sub-agent reports a build failure in a file that is NOT in its edit scope, run `.\a.ps1 dq` myself via PowerShell before believing the block. Cost is ~30 seconds, saves potentially blocking the entire pipeline on a non-issue.
- If my re-run also FAILS with the same error → the agent was right; proceed with hard-stop / user question.
- If my re-run PASSES → mark the disputed step `[x] done` with corrected Step Log explaining the stale-cache cause, and re-delegate the remaining steps.
- Do NOT assume the agent verified its own run was deterministic - Gradle incremental build is notorious for caching across DI graph changes.
- Related: [[build-gotchas]] documents the broader "daemon stopped → retry" pattern.
