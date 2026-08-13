# Phase 03 — Б1: Verify single-eye toggle (already implemented)

**Strategic spec:** [`../S0030_bugfix-panel-stereo-dialog-ui.md`](../S0030_bugfix-panel-stereo-dialog-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Б1 was implemented on 2026-04-27 as part of `spec_panel-stereo-single-eye`. The toggle exists in `fragment_settings_playback.xml` and is wired in `PlaybackSettingsFragment.kt`. This phase verifies correctness and confirms no follow-up work is needed.

## Files Touched

| File | Change |
|------|--------|
| none | verification only |

---

## Steps

### Step 3.1 — Verify toggle presence and wiring

**Status:** `[ ] not done`
**Depends on:** —

**Prompt for developer:**
Confirm:

1. `fragment_settings_playback.xml` contains `android:id="@+id/switchPanelStereoSingleEye"` — exists at line 209.
2. `PlaybackSettingsFragment.kt` has `binding.switchPanelStereoSingleEye.setOnCheckedChangeListener` and state-restore at lines 326/401-402.
3. `SettingsRepositoryImpl.kt` has `KEY_PANEL_STEREO_SINGLE_EYE` (line 201) read/write (lines 430, 653).
4. `AppSettings.kt` has `panelStereoSingleEye: Boolean` field.
5. String resources exist in EN/RU/UK: search `pref_panel_stereo_single_eye_title` in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.

If all 5 checks pass → step is done, no code changes.
If any check fails → fix the missing piece and log to dev/CHANGELOG via `add_to_dev_log.ps1`.

**Verification:** All 5 checks pass. No compilation errors in files above.

---

### Step 3.2 — Confirm FEATURES docs include single-eye entry

**Status:** `[ ] not done`
**Depends on:** 3.1

**Prompt for developer:**
Search `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` for "single eye" / "один глаз" / "один зір" or `panelStereo`.
If entries are present and accurate → step done.
If missing → add a bullet in each file under the panel player section. Style matches existing entries. Use `..` (two dots) for ellipsis, use `ё` in Russian where applicable.

**Verification:** `grep -i "single.eye\|один глаз\|один зір" docs/FEATURES*.md` returns at least one match per file.
