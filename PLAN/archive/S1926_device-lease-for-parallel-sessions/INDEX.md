# Tactical Plan: S1926 - device-lease-for-parallel-sessions

**Strategic spec:** [`../S1926_device-lease-for-parallel-sessions.md`](../S1926_device-lease-for-parallel-sessions.md)
**Research inputs:** none - all three §6 items were answered from the existing lock library and the readiness probe's own contract; findings are in §4 and the ADRs.
**Feature:** device lease for parallel agent sessions
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | device-lease-script | - | ✅ Done | 4/4 | [PHASE_01__device-lease-script.md](PHASE_01__device-lease-script.md) |
| 02 | readiness-probe-integration | 01 | ✅ Done | 3/3 | [PHASE_02__readiness-probe-integration.md](PHASE_02__readiness-probe-integration.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All three strategic §6 items are Resolved before Phase 01 starts.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES."
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not applicable, no Kotlin touched.
- [x] `/spec-check S1926` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1926`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-tech`.
