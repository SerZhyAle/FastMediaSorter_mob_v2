# Tactical Plan: S0963 - vr-cinema-immersive-browser

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Research inputs:** [`research/01__immersive-browser-architecture.md`](research/01__immersive-browser-architecture.md)
**Feature:** VR Cinema - Pillar 2: immersive browser (BROWSE window in headset)
**Tier:** 4 - Strategic child of S0773
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | launch-transport-extension | - | ✅ Done | 4/4 | [PHASE_01__launch-transport-extension.md](PHASE_01__launch-transport-extension.md) |
| 02 | immersive-browse-render-primitives | 01 | ✅ Done | 4/4 | [PHASE_02__immersive-browse-render-primitives.md](PHASE_02__immersive-browse-render-primitives.md) |
| 03 | immersive-browse-activity | 01, 02 | ✅ Done | 6/6 | [PHASE_03__immersive-browse-activity.md](PHASE_03__immersive-browse-activity.md) |
| 04 | entry-routing | 03 | ✅ Done | 3/3 | [PHASE_04__entry-routing.md](PHASE_04__entry-routing.md) |
| 05 | resource-menu-entry | 01, 04 | ✅ Done | 5/5 | [PHASE_05__resource-menu-entry.md](PHASE_05__resource-menu-entry.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Both strategic §6 research items are Resolved (see `research/01__immersive-browser-architecture.md` §8).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated here; strategic §8 owned by parent epic S0773; `/skill-release` populates FEATURES from the `ALL_FEATURES` diff.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `src/vr` classes).
- [ ] `/spec-check S0963` returns `Verified` (device evidence via `/spec-test-device` on Quest 3 first - immersive paths are on-device-only).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`; update `Phases: X/6 done`.
2. During a phase: flip each step to `[~]` when started, `[x]` when its Verification passes. Never `[x]` on intent.
3. On completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; set journal status to the matching `Block*` if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0963`.

---

## On-device validation note

Immersive rendering runs only on an XR device (Quest 3/3S); the native `.so` is arm64-only and absent on x86_64 emulators. Compile-prove every phase on `noLegal` (`.\a.ps1 fkn`) and on `standard` (`.\a.ps1 fc`, No-Op path). Behavioural verification of BROWSE/grid/selection is on-device only (`/spec-test-device` -> Quest 3). The spec enters `BlockNeedUserTest` after the last code phase, with `S0963:` probes at flow entries.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-11 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
