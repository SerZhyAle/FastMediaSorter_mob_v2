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
