# Tactical Plan: S1049 - main-panels-uniform-button-width

**Strategic spec:** [`../S1049_main-panels-uniform-button-width.md`](../S1049_main-panels-uniform-button-width.md)
**Research inputs:** none - strategic §6 resolved via direct owner decision in chat (2026-07-15), no separate research artifact
**Feature:** Unify button/cell width across the four main-screen top panels (portrait)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (journal: BlockNeedUserTest - implementation done, awaiting device test)
**Phases:** 4 / 4 done
**Last updated:** 2026-07-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | streams-chip-width | - | ✅ Done | 2/2 | [PHASE_01__streams-chip-width.md](PHASE_01__streams-chip-width.md) |
| 02 | command-bar-grid | - | ✅ Done | 3/3 | [PHASE_02__command-bar-grid.md](PHASE_02__command-bar-grid.md) |
| 03 | resource-tabs-grid | - | ✅ Done | 4/4 | [PHASE_03__resource-tabs-grid.md](PHASE_03__resource-tabs-grid.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01-03 touch disjoint resources/sections and carry no cross-phase data dependency; sequential numeric
order is a convenience (01/02 are the smallest, lowest-risk edits; 03 is the only phase adding new resources
and Kotlin branching, and both 02 and 03 edit `activity_main.xml` so doing 02 first avoids two phases racing
the same file in one working session - not a build-time dependency).

---

## Pre-Implementation Blockers

None - strategic §6 (resource-tabs width strategy) is Resolved. No open research items block Phase 01.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; strategic §8 says "Без изменений в docs/FEATURES."
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (MainResourceTabsManager.kt internals changed).
- [ ] `/spec-check S1049` returns `Verified`. (Pending on-device verification first - see journal `BlockNeedUserTest` note.)
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1049`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-07-15 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-15 - All 4 phases executed and closed by `/spec-dev`. Journal status flipped to `BlockNeedUserTest` (right after Phase 03, so the S1049 debug tag inserted before the final build was already blessed by `assert-no-ticket-logs` for the rest of the run). Debug tag: 1, in `MainResourceTabsManager.createTabs()`.
