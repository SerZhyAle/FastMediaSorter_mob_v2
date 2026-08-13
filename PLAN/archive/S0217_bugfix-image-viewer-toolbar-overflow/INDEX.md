# Tactical Plan: S0217 — bugfix-image-viewer-toolbar-overflow

**Strategic spec:** [`../S0217_bugfix-image-viewer-toolbar-overflow.md`](../S0217_bugfix-image-viewer-toolbar-overflow.md)
**Feature:** Inline image-edit buttons in player toolbar (eligibility-driven, no forced overflow)
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | planner-eligibility-flip | — | ✅ Done | 1/1 | [PHASE_01__planner-eligibility-flip.md](PHASE_01__planner-eligibility-flip.md) |
| 02 | layout-inline-buttons | 01 | ✅ Done | 3/3 | [PHASE_02__layout-inline-buttons.md](PHASE_02__layout-inline-buttons.md) |
| 03 | controller-wiring | 02 | ✅ Done | 5/5 | [PHASE_03__controller-wiring.md](PHASE_03__controller-wiring.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items resolved on 2026-05-16. No blockers — Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped (strategic §8: "Без изменений в docs/FEATURES").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — public API of `CommandPanelController` / `PlayerBindingSafeViews` extended with 5 new bar-view properties.
- [ ] `/spec-check S0217` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0217`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
