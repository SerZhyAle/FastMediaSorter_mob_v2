---
name: detekt-baseline-not-tracked-input
description: After editing config/detekt/baseline-app_v2.xml the :app_v2:detekt task does not re-run on the baseline change alone - force --rerun-tasks
metadata:
  type: project
---

Editing `config/detekt/baseline-app_v2.xml` (e.g. the single-entry ImportOrdering refresh
after import-block drift) does NOT invalidate the `:app_v2:detekt` task by itself - Gradle
returns a cached `detekt.xml`/`detekt.txt` that still lists the just-suppressed finding.

**Why:** the detekt Gradle plugin (this project's version) does not declare the baseline file
as a tracked task input, so a baseline-only change looks up-to-date / cache-hit.

**How to apply:** after refreshing a baseline entry, run `.\gradlew.bat :app_v2:detekt --rerun-tasks`
(build lock held) before re-judging. The scoped gate `scripts/quality/assert-detekt.ps1 -Gate
-ChangedFiles ...` reads `detekt.xml`; if you skip the forced rerun it re-reads the stale report and
falsely still fails on your file. Verify the refreshed `<ID>` matches the current `Signature=` in
`app_v2/build/reports/detekt/detekt.txt` head+tail before trusting it. See [[feedback_pwsh_efficiency]].
