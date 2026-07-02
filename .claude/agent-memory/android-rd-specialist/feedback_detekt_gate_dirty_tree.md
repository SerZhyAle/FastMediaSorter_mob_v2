---
name: detekt-gate-dirty-tree
description: post-change/close-and-log detekt gate is project-wide and fails on unrelated uncommitted WIP; verify only your own files
metadata:
  type: feedback
---

The detekt gate inside `post-change.ps1` / `close-and-log.ps1` (ChangeType Kotlin/Mixed) runs `gradle :app_v2:detekt :wear:detekt` over the **whole module**, not just your touched files. On this single-dev repo the working tree usually carries other tickets' uncommitted WIP, so detekt reports NEW-above-baseline issues in files you never touched (e.g. camera-capture, streams-filter, leak-test classes) and the gate FAILs.

**Why:** working tree = truth here, many in-flight tickets at once; the detekt baseline reflects the last-baselined state, so any uncommitted code with fresh violations trips the project-wide gate regardless of who wrote it.

**How to apply:**
- After your own edits, re-run `gradlew :app_v2:detekt` and filter for YOUR files (`Select-String 'YourFile.kt'`). If your files are absent from the output, your change is detekt-clean even though the gate is red.
- Fix only issues detekt attributes to files you edited. Common self-inflicted ones: `MagicNumber` (extract literals to named `const val` - const declarations are exempt), `MaxLineLength`/`ArgumentListWrapping` (wrap call args one-per-line with trailing comma), `SpacingBetweenDeclarationsWithComments` (blank line before a comment block).
- Do NOT fix unrelated files' WIP issues, and do NOT `detektBaseline` to absorb them (that hides other tickets' real debt). Close your ticket via direct `update.ps1` + `add_to_dev_log.ps1`, documenting in `## Last Audit` that your files pass and the remaining project-wide detekt reds are pre-existing unrelated WIP. See [[feedback_build_pre_existing_test_failures]] for the analogous unit-test policy.

**`-ScopeToFile` is FILE-granular, not line-granular (S0828, 2026-07-01).** The scoped gate flags a file if it appears in the detekt report AT ALL - it cannot tell your new lines from the file's PRE-EXISTING findings. So if the file you edit already had un-baselined findings (a file created without ever being detekt-clean, e.g. `PrintDispatchActivity.kt` from S0613: ReturnCount / LongParameterList / MaxLineLength / TooGenericExceptionCaught), your clean change still FAILs the gate until the WHOLE file is clean. Options: fix the cheap ones (wrap long lines, remove redundant `${e.message}` in `Timber.e(e, ..)` which already logs the throwable) and add precise function-level `@Suppress("Rule")` with a WHY comment for the structural ones (guard-clause early returns, launcher param fan-in). This is legit under Rule 7 (fix warnings in touched files), not scope-creep, because they are IN your file.

**assert-detekt reads a STALE report on an UP-TO-DATE task.** `scripts/quality/assert-detekt.ps1` runs `:app_v2:detekt` but if gradle judges it UP-TO-DATE it does NOT regenerate `app_v2/build/reports/detekt/detekt.xml`, so the gate re-judges old line numbers and gives a false FAIL after you just fixed something. Force a fresh report with `.\gradlew.bat :app_v2:detekt --rerun-tasks` before re-running the assert (a real source edit also invalidates it). `detektStandardDebug` is NOT a task - use plain `:app_v2:detekt`.
