# Tactical Plan: S0125 - settings-activity-revision

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Feature:** Revised settings activity
**Tier:** 4 - Strategic
**Priority:** 65
**Status:** In Progress
**Phases:** 6 / 7 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec and the approved blueprint.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | inventory-shell-foundation | - | ✅ Done | 4/4 | [PHASE_01__inventory-shell-foundation.md](PHASE_01__inventory-shell-foundation.md) |
| 02 | general-native-page | 01 | ✅ Done | 3/3 | [PHASE_02__general-native-page.md](PHASE_02__general-native-page.md) |
| 03 | operations-native-page | 02 | ✅ Done | 3/3 | [PHASE_03__operations-native-page.md](PHASE_03__operations-native-page.md) |
| 04 | media-native-page | 03 | ✅ Done | 3/3 | [PHASE_04__media-native-page.md](PHASE_04__media-native-page.md) |
| 05 | playback-native-page | 04 | ✅ Done | 3/3 | [PHASE_05__playback-native-page.md](PHASE_05__playback-native-page.md) |
| 06 | search-reexposure-gate | 05 | ✅ Done | 3/3 | [PHASE_06__search-reexposure-gate.md](PHASE_06__search-reexposure-gate.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 2/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 has no open blockers, and the former Phase 06.3 owner sign-off gate was resolved on 2026-05-19.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated if Phase 06 makes the revised host public.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated for revised settings changes.
- [ ] `/spec-check S0125` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0125`.

---

## Blockers Log

- 2026-05-19 - No pre-implementation blockers. Public re-exposure remains gated inside Phase 06.
- 2026-05-19 - Phase 06 blocker cleared after owner sign-off and public MainActivity re-exposure.
- 2026-05-19 - Phase 07 final audit is waiting for manual device checks before `/spec-check S0125` can close the ticket.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech` from the approved 2026-05-19 blueprint and the current live revised-settings baseline.