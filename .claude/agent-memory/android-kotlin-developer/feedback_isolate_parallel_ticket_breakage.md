---
name: feedback_isolate_parallel_ticket_breakage
description: When the working tree won't compile because a parallel ticket left half-finished edits in forbidden files, isolate own work by stashing ALL the parallel files in ONE atomic git command - never multi-line backslash pathspecs
metadata:
  type: feedback
---

When a task forbids touching certain files (e.g. "a parallel ticket owns the settings fragments / `fragment_settings_*.xml`") and a whole-variant build then fails to compile, first confirm **every** error is inside those forbidden files (group errors by filename). If 100% of errors are the parallel ticket's and 0 are in your files, that is a pre-existing working-tree breakage, not your bug - same spirit as [[feedback_build_pre_existing_test_failures]] but for compile, and for *uncommitted* parallel edits rather than committed tech-debt tests.

To prove your own code compiles without altering the parallel work:

- Enumerate **all** modified files of the parallel family up front (`git status --porcelain | grep ...`). The breakage is usually a `.kt` whose binding view-ids no longer match its half-rewritten layout, so the fragment AND its layouts must move together.
- Stash the entire set in **one** `git stash push -m "..." -- f1 f2 f3 ...` command with all pathspecs on a single line, then compile, then `git stash pop` once. Verify the file count: `git stash show --name-only stash@{0} | wc -l`.
- After popping, verify each file matches the stash (`git diff stash@{0} -- <f>` is empty) before dropping anything.

**Why:** in the Bash tool, a `git stash push -- \` with backslash line-continuation across multiple lines silently captured only a *subset* of the pathspecs. That scattered the parallel ticket's edits across several partial stashes, left an older copy of one `.kt` in the tree, and nearly lost ~100 lines of their work. Recovery required `git checkout stash@{N} -- <file>` surgery and per-file `git diff` against the stash to confirm the fuller version was restored (compare `git diff HEAD stash -- f` line counts: the larger diff is the real latest). Incident: S0369 implementation, 2026-06-06.

**How to apply:** one atomic stash/pop pair, single-line pathspecs, count-and-diff verify. Never chase the breakage with several incremental stashes - each pop can conflict and "keep" the stash, compounding the mess. Touching another ticket's files - even transiently - must end with the tree byte-identical to how you found it.

**Committed-broken files (stash won't help):** if the blocker is a file with an EMPTY `git status --porcelain` (committed as-is, but no longer compiles against the current tree - e.g. a committed test whose MockK `coEvery {..} just Runs` no longer type-infers), `git stash` cannot isolate it because there is nothing uncommitted to stash. Instead `Move-Item` it out of the source root to `temp/` (a non-source dir), run your test, then move it back and confirm `git status` is clean. A broken file in `src/test` blocks the ENTIRE `compileStandardDebugUnitTestKotlin` (so no unit test runs, including yours); a broken MAIN file blocks `compileStandardDebugKotlin` and can't be safely parked (too many dependents) - retry or report. Kotlin reports all errors of a compile in one pass, so the first failing run already names every broken file - park them all at once. Incident: S0815 Phase 1, 2026-07-02 (RestoreFromGoogleDriveUseCaseTest.kt committed-broken).

**Transient incremental-compile failures from concurrent edits:** in this single-dev many-ticket repo the working tree changes UNDER you between builds. A `compileStandardDebugKotlin` failure on a MAIN file you never touched (e.g. `BackupMapper.kt` "no parameter with name X" while X clearly exists in the current `AppSettings`/`BackupData`) is usually Kotlin incremental-compilation staleness: the neighbouring model file was edited mid-build and the ABI snapshot went stale. Before flagging a hard stop, confirm the current sources are actually consistent (grep the symbol) and simply RE-RUN - it compiled clean on the plain retry (41s, UP-TO-DATE). Same "don't trust the first incremental failure" spirit as [[feedback_verify_subagent_build_failures]].
