# Tactical Plan: S1502 - stream-catalog-thumbnail-performance

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Research inputs:** [`research/01__as-is-scaling-bottlenecks.md`](research/01__as-is-scaling-bottlenecks.md)
**Feature:** Streams catalog performance at 15k+ channels
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 5 done (04 and 05 each carry one outstanding step - see their Step Logs)
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | measurement-baseline | - | ✅ Done | 3/3 | [PHASE_01__measurement-baseline.md](PHASE_01__measurement-baseline.md) |
| 02 | filter-off-main-thread | 01 | ✅ Done | 3/3 | [PHASE_02__filter-off-main-thread.md](PHASE_02__filter-off-main-thread.md) |
| 03 | cheapen-catalog-pass | 02 | ✅ Done | 6/6 | [PHASE_03__cheapen-catalog-pass.md](PHASE_03__cheapen-catalog-pass.md) |
| 04 | decouple-outcome-writes | 03 | 🚧 In Progress | 8/9 | [PHASE_04__decouple-outcome-writes.md](PHASE_04__decouple-outcome-writes.md) |
| 05 | docs-catalog-cleanup | all | 🚧 In Progress | 2/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

**Why the measurement phase is first.** ADR-1 orders the pillars A - B - C - D and says nothing about pillar E. Two acceptance criteria are comparisons against current behaviour rather than absolute limits - §11.4 (screen open must not get longer) and §11.6 (peak memory must not grow) - and §6.1 asks for the measurement "до и после шага A". Neither is provable once the changes have landed. Phase 01 touches no application source, so running it first costs nothing and is the only ordering that yields a baseline at all.

---

## Pre-Implementation Blockers

None unchecked.

- [x] **Research §6.2** - how to break the "one write, full recompute" coupling. Resolved 2026-08-08 by code reading: Room invalidates per table, so a narrowed observed query cannot help; the separate-table option is the only one that works. Phase 04 implements it with DB version 48 and `Migration47To48`.
- Strategic §6.1 (real cost on floor hardware) is what Phase 01 and Phase 04 Step 04.9 produce between them - the before-numbers and the after-numbers. It closes on a device run, which is the `BlockNeedUserTest` gate, not a blocker for starting.
- Strategic §6.3 (is paging needed) gates pillar D only, which this plan excludes; it is answered by the Step 04.9 numbers, not by this plan.

---

## Out of plan, with reason

- **Pillar D (paging).** ADR-1 makes it conditional on the measurement, so planning it now would decide by reputation what the spec says to decide by number. `androidx.paging:paging-runtime-ktx:3.2.1` is already on the classpath (`app_v2/build.gradle.kts:1384`), so a follow-up ticket starts from a dependency that is present.
- **Artwork delivery, frame capture, health probe.** Strategic §2 non-goals; research "Already bounded" section confirms each is visible-range limited and does not grow with catalog size.
- **Grid/pinned adapters missing the artwork repaint.** Parked as S1503.
- **Catalog size itself.** Owned by S1476.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 reads "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 04 adds public classes.
- [ ] `temp/S1502/baseline/` and `temp/S1502/after/` hold a matched pair of five checkpoint measurements from the same device, with a written per-criterion verdict (§11.1-§11.4, §11.6).
- [ ] `/spec-check S1502` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1502`.

---

## Blockers Log

- 2026-08-08 - **The frame-based measurements have no usable baseline yet, and the emulator cannot give them one.** `streams-list-scroll`, `streams-grid-scroll` and `streams-search` sample only 27-30 frames per scroll burst here, and repeats of the identical run spread 46-60 %. `prerelease-measure.ps1` now refuses to present such a record as comparable (`insufficient: true`). Unblocking needs a quiet host and a longer scroll, and properly needs floor-tier hardware (API 23, 128 MB heap) which is not available to this session. §11.2 and §11.3 stay unproven until then; §11.1 loses its only measured evidence too. Not a blocker for Phases 03 and 04, which are code changes gated by static predicates and unit tests.

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - Phase 03 done. Query matching stopped allocating a lowercased copy per catalog row, and the pinned/unpinned split collapsed from three passes to one computed inside `applyFilter` and carried on the state. A 20k-row shape guard now runs in CI (0.115 s against a 2,000 ms ceiling).
