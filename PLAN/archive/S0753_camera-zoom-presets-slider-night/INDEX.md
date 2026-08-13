# Tactical Plan: S0753 - camera-zoom-presets-slider-night

**Strategic spec:** [`../S0753_camera-zoom-presets-slider-night.md`](../S0753_camera-zoom-presets-slider-night.md)
**Research inputs:** [`research/01__night-exposure-routes.md`](research/01__night-exposure-routes.md), [`research/02__zoom-presets-clamping.md`](research/02__zoom-presets-clamping.md)
**Feature:** Camera zoom presets + zoom slider + legible overlay controls + night mode
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-06-27

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | zoom-presets | - | ✅ Done | 2/2 | [PHASE_01__zoom-presets.md](PHASE_01__zoom-presets.md) |
| 02 | zoom-slider | 01 | ✅ Done | 5/5 | [PHASE_02__zoom-slider.md](PHASE_02__zoom-slider.md) |
| 03 | control-legibility | 02 | ✅ Done | 4/4 | [PHASE_03__control-legibility.md](PHASE_03__control-legibility.md) |
| 04 | night-mode | 03 | ✅ Done | 6/6 | [PHASE_04__night-mode.md](PHASE_04__night-mode.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (night-mode route, transparency level, slider geometry, night scope, guaranteed-max behaviour all decided 2026-06-27).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the delivered capability (via `scripts/all_features/add.ps1`). Do NOT edit `docs/FEATURES*.md` per-spec - that is `/skill-release`-owned (CLAUDE.md Rule 11).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new test class + new helper symbols).
- [ ] New strings pass `scripts/check_strings_localized.ps1 -KeyPrefix "camera_control_night"`.
- [ ] `/spec-check S0753` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` only when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log, and set the journal status via `update.ps1 -Status Block...` with a `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0753`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-27 - Initial tactical plan authored by `/spec-tech`.
