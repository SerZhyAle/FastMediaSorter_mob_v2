---
name: incremental-build-phantom-unresolved
description: a.ps1 dq after cross-cutting multi-file edits can throw phantom "unresolved reference" cascades confined to the changed files; clean build (a.ps1 cd) fixes it
type: project
---

After a change set that edits several files referencing shared extension functions / data-class members (e.g. `collectOnLifecycle`, `resolveActivityCompat`, `AppSettings.*`), an incremental `a.ps1 dq`/`fk` build may FAIL with a flood of `Unresolved reference` + `Cannot infer type for this parameter` + `suspend ... can only be called from a coroutine` errors **confined to exactly the edited files**, even though every named symbol genuinely exists in the tree.

**Why:** K2 incremental compilation recompiles only the changed file subset and, in this failure mode, does not see symbols declared in the unchanged files. It is 100% reproducible on retry (NOT a flaky daemon), so a plain re-run of the same incremental command does not help. Tell-tale signs: "37 actionable tasks: 1 executed, 36 up-to-date", "Reusing configuration cache", errors only in just-edited files, untouched files using the same imports compile fine.

**How to apply:** Before treating such a cascade as a real code bug, verify 1-2 of the "unresolved" symbols actually exist (Grep the declaration). If they do and the errors sit only in edited files, run a clean build `.\a.ps1 cd` (or clear caches) - it compiles. Cost me two wasted incremental builds on S0646. Do NOT start "fixing" imports/types that are already correct. Reserve `cd` for this; routine compile checks still use `dq`/`fk`.
