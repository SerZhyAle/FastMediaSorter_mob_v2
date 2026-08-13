# Tactical Plan: S0439 - screen-rotation-follow-os

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Research inputs:** [`research/01__current-rotation-model.md`](research/01__current-rotation-model.md)
**Feature:** Separate program/player "follow OS rotation"
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device test - BlockNeedUserTest)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-model-migration | - | ✅ Done | 4/4 | [PHASE_01__settings-model-migration.md](PHASE_01__settings-model-migration.md) |
| 02 | program-orientation-applier | 01 | ✅ Done | 4/4 | [PHASE_02__program-orientation-applier.md](PHASE_02__program-orientation-applier.md) |
| 03 | player-precedence | 01 | ✅ Done | 2/2 | [PHASE_03__player-precedence.md](PHASE_03__player-precedence.md) |
| 04 | settings-ui-toggles | 01, 03 | ✅ Done | 4/4 | [PHASE_04__settings-ui-toggles.md](PHASE_04__settings-ui-toggles.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (see `research/01__current-rotation-model.md`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0439` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0439`.

---

## Blockers Log

- 2026-06-16 - Phase 01 steps 01.1-01.3 done (edits + grep-verified). Step 01.4 (compile gate) BLOCKED: the working tree does not compile because S0438 (keep-screen-on-player, In Progress) lifted `settingsRepository` into `BaseActivity` while ~10 subclass activities still declare it without `override`. Not an S0439 defect. Next: complete/commit S0438 so the module builds, then `/spec-dev S0439` resumes at 01.4.

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.
