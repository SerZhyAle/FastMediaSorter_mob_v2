---
name: verify-subagent-build-failures
description: When a research report consumes another sub-agent's build summary, treat "build failed" as a claim to verify - stale incremental kapt caches produce false negatives
metadata:
  type: feedback
---

When the research report consumes another sub-agent's build summary (e.g. an `android-kotlin-developer` reports "BUILD FAILED" with a compile error in an unrelated pre-existing file), treat it as a claim to verify by reading the actual log or asking for a re-run - not as ground truth. Stale incremental Gradle/kapt cache from a previous Phase's Hilt graph regeneration produces false negatives.

**Why:** Observed during S0242 /spec-all execution (2026-05-18). A writer agent reported `Unresolved reference 'onMetadataErrors'` in `SmbMediaScanner.kt:175` claiming unrelated pre-existing breakage. Reading the file confirmed `ScanProgressCallback.onMetadataErrors(..)` was indeed defined. A fresh `a.ps1 dq` from the operator's session: BUILD SUCCESSFUL in 26s. The agent had not done a clean run after the Phase 01 Hilt additions polluted incremental cache.

**How to apply:**
- The researcher does NOT itself run `a.ps1 dq` (that is a build operation). But when a delegated-research scope includes "interpret the previous agent's build report", the correct move is to:
  1. Read the cited compile-error file directly via `Read`/`Grep` and check whether the missing symbol actually exists on disk.
  2. If the symbol exists: cite the discrepancy in the research report under "Risks Identified" - "Sub-agent report disagrees with disk; likely stale incremental cache. Recommend writer-agent re-run with clean build."
  3. If the symbol is genuinely missing: cite the failure as real, with file:line and the actual missing reference.
- Do NOT propagate an unverified build failure into the research report as if it were authoritative - this can block downstream spec work on a non-issue.
- Related: [[build-gotchas]] documents the broader "daemon stopped → retry" pattern that the researcher should also account for when interpreting build summaries.
