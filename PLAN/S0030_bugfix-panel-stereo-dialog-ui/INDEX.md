# Tactical Plan: S0030 — bugfix-panel-stereo-dialog-ui

**Strategic spec:** [`../S0030_bugfix-panel-stereo-dialog-ui.md`](../S0030_bugfix-panel-stereo-dialog-ui.md)
**Feature:** UI-баги панельного диалога стерео и настроек
**Tier:** 2 — Easy
**Status:** ✅ Done
**Phases:** 4 / 4 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Strategic rationale lives in `../S0030_bugfix-panel-stereo-dialog-ui.md`.

---

## Research findings

| Bug | Finding |
|-----|---------|
| **Б1** | Already implemented in spec_panel-stereo-single-eye (2026-04-27). `switchPanelStereoSingleEye` exists in `fragment_settings_playback.xml` (line 209) and is wired in `PlaybackSettingsFragment.kt` (lines 326, 401-402). DataStore key `KEY_PANEL_STEREO_SINGLE_EYE` exists in `SettingsRepositoryImpl.kt`. No new code needed — Phase 03 is verification only. |
| **Б2** | `switchVrOverrideFormatType` uses `MaterialSwitch` with `android:layout_width="match_parent"` in both portrait and landscape layouts. This puts text far left and thumb far right. Fix: wrap in `LinearLayout horizontal` (switch wrap_content left + TextView weight=1 right). ID unchanged. |
| **Б3** | In `handleStereoModeSelection`, after `host().setStereoMode(mode)`, the code reads `host().stereoMode.value` (= `effectiveStereoMode`). The coordinator resolves AUTO → detected mode before storing in `effectiveStereoMode`. So when user picks AUTO, `bindStereoMode(effectiveMode)` is called with e.g. SBS, jumping the radio. Fix: `bindStereoMode(mode)` instead. |

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | b2-layout-override-format | — | ✅ Done | 3/3 | [PHASE_01__b2-layout-override-format.md](PHASE_01__b2-layout-override-format.md) |
| 02 | b3-auto-mode-stays-selected | — | ✅ Done | 3/3 | [PHASE_02__b3-auto-mode-stays-selected.md](PHASE_02__b3-auto-mode-stays-selected.md) |
| 03 | b1-verify-single-eye-toggle | 01 02 | ✅ Done | 2/2 | [PHASE_03__b1-verify-single-eye-toggle.md](PHASE_03__b1-verify-single-eye-toggle.md) |
| 04 | docs-catalog-cleanup | 01 02 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

- [x] **Research Б1**: toggle already exists — no new code; Phase 03 is verify-only.
- [x] **Research Б3 (§6.3)**: global per-app mode (consistent with other dialog options) — confirmed.
- [x] **Research Б3 (§6.2)**: secondary detected-mode label optional — include via `updateStereoDetectedLabel` enhancement.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` updated with "Show one eye" toggle bullet (confirmed present EN/RU/UK).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` scan run (818 records, 2026-04-29).
- [x] Strategic spec `Status:` advanced to `Implemented`.

---

## Blockers Log

_Empty._

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by spec-tech.
