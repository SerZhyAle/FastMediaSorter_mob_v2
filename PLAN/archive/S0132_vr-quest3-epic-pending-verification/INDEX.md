# Tactical Plan: S0132 — vr-quest3-epic-pending-verification

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Feature:** VR Quest 3 — verification and fix of all pending tasks absorbed from archived tickets S0006/S0008/S0009/S0012/S0014/S0019/S0026/S0030/S0032/S0038/S0041/S0065/S0078/S0080
**Tier:** 5 — Epic
**Priority:** 80
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | group-a-stereo-rendering | — | ⬜ Not started | 0/4 | [PHASE_01__group-a-stereo-rendering.md](PHASE_01__group-a-stereo-rendering.md) |
| 02 | group-b-immersive-ui | 01 | ⬜ Not started | 0/4 | [PHASE_02__group-b-immersive-ui.md](PHASE_02__group-b-immersive-ui.md) |
| 03 | group-c-routing-task | — | ⬜ Not started | 0/3 | [PHASE_03__group-c-routing-task.md](PHASE_03__group-c-routing-task.md) |
| 04 | panel-3d-flow | 02, 03 | ⬜ Not started | 0/1 | [PHASE_04__panel-3d-flow.md](PHASE_04__panel-3d-flow.md) |
| 05 | group-d-low-priority | — | ⬜ Not started | 0/4 | [PHASE_05__group-d-low-priority.md](PHASE_05__group-d-low-priority.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

*(none — research steps are embedded in phases 01 and 05)*

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` / `_RU.md` / `_UK.md` — no update required (epic absorbs existing features, no new user-visible capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after any `.kt` change.
- [ ] `/spec-check S0132` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set journal status via `update.ps1 -Id S0132 -Status Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0132`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
