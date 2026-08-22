# Tactical Plan: S1639 - gson-persistence-contract-gate

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Research inputs:** none - strategic §6 items 1 and 2 were resolved in place from the 2026-08-14 serialization inventory, item 3 closed as a non-goal with `Carrier: S0552`
**Feature:** Mechanical gate on the Gson persistence contract
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 55
**Status:** In progress
**Phases:** 5 / 5 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | serialization-inventory | - | ✅ Done | 3/3 | [PHASE_01__serialization-inventory.md](PHASE_01__serialization-inventory.md) |
| 02 | pinning-verdict | 01 | ✅ Done | 3/3 | [PHASE_02__pinning-verdict.md](PHASE_02__pinning-verdict.md) |
| 03 | exemption-registry | 02 | ✅ Done | 2/2 | [PHASE_03__exemption-registry.md](PHASE_03__exemption-registry.md) |
| 04 | gate-wiring | 03 | ✅ Done | 3/3 | [PHASE_04__gate-wiring.md](PHASE_04__gate-wiring.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item carries `Status: Resolved` or `Resolved as non-goal`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 reads "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, this ticket adds no Kotlin.
- [ ] `/spec-check S1639` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1639`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
