# Tactical Plan: S1188 - bugfix-edge-gesture-position-follows-android-bars

**Strategic spec:** [`../S1188_bugfix-edge-gesture-position-follows-android-bars.md`](../S1188_bugfix-edge-gesture-position-follows-android-bars.md)
**Research inputs:** none
**Feature:** Edge gesture bands follow the free screen edges, not the app rotation
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-27

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | edge-axis-model | - | ✅ Done | 1/1 | [PHASE_01__edge-axis-model.md](PHASE_01__edge-axis-model.md) |
| 02 | overlay-axis-aware | 01 | ✅ Done | 6/6 | [PHASE_02__overlay-axis-aware.md](PHASE_02__overlay-axis-aware.md) |
| 03 | settings-schema-axis | 01 | ✅ Done | 4/4 | [PHASE_03__settings-schema-axis.md](PHASE_03__settings-schema-axis.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Axis contract (binding for every phase)

Both the overlay and the settings diagram read the same rule, so the diagram can never promise a layout the overlay does not render.

- `VERTICAL` - bands on the left and right edges. Selected whenever at least one side inset is zero.
- `HORIZONTAL` - bands on the top and bottom edges. Selected only when `insets.left > 0 && insets.right > 0`, i.e. system bars occupy both side edges and leave top/bottom free.
- `ScreenshotGestureZone.isRightEdge` selects the far edge of the active pair: right edge under `VERTICAL`, bottom edge under `HORIZONTAL`.
- `ScreenshotGestureZone.isBottomBand` selects the along-edge offset in both axes: 10% (false) or 60% (true) of the safe extent, measured from safe top under `VERTICAL` and from safe left under `HORIZONTAL`.
- Drag decomposition: `inward` crosses the band's own edge, `lateral` runs along it. `VERTICAL` uses `inward = ±dx, lateral = dy`; `HORIZONTAL` uses `inward = ±dy, lateral = dx`. The far edge of each pair negates `inward`.
- Direction mapping is a pure transpose, so no stored binding changes meaning: `UP` = lateral negative, `DOWN` = lateral positive, `RIGHT` = straight inward.

---

## Pre-Implementation Blockers

None - the strategic spec carries no open research items, and §5 closed the scope question on 2026-07-27.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic spec has no §8 FEATURES sentence; the public showcase is owned by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phase 01 adds a public type.
- [ ] `/spec-check S1188` returns `Verified`.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1188`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`.
