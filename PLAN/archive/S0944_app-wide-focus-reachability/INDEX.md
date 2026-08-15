# Tactical Plan: S0944 - app-wide-focus-reachability

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Research inputs:** none new (builds on S0943 research/01 - per-view/window-level, not per-screen)
**Feature:** App-wide focus reachability without touch (remote / D-pad / keyboard / gamepad)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 55
**Status:** Verified (delivered scope: initial-focus fallback; phases 02-06 deferred by owner)
**Phases:** 1 / 6 done (Phase 01 ✅ device-verified; 02-06 ⏭️ deferred)
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate.
>
> **Design axis:** reachability is enforced by UNIVERSAL window-level mechanisms reusing the existing infra (`BaseActivity` initial-focus hook + `FocusTargetResolver` trap-avoidance), NOT by hand-editing hundreds of screens. Most of the machinery already exists; the gaps are the universal fallbacks.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | initial-focus-fallback | - | ✅ Done | 2/2 | [PHASE_01__initial-focus-fallback.md](PHASE_01__initial-focus-fallback.md) |
| 02 | dialog-initial-focus | 01 | ⏭️ Skipped | 0/2 | [PHASE_02__dialog-initial-focus.md](PHASE_02__dialog-initial-focus.md) |
| 03 | scroll-follows-focus | 01 | ⏭️ Skipped | 0/2 | [PHASE_03__scroll-follows-focus.md](PHASE_03__scroll-follows-focus.md) |
| 04 | reachability-gate | 01 | ⏭️ Skipped | 0/2 | [PHASE_04__reachability-gate.md](PHASE_04__reachability-gate.md) |
| 05 | wear-gamepad-input | 01 | ⏭️ Skipped | 0/2 | [PHASE_05__wear-gamepad-input.md](PHASE_05__wear-gamepad-input.md) |
| 06 | docs-catalog-cleanup | all | ⏭️ Skipped | 0/1 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

> **Closed 2026-07-04 by owner after Phase 01.** Delivered scope: universal initial-focus fallback (every non-overriding screen lands focus on a real control in non-touch), device-verified. Remaining reachability work stays documented as deferred backlog in the phase files above - dialog/bottom-sheet initial focus (02), scroll-follows-focus (03), mechanical reachability gate (04, research-gated), wear/gamepad input (05). Catalog + capability (06) folded into this closure. Reopen or draft a fresh ticket if the backlog is resumed.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research §6.1:** Mechanical reachability gate feasibility - required before Phase 04.
- [ ] **Research §6.2:** Wear rotary + gamepad HAT/stick routing - required before Phase 05.

Phases 01-03 + 06 are unblocked.

---

## Completion Gate

- [ ] All phases show ✅ Done or ⏭️ Skipped (with reason).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0944` returns `Verified` for the delivered scope.

---

## How to Track Progress

1. Before a phase: flip row to `🚧 In Progress`.
2. During: flip step to `[~]` then `[x]` on Verification pass.
3. On completion: confirm steps + Done Criteria, flip row `✅ Done`.
4. Blocked: flip `⛔ Blocked`, add Blockers Log bullet.

---

## Blockers Log

- 2026-07-04 - Phases 04/05 blocked on research §6.1/§6.2.

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-all`.
