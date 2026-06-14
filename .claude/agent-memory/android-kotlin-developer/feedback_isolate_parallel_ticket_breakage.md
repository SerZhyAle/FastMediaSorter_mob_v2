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
