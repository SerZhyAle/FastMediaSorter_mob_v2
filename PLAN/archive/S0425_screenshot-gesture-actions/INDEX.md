# Tactical Plan: S0425 - screenshot-gesture-actions

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Research inputs:** [`research/01__capture-dispatch-and-routes.md`](research/01__capture-dispatch-and-routes.md)
**Feature:** Assignable post-capture actions for directional screenshot gestures
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (awaiting on-device test)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-enums-settings | - | ✅ Done | 5/5 | [PHASE_01__foundations-enums-settings.md](PHASE_01__foundations-enums-settings.md) |
| 02 | locatable-save-result | 01 | ✅ Done | 3/3 | [PHASE_02__locatable-save-result.md](PHASE_02__locatable-save-result.md) |
| 03 | direction-detection | 01 | ✅ Done | 3/3 | [PHASE_03__direction-detection.md](PHASE_03__direction-detection.md) |
| 04 | action-dispatcher | 01, 02 | ✅ Done | 4/4 | [PHASE_04__action-dispatcher.md](PHASE_04__action-dispatcher.md) |
| 05 | wire-capture-pipelines | 03, 04 | ✅ Done | 4/4 | [PHASE_05__wire-capture-pipelines.md](PHASE_05__wire-capture-pipelines.md) |
| 06 | settings-ui | 01 | ✅ Done | 4/4 | [PHASE_06__settings-ui.md](PHASE_06__settings-ui.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items resolved (see `research/01`). The residual UX point - exact three-direction angle windows - is a device-test tuning item (ADR-2), not a planning blocker; Phase 03 ships defensible defaults.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates one sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] `/spec-check S0425` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0425`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
