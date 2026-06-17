---
name: no-concurrent-gradle-invocations
description: Running multiple gradle/kotlin invocations at once (post-change Kotlin gates + a build) causes OOM via multiple Kotlin daemon sessions
type: feedback
---

Never run several gradle-backed commands concurrently on this machine. The `post-change.ps1` Kotlin ChangeType gate spins a Gradle/Kotlin compile (observed 2-16 min each). Launching 6 of them in the background **and** an `a.ps1 fk` build at the same time produced "Detected multiple Kotlin daemon sessions" then `java.lang.OutOfMemoryError: Java heap space` in `kaptGenerateStubsStandardDebugKotlin`, failing the build after 25 min.

**Why:** each invocation starts its own Kotlin daemon; the machine's heap can't host 7 simultaneously. The failure looks like a code/compile error but is pure resource contention - retrying serially passes.

**How to apply:** during `/spec-dev`, run post-change closures **sequentially** (one pwsh process, looped) and only start the Phase-Done build **after** they finish. If a build OOMs with multiple-daemon warnings, it's contention - kill stragglers, retry the single build clean (see [[project_build_gotchas]]). kapt recovery: `scripts/utils/recover-kapt-stall.ps1`.
