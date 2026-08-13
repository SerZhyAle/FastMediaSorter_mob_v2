# Tactical Plan: S0208 — player-big-buttons-tuning

**Strategic spec:** [`../S0208_player-big-buttons-tuning.md`](../S0208_player-big-buttons-tuning.md)
**Feature:** Player Big Buttons Mode — refine for narrow and wide screens
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 40
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-05-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dimens-resources | — | ⬜ Not started | 0/3 | [PHASE_01__dimens-resources.md](PHASE_01__dimens-resources.md) |
| 02 | dynamic-slot-count | 01 | ⬜ Not started | 0/3 | [PHASE_02__dynamic-slot-count.md](PHASE_02__dynamic-slot-count.md) |
| 03 | manager-height-layout | 01, 02 | ⬜ Not started | 0/5 | [PHASE_03__manager-height-layout.md](PHASE_03__manager-height-layout.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ⬜ Not started | 0/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items in the strategic spec are Resolved — no blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped (strategic §8 says "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 03 changes class layer surface).
- [ ] `/spec-check S0208` returns `Verified` (after device test confirms 411dp/1240dp behaviour).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0208`.

---

## Blockers Log

- 2026-05-15 — none.

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.
