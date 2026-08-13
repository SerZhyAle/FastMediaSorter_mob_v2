# Tactical Plan: S0426 - home-screen-weather-block

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Research inputs:** none in this folder - research lives in the archived S0404 epic (`temp/done/S0404_android-launcher-mode-profiles/research/07__weather-information.md`), summarised in strategic §8
**Feature:** weather gadget on the launcher desktop
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | weather-data-layer | - | ✅ Done | 5/5 | [PHASE_01__weather-data-layer.md](PHASE_01__weather-data-layer.md) |
| 02 | weather-resources | 01 | ✅ Done | 3/3 | [PHASE_02__weather-resources.md](PHASE_02__weather-resources.md) |
| 03 | weather-gadget | 01, 02 | ✅ Done | 4/4 | [PHASE_03__weather-gadget.md](PHASE_03__weather-gadget.md) |
| 04 | location-picker | 03 | ✅ Done | 4/4 | [PHASE_04__location-picker.md](PHASE_04__location-picker.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §4 items 1-3 all RESOLVED 2026-07-24 (owner decisions D1-D5).

## Deferred from this plan

- Strategic §2 goal 3 - opt-in coarse auto-location - out of scope here: owner scoped the first iteration to the primitive manual-place path (D3), and the goal itself calls auto-location optional. The location param is a plain coordinate pair, so adding a "use my location" button later fills the same field without touching the gadget or the data layer.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - not touched here; `/skill-release` owns them. Capability recorded in `docs/ALL_FEATURES.jsonl` in Phase 05.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0426` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0426`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
