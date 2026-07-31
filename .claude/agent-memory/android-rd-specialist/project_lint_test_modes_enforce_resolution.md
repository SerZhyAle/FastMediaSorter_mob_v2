---
name: lint-test-modes-enforce-resolution
description: lint-rules detector tests replay in extra TestModes (import alias, extra parentheses) that fail any detector matching identifiers or short names instead of resolved FQNs
metadata:
  type: project
---

`TestLintTask` in `lint-rules/src/test/.../CustomLintRulesTest.kt` replays every case in extra **test modes** and fails when the verdict differs between them. Two bite hard, and both surface as a confusing `ComparisonFailure` that looks like a broken expectation rather than a detector bug:

- **IMPORT_ALIAS** rewrites the sources with import aliases for every referenced type. Any detector matching an identifier, a simple name, or an annotation **short name** breaks here. Reading `KtAnnotationEntry.shortName` off a `KtParameter` - the obvious way to find a Hilt `@ApplicationContext` on a constructor property - fails this mode. Resolve instead: `UMethod.uastParameters` -> `uAnnotations` -> `qualifiedName`.
- **Extra parentheses** wraps expressions, so `player.release()` becomes `(player).release()`. Any receiver or argument inspection must unwrap `UParenthesizedExpression` (or call `skipParenthesizedExprDown()`), or the two modes disagree.

**Why:** the harness is deliberately enforcing "resolve types, don't match text" - the exact discipline these five detectors lacked before S1195. Treat a failure in these modes as the harness finding a real robustness bug, not as noise to skip. `.skipTestModes(TestMode.IMPORT_ALIAS)` exists but using it forfeits the guarantee.

**How to apply:** when adding or editing a detector under `lint-rules/src/main`, match on resolved qualified names from the start and unwrap parentheses in any expression walk. Iterate with `.\a.ps1 flr` (about 20-26s, `-Tests '*Filter*'` to narrow) rather than a ~7 minute `lintStandardDebug`.

Second, separate trap when re-measuring: **a lint baseline entry matches on the issue's message text.** Changing a detector's report wording unmatches every baselined finding for that rule and resurfaces them as live errors, so a live count can rise sharply without any new defect. Check the resurfaced files against `app_v2/lint-baseline.xml` before treating a rise as a regression.
