# Tactical Plan: S0523 - menu-quick-capture-device-folders

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Research inputs:** [`research/01__recordings-folder-indexing.md`](research/01__recordings-folder-indexing.md), [`research/02__host-capture-adaptation.md`](research/02__host-capture-adaptation.md)
**Feature:** Quick capture from the main overflow menu into the phone's public folders
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - BlockNeedUserTest (awaiting on-device test)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-foundation | - | ✅ Done | 3/3 | [PHASE_01__settings-foundation.md](PHASE_01__settings-foundation.md) |
| 02 | public-recordings-destination | - | ✅ Done | 2/2 | [PHASE_02__public-recordings-destination.md](PHASE_02__public-recordings-destination.md) |
| 03 | voice-capture-engine | 02 | ✅ Done | 3/3 | [PHASE_03__voice-capture-engine.md](PHASE_03__voice-capture-engine.md) |
| 04 | camera-capture-engine | - | ✅ Done | 2/2 | [PHASE_04__camera-capture-engine.md](PHASE_04__camera-capture-engine.md) |
| 05 | overflow-menu-entries | 01, 03, 04 | ✅ Done | 3/3 | [PHASE_05__overflow-menu-entries.md](PHASE_05__overflow-menu-entries.md) |
| 06 | settings-ui-toggles | 01 | ✅ Done | 3/3 | [PHASE_06__settings-ui-toggles.md](PHASE_06__settings-ui-toggles.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner UI gate (Rule 10):** voice quick-capture start/stop UX confirmed 2026-06-19 - owner chose the **modal recording dialog** (Stop-and-save + Cancel; dismissing cancels). Phase 03 already assumes this; no plan change. See [`research/02__host-capture-adaptation.md`](research/02__host-capture-adaptation.md) and strategic §6.2.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence (the feature is user-visible).
- [ ] `docs/ALL_FEATURES.jsonl` has the new capability record.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added).
- [ ] `/spec-check S0523` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to the matching `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0523`.

---

## Blockers Log

- 2026-06-19 - Pre-impl voice-UX gate RESOLVED: owner chose the modal recording dialog. Phase 03 unblocked.
- 2026-06-19 - Phase 07 BLOCKED on the final packaging build (environmental, not S0523): concurrent IDE holds a lock on `app_v2/build` so `clean` fails ("Unable to delete directory"), and the Kotlin daemon was OOM-killed ("Daemon compilation failed: null") by concurrent gradle. Source is compile-clean (green via fk/fc per-phase + photos/lite). Resume: idle the IDE, run `a.ps1 cd`, then `/spec-dev S0523 --resume`.

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
