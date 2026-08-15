# Tactical Plan: S1083 - bugfix-stream-playback-controls

**Strategic spec:** [`../S1083_bugfix-stream-playback-controls.md`](../S1083_bugfix-stream-playback-controls.md)
**Research inputs:** [`research/01__live-stream-detection.md`](research/01__live-stream-detection.md)
**Feature:** Playback-control dialog honesty for internet streams (HUE, brightness, speed)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** In Progress
**Phases:** 3 / 4 done · 03 ⏭️ Skipped (owner decision, honest-hiding end state)
**Last updated:** 2026-07-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | source-character-signal | - | ✅ Done | 3/3 | [PHASE_01__source-character-signal.md](PHASE_01__source-character-signal.md) |
| 02 | dialog-honesty-gating | 01 | ✅ Done | 2/2 | [PHASE_02__dialog-honesty-gating.md](PHASE_02__dialog-honesty-gating.md) |
| 03 | stream-color-lifecycle | 01, 02 | ⏭️ Skipped | 0/4 | [PHASE_03__stream-color-lifecycle.md](PHASE_03__stream-color-lifecycle.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Live-stream vs VOD vs file detection - resolved, see [`research/01__live-stream-detection.md`](research/01__live-stream-detection.md) (strategic §6.1). Unblocks Phase 01/02.
- [x] **Research:** Colour GL-effects on the live decode path (strategic §6.2) - N/A: Phase 03 skipped by owner 2026-07-20, colour stays hidden for streams (Phase 02 final state), so the device experiment was not required.
- [x] **Research:** Reapply colour after stream reconnect (strategic §6.3) - N/A: dependent on §6.2; moot with Phase 03 skipped.

> Phases 01, 02, 04 done. Phase 03 ⏭️ Skipped per owner decision - the §6.2/§6.3 blockers no longer gate the ticket.

---

## Completion Gate

- [x] Phases 01, 02, 04 show ✅ Done. Phase 03 is ⏭️ Skipped (owner decision 2026-07-20, honest-hiding end state).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if Phase 03 lands colour-on-streams and strategic §8 is upgraded from "Без изменений"; skip otherwise.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1083` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec blocks, set journal status via `update.ps1 -Status Block* -StatusNote '..'`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1083`.

---

## Blockers Log

- 2026-07-17 - Phase 03 blocked at authoring: colour on the live decode path is device-gated (strategic §6.2). Next: run the device experiment on a live HLS/DASH stream, then either implement Phase 03 or mark it ⏭️ Skipped and keep the Phase 02 hidden-sections end state.
- 2026-07-20 - Resolved: owner chose the honest-hiding end state. Phase 03 ⏭️ Skipped (device experiment not run); Phase 02 stands as final. Proceeded to Phase 04 and closed the ticket to BlockNeedUserTest for on-device confirmation of the hidden-section behaviour.

---

## Change Log

- 2026-07-17 - Initial tactical plan authored by `/spec-tech`. §6.1 resolved from code; §6.2/§6.3 remain device-gated blockers for Phase 03.
- 2026-07-20 - `/spec-dev`: owner picked honest-hiding. Phase 03 ⏭️ Skipped, Phase 04 executed, one `S1083:` device probe added at the dialog section-gate flow entry; status -> BlockNeedUserTest.
