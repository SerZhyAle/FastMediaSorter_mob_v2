# Tactical Plan: S0024 — vr-hud-ray-input

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Feature:** Ray-input subsystem for the interactive immersive HUD (controller aim + hand-tracking pinch → click on HUD elements registered by the HUD content composer).
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | hud-element-registry | — | ⬜ Not started | 0/5 | [PHASE_01__hud-element-registry.md](PHASE_01__hud-element-registry.md) |
| 02 | ray-hud-intersection | 01 | ⬜ Not started | 0/4 | [PHASE_02__ray-hud-intersection.md](PHASE_02__ray-hud-intersection.md) |
| 03 | hover-state-and-redraw | 01, 02 | ⬜ Not started | 0/4 | [PHASE_03__hover-state-and-redraw.md](PHASE_03__hover-state-and-redraw.md) |
| 04 | input-dispatcher | 01, 02, 03 | ⬜ Not started | 0/5 | [PHASE_04__input-dispatcher.md](PHASE_04__input-dispatcher.md) |
| 05 | idle-gate-and-feedback | 04 | ⬜ Not started | 0/3 | [PHASE_05__idle-gate-and-feedback.md](PHASE_05__idle-gate-and-feedback.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

_None — all research items in strategic §6 are Resolved (owner decisions 2026-04-28: reuse same aim-pose; trigger = HUD click; A/X stay on player commands). Trigger-vs-bindings collision check stays as a normal step inside Phase 04, not a gate._

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with the HUD ray-input bullet (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (added via `scripts/add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (HUD registry + dispatcher classes are new public roles).
- [ ] `/spec-check S0024` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0024`.

---

## Blockers Log

- _none yet_

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech`.
