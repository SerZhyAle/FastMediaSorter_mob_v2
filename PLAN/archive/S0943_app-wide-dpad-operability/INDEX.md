# Tactical Plan: S0943 - app-wide-dpad-operability

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Research inputs:** [`research/01__tv-focus-indicator-approach.md`](research/01__tv-focus-indicator-approach.md), [`research/02__focus-frame-offset-root-cause.md`](research/02__focus-frame-offset-root-cause.md)
**Feature:** Full non-touch operability (remote / D-pad / keyboard / gamepad) across the whole app
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 60
**Status:** Verified (delivered scope: focus indicator; reachability deferred to follow-up)
**Phases:** 1 / 7 done (Phase 01 ✅; 02-07 ⏭️ deferred)
**Last updated:** 2026-07-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | focus-indicator (in-place decoration) | - | ✅ Done | 4/4 | [PHASE_01__focus-frame-accuracy.md](PHASE_01__focus-frame-accuracy.md) |
| 02 | reachability-contract | 01 | ⏭️ Skipped | 0/4 | [PHASE_02__reachability-contract.md](PHASE_02__reachability-contract.md) |
| 03 | scroll-follows-focus | 02 | ⏭️ Skipped | 0/2 | [PHASE_03__scroll-follows-focus.md](PHASE_03__scroll-follows-focus.md) |
| 04 | custom-surfaces-input | 02 | ⏭️ Skipped | 0/3 | [PHASE_04__custom-surfaces-input.md](PHASE_04__custom-surfaces-input.md) |
| 05 | reachability-gate | 02 | ⏭️ Skipped | 0/2 | [PHASE_05__reachability-gate.md](PHASE_05__reachability-gate.md) |
| 06 | wear-gamepad-input | 02 | ⏭️ Skipped | 0/2 | [PHASE_06__wear-gamepad-input.md](PHASE_06__wear-gamepad-input.md) |
| 07 | docs-catalog-cleanup | all | ⏭️ Skipped | 0/2 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

> **Closed 2026-07-04 by owner after Phase 01.** Delivered scope: the focus indicator (in-place per-view decoration), device-verified. The reachability workstream (phases 02-06: reachability contract, scroll-follows-focus, custom-surface D-pad, mechanical gate, wear/gamepad) was NOT built - it is carried forward to a separate follow-up ticket so it is not lost. Phase 07 (docs/catalog) folded into this closure.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.2:** Focus-frame offset root cause - resolved, see [`research/02__focus-frame-offset-root-cause.md`](research/02__focus-frame-offset-root-cause.md). Unblocks Phase 01.
- [x] **Research §6.1:** Focus-indicator approach resolved - platform-standard is per-view/in-place decoration (one window-level focus listener decorating the focused view), NOT a coordinate-computed overlay and NOT per-instance styling. See [`research/01__tv-focus-indicator-approach.md`](research/01__tv-focus-indicator-approach.md). Delivered in Phase 01. Reachability/order (initial focus, no-trap) remains for Phase 02.
- [ ] **Research §6.3:** Mechanical reachability gate feasibility (instrumented focus-tree walk on TV emulator vs static layout analysis) - required before Phase 05. See strategic §6.3.
- [ ] **Research §6.4:** Wear rotary + gamepad HAT/stick input routing into the directional-navigation contract - required before Phase 06. See strategic §6.4.

Phase 01 is unblocked and may start now. Phases 02/05/06 must not start while their blocker is unchecked.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - updated per strategic §8 (introduces a user-perceived capability), owned by `/skill-release` from the ALL_FEATURES diff.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0943` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`; update `Phases: X/N done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log bullet; if the whole spec is blocked, set the journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0943`.

---

## Blockers Log

- 2026-07-04 - Phases 02/05/06 blocked on open research §6.1/§6.3/§6.4. Next: run `android-solution-researcher` on those items before their phases.

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-tech`; research §6.2 resolved, Phase 01 unblocked.
