# Tactical Plan: S0619 - video-control-wide-sliders

**Strategic spec:** [`../S0619_video-control-wide-sliders.md`](../S0619_video-control-wide-sliders.md)
**Research inputs:** none
**Feature:** Широкие бегунки в диалоге управления видео
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | slider-visual-resources | - | ✅ Done | 4/4 | [PHASE_01__slider-visual-resources.md](PHASE_01__slider-visual-resources.md) |
| 02 | apply-dialog-sliders | 01 | ✅ Done | 2/2 | [PHASE_02__apply-dialog-sliders.md](PHASE_02__apply-dialog-sliders.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 items are not human-research blockers:

- §6.1 (large thumb clipping in rotated portrait canvas) and §6.2 (touch-grab accuracy with the vertical touch remap) are on-device verifications - covered by Phase 02 verification predicates and the `BlockNeedUserTest` device check.
- §6.3 (styling approach fork) is resolved in this plan: keep the existing dual-orientation slider widget and give it a named SeekBar style with a custom thick track + large oval thumb (option a). Migration to a Material Slider (option b) is rejected - it would not preserve the widget's portrait-vertical / landscape-horizontal dual behaviour without a larger rewrite.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений", pure UX polish).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated only if a Kotlin public API changed (this plan touches resources + layouts only - expected no-op).
- [ ] `/spec-check S0619` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0619`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech`.
