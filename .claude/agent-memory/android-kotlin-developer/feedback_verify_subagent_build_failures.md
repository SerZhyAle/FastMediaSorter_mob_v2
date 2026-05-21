---
name: feedback_verify_subagent_build_failures
description: When a sub-agent reports a build failure that blocks spec work, re-run the build yourself before accepting it as a hard stop - agent's incremental kapt cache may be stale.
metadata:
  type: feedback
---

When a delegated sub-agent (android-kotlin-developer) reports "BUILD FAILED" with a compile error in an unrelated pre-existing file, re-run `.\a.ps1 dq` yourself before treating it as a hard stop. Agent's incremental Gradle/kapt cache may be stale - especially after a previous Phase added many new files (Hilt graph regenerates).

**Why:** Observed during S0242 /spec-all execution (2026-05-18). Agent reported `Unresolved reference 'onMetadataErrors'` in `SmbMediaScanner.kt:175` claiming unrelated pre-existing breakage. Reading the file confirmed `ScanProgressCallback.onMetadataErrors(...)` was indeed defined. A fresh `.\a.ps1 dq` from my own session: BUILD SUCCESSFUL in 26s. Agent had not done a clean run after the Phase 01 Hilt additions polluted incremental cache.

**How to apply:**
- After my own implementation Phase lands many new `.kt` files or DI modules (Hilt graph regenerated), my next `.\a.ps1 dq` may report a "compile error" in a file I never touched. Before reporting it as a hard stop, run `./gradlew --stop`, ensure `temp/gradle-tmp` exists, then re-run `.\a.ps1 dq` once - the stale kapt/incremental cache may be the only thing broken.
- If the re-run also FAILS with the same error → the failure is real; investigate the named class.
- If the re-run PASSES → mark the build green, document "stale incremental cache after Hilt graph regen" in the Step Log, continue.
- Do NOT assume Gradle's incremental build is deterministic across DI graph changes - it is notorious for caching stale type-resolution state.
- Related: [[project_build_gotchas]] documents the broader "daemon stopped → retry" pattern.
