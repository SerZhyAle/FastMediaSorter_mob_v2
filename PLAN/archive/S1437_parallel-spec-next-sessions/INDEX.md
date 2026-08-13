# Tactical Plan: S1437 - parallel-spec-next-sessions

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Research inputs:** [`research/00__as-is-map.md`](research/00__as-is-map.md) · [`research/01__lease-storage.md`](research/01__lease-storage.md) · [`research/02__exhausted-candidates.md`](research/02__exhausted-candidates.md)
**Feature:** Parallel `/spec-next` and `/spec-do` sessions
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ticket-lease-store | - | ✅ Done | 5/5 | [PHASE_01__ticket-lease-store.md](PHASE_01__ticket-lease-store.md) |
| 02 | preflight-lease-filter | 01 | ✅ Done | 3/3 | [PHASE_02__preflight-lease-filter.md](PHASE_02__preflight-lease-filter.md) |
| 03 | per-session-round-state | 01, 02 | ✅ Done | 4/4 | [PHASE_03__per-session-round-state.md](PHASE_03__per-session-round-state.md) |
| 04 | catalog-write-serialization | - | ✅ Done | 4/4 | [PHASE_04__catalog-write-serialization.md](PHASE_04__catalog-write-serialization.md) |
| 05 | picker-driver-contract | 01, 02, 03 | ✅ Done | 5/5 | [PHASE_05__picker-driver-contract.md](PHASE_05__picker-driver-contract.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

**Ordering note.** Phase 03 removes the refusal that today stops a second session. It must not land before Phase 02, because the refusal is the only thing currently preventing two sessions from picking the same ticket (strategic §10). Phase 04 is topologically independent and may run at any point.

---

## Pre-Implementation Blockers

Both strategic §6 items are `Resolved` - no blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped, strategic §8 says "Без изменений".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [ ] `/spec-check S1437` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1437`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
