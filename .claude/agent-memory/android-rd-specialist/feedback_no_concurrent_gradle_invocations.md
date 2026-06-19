---
name: no-concurrent-gradle-invocations
description: Never run several gradle-backed builds at once - multiple Kotlin daemons OOM the machine; post-change.ps1 itself is static (no compile)
type: feedback
---

Never run several **gradle-backed builds** concurrently on this machine. Launching multiple background builds (`a.ps1 d`/`fk`/`fc`/`dq`, gradle `assemble*`) at once produced "Detected multiple Kotlin daemon sessions" then `java.lang.OutOfMemoryError: Java heap space` in `kaptGenerateStubsStandardDebugKotlin`, failing after ~25 min.

**Correction (verified 2026-06-19 reading scripts/post-change.ps1):** `post-change.ps1 -ChangeType Kotlin` does **NOT** spin a Gradle/Kotlin compile. It chains only static steps: dev-log, `catalog_sync.ps1` (regex file scan, ~30s over ~1500 files), and grep-based asserts (ticket-log, flavor-flag, neuroslop, fgs-notif, pm-flags). It is safe to run on its own or before a build. The OOM risk is concurrent **actual builds**, not post-change gates.

**Why:** each real gradle build starts its own Kotlin daemon; the machine's heap can't host many simultaneously. The failure looks like a code/compile error but is pure resource contention - retrying serially passes.

**How to apply:** run only one gradle build at a time. post-change closures can run freely (no daemon). If a build OOMs with multiple-daemon warnings, it's contention - kill stragglers, retry the single build clean (see [[project_build_gotchas]]). kapt recovery: `scripts/utils/recover-kapt-stall.ps1`.
