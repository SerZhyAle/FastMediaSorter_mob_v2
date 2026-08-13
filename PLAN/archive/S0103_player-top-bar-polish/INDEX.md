# Tactical Plan: S0103 — player-top-bar-polish

**Strategic spec:** [`../S0103_player-top-bar-polish.md`](../S0103_player-top-bar-polish.md)
**Feature:** Player top command bar — audio filters, black-screen fix, slideshow anchor, smart overflow
**Tier:** 2 — Easy
**Priority:** 55
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | command-visibility-fixes | — | ✅ Done | 3/3 | [PHASE_01__command-visibility-fixes.md](PHASE_01__command-visibility-fixes.md) |
| 02 | slideshow-position | 01 | ✅ Done | 3/3 | [PHASE_02__slideshow-position.md](PHASE_02__slideshow-position.md) |
| 03 | sleep-timer-bar | 02 | ✅ Done | 3/3 | [PHASE_03__sleep-timer-bar.md](PHASE_03__sleep-timer-bar.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 has no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (polish, not new feature; see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` file changes.
- [ ] `/spec-check S0103` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0103`.

---

## Key Context

- **Portrait layout planner**: `CommandPanelController` → `CommandPanelLayoutPlanner.buildActiveCommands()` → `planLayout()`.
- **Landscape**: `CommandPanelController.updateCommandAvailability()` landscape branch directly sets `isVisible` per button.
- **`isVideo` in both files includes `MediaType.AUDIO`** — root cause of fullscreen/edit bugs.
- **`btnBlackScreenCmd` is missing from the landscape branch entirely** — root cause of goal 2 bug.
- **N=1 promote** (bar-capable sole overflow item) is already implemented in `planLayout()`; the remaining gap is that `SLEEP_TIMER` is `barCapable = false`, so it can't be promoted. Phase 03 fixes this.
- `CommandPanelController.kt` is >500 lines — backup required before editing.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
