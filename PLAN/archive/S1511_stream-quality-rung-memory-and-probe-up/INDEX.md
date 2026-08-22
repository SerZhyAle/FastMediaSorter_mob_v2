# Tactical Plan: S1511 - stream-quality-rung-memory-and-probe-up

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Research inputs:** none as separate artifacts - the source measurement is the attachment cited in strategic section 0, and the code findings are recorded in strategic sections 4 and 6.
**Feature:** Remember a channel's learned quality rung across sessions and probe upward again
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 55
**Status:** In Progress
**Phases:** 5 / 5 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | rung-memory-store | - | ✅ Done | 4/4 | [PHASE_01__rung-memory-store.md](PHASE_01__rung-memory-store.md) |
| 02 | controller-restore-and-probe-policy | - | ✅ Done | 5/5 | [PHASE_02__controller-restore-and-probe-policy.md](PHASE_02__controller-restore-and-probe-policy.md) |
| 03 | probe-cost-measurement | 02 | ✅ Done | 2/2 | [PHASE_03__probe-cost-measurement.md](PHASE_03__probe-cost-measurement.md) |
| 04 | playback-wiring | 01, 02, 03 | ✅ Done | 5/5 | [PHASE_04__playback-wiring.md](PHASE_04__playback-wiring.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None outstanding. Strategic section 6 carries one Open item, Q2 (what a probe-up actually costs on Media3), and Phase 03 is that measurement - it is a phase rather than a blocker because Phases 01 and 02 do not depend on its answer, only Phase 04's constants do.

---

## Architecture decisions this plan rests on

- The policy class stays free of Android types (strategic ADR-1), so restore and probe scheduling are pure functions of supplied time and stored counters, and every one of them is unit-tested off-device.
- Memory is its own Room table, never a column on `stream_sources` (ADR-2), because Room invalidates per table and the catalog list re-emits on every write to that row.
- The record is keyed by normalized channel address plus rung bitrate, not by the catalog row id (ADR-5), so a re-imported channel keeps what it learned.
- The probe rides the existing 5 s stats tick rather than a new timer (ADR-3).
- The ceiling is raised by the same live `setParameters` that lowers it (ADR-4); no `prepare` is re-issued.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; strategic section 8 routes the showcase through `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - new public classes were added.
- [ ] `/spec-check S1511` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1511`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-13 - Initial tactical plan authored by `/spec-tech`.
