# Tactical Plan: S0469 - save-photos-to-clipboard

**Strategic spec:** [`../S0469_save-photos-to-clipboard.md`](../S0469_save-photos-to-clipboard.md)
**Research inputs:** [`research/01__capture-finalization-source.md`](research/01__capture-finalization-source.md)
**Feature:** Save captured photos to clipboard
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting on-device test (journal: BlockNeedUserTest)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-flag | - | ✅ Done | 2/2 | [PHASE_01__settings-flag.md](PHASE_01__settings-flag.md) |
| 02 | clipboard-writer | - | ✅ Done | 1/1 | [PHASE_02__clipboard-writer.md](PHASE_02__clipboard-writer.md) |
| 03 | capture-wiring | 01, 02 | ✅ Done | 3/3 | [PHASE_03__capture-wiring.md](PHASE_03__capture-wiring.md) |
| 04 | settings-ui | 01 | ✅ Done | 3/3 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - the single strategic §6 research item (capture-finalization source) is Resolved (see Research inputs). §6 Q2 (image clip in text field) is an inherited device-test note, not a code blocker.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the delivered capability.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0469` returns `Verified` (after device sign-off).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All code done: device-test gate -> `BlockNeedUserTest` -> `/spec-test-device S0469` -> `/spec-check S0469`.

---

## Blockers Log

- (none yet)
