# Tactical Plan: S0409 - welcome-enable-all

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Research inputs:** [`research/01__default-player-dialog-sequencing.md`](research/01__default-player-dialog-sequencing.md), [`research/03__profile-preset-ordering.md`](research/03__profile-preset-ordering.md)
**Feature:** Кнопка «Включить всё» на экране приветствия
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-12

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | enable-all-settings-usecase | - | ✅ Done | 2/2 | [PHASE_01__enable-all-settings-usecase.md](PHASE_01__enable-all-settings-usecase.md) |
| 02 | sequencing-seams | - | ✅ Done | 2/2 | [PHASE_02__sequencing-seams.md](PHASE_02__sequencing-seams.md) |
| 03 | enable-all-orchestrator | 01, 02 | ✅ Done | 3/3 | [PHASE_03__enable-all-orchestrator.md](PHASE_03__enable-all-orchestrator.md) |
| 04 | welcome-trigger-ui | 03 | ✅ Done | 3/3 | [PHASE_04__welcome-trigger-ui.md](PHASE_04__welcome-trigger-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see research/ artifacts). No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence (new user-facing capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] `/spec-check S0409` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to a `Block*` value.
5. All done: flip `Status:` to `Done`, run `/spec-check S0409`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-12 - Initial tactical plan authored by `/spec-tech`.
