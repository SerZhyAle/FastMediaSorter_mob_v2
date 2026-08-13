# Tactical Plan: S0107 — image-draw-overlay

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Feature:** Draw Overlay — annotate images with brush, rectangle, eraser and save as new file
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 5 done
**Last updated:** 2026-05-06 (rotation blocker resolved)

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | menu-entry | — | ✅ Done | 3/3 | [PHASE_01__menu-entry.md](PHASE_01__menu-entry.md) |
| 02 | draw-canvas-manager | 01 | ✅ Done | 5/5 | [PHASE_02__draw-canvas-manager.md](PHASE_02__draw-canvas-manager.md) |
| 03 | draw-toolbar-layout | 02 | ✅ Done | 4/4 | [PHASE_03__draw-toolbar-layout.md](PHASE_03__draw-toolbar-layout.md) |
| 04 | save-merge-flow | 02, 03 | ✅ Done | 5/5 | [PHASE_04__save-merge-flow.md](PHASE_04__save-merge-flow.md) |
| 05 | docs-catalog-cleanup | all | 🚧 In Progress | 0/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Rotation behaviour in Draw Mode (strategic §6.3) — **Resolved 2026-05-06:** option (b) — freeze screen orientation (`requestedOrientation = SCREEN_ORIENTATION_LOCKED`) while Draw Mode is active. On exit (Save or Cancel), orientation is unlocked. Landscape draw toolbar variant is still required (Draw Mode may be entered from landscape orientation). See strategic Proposal P-2 for required PHASE_02/PHASE_03 edits.
- [x] **Research:** Verify that `ImageLoadingManager` (or equivalent) exposes the currently loaded Bitmap for in-memory merge in Phase 04 without reloading. **Resolved 2026-05-09:** `ImageLoadingManager` did NOT expose it — added `onStaticImageLoaded(bitmap)` to `ImageLoadingCallback` interface + implemented in `PlayerImageLoadingCallbackImpl` to set `viewModel.currentDisplayedBitmap`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after new `.kt` files added.
- [ ] `/spec-check S0107` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0107`.

---

## Blockers Log

*(none yet)*

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-06 — Rotation blocker resolved (option b: freeze orientation); INDEX pre-impl blocker checked off. See strategic P-1, P-2 for pending structural changes.
