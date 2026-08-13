# Phase 02 - Apply Slider Style to the Dialog

**Strategic spec:** [`../S0619_video-control-wide-sliders.md`](../S0619_video-control-wide-sliders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Apply `@style/Widget.FMS.PlaybackSlider` to all four sliders (volume, hue, brightness, speed) of the playback-control dialog in both the portrait and landscape layouts, so every slider becomes wide with a large thumb in both orientations.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (style + drawables + dimens exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | ≤ 560 |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Modified | ≤ 580 |

> Landscape parity: both portrait and landscape variants are edited in this phase (Steps 02.1 and 02.2). IDs are identical across both, so the same style reference applies.

---

## Steps

### Step 02.1 - Apply the style in the portrait layout

**Files:** `app_v2/src/main/res/layout/dialog_playback_control.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `style="@style/Widget.FMS.PlaybackSlider"` to each of the four `com.sza.fastmediasorter.ui.player.VerticalSeekBar` views: `@id/seekVolume`, `@id/seekHue`, `@id/seekBrightness`, `@id/seekSpeed`. Keep every existing attribute on those views unchanged - especially `android:focusable`, `android:focusableInTouchMode`, the `android:max` values, and the portrait `android:layout_height="200dp"` (track length). Do not alter any other element in the file.

**Verification:**

- `Grep` - `@style/Widget.FMS.PlaybackSlider` matches exactly 4 times in `layout/dialog_playback_control.xml`.
- `Grep` - `seekVolume`, `seekHue`, `seekBrightness`, `seekSpeed` ids all still present.
- `Grep` - `android:max="360"` (hue) and `android:max="100"` (brightness) still present (existing attrs untouched).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: layout/dialog_playback_control.xml (style on 4 sliders). Dev log recorded.

---

### Step 02.2 - Apply the style in the landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_playback_control.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `style="@style/Widget.FMS.PlaybackSlider"` to the same four `VerticalSeekBar` views (`seekVolume`, `seekHue`, `seekBrightness`, `seekSpeed`) in the landscape variant. Keep the landscape `android:layout_height="wrap_content"` and all focus/max attributes unchanged. Confirm the slider IDs match the portrait layout one-to-one so the shared ViewBinding stays valid.

**Verification:**

- `Grep` - `@style/Widget.FMS.PlaybackSlider` matches exactly 4 times in `layout-land/dialog_playback_control.xml`.
- `Grep` - the four slider ids are present and unchanged.
- `Grep -n "#"` against both edited layouts returns zero new hardcoded colour literals introduced by this phase.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: layout-land/dialog_playback_control.xml (style on 4 sliders). IDs match portrait one-to-one. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL (resources + Kotlin + S0619 tag).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the layout change via `.\scripts\add_to_dev_log.ps1`.
- [ ] On-device check (recorded for `BlockNeedUserTest`): in portrait and landscape, each slider shows a thick track and a large thumb; the thumb is grabbable and drag tracks the finger without offset (resolves strategic §6.1 and §6.2). - PENDING device test.

---

## Handoff Notes to Next Phase

- All user-visible changes are now in place. Phase 03 records the dev log / catalog and runs quality gates.
- The temporary `Timber.d("S0619: ..")` verification tag is added at `PlaybackControlDialogFragment.onViewCreated` only at the final transition into `BlockNeedUserTest` (handled by `/spec-dev`), never in an intermediate phase.

---

## Rollback Plan

Revert phase commit(s) - removing the `style=` attributes restores the stock SeekBar appearance. No data migration or persisted state involved.
