# Tactical Plan: panel-fps-overlay-landscape

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Feature:** Add diagnostic FPS overlay over the flat (non-immersive) player, gated by a new setting independent of the existing VR-HUD-FPS toggle.
**Tier:** 2 — Easy
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-model | — | ✅ Done | 4/4 | [PHASE_01__settings-model.md](PHASE_01__settings-model.md) |
| 02 | settings-ui | 01 | ✅ Done | 3/3 | [PHASE_02__settings-ui.md](PHASE_02__settings-ui.md) |
| 03 | fps-meter | — | ✅ Done | 2/2 | [PHASE_03__fps-meter.md](PHASE_03__fps-meter.md) |
| 04 | overlay-binding | 01, 03 | ✅ Done | 3/3 | [PHASE_04__overlay-binding.md](PHASE_04__overlay-binding.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 has 5 Open research items, all resolved inline by /spec-all on 2026-04-28:

- [x] §6.1 **Position** — fixed at top-end (start aligned with right edge in LTR, with start margin from edge), below the toolbar safe area, well above progress bar.
- [x] §6.2 **WindowInsets** — overlay parents to the existing player-content container; consumes the same `WindowInsetsCompat` flow already wired in `PlayerActivity`.
- [x] §6.3 **FPS source** — `Choreographer.FrameCallback` (frame-presentation rate, the user-relevant plumbness signal).
- [x] §6.4 **Advanced mode** — declined per strategic Non-goals; only basic FPS in this spec.
- [x] §6.5 **Orientation** — overlay shows in any orientation when video is playing; the slug name "landscape" is historical from the original user request.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with one bullet per Phase 05.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0021` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
