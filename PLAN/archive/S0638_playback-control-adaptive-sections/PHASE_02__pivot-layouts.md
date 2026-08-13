# Phase 02 - Pivot both layouts

**Strategic spec:** [`../S0638_playback-control-adaptive-sections.md`](../S0638_playback-control-adaptive-sections.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Restructure both dialog layouts to the adaptive pivot: landscape = horizontal section strip on top + content below; portrait = vertical section rail on the left + content on the right. Wrap the scrollable region in `MaxHeightLinearLayout` (id `playbackResizableArea`) in both. Every existing view id is preserved so the generated binding and fragment code keep working.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`MaxHeightLinearLayout` exists).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Modified | ≤ 600 |

> Both files exceed 500 LOC - Step 02.1 backs them up before edits. Landscape counterpart edited in the same phase (Step 02.3) - portrait-only edit forbidden.

---

## Steps

### Step 02.1 - Backup both layouts

**Files:** `temp/` (backup copies)
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/res/layout/dialog_playback_control.xml` and `app_v2/src/main/res/layout-land/dialog_playback_control.xml` to `temp/` with a timestamp suffix (both files are >500 LOC). These are restore points for Step 02.2/02.3.

**Verification:**

- `Glob` - at least one `temp/dialog_playback_control*portrait*.xml` (or timestamped copy of each) exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Backed up both layouts to `temp/dialog_playback_control_portrait_*.xml` and `temp/dialog_playback_control_land_*.xml`.

---

### Step 02.2 - Portrait: vertical rail + content master-detail

**Files:** `app_v2/src/main/res/layout/dialog_playback_control.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Rewrite the portrait layout to a master-detail pivot. Keep the root vertical `LinearLayout` and the existing title row (`tvPlaybackControlTitle` + `btnClosePlaybackControl`) unchanged at the top. Replace the current `HorizontalScrollView` + below-it `ScrollView` block with a single `MaxHeightLinearLayout` (`com.sza.fastmediasorter.ui.common.widget.MaxHeightLinearLayout`, id `@+id/playbackResizableArea`, `android:orientation="horizontal"`, width `match_parent`, height `wrap_content`) holding two columns:
> - LEFT - the section rail: a vertical `ScrollView` (width `wrap_content`, height `match_parent`, `android:requiresFadingEdge="vertical"`, `android:fadingEdgeLength="24dp"`, `android:scrollbars="none"`) wrapping the existing `MaterialButtonToggleGroup` `@id/groupPlaybackSections` set to `android:orientation="vertical"` with a fixed `android:layout_width="96dp"`. Keep all seven section buttons with their existing ids/icons/strings but switch each to `app:iconGravity="top"`, `android:textSize="11sp"`, `android:maxLines="1"`, `android:ellipsize="end"`, `android:minHeight="56dp"`, `android:layout_width="match_parent"`, and keep `focusable`/`focusableInTouchMode="false"`.
> - a thin 1dp vertical divider (`?attr/colorOutlineVariant`) with horizontal margin.
> - RIGHT - the content: a `ScrollView` (width `0dp`, `layout_weight="1"`, height `match_parent`, `fillViewport="true"`) wrapping the existing content `LinearLayout` with ALL seven section blocks (`sectionVolume`, `sectionAudio`, `sectionSubtitles`, `sectionStereo3d`, `sectionHue`, `sectionBrightness`, `sectionSpeed`) and every child id moved verbatim - do not rename, delete, or alter the inner controls (sliders stay `VerticalSeekBar` with the same style; the 3D two-column block stays as-is).
> Do not introduce hardcoded hex colors - reuse `?attr/`/existing styles. No new strings.

**Verification:**

- `Grep` - `com.sza.fastmediasorter.ui.common.widget.MaxHeightLinearLayout` present.
- `Grep` - `@+id/playbackResizableArea` present.
- `Grep` - `android:id="@+id/groupPlaybackSections"` present and within the same file `android:orientation="vertical"` appears on the toggle group.
- `Grep` - `app:iconGravity="top"` present (rail buttons restyled).
- `Grep` - `requiresFadingEdge` present.
- `Grep` - all seven section ids (`sectionVolume`, `sectionAudio`, `sectionSubtitles`, `sectionStereo3d`, `sectionHue`, `sectionBrightness`, `sectionSpeed`) still present.
- `Grep` - no `="#` hex color literal introduced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Portrait rewritten to master-detail (vertical rail left + content right) in `playbackResizableArea`; rail buttons `iconGravity=top`; vertical fading edge. Verification 7/7 PASS.

---

### Step 02.3 - Landscape: horizontal strip on top + content below

**Files:** `app_v2/src/main/res/layout-land/dialog_playback_control.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Rewrite the landscape layout to a strip-on-top pivot. Keep the root vertical `LinearLayout` and the existing title row unchanged at the top. Replace the current `[vertical rail + divider + content ScrollView]` horizontal block with, in vertical order:
> - the section strip: a `HorizontalScrollView` (width `match_parent`, height `wrap_content`, `android:requiresFadingEdge="horizontal"`, `android:fadingEdgeLength="24dp"`, `android:scrollbars="none"`) wrapping the existing `MaterialButtonToggleGroup` `@id/groupPlaybackSections` set to `android:orientation="horizontal"`, width `wrap_content`. Keep all seven section buttons with existing ids/icons/strings, `app:iconGravity="textStart"`, `android:paddingHorizontal="12dp"`, `android:minHeight="48dp"`, `textAllCaps="false"`, focus flags preserved.
> - a 1dp horizontal divider (`?attr/colorOutlineVariant`) with vertical margin.
> - the content: a `MaxHeightLinearLayout` (id `@+id/playbackResizableArea`, `android:orientation="vertical"`, width `match_parent`, height `wrap_content`) containing a single `ScrollView` (width `match_parent`, height `match_parent`, `fillViewport="true"`) wrapping the existing content `LinearLayout` with ALL seven section blocks and every inner id verbatim. The 3D block keeps its compact two-column landscape form; sliders stay `VerticalSeekBar` (render horizontally in landscape). No id renames, no control deletions, no new strings, no hardcoded hex colors.

**Verification:**

- `Grep` - `com.sza.fastmediasorter.ui.common.widget.MaxHeightLinearLayout` present.
- `Grep` - `@+id/playbackResizableArea` present.
- `Grep` - `HorizontalScrollView` present and `requiresFadingEdge="horizontal"` present.
- `Grep` - `android:id="@+id/groupPlaybackSections"` present and `android:orientation="horizontal"` on the toggle group.
- `Grep` - all seven section ids still present.
- `Grep` - no `="#` hex color literal introduced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Landscape rewritten to strip-on-top (horizontal `HorizontalScrollView` + horizontal fading edge) + content in `playbackResizableArea`. Verification 6/6 PASS. `.\a.ps1 fc` BUILD SUCCESSFUL (binding regenerated, portrait/landscape id parity holds).

---

## Phase Done Criteria

- [ ] Steps 02.1-02.3 are `[x] done`.
- [ ] Project compiles - run `/build` (binding regenerates with the new `playbackResizableArea` field typed `MaxHeightLinearLayout`; no missing-id errors).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Both layouts expose identical view ids (binding parity) - spot-check `groupPlaybackSections`, `playbackResizableArea`, and the seven `section*` ids in each file.
- [ ] Dev log entry added for both layout files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Both layouts now expose `playbackResizableArea` (`MaxHeightLinearLayout`) as the height-bounded scroll region: in portrait it holds rail + content (both scroll), in landscape it holds the content scroller (the strip scrolls horizontally above it). Phase 03 sets `playbackResizableArea.maxHeightPx` from the fragment so the dialog never exceeds the screen.

---

## Rollback Plan

Restore both layout files from the Step 02.1 `temp/` backups. No data or persisted state involved.
