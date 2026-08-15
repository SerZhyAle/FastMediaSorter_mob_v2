# Tactical Plan: S0535 - unify-collapsible-groups

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Research inputs:** [`research/01__collapsible-inventory.md`](research/01__collapsible-inventory.md) · [`research/02__unification-design-decisions.md`](research/02__unification-design-decisions.md)
**Feature:** Унификация сворачиваемых групп
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec and research artifacts.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | unified-header | - | ✅ Done | 5/5 | [PHASE_01__unified-header.md](PHASE_01__unified-header.md) |
| 02 | sections-orchestrator | 01 | ✅ Done | 4/4 | [PHASE_02__sections-orchestrator.md](PHASE_02__sections-orchestrator.md) |
| 03 | settings-migration | 02 | ✅ Done | 5/5 | [PHASE_03__settings-migration.md](PHASE_03__settings-migration.md) |
| 04 | editors-player-migration | 02 | ✅ Done | 4/4 | [PHASE_04__editors-player-migration.md](PHASE_04__editors-player-migration.md) |
| 05 | lists-dialogs-absorb | 02 | ✅ Done | 5/5 | [PHASE_05__lists-dialogs-absorb.md](PHASE_05__lists-dialogs-absorb.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (see [`research/02__unification-design-decisions.md`](research/02__unification-design-decisions.md)).

---

## Scope notes

- **Landscape-variant creation parked.** AddResource, ResourceEditor and Media-settings layouts have no `layout-land/` counterpart today; the screens still render in landscape via portrait fallback. Authoring landscape-optimized variants is landscape work, not collapsible unification - parked as separate `/spec-draft` candidates (see research 01 §5), out of scope here. This plan only edits a landscape layout where it already exists and is touched for unification (player panels, Phase 04).
- **Player hex-color and StrictMode-alignment defects** are fixed inside Phase 04 because that phase already edits those files for unification - folded in, not parked.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed - new orchestrator + store).
- [ ] `/spec-check S0535` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state with `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0535`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-20 - Owner addendum: bold header title is an explicit unified visual token (strategic §3.1/§5.1/§11.1); Phase 01 keeps `csh_title` bold, Phase 06 documents it.
