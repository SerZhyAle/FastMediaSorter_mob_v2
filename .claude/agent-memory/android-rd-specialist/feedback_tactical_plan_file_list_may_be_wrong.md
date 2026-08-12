---
name: tactical-plan-file-list-may-be-wrong
description: A tactical phase's "Files Touched" table is a guess made before execution - locate every named file with the catalog before editing, and amend the plan when it is wrong
type: feedback
---

Treat a tactical phase's **Files Touched** table as a hypothesis, not a map. Before the first edit, resolve every named class/layout through `dev/CATALOG/scripts/query.ps1` and grep for the view ids the steps mention. When the plan is wrong, correct the phase file in place (with a dated "correction" note saying what was actually found) and then implement against reality.

**Why:** `/spec-tech` writes the table from the strategic survey, which can be stale or imprecise. Confirmed twice on S1190 (2026-07-27):
- Phase 02 named `activity_welcome.xml` in three width variants as the home of the Welcome language switch. That file holds only the pager and the bottom bar; the switch is a `MaterialButtonToggleGroup` in `page_welcome_enhanced.xml` (`layout` + `layout-land`, two variants), bound by `WelcomePagerAdapter` - a class the plan never mentioned.
- The same phase asserted the settings row would not change shape. It had to: `SettingsDropdownRow`'s whole contract is an inline dropdown, so opening a dialog meant swapping it for `SettingsSelectionRow` - which pulled Rule 22 (settings docs sync) into a phase that never listed it.
- Phase 02 also told the developer to delete `string-array name="languages"`; that resource does not exist anywhere in `app_v2/src` or `wear/src`.

Editing the guessed file, or trusting "this file is not affected", would have produced a change that compiles and does nothing.

**The plan also asserts repo FACTS and RULES, and those can be wrong too (S1431, 2026-08-09).** Three shapes beyond a wrong file list, all in one plan:
- **A "this variant does not exist" claim, inverted.** Phase 06 stated `dialog_launcher_settings.xml` "has no `res/layout-land/` counterpart" - it has one, and that file's own comment explains why the id must exist in both (one ViewBinding; a one-sided id is a null field in the other orientation). A negative claim is the dangerous kind: it reads as permission to skip Rule 11. Enumerate the qualifier directories yourself (`ls res/ | grep ^layout`) rather than trusting it.
- **A step that contradicts a CLAUDE.md rule.** Phase 07 told the developer to hand-write the showcase sentence into `docs/FEATURES*.md`. CLAUDE.md section 11 reserves those files for `/skill-release`, generated from the `ALL_FEATURES` diff. Rewrite the step, do not execute it - a hand-written sentence is overwritten at the next render and meanwhile claims a capability no release note announced.
- **Line budgets are predictions, not limits.** Three phases landed 4-23 lines over budget because of corrections the plan could not foresee. Update the table to the actual with a Step Log line saying what accounts for it; only Rule 2's 1500-LOC ceiling is a real limit.

**How to apply:**
- Grep for the *view id* or *string key* the step names, not just the file - that is what proves where the UI actually lives.
- An unlisted gate that the real files trigger (Rule 11 orientation counterpart, Rule 22 settings sync) is part of the phase whether the plan says so or not.
- Distrust the plan's negative claims ("no counterpart", "not affected", "no landscape variant") harder than its positive ones - a wrong positive fails loudly at compile, a wrong negative ships a hole.
- When a step contradicts CLAUDE.md, the rule wins: rewrite the step in place with a "plan defect corrected during execution" note naming the rule, then continue.
- Amend the plan before implementing, so the next reader sees the corrected list and the reason.
- See [[spec-tech-plan-quality]], [[spec-dev-continue-verify-code-first]], [[check-existing-tooling-first]], [[enumerate-all-layout-variants-not-just-land]], [[per-phase-debug-tags-break-ticket-log-gate]].
