# Tactical Plan: S1453 - gate-shared-test-flavor-scope

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Research inputs:** [`research/01__reference-resolution.md`](research/01__reference-resolution.md), [`research/02__mount-map-drift.md`](research/02__mount-map-drift.md), [`research/03__suite-completeness-blind-spot.md`](research/03__suite-completeness-blind-spot.md)
**Feature:** Mechanical gate on the flavor scope of shared unit tests
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | mount-map-parser | - | ✅ Done | 4/4 | [PHASE_01__mount-map-parser.md](PHASE_01__mount-map-parser.md) |
| 02 | shared-test-scope-gate | 01 | ✅ Done | 5/5 | [PHASE_02__shared-test-scope-gate.md](PHASE_02__shared-test-scope-gate.md) |
| 03 | mirror-check-and-registration | 02 | ✅ Done | 4/4 | [PHASE_03__mirror-check-and-registration.md](PHASE_03__mirror-check-and-registration.md) |
| 04 | suite-completeness-consumer | 01 | ✅ Done | 3/3 | [PHASE_04__suite-completeness-consumer.md](PHASE_04__suite-completeness-consumer.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 research items are Resolved and their artifacts are listed above.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 says "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin is touched.
- [ ] `/spec-check S1453` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1453`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
