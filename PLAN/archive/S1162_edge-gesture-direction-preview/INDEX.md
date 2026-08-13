# Tactical Plan: S1162 - edge-gesture-direction-preview

**Strategic spec:** [`../S1162_edge-gesture-direction-preview.md`](../S1162_edge-gesture-direction-preview.md)
**Research inputs:** none (architecture resolved inline - see strategic §4)
**Feature:** Direction hint shown during an edge gesture
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | action-feed | - | ✅ Done | 3/3 | [PHASE_01__action-feed.md](PHASE_01__action-feed.md) |
| 02 | hint-window | 01 | ✅ Done | 3/3 | [PHASE_02__hint-window.md](PHASE_02__hint-window.md) |
| 03 | direction-highlight | 02 | ✅ Done | 2/2 | [PHASE_03__direction-highlight.md](PHASE_03__direction-highlight.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Design summary (binding for all phases)

The edge bands are bare `View`s, one per zone, each in its own `WindowManager` window owned by
`ScreenGestureOverlayManager` (`src/screenCapture`). A single touch handler already receives
DOWN / MOVE / UP-CANCEL and classifies the inward drag angle into UP / RIGHT / DOWN.

Two constraints shape the whole plan:

1. **The action lookup is suspending.** `ScreenshotGestureActionDispatcher.actionFor` reads settings,
   so it cannot run at touch-down. The twelve slot actions are resolved *before* the bands are shown -
   both hosts already sit in a coroutine there, resolving `enabledZones()` - and handed to the
   manager, which then builds the hint synchronously. This is what strategic §7 means by "the triple
   is assembled in advance".
2. **The hint must never eat a touch.** Its window carries `FLAG_NOT_TOUCHABLE`, so the gesture keeps
   flowing to the band underneath for the whole drag. Without that flag the hint would sit exactly
   where the finger is heading and swallow the gesture it is advertising.

The hint window is created on ACTION_DOWN (ADR-2, revised 2026-07-24 - no threshold, no delay) and
removed on UP, CANCEL, outward travel, and gesture fire. Views are built programmatically, like the
bands: the hosts are Services with no app theme, and inflating Material widgets from an unthemed
context crashes.

Both hosts get the same one-line change (resolve the action map, pass it to `show`). All hint
behaviour lives in the shared manager, so the two hosts cannot drift apart.

---

## Pre-Implementation Blockers

None - strategic §6 has no `Open` items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (one new class).
- [x] Capability recorded - in the gitignored `docs/ALL_FEATURES_noLegal.jsonl`, since the edge overlay only ships in noLegal (standard needs `fms.edgeGestureOverlay=on`, which is off by default).
- [ ] `/spec-check S1162` returns `Verified` - after the device test, not before.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[x] done` only when its Verification passes.
3. On phase completion: confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
