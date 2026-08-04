---
name: no-concurrent-gradle-invocations
description: Never run several gradle-backed builds at once - multiple Kotlin daemons OOM/corrupt the machine; post-change.ps1 is mostly static BUT its settings-doc-sync gate runs gradle
type: feedback
---

Never run several **gradle-backed builds** concurrently on this machine. Launching multiple background builds (`a.ps1 d`/`fk`/`fc`/`dq`, gradle `assemble*`) at once produced "Detected multiple Kotlin daemon sessions" then `java.lang.OutOfMemoryError: Java heap space` in `kaptGenerateStubsStandardDebugKotlin`, failing after ~25 min.

**Two IDEs amplify this:** VS Code + Antigravity-IDE each run a redhat.java language server that does background Gradle syncs. They issue "stop command received" / kill the Kotlin daemon mid-CLI-build and leave the build cache half-deleted (`NoSuchFileException` on `merged_res/*.arsc.flat` or `transform*ClassesWithAsm/jars/0.jar` "used by another process"). Recovery: `gradlew --stop`, kill leftover `java` procs whose StartTime is during your failed runs (preserve the IDE language servers - their cmdline shows `.vscode`/`.antigravity-ide/extensions/redhat.java`), delete the corrupted `app_v2/build/intermediates/merged_res/<variant>`, then retry a single `--no-daemon` build.

**Correction (verified 2026-06-21):** `post-change.ps1` is *mostly* static (dev-log, `catalog_sync.ps1` regex scan, grep-based asserts) - EXCEPT the **settings-doc-sync gate**. When the touched file is a settings surface/doc, `post-change.ps1` runs `assert-settings-doc-sync.ps1 -Gate`, whose manifest-fresh stage executes `gradlew :app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest` - a real Kotlin compile+test (~4 min). So that path is NOT daemon-free; never run it concurrently with another build. (`-SkipManifestTest` skips the gradle part; `-Gate` does NOT.) For other ChangeTypes post-change stays static and safe to run anytime.

**Why:** each real gradle build starts its own Kotlin daemon; the machine's heap can't host many simultaneously. The failure looks like a code/compile error but is pure resource contention - retrying serially passes.

**How to apply:** run only one gradle build at a time. post-change closures can run freely (no daemon). If a build OOMs with multiple-daemon warnings, it's contention - kill stragglers, retry the single build clean (see [[build-gotchas]]). kapt recovery: `scripts/utils/recover-kapt-stall.ps1`.
