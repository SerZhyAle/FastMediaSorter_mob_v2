---
name: spec-dev-continue-verify-code-first
description: On /spec-dev continuation of an In-Progress spec, trust the live code (grep symbols, check files) over phase-file checkboxes - a prior session may have written code without updating tracking
metadata:
  type: feedback
---

When continuing a `/spec-dev` ticket whose journal status is `In Progress`, do NOT trust the phase-file/INDEX checkboxes as the cursor. Verify the actual code state first by **reading the working tree**: targeted `Grep` for the spec's new symbols/classes, check whether the expected files exist, consult the catalog. Do not reach for `git status`/`git diff` to reconcile - the working tree itself is the truth, and on this repo it mixes many unrelated tickets anyway (see [[dirty-tree-is-normal-wip]]).

**Why:** On S0356 the journal was `In Progress` and every phase file showed `0/N` / "Not started", yet a prior session had already implemented Phases 02/03/04 in code (new `MediaFileIntegrity.kt`, three scanners routed through the guard, `reconcileFavoriteFlags` per-element isolation, reconcile log already at `Timber.w`) and left all tracking unupdated. Strategic §6.2 was even pre-marked `Resolved`. Following the checkboxes blindly would have re-implemented finished work or missed that only Phase 01 + Phase 05 closure were the real gaps.

**How to apply:** First action on an In-Progress continuation = reconcile against the live files. Grep the spec's headline symbols; check the expected new classes/tests exist on disk. Then mark phase steps to match reality, fill only the genuine gaps, and document any divergence in the Step Log. Related: [[feedback_subagent_impl_skips_final_phase]].
