# Tactical Plan: S0610 - standalone-image-player-commands

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Research inputs:** [`research/01__destination-source.md`](research/01__destination-source.md) · [`research/02__post-operation-screen-behavior.md`](research/02__post-operation-screen-behavior.md) · [`research/03__print-receiver-and-overflow.md`](research/03__print-receiver-and-overflow.md)
**Feature:** Print + Copy/Move commands in the standalone image player
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | print-send-to-receiver | - | ✅ Done | 3/3 | [PHASE_01__print-send-to-receiver.md](PHASE_01__print-send-to-receiver.md) |
| 02 | destination-buttons-root-view | - | ✅ Done | 2/2 | [PHASE_02__destination-buttons-root-view.md](PHASE_02__destination-buttons-root-view.md) |
| 03 | standalone-bottom-panels-layout | - | ✅ Done | 2/2 | [PHASE_03__standalone-bottom-panels-layout.md](PHASE_03__standalone-bottom-panels-layout.md) |
| 04 | standalone-copy-move-wiring | 02, 03 | ✅ Done | 4/4 | [PHASE_04__standalone-copy-move-wiring.md](PHASE_04__standalone-copy-move-wiring.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see Research inputs). No blockers - Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 introduces a new perceived capability; update at release time via `/skill-release` from the `ALL_FEATURES` diff (do not hand-edit per-spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0610` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0610`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech`.
