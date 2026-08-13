# Phase 03 - Fill the matrix

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-07-27
**Completed:** 2026-07-30

---

## Objective

Bring the CSV matrix to full coverage: drop the stale rows, add the missing rows, and give the reader, link-download, player-interaction, streams and launcher blocks differentiated per-profile values.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and Phase 02 is ✅ Done.
- [ ] **Owner sign-off on the per-column values** (INDEX Pre-Implementation Blockers). Do not start this phase on the draft tables alone.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | ≤ 240 rows |
| `scripts/check_device_profile_presets.ps1` | Modified | ≤ 320 |

> The CSV is 192 rows today: minus 3 stale, plus ~37 new rows, giving roughly 226. The file may be edited in a spreadsheet and saved quoted or plain - the loader parses both. Keep the `option` column first and the `Other` column last and empty.
>
> Cell-format reminders that cost a rebuild if missed: size fields are written in the unit the Settings screen shows (image/video in KB, audio in MB), booleans are `TRUE`/`FALSE`, enums are the exact enum name, and an empty cell means "no override".

---

## Steps

### Step 03.1 - Remove the three stale rows

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `screenshotGestureActionDown`, `screenshotGestureActionRight` and `screenshotGestureActionUp` rows. All three are empty across every profile and Phase 02 removed their applier branches.

**Verification:**

- `Grep` - `screenshotGestureActionDown` returns zero hits in the CSV.
- `Grep` - `screenshotGestureActionUp` returns zero hits in the CSV.

**Status:** `[x]` done

---

### Step 03.2 - Add the missing rows as empty rows

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1 -AddMissing` to append every remaining uncovered field as an empty row, then inspect the diff. The registry from Phase 01 keeps the deliberately non-presettable fields out, so the appended set should be the launcher fields, the per-zone gesture fields, the two stream track languages and `cameraAspectRatio`. If a registered field was appended, the registry wiring from step 01.2 is wrong - fix that rather than hand-deleting the row.

**Verification:**

- `Grep` - `launcherDesktopLocked` matches exactly once in the CSV.
- `Grep` - `screenshotGestureLeftTopDown` matches exactly once in the CSV.
- `Grep` - `defaultPassword` still matches exactly once (pre-existing row, untouched).
- Value equality - `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` no longer reports any `MISSING from CSV rows`.

**Status:** `[x]` done

---

### Step 03.3 - Fill the reader block

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 03.2

**Prompt for developer:**

> Apply the signed-off values for `textReaderTheme`, `pdfColorMode`, `pdfScrollMode`, `epubLineHeight`, `epubHorizontalMargin` and `syntaxHighlighting` from research artifact §7.1. The intent: `ebook_reader` reads on paper-like defaults (sepia, page mode, tighter line height, wider margin), the dark-context profiles (`tv_media_box`, `car_head_unit`, `photo_frame`, `audio_player`, `vr_headset`) get dark/night reader themes, and `personal_smartphone` / `home_tablet` keep continuous scroll.

**Verification:**

- Value equality - the `textReaderTheme` row's `ebook_reader` cell equals `SEPIA`.
- Value equality - the `pdfColorMode` row's `tv_media_box` cell equals `NIGHT`.
- Value equality - the `epubHorizontalMargin` row's `tv_media_box` cell equals `48`.
- Value equality - the checker's value-sanity rule (step 01.4) reports zero out-of-range values.

**Status:** `[x]` done

**Deviation from the signed-off table - `epubLineHeight` snapped to the slider step.** The reader
dialog slider (`dialog_epub_reader_settings.xml`) is `valueFrom=1.0 valueTo=3.0 stepSize=0.2`, and
Material `Slider.setValue` rejects an off-step value outright, so an off-step preset would make the
reader settings dialog fail to open. Research §7.1 values `1.5`, `1.7` and `1.9` are off-step and were
snapped while preserving the ordering the owner signed off: `home_tablet` 1.7 -> 1.8,
`tv_media_box` / `car_head_unit` 1.9 -> 2.0, `ebook_reader` 1.5 -> 1.4 (still tighter than the 1.6
default, which is the intent). `epubHorizontalMargin` values were already on the 4 px step.
The step rule is now enforced mechanically, see step 03.6.

---

### Step 03.4 - Fill the link-download and player-interaction blocks

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 03.3

**Prompt for developer:**

> Apply the signed-off values from research artifact §7.2 and §7.3. Key intents: `linkDownloadAudioOnly` is `TRUE` only on `audio_player`; `linkDownloadMaxResolution` scales with the screen and the connection (`480p` on `car_head_unit` and `audio_player`, `best` on the TV and video profiles); `linkAutoDownloadEnabled` is `FALSE` where the device is metered or single-purpose; `nineZoneGridEnabled` is `FALSE` on the remote-driven and driving profiles so the player falls back to the simpler 3-zone tap layout; `playerFollowSystemRotation` is `TRUE` only on the hand-held profiles.

**Verification:**

- Value equality - the `linkDownloadAudioOnly` row has `TRUE` in exactly one cell, `audio_player`.
- Value equality - the `nineZoneGridEnabled` row's `car_head_unit` cell equals `FALSE`.
- Value equality - every non-empty `linkDownloadMaxResolution` cell is one of `480p`, `720p`, `1080p`, `best`.

**Status:** `[x]` done

---

### Step 03.5 - Fill the streams and launcher blocks

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 03.4

**Prompt for developer:**

> Apply the signed-off values from research artifact §7.4 and §7.5. Leave the stream rows empty for `photo_frame` and `ebook_reader` - `enableStreams` is already `FALSE` there and a filter value for a hidden feature is noise. Launcher rows are filled for every profile that benefits regardless of flavor: Phase 02 makes a build without the home surface ignore them at apply time.

**Verification:**

- Value equality - the `streamsDefaultMediaFilter` row's `audio_player` cell equals `AUDIO` and its `photo_frame` cell is empty.
- Value equality - the `launcherDesktopLocked` row has `TRUE` in the `photo_frame` and `car_head_unit` cells.
- Value equality - every non-empty `streamsCatalogRefreshPolicy` cell is one of `MANUAL`, `ON_OPEN`, `PERIODIC_WIFI`.

**Status:** `[x]` done

**Deviation from the signed-off table - `launcherDensityFactor` snapped to the selectable set.**
`AppSettings.LAUNCHER_DENSITY_OPTIONS` is `0.75 / 1.0 / 1.25 / 1.5` and
`LauncherSettingsDialogFragment` resolves the stored factor with `indexOf`, so an off-list value
leaves the density row showing nothing. Research §7.5 values `1.3` (`tv_media_box`) and `1.2`
(`car_head_unit`, `photo_frame`, `vr_headset`) are off-list and were snapped to the nearest
selectable `1.25`. `home_tablet` keeps the signed-off `1.0`.

Stream rows for `photo_frame` and `ebook_reader` stay empty in all four stream rows, including
`streamsCatalogRefreshPolicy`, per this step's prompt and the research §7.4 note - `enableStreams`
is already `FALSE` on both profiles.

---

### Step 03.6 - Make both value contracts mechanical

**Files:** `scripts/check_device_profile_presets.ps1`
**Depends on:** Step 03.5

Added while filling the matrix: the two deviations above were caught by reading the UI, not by the
gate, which is exactly the failure mode the ticket exists to end (strategic ADR-2). The checker's
`$allowedValues` / `$valueRules` tables are data-driven by design, so each contract is one entry.

**Prompt for developer:**

> Add value-sanity entries for the fields this phase filled: the three stream enums, the two stream
> track languages, `launcherWallpaperMode`, `launcherDensityFactor` (the selectable set), plus step
> rules for `epubLineHeight` (1.0..3.0 on 0.2) and `epubHorizontalMargin` (0..48 on 4).

**Verification:**

- Value equality - `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0 on the filled matrix.
- Value equality - with `epubLineHeight/tv_media_box` set to `1.9` and `launcherDensityFactor/tv_media_box` to `1.3`, the checker exits 1 and names both field/profile pairs. Ran 2026-07-30: `NEGATIVE_EXIT=1`, both reported; restored matrix exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.
- [x] Strategic §6.3 held: `smbEnabled`, `sftpEnabled`, `ftpEnabled`, `googleDriveEnabled`, `oneDriveEnabled` and `dropboxEnabled` each still have zero non-empty cells.
- [x] The `Other` column still has zero non-empty cells.
- [x] Project compiles - run `/build` (do not invoke gradle directly). The CSV is an asset, so a packaging build is the meaningful proof.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API unchanged - no catalog regeneration needed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The matrix is now consistent, so the gate can be wired without turning every subsequent build red. The populated-row count roughly doubles, which is what makes the Phase 05 number worth showing.

---

## Rollback Plan

Revert phase commit(s) - the CSV is a bundled asset with no persisted state. A user who already applied a profile keeps their settings; only a future apply would differ.

