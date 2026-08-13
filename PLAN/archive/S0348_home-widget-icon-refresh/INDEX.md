# Tactical Plan: S0348 - home-widget-icon-refresh

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Feature:** Icon-style home-screen widgets (first wave)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (journal: BlockNeedUserTest - awaiting on-device verification)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **Wave scope (owner decision 2026-06-04):** this tactical plan covers ONLY the first wave - icon refresh of existing `1x1` widgets, Camera-OCR compaction, the home-widget catalog/registry, and the settings-driven pin picker. The five NEW widgets (Quick Audio Recorder, Capture & OCR Panel, Audio Now Playing Controller, Random Photo Frame, Scheduled Tasks Manager) are spun out into separate sub-specifications and are NOT implemented here. See strategic §13 "Spun-out sub-specs".

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | icon-style-layouts | - | ✅ Done | 5/5 | [PHASE_01__icon-style-layouts.md](PHASE_01__icon-style-layouts.md) |
| 02 | camera-ocr-compact | 01 | ✅ Done | 2/2 | [PHASE_02__camera-ocr-compact.md](PHASE_02__camera-ocr-compact.md) |
| 03 | widget-registry | 01 | ✅ Done | 4/4 | [PHASE_03__widget-registry.md](PHASE_03__widget-registry.md) |
| 04 | settings-pin-picker | 02, 03 | ✅ Done | 6/6 | [PHASE_04__settings-pin-picker.md](PHASE_04__settings-pin-picker.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items carry `Статус: Resolved`. No open research blockers.

- [x] All §6 research items resolved in strategic spec.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence (icon-style `1x1` widgets, compact Camera-OCR, settings-driven widget placement).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed - new registry + pinner classes).
- [ ] `/spec-check S0348` returns `Verified` (against first-wave criteria 1-12; criteria 13-17 belong to the spun-out sub-specs).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0348`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-04 - Initial tactical plan (first wave) authored by `/spec-tech`.
