# Tactical Plan: S0670 - compact-playback-control-dialog

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Research inputs:** [`research/01__dialog-height-cause.md`](research/01__dialog-height-cause.md), [`research/02__3d-tab-flavor-gate.md`](research/02__3d-tab-flavor-gate.md)
**Feature:** Compact, context-aware playback control dialog
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | flavor-capability | - | ✅ Done | 2/2 | [PHASE_01__flavor-capability.md](PHASE_01__flavor-capability.md) |
| 02 | resources | - | ✅ Done | 3/3 | [PHASE_02__resources.md](PHASE_02__resources.md) |
| 03 | dialog-layouts | 02 | ✅ Done | 2/2 | [PHASE_03__dialog-layouts.md](PHASE_03__dialog-layouts.md) |
| 04 | tab-visibility-logic | 01, 03 | ✅ Done | 2/2 | [PHASE_04__tab-visibility-logic.md](PHASE_04__tab-visibility-logic.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved. No open blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0670` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0670`.

---

## Blockers Log

- none

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
