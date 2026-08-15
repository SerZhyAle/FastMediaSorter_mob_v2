# Phase 01 - Flat media fragments (images, video, audio)

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase (establishes the column convention reused by later phases)
**Blocks:** Phase 02, 03, 04, 05 (shared convention reference)
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Complete landscape 2-column grouping of remaining solo COMPACT toggle rows in the three flat (card-less) media fragments, using the canonical weighted horizontal LinearLayout. Landscape-only; portrait untouched.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Read `research/02__column-count-rule.md` and `research/04__canonical-mechanism.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_images.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_settings_video.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 200 |

> All three landscape files are < 500 LOC - no backup step required. Portrait counterparts are intentionally NOT edited (portrait single-column is a non-goal per strategic §2).

---

## Steps

### Step 01.1 - Pair remaining solo toggles in images landscape

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_images.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the still-solo COMPACT toggles `rowDynamicBackground` and `rowSlideshowBackgroundMusic` into a single weighted horizontal LinearLayout (outer `orientation="horizontal"`, `baselineAligned="false"`; two inner wrapper LinearLayouts each `layout_width="0dp" layout_weight="1"`, left wrapper `layout_marginEnd="@dimen/dialog_field_spacing"`, right wrapper `layout_marginStart="@dimen/dialog_field_spacing"`; each toggle `layout_width="match_parent"`). If the two toggles are conditionally hidden independently, keep each in its own wrapper so a `gone` sibling leaves a clean empty column. Add `nextFocusRight`/`nextFocusLeft` between the two toggles (research 06). Do not touch portrait. Do not introduce a third nested weight level.

**Verification:**

- `Grep` - `rowDynamicBackground` and `rowSlideshowBackgroundMusic` both still present in the file.
- `Grep` - a new `orientation="horizontal"` LinearLayout with `baselineAligned="false"` wrapping them exists (`layout_weight="1"` appears at least twice in their vicinity).
- `Grep` - `nextFocusRight` present at least once in the file.
- `Grep -n "#"` in the touched block returns no hardcoded hex colors (Rule 19).

**Status:** `[ ]` not done

---

### Step 01.2 - Pair remaining solo toggles in video landscape

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_video.xml`
**Depends on:** Step 01.1 (same pattern)

**Prompt for developer:**

> Group the still-solo COMPACT toggles `rowVideoFrameCopyToClipboard` and `rowPlayerShowFps` into one weighted horizontal LinearLayout (same shape as 01.1). Add `nextFocusRight`/`nextFocusLeft` between them. Leave `tvSelectedSnapshotResource` and the snapshot row as-is (its ellipsize defect is tracked separately as S0617 - out of scope here). Do not touch portrait.

**Verification:**

- `Grep` - `rowVideoFrameCopyToClipboard` and `rowPlayerShowFps` both present.
- `Grep` - `layout_weight="1"` appears in their wrapping block.
- `Grep` - `nextFocusRight` present.

**Status:** `[ ]` not done

---

### Step 01.3 - Pair remaining solo toggles in audio landscape

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_audio.xml`
**Depends on:** Step 01.1 (same pattern)

**Prompt for developer:**

> Identify the still-solo COMPACT toggles in audio landscape (e.g. `rowSearchAudioCoversOnline`, `rowEnablePhotosDuringAudio`) and pair adjacent compatible ones into a weighted horizontal LinearLayout (same shape as 01.1). Only pair toggles that are logically related and not conditionally `gone` in a way that would leave a lopsided row; if a toggle has no safe partner, leave it solo. Add `nextFocusRight`/`nextFocusLeft` between paired toggles. Keep `layoutAudioSizeInputs` (TextInput pair) and `actvAudioEmptyStateMode` (dropdown) single/full-width per the WIDE rule. Do not touch portrait.

**Verification:**

- `Grep` - at least one new `orientation="horizontal"` LinearLayout with two `layout_weight="1"` children added.
- `Grep` - `nextFocusRight` present.
- `Grep` - `actvAudioEmptyStateMode` not placed inside a weighted column (stays WIDE).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (resource/manifest check sufficient: `.\a.ps1 fr`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] No portrait `layout/fragment_settings_{images,video,audio}.xml` file was modified.
- [ ] Dev log entry added for the three modified files.

---

## Handoff Notes to Next Phase

The canonical pairing shape (outer horizontal LinearLayout + two `0dp`/`weight=1` wrappers + `dialog_field_spacing` margins + `baselineAligned="false"` + `nextFocusLeft/Right`) is now demonstrated in the flat fragments. Phases 02-05 reuse this exact shape. Keep nesting at one weight level.

---

## Rollback Plan

Revert the phase commit(s) - landscape-only XML, no data migration or code change.
