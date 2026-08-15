# Tactical Plan: S0389 - standalone-player-parity

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Feature:** Паритет STANDALONE-плеера с in-app проигрывателем и маршрутизация «Открыть в FastMediaSorter»
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 55
**Status:** Verified - device-tested 2026-06-10 (paging + Open-in-FMS confirmed on Pixel_6 emulator; panel-overflow defect found & fixed)
**Phases:** 6 / 7 done (04 skipped → S0390)
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capability-contract | - | ✅ Done | 2/2 | [PHASE_01__capability-contract.md](PHASE_01__capability-contract.md) |
| 02 | local-path-resolution | 01 | ✅ Done | 2/2 | [PHASE_02__local-path-resolution.md](PHASE_02__local-path-resolution.md) |
| 03 | standalone-folder-paging | 01, 02 | ✅ Done | 3/3 | [PHASE_03__standalone-folder-paging.md](PHASE_03__standalone-folder-paging.md) |
| 04 | command-panel-parity | 01, 03 | ⏭️ Skipped | 0/3 | [PHASE_04__command-panel-parity.md](PHASE_04__command-panel-parity.md) |
| 05 | open-in-fms-resolver | 02 | ✅ Done | 3/3 | [PHASE_05__open-in-fms-resolver.md](PHASE_05__open-in-fms-resolver.md) |
| 06 | open-in-fms-routing | 05 | ✅ Done | 2/2 | [PHASE_06__open-in-fms-routing.md](PHASE_06__open-in-fms-routing.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Dependency:** S0380 (split standalone player) - resolved 2026-06-09: owner confirmed it works on device, S0380 advanced to `Verified` and archived. Phase 06 routing is unblocked.

---

## Completion Gate

- [x] All phases show ✅ Done (04 skipped → S0390).
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new use cases / managers added).
- [x] Device-test pass (§3.3) - 2026-06-10 on Pixel_6 emulator: paging enumeration + Open-in-FMS routing to in-app player confirmed; panel-overflow defect found & fixed (HorizontalScrollView + pinned overflow).
- [x] Strategic spec `Status:` advanced to `Verified`; see strategic `## Last Audit`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0389`.

---

## Blockers Log

- 2026-06-09 - S0380 device-test pending (BlockNeedUserTest). Phase 06 gated until confirmed. Resolved same day: owner confirmed S0380 works; S0380 → Verified + archived.
- 2026-06-09 - Phase 04 (command-panel parity) ⏭️ Skipped → spun off to a separate ticket. Reason: standalone hosts do not use the in-app panel engine (`CommandPanelController`/`CommandPanelAvailabilityUpdater`); only crop/draw/compress/rotate have reusable generic helpers, and the owner-requested priority bar↔overflow placement needs a planner generalization. Remaining type-specific actions each need a helper-refactor seam (see strategic §10 follow-up tickets). S0389 ships with folder paging + Open-in-FMS routing (the two headline features).
- 2026-06-09 - Pre-existing blocker noted: `app_v2` unit-test source set fails to compile due to `TranslationLanguageCodeMapperTest` referencing ML Kit moved to `translate_feature` (S0386, paused). Quarantined during S0389 test runs and restored. Needs a separate fix ticket.

---

## Change Log

- 2026-06-09 - Initial tactical plan authored by `/spec-tech`.
