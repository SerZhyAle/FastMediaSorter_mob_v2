# Tactical Plan: S1432 - build-lock-turn-queue

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Research inputs:** [`research/01__ownership-and-liveness.md`](research/01__ownership-and-liveness.md) · [`research/02__wakeup-signal-mechanism.md`](research/02__wakeup-signal-mechanism.md) · [`research/03__work-allowed-while-queued.md`](research/03__work-allowed-while-queued.md)
**Feature:** FIFO queue on the shared agent locks, with an out-of-band "your turn" signal
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | queue-core | - | ✅ Done | 6/6 | [PHASE_01__queue-core.md](PHASE_01__queue-core.md) |
| 02 | waiter-and-status | 01 | ✅ Done | 4/4 | [PHASE_02__waiter-and-status.md](PHASE_02__waiter-and-status.md) |
| 03 | build-entrypoints-wait | 01 | ✅ Done | 3/3 | [PHASE_03__build-entrypoints-wait.md](PHASE_03__build-entrypoints-wait.md) |
| 04 | code-lock-queue | 01, 02 | ✅ Done | 4/4 | [PHASE_04__code-lock-queue.md](PHASE_04__code-lock-queue.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all three strategic §6 research items are Resolved and their artifacts are listed above.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin public API changes.
- [ ] `/spec-check S1432` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1432`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
