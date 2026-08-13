# Tactical Plan: S1448 - code-lock-queue-head-starvation

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Research inputs:** none - the strategic spec carries the full line-level trace in §4.
**Feature:** CODE.LOCK queue fairness and liveness signals
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | lock-retires-tickets | - | ✅ Done | 5/5 | [PHASE_01__lock-retires-tickets.md](PHASE_01__lock-retires-tickets.md) |
| 02 | waiter-heartbeat | 01 | ✅ Done | 3/3 | [PHASE_02__waiter-heartbeat.md](PHASE_02__waiter-heartbeat.md) |
| 03 | honest-refusal-and-status | 01 | ✅ Done | 3/3 | [PHASE_03__honest-refusal-and-status.md](PHASE_03__honest-refusal-and-status.md) |
| 04 | lease-liveness-from-lock | - | ✅ Done | 2/2 | [PHASE_04__lease-liveness-from-lock.md](PHASE_04__lease-liveness-from-lock.md) |
| 05 | queue-scenario-harness | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_05__queue-scenario-harness.md](PHASE_05__queue-scenario-harness.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The single strategic §6 item is Resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [x] `/spec-check S1448` returns `Verified` - 2026-08-07, PASS/WARN/FAIL 23/0/0.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1448`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
