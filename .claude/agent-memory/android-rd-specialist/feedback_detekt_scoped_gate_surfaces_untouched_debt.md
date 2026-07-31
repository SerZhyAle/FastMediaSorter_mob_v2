---
name: detekt-scoped-gate-surfaces-untouched-debt
description: Editing a file with pre-existing non-baselined detekt findings makes the scoped gate fail on them; report can go stale needing --rerun-tasks
type: feedback
---

Touching a `.kt` file that already has detekt findings NOT frozen in `config/detekt/baseline-app_v2.xml` makes `assert-detekt -Gate -ChangedFiles <file>` fail on those pre-existing findings, even if your edit is elsewhere in the file - the scoped gate flags any non-baselined finding that lands in a changed file, not just ones on lines you touched.

**Why:** the scoped gate's "NEW" = "not in baseline AND in a changed file". Baseline coverage is per-signature; a file that was never baselined (e.g. a recent S0844-style extraction) carries live findings that only surface once someone edits it. Confirmed on S0922: `CameraOrientationManager` had 6 un-baselined `MagicNumber` findings on its orientation buckets that only blocked the gate after I bugfixed the file.

**How to apply:**
- When the scoped detekt gate fails after your edit, grep the report `app_v2/build/reports/detekt/detekt.txt` for your file - fix ALL its findings (Rule 19 detekt-clean-first), don't assume they're "someone else's". Prefer companion `const val` primitives (exempt from MagicNumber) over re-baselining pre-existing debt.
- The detekt report/task can serve STALE results after a source edit: `gradlew --stop` alone did NOT refresh it (config-cache reuse). Force a fresh report with `.\gradlew.bat :app_v2:detekt --rerun-tasks`, then re-run the gate. A `BUILD FAILED` from that task is expected (project-wide un-baselined debt); judge YOUR file by grepping the fresh report for 0 findings, then confirm via `assert-detekt -Gate -ChangedFiles`.
- Adding branches to one of this repo's deliberate closed-`when` chains (the S0912 "either a route declares its own pair here, or the single `else` reports it unavailable - no second toggle to forget" pattern, e.g. `ResolvePanelRouteAvailabilityUseCase.resolve`) trips `CyclomaticComplexMethod` fast - S1170 took it from 20 to 25 with five branches. Split the new tail into a private helper that the original `else` delegates to, and let THAT helper hold the terminal default. Complexity drops and the closed-set invariant survives, because the chain still ends in exactly one "unknown -> unavailable". Do not instead `@Suppress` it.
- See [[feedback_write_detekt_clean_first_time]], [[feedback_detekt_baseline_signature_resurface]], [[project_detekt_baseline_hand_edit_daemon_stale]].
