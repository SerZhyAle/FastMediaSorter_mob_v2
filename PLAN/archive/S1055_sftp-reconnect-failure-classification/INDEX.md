# Tactical Plan: S1055 - sftp-reconnect-failure-classification

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Research inputs:** [`research/01__error-display-surface.md`](research/01__error-display-surface.md), [`research/02__reconnect-site-inventory.md`](research/02__reconnect-site-inventory.md)
**Feature:** Four-way SFTP failure classification on the live reconnect path (contract O2)
**Tier:** 0 - Security/Compliance (urgent) - ad-hoc
**Priority:** 70
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | typed-outcome-and-messages | - | ✅ Done | 3/3 | [PHASE_01__typed-outcome-and-messages.md](PHASE_01__typed-outcome-and-messages.md) |
| 02 | classifier-branches | 01 | ✅ Done | 2/2 | [PHASE_02__classifier-branches.md](PHASE_02__classifier-branches.md) |
| 03 | reconnect-guard | 02 | ✅ Done | 1/1 | [PHASE_03__reconnect-guard.md](PHASE_03__reconnect-guard.md) |
| 04 | classifier-tests | 02 | ✅ Done | 1/1 | [PHASE_04__classifier-tests.md](PHASE_04__classifier-tests.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are Resolved (see Research inputs above).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed (new exception subtype).
- [ ] `/spec-check S1055` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1055`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-15 - Initial tactical plan authored by `/spec-tech`.
