# Phase 05 — Interactive HUD Controls

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Deferred — depends on S0024
**Depends on:** **S0024** `spec_vr-hud-ray-input` (Approved, awaiting tactical decomposition)
**Blocks:** —
**Steps done:** 0 / 0 (placeholder)
**Started:** —
**Completed:** —

---

## Objective (deferred)

Wire interactive controls (seekbar, play/pause button, prev/next, audio/subs/speed/HUE/brightness selectors, stereo-mode tab) into the HUD-overlay, consuming the ray-input subsystem from S0024. Without S0024 this phase cannot proceed: there is no available ray-vs-plane intersection layer in the codebase.

---

## Why deferred

Strategic §«Proposed Structural Changes» P-2 (filed by `/spec-update` 2026-04-28):

> S0009 §2 Non-goal #1: «Интерактивный HUD (клики лучом/рукой, фокус, hover) — это уже решается подсистемой hand-tracking и не относится к данной спеке». S0019 требует interactivity (ray-clicks по seekbar/кнопкам/закладкам). Сейчас в проекте есть подсистемы для controllers + hand-tracking ввода (S0007), но нет общего слоя ray-vs-HUD-плоскость пересечения для plane-quad-слоя HUD.

Resolution chosen at start of F2: split into separate spec **S0024** (Approved on 2026-04-28). This phase remains a placeholder.

---

## Re-activation plan

After S0024 reaches `Implemented` status:

1. Replace this phase file content with concrete steps consuming the new ray-input registry.
2. Topics to cover in steps (when written):
   - Register seekbar element in HUD composer with click callback → `viewModel.seekTo(...)`.
   - Register play/pause button with toggle callback.
   - Register prev/next buttons → reuse Phase 03 navigation.
   - Register stereo-mode tabs → call existing `PlaybackControlDialogFragment` apply-path.
   - Register audio/subtitle/speed/HUE/brightness selectors (reuse existing dialog logic).
3. After steps written: `Status` flip to `⬜ Not started`, then run `/spec-dev` from this phase.

---

## Phase Done Criteria

- [ ] S0024 reached `Implemented` (gate).
- [ ] Steps populated with concrete handlers per §«Re-activation plan» topics.
- [ ] HUD ray-clicks reach the existing dialog apply-path without duplicating logic.
- [ ] Project compiles — `/build` for `vr debug`.

---

## Rollback Plan

Phase 05 has no production code. Reverting requires no rollback.
