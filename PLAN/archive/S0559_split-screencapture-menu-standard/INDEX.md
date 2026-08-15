# Tactical Plan: S0559 - split-screencapture-menu-standard

**Strategic spec:** [`../S0559_split-screencapture-menu-standard.md`](../S0559_split-screencapture-menu-standard.md)
**Research inputs:** none (strategic §6 resolved by quiz 2026-06-20; architecture mapped inline during `/spec-tech`)
**Feature:** Screen capture - Play-safe menu-triggered screenshot on standard; gesture/silent path stays noLegal
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - BlockNeedUserTest (on-device acceptance pending)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-engine-seam | - | ✅ Done | 5/5 | [PHASE_01__shared-engine-seam.md](PHASE_01__shared-engine-seam.md) |
| 02 | menu-launcher-contract | 01 | ✅ Done | 4/4 | [PHASE_02__menu-launcher-contract.md](PHASE_02__menu-launcher-contract.md) |
| 03 | settings-menu-action | 02 | ✅ Done | 4/4 | [PHASE_03__settings-menu-action.md](PHASE_03__settings-menu-action.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 research items are all `Resolved` (quiz 2026-06-20). The exact visual layout of the new action follows the existing Operations-tab one-shot action pattern; a separate `/ui-clarify` pass is only needed if a new settings card is introduced (Phase 03 reuses the existing destinations layout, so none is).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT touched here (strategic §8: one line at release, populated by `/skill-release` from the `ALL_FEATURES` diff).
- [ ] `docs/ALL_FEATURES.jsonl` records the standard menu-screenshot capability.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public types added).
- [ ] `/spec-check S0559` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0559`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
