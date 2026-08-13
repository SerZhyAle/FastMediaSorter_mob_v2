# Tactical Plan: S0847 - corner-edge-gestures-twelve-directions

**Strategic spec:** [`../S0847_corner-edge-gestures-twelve-directions.md`](../S0847_corner-edge-gestures-twelve-directions.md)
**Feature:** Expand the single left-edge gesture strip into 4 independently-toggleable edge bands (2 left, 2 right at 10-40% / 60-90% height), each with the DOWN/RIGHT/UP triple - up to 12 gestures total.
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 code+docs done; real-device gesture-geometry verification pending (Phase 04.4)
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec (§6 REVISED zone geometry is authoritative).

---

## Design decisions (locked from strategic §6)

- **Zones:** `ScreenshotGestureZone { LEFT_TOP, LEFT_BOTTOM, RIGHT_TOP, RIGHT_BOTTOM }`. Geometry over safe height: TOP band = 10%..40%, BOTTOM band = 60%..90%. Left zones anchor `x=0`; right zones anchor `x = screenWidth - stripWidth`. Middle 40%..60% intentionally unused.
- **Directions:** reuse `ScreenshotGestureDirection { DOWN, RIGHT, UP }` per zone. Left edge = rightward inward drag (`dx>0`). Right edge = leftward inward drag (`dx<0`); classification mirrors by negating `dx` before `atan2`, so the same UP/RIGHT/DOWN angle windows map to inward swipes.
- **Settings model:** 4 boolean enable-toggles + 12 action slots (zone x direction). Symmetric zone-prefixed fields replace the legacy 3 (`screenshotGestureActionDown/Right/Up`). Persistence migrates the 3 legacy keys into the LEFT_TOP zone on read (no data loss, no Room).
- **Defaults:** LEFT_TOP enabled by default and seeded from the legacy triple (up=OPEN_PANEL, right=OPEN_IN_DRAW, down=SILENT_SCREENSHOT) - preserves today's single-strip UX. LEFT_BOTTOM / RIGHT_TOP / RIGHT_BOTTOM disabled by default, all slots DO_NOT_USE (opt-in, no surprise hit zones).
- **Catalog:** single shared `ScreenshotGestureAction` picker for all 12 slots. No reserved launch-only zones.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-model | - | ✅ Done | 4/4 | [PHASE_01__domain-model.md](PHASE_01__domain-model.md) |
| 02 | persistence-migration | 01 | ✅ Done | 3/3 | [PHASE_02__persistence-migration.md](PHASE_02__persistence-migration.md) |
| 03 | dispatch-and-wiring | 01 | ✅ Done | 5/5 | [PHASE_03__dispatch-and-wiring.md](PHASE_03__dispatch-and-wiring.md) |
| 04 | detection-geometry | 03 | 🚧 In Progress | 3/4 | [PHASE_04__detection-geometry.md](PHASE_04__detection-geometry.md) - 04.4 real-device gesture tuning pending |
| 05 | settings-ui | 01,02 | ✅ Done | 5/5 | [PHASE_05__settings-ui.md](PHASE_05__settings-ui.md) |
| 06 | docs-and-closure | all | ✅ Done | 4/4 | [PHASE_06__docs-and-closure.md](PHASE_06__docs-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 product decisions are all Resolved (owner 2026-07-11 REVISED zone geometry). Detection tuning + settings layout are engineering decisions locked in the Design section above; final touch-geometry values are device-test tuning candidates verified in Phase 04 on the attached emulator.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `standard debug` build passes (edited main + screenCapture + noLegal source sets compile).
- [ ] Settings docs regenerated (Rule 22): `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + `docs/settings/settings-annotations.json`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `ScreenshotGestureZone` class).
- [ ] `docs/ALL_FEATURES.jsonl` has an S0847 record (user-visible capability: up to 12 edge gestures).
- [ ] Strings localized EN/RU/UK (`scripts/check_strings_localized.ps1` exit 0).
- [ ] On-device verification on emulator-5556 (Phase 04 gesture geometry per zone) - `BlockNeedUserTest` -> `/spec-test-device`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log, set the journal status to the matching `Block*`.
5. All done: run `/spec-check S0847`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-11 - Initial tactical plan authored by `/spec-tech` (via `/spec-all` / `/spec-next`).
