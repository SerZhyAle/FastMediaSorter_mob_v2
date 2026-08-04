---
name: post-change-scopes-detekt-to-one-file
description: post-change -ScopeToFile checks detekt ONLY for the single -File argument, so a multi-file change gets a green PASS while other touched files carry new findings
type: feedback
---

`scripts/post-change.ps1 -File <one path> -ScopeToFile` diff-scopes the detekt gate to **that one file**. Every other file in the same logical change is unchecked - the facade reports `detekt-gate PASS [scoped]` and `post-change: PASS` while touched files sit on new findings.

**Why:** confirmed on S1190 phase 02 (2026-07-27). `post-change` was run with `-File UiLanguagePickerItems.kt` and returned `PASS (Mixed)`, `detekt-gate PASS [scoped] - none among changed files`. A separate `assert-detekt -Gate -ChangedFiles <all nine files>` then failed on four of them: `MaxLineLength`, `ArgumentListWrapping`, `ImportOrdering`, `LongMethod 80/80`, `NoBlankLineBeforeRbrace`, plus three `MultiLineIfElse` that were pre-existing but never baselined. A phase closed on the facade's PASS alone would have shipped all of that.

**How to apply:**
- After a multi-file change, do not treat `post-change -ScopeToFile` as detekt proof. Run the gate directly over the whole set:
  `scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles @('path1','path2',..)` and require `PASS [scoped]`.
- Read the per-file findings from `app_v2/build/reports/detekt/detekt.txt` (grep `<ClassName>.kt:`); the gate prints only file names.
- Expect a second and third round: fixing a `MaxLineLength` by splitting a line shifts nothing, but extracting a method changes which non-baselined findings surface. Iterate until the gate is green over the full list.
- See [[detekt-scoped-gate-surfaces-untouched-debt]], [[detekt-scoped-gate-flags-shifted-preexisting-findings]], [[verify-full-evidence]].
