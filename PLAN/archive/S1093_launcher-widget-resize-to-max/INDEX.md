# Tactical Plan: S1093 - launcher-widget-resize-to-max

**Strategic spec:** [`../S1093_launcher-widget-resize-to-max.md`](../S1093_launcher-widget-resize-to-max.md)
**Research inputs:** none
**Feature:** Launcher: drag-resize gadgets from seed size up to full screen
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resize-persistence | - | ✅ Done | 3/3 | [PHASE_01__resize-persistence.md](PHASE_01__resize-persistence.md) |
| 02 | resize-gesture | 01 | ✅ Done | 4/4 | [PHASE_02__resize-gesture.md](PHASE_02__resize-gesture.md) |
| 03 | docs-catalog-cleanup | 01,02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 lists no open research items.

---

## Key invariants to preserve (careful-with-existing)

- **Cells never overlap.** Resize reuses `LauncherCellDao.findOverlapping(..., excludeId=id)` inside a `withTransaction`, exactly like `moveCell` - growth into another cell's squares is rejected; growth into the cell's own current squares is allowed (self excluded).
- **Drag-to-move is untouched.** The resize handle is an additive child on GADGET cells only; the existing edit scrim (long-press -> move) keeps its own touches. Shortcuts get no handle and stay 1x1.
- No Room schema change: `LauncherCellEntity.spanW/spanH` already exist; resize only writes them.

---

## Design decision (non-blocking)

- **One bottom-right corner handle** resizes width and height together (delta-driven), rather than separate edge handles. This covers the strategic goal (grow to full width and height, shrink to the seed floor); per-edge handles are a later refinement, not required for the goal. Strategic §2.1 mentions edges and corners - the corner handle is the primary affordance delivered here.
- **Preview-overlay gesture:** the real cell view is never mutated during the drag; a translucent preview rectangle shows the candidate footprint, and only the authoritative cells Flow changes the real cell on a committed resize. A rejected (colliding) resize therefore needs no explicit snap-back - the cell simply never moved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip here; strategic §8 has a FEATURES sentence but FEATURES is release-owned (recorded to ALL_FEATURES in Phase 03).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new repository method + new manager class).
- [ ] `/spec-check S1093` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1093`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
