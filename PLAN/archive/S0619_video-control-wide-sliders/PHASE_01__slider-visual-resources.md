# Phase 01 - Slider Visual Resources

**Strategic spec:** [`../S0619_video-control-wide-sliders.md`](../S0619_video-control-wide-sliders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Create the visual resources for a wide, finger-friendly playback slider - dimensions, a thick rounded track drawable, a large oval thumb drawable, and a single named SeekBar style binding them - without touching any layout or screen yet.

---

## Prerequisites

- [ ] Strategic §6 items are non-blocking (see INDEX Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 745 |
| `app_v2/src/main/res/drawable/seekbar_playback_thumb.xml` | New | ≤ 25 |
| `app_v2/src/main/res/drawable/seekbar_playback_track.xml` | New | ≤ 45 |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ 425 |

> `dimens.xml` is 734 LOC (>500) - back it up to `temp/` before editing (Step 01.1). `themes.xml` is 408 LOC - no backup needed.

---

## Steps

### Step 01.1 - Add playback-slider dimensions

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `app_v2/src/main/res/values/dimens.xml` to `temp/dimens_S0619_<timestamp>.xml` first (file is >500 LOC). Then append three dimension resources for the playback slider: `playback_slider_track_height` = `12dp` (thick track), `playback_slider_thumb_size` = `28dp` (large visible thumb), `playback_slider_min_height` = `48dp` (minimum touch target). Group them under an XML comment `<!-- Playback control sliders (S0619) -->`.

**Verification:**

- `Grep` - `playback_slider_track_height` matches once in `dimens.xml`.
- `Grep` - `playback_slider_thumb_size` matches once in `dimens.xml`.
- `Grep` - `playback_slider_min_height` matches once in `dimens.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: dimens.xml (+5 LOC, backup in temp/). Dev log recorded.

---

### Step 01.2 - Create the large oval thumb drawable

**Files:** `app_v2/src/main/res/drawable/seekbar_playback_thumb.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new oval `<shape>` drawable sized `@dimen/playback_slider_thumb_size` square. Fill with the theme accent via `android:color="?attr/colorPrimary"` (never a hardcoded hex). Add a thin contrasting `<stroke>` using `?attr/colorSurface` so the thumb stays distinct over both track halves - large size plus shape outline gives non-colour-only distinguishability. The shape must be rotation-invariant (a circle) so it renders correctly through the widget's rotated canvas in portrait.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/seekbar_playback_thumb.xml` exists.
- `Grep` - `android:shape="oval"` matches once in the file.
- `Grep` - `?attr/colorPrimary` present; `Grep -n "#"` returns zero hits (no hardcoded colours).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: drawable/seekbar_playback_thumb.xml (New, 11 LOC). Dev log recorded.

---

### Step 01.3 - Create the thick rounded track drawable

**Files:** `app_v2/src/main/res/drawable/seekbar_playback_track.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new `<layer-list>` progress drawable with three layers: `@android:id/background` (rounded-corner rect filled `?attr/colorSurfaceVariant`), `@android:id/secondaryProgress`, and `@android:id/progress` (both clip layers filled `?attr/colorPrimary`, rounded corners). Use rounded corners on every layer so the thick bar reads as a pill. Do not hardcode any hex colour or fixed track height here - thickness comes from the style's `android:maxHeight` in Step 01.4.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/seekbar_playback_track.xml` exists.
- `Grep` - `@android:id/progress` and `@android:id/background` both present.
- `Grep -n "#"` returns zero hits in the file (theme attrs only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: drawable/seekbar_playback_track.xml (New, 30 LOC). Dev log recorded.

---

### Step 01.4 - Define the named playback-slider style

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Add a style `Widget.FMS.PlaybackSlider` with parent `Widget.AppCompat.SeekBar`. Set `android:progressDrawable` = `@drawable/seekbar_playback_track`, `android:thumb` = `@drawable/seekbar_playback_thumb`, `android:maxHeight` = `@dimen/playback_slider_track_height`, `android:minHeight` = `@dimen/playback_slider_min_height`, `android:splitTrack` = `false`, and vertical padding sufficient to keep the large thumb from being clipped. Do NOT set this as the app-wide `seekBarStyle` in any theme - it must apply only where referenced, so other screens' SeekBars are unaffected (strategic non-goal).

**Verification:**

- `Grep` - `name="Widget.FMS.PlaybackSlider"` matches once in `themes.xml`.
- `Grep` - `@drawable/seekbar_playback_track` and `@drawable/seekbar_playback_thumb` both referenced inside that style.
- `Grep` - `seekBarStyle` does NOT appear in any `<style name="Theme.` parent theme (style stays opt-in).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: themes.xml (+11 LOC). `seekBarStyle` appears only in the explanatory comment, not as a theme item. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - validated by the single `.\a.ps1 fc` run after Phase 02 (resources + Kotlin + tag), BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the resource change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Phase 02 references `@style/Widget.FMS.PlaybackSlider` on the four `VerticalSeekBar` views in both orientation layouts.
- The thumb is a circle and the track is symmetric, so both render correctly through the portrait rotated canvas (resolves strategic §6.1 at the resource level; final confirmation is the device test).

---

## Rollback Plan

Revert phase commit(s) - new drawables and additive dimens/style entries; no data migration or user-facing surface changed until Phase 02 wires them in.
