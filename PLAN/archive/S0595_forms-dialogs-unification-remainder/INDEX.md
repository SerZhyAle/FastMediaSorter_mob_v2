# Tactical Plan: S0595 - forms-dialogs-unification-remainder

**Strategic spec:** [`../S0595_forms-dialogs-unification-remainder.md`](../S0595_forms-dialogs-unification-remainder.md)
**Research inputs:** none (strategic §1 + archived S0567 §1.1 survey carry the codebase analysis)
**Feature:** Forms and dialog components unification (remainder of S0567)
**Tier:** 3 - Standard
**Priority:** 50
**Status:** Verified
**Phases:** 3 / 3 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
> S0567 already delivered the P0 widgets (`SettingsSelectionRow`, `SettingsDropdownRow`, `SettingsInputRow`) and `ListSelectionDialog<T>` (phases 01-04). This plan carries only the unfinished P1/P2 remainder.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resource-form-primitives | - | ✅ Done | 4/4 | [PHASE_01__resource-form-primitives.md](PHASE_01__resource-form-primitives.md) |
| 02 | action-help-row-audit | 01 | ✅ Done | 3/3 | [PHASE_02__action-help-row-audit.md](PHASE_02__action-help-row-audit.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> Phases 01 and 02 reuse the shipped S0567 compound-view conventions: prefixed `attrs.xml` styleable, `view_*.xml` `<merge>` layout, `TooltipDialog` ownership, modelled on `SettingsToggleRow` (Pattern B). Phase 02 audits surviving cases, so it depends on Phase 01.

---

## Pre-Implementation Blockers

- None. Strategic §6 carries no open research items; the S0567 §1.1 survey is resolved and concrete.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; visual-debt refactor, no new user-facing capability.
- [ ] `docs/ALL_FEATURES.jsonl` - skip; no new shippable capability delivered.
- [ ] `dev/CHANGELOG.md` has an entry for every modified logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public widget API added).
- [ ] `scripts/quality/assert-settings-doc-sync.ps1` passes (settings presence/position/naming unchanged - regen only if it flags).
- [ ] `/spec-check S0595` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Implemented`, run `/spec-check S0595`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-all` F2 (reuses archived S0567 phases 05-07).
