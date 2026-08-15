# Tactical Plan: S0679 - draw-editor-crop-tool

**Strategic spec:** [`../S0679_draw-editor-crop-tool.md`](../S0679_draw-editor-crop-tool.md)
**Research inputs:** [`research/01__crop-resolution-strategy.md`](research/01__crop-resolution-strategy.md)
**Feature:** Crop tool inside the draw/annotation editor
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | tool-entry | - | ✅ Done | 3/3 | [PHASE_01__tool-entry.md](PHASE_01__tool-entry.md) |
| 02 | crop-compositor | 01 | ✅ Done | 2/2 | [PHASE_02__crop-compositor.md](PHASE_02__crop-compositor.md) |
| 03 | selection-overlay | 01 | ✅ Done | 3/3 | [PHASE_03__selection-overlay.md](PHASE_03__selection-overlay.md) |
| 04 | apply-and-host-wiring | 02, 03 | ✅ Done | 4/4 | [PHASE_04__apply-and-host-wiring.md](PHASE_04__apply-and-host-wiring.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (§6.1 full-res for local files, §6.2 reversible-until-first-stroke, §6.3 repeated crop allowed, §6.4 naming).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT updated here; strategic §8 provides text but `docs/FEATURES*` is `/skill-release`-owned. Capability recorded in `docs/ALL_FEATURES.jsonl` (Phase 05).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class `DrawCropCompositor`, `DrawCropOverlayController`).
- [ ] `/spec-check S0679` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log, and set the journal status to the matching `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0679`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`.
