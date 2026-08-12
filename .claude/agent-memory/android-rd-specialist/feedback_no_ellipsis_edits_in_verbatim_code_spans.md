---
name: never-style-edit-a-spec-file
description: the house text style never applies to PLAN/*.md - no gate checks it, and editing a spec's punctuation is the mistake, not the fix
type: feedback
---
Never edit a spec file for `..`/`...`, `ё`, or dashes. Not at `Draft`, not before `Approved`, not
ever. The canon's scope list (`rules/DOCUMENTATION_CONCEPT.md` section 5) is authoritative and reads
"It does **not** apply to code, **specs**, commands, logs, vendored files, or chat". The style covers
documentation prose and user-visible UI text only.

**Why:** the owner stopped work over this twice in five weeks. 2026-07-02: "stop to change ... to ..
in places you have not to! stop waste my tokns on it!" - answered by teaching the gate to skip inline
backtick spans, which was too narrow. 2026-08-09: the same gate refused S1458's promotion on line 29
of its §0 verbatim capture, where `"/spec-dev ..."` meant elided arguments; the forced edit changed
the captured text's meaning. S1543 removed the check outright rather than narrowing it a third time.

**How to apply:**
- `scripts/spec_catalog/check-owner-inputs.ps1` now judges **§3.3 owner inputs only**. If a style
  blocker ever fires again on a spec, that is a gate regression - fix the script (Rule 13), never the
  spec text.
- `scripts/utils/fix-ellipsis-docs.ps1` defaults to `-Dirs @("docs")`. Passing `-Dirs PLAN` is a
  deliberate act with no rule behind it - do not.
- Six process texts used to promise a sweep "at Draft -> Approved" and no longer do: `spec-draft.md`,
  `spec.md`, `spec-update.md`, `.claude/reference/spec.md`, and the two `.github/prompts/spec*.md`.
  If a seventh turns up saying it, it is stale - correct it.
- The inverse gap is real and parked, not fixed: nothing enforces the style where it *does* apply
  (docs prose, `strings.xml`), and the five `fix-ellipsis*`/`fix-yo*` scripts have zero callers. That
  is S1544, and adding a gate there needs its own cost ruling - see [[gate-cost-mining]].
