# Tactical Plan: S0034 — resource-icons-system

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Feature:** Resource Icons System
**Tier:** 2 — Easy
**Priority:** 50
**Status:** In Progress
**Phases:** 1 / 8 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | designer-prompt | — | ⬜ Not started | 0/3 | [PHASE_01__designer-prompt.md](PHASE_01__designer-prompt.md) |
| 02 | data-foundations | 01 | ✅ Done | 6/6 | [PHASE_02__data-foundations.md](PHASE_02__data-foundations.md) |
| 03 | icon-library-registry | 02 | ⬜ Not started | 0/5 | [PHASE_03__icon-library-registry.md](PHASE_03__icon-library-registry.md) |
| 04 | composite-rendering | 03 | ⬜ Not started | 0/4 | [PHASE_04__composite-rendering.md](PHASE_04__composite-rendering.md) |
| 05 | assignment-logic | 02, 03 | ⬜ Not started | 0/5 | [PHASE_05__assignment-logic.md](PHASE_05__assignment-logic.md) |
| 06 | icon-selector-ui | 03, 04 | ⬜ Not started | 0/6 | [PHASE_06__icon-selector-ui.md](PHASE_06__icon-selector-ui.md) |
| 07 | main-screen-integration | 04, 05 | ⬜ Not started | 0/4 | [PHASE_07__main-screen-integration.md](PHASE_07__main-screen-integration.md) |
| 08 | docs-catalog-cleanup | all | ⬜ Not started | 0/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items are Resolved. Phase 01 produces the designer prompt — handoff to the designer is the hard external dependency before Phase 03 (icon assets cannot be embedded until they exist).

- [ ] **External:** Vector icon set (50 SVG files, 5 themed groups) delivered by designer per Phase 01 prompt — required before Phase 03 Step 03.2.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0034` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confcurrent every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0034`.

---

## Blockers Log

- 2026-04-29 — Phase 03 will block on external designer asset delivery once Phase 01 prompt is dispatched. Set spec status to `BlockExternal` while waiting.

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech`.
