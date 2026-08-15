# Tactical Plan: S0405 - always-on-top-overlay-screenshot

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Research inputs:** [`research/01__overlay-mechanism.md`](research/01__overlay-mechanism.md) · [`research/02__screen-capture-mechanism.md`](research/02__screen-capture-mechanism.md) · [`research/03__receiver-coverage.md`](research/03__receiver-coverage.md) · [`research/05__flavor-scope.md`](research/05__flavor-scope.md) · [`research/07__foreground-service.md`](research/07__foreground-service.md) · [`research/08__gesture-strip-touch-model.md`](research/08__gesture-strip-touch-model.md) · [`research/09__left-edge-back-gesture-conflict.md`](research/09__left-edge-back-gesture-conflict.md) · [`research/11__screenshot-destination.md`](research/11__screenshot-destination.md) · [`research/12__one-hand-operation-plus-analysis.md`](research/12__one-hand-operation-plus-analysis.md)
**Feature:** Edge gesture-strip overlay → MediaProjection screenshot → destination
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 6 / 6 done
**Last updated:** 2026-06-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec. First flavor target is `noLegal` (sideload); Play targets `standard`/`photos` are a later, out-of-scope rollout.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capability-settings-model | - | ✅ Done | 5/5 | [PHASE_01__capability-settings-model.md](PHASE_01__capability-settings-model.md) |
| 02 | screenshot-save-path | 01 | ✅ Done | 3/3 | [PHASE_02__screenshot-save-path.md](PHASE_02__screenshot-save-path.md) |
| 03 | nolegal-capture-service | 02 | ✅ Done | 4/4 | [PHASE_03__nolegal-capture-service.md](PHASE_03__nolegal-capture-service.md) |
| 04 | nolegal-gesture-overlay | 03 | ✅ Done | 4/4 | [PHASE_04__nolegal-gesture-overlay.md](PHASE_04__nolegal-gesture-overlay.md) |
| 05 | settings-ui | 01, 04 | ✅ Done | 4/4 | [PHASE_05__settings-ui.md](PHASE_05__settings-ui.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None block the `noLegal` iteration. The following are deferred to the future Play rollout (out of scope here):

- **Deferred (Play only):** §6.4 policy disclosure/consent for `standard`/`photos` Play submission. Does NOT gate `noLegal` (sideload, no Play review). See strategic §6.4 + ADR-5.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` (+ `_RU`/`_UK` if present) updated - this is a `noLegal`-only capability; `docs/FEATURES.md` (Play builds) is NOT touched until the Play rollout.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; noLegal-only classes carry `-NoFlavors`.
- [ ] `/spec-check S0405` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0405`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-11 - Initial tactical plan authored by `/spec-tech`.
