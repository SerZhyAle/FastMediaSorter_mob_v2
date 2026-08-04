---
name: post-change-detekt-stale-report
description: post-change detekt gate can FAIL on a stale cached detekt.txt after a Kotlin edit; force :app_v2:detekt --rerun-tasks
type: feedback
---

`post-change.ps1 -ScopeToFile`'s detekt gate reads `app_v2/build/reports/detekt/detekt.txt`, but a Kotlin edit does NOT always invalidate the gradle `:app_v2:detekt` task - it serves a build-cache/UP-TO-DATE hit, so the gate re-judges a STALE report (old line numbers, findings from before your edit) and FAILs on a finding you did not introduce (often a pre-existing `LongParameterList` in the same file, or a line-shifted signature).

**Why:** the detekt task is cached; the gate does not force a rerun, and the daemon/report can lag the source (seen S1169, 2026-07-24 - a conditional-UPDATE edit to StreamSourceDao.kt surfaced a stale `updateCatalogByUrl` 8-param finding from S1117 with a 1-hour-old report mtime).

**How to apply:**
- When post-change detekt FAILs on a file you just touched, FIRST check the report mtime (`stat -c '%y' app_v2/build/reports/detekt/detekt.txt`). If it predates your edit, the report is stale.
- Force a fresh run under the build lock: `Enter-BuildLockOrExit; ./gradlew.bat :app_v2:detekt --rerun-tasks -q; Exit-AgentLock -Name Build`. The full run reports BUILD FAILED on a dirty tree (project-wide un-baselined WIP debt) - that is expected; what matters is your file's finding count in the fresh `detekt.txt`.
- Then re-run `post-change.ps1 -ScopeToFile` - it now judges the fresh report and PASSes if your changed file is clean.
- Genuinely pre-existing same-file debt surfaced by proximity (e.g. a Room `@Query` with one bound param per column tripping `LongParameterList`) is fixed with a one-line `@Suppress("LongParameterList")` + WHY comment - safe when the method is NOT already baselined (a baselined method would shift its signature, see [[detekt-baseline-signature-resurface]]).
