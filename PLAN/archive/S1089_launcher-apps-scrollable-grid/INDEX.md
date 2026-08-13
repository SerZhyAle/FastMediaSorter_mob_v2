# Tactical Plan: S1089 - launcher-apps-scrollable-grid

**Strategic spec:** [`../S1089_launcher-apps-scrollable-grid.md`](../S1089_launcher-apps-scrollable-grid.md)
**Research inputs:** none
**Feature:** Launcher: labeled, scrollable app grid in the Start menu
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 2 / 2 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | labeled-app-grid | - | ✅ Done | 3/3 | [PHASE_01__labeled-app-grid.md](PHASE_01__labeled-app-grid.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 lists no open research items.

---

## Design decision (non-blocking)

- The all-apps grid gets its OWN labeled cell layout + adapter (`item_launcher_app_grid_cell.xml` + `LauncherAppGridAdapter`). The shared `LauncherTaskbarIconAdapter` / `item_launcher_taskbar_icon.xml` (also used by the pinned/recents taskbar strips) is left untouched, so those strips do not regress. The grid already scrolls (fixed-height RecyclerView) and reuses cells; this change only adds visible labels and enough height for ~3 rows.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new adapter class).
- [ ] `/spec-check S1089` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1089`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
