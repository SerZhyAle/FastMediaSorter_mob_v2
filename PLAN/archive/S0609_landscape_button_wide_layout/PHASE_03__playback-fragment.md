# Phase 03 - Playback fragment landscape completion

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (shared column convention)
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Densify the Player UI card in playback landscape (pair the five solo COMPACT toggles) and lay the Background-Audio exit-behavior options horizontally. Landscape-only.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Read `research/01__settings-fragment-element-inventory.md` (playback gaps).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 550 |

> File is ~453 LOC (< 500) - no backup step needed at start, but re-check after edits; if it crosses 500, add a backup before further edits. Portrait `layout/fragment_settings_playback.xml` is NOT edited.

---

## Steps

### Step 03.1 - Pair Player UI solo toggles

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the Player UI card, group these still-solo COMPACT toggles into weighted horizontal 2-column rows (Phase 01 shape): `rowShowBlackScreenButton` + `rowEnablePip`; `rowPanelStereoSingleEye` + `rowBigButtonsMode`; leave `rowSmallControls` solo if no clean partner remains, or pair it with `rowBigButtonsMode` and move stereo elsewhere - choose the grouping that keeps related options together. Add `nextFocusRight`/`nextFocusLeft` on each new pair. Keep `rowEnablePip` behavior id `layoutPip` references intact if present.

**Verification:**

- `Grep` - `rowShowBlackScreenButton`, `rowEnablePip`, `rowPanelStereoSingleEye`, `rowBigButtonsMode` all still present.
- `Grep` - at least two new `layout_weight="1"` pairings added in the Player UI region.
- `Grep` - `nextFocusRight` present.

**Status:** `[ ]` not done

---

### Step 03.2 - Lay exit-behavior radio options horizontally

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the Background Audio card, change `radioGroupExitBehavior` to `android:orientation="horizontal"` in landscape so its (up to 3) `MaterialRadioButton` options sit in one row instead of a tall vertical stack. Verify the radio labels do not clip - if a long localized label (`background_audio_exit_behavior_*`) forces clipping, keep each radio button `wrap_content` and allow the row to wrap, or fall back to vertical for that one group. Keep the section's long summary `TextView` full-width (do not column-split explanatory text per research 03).

**Verification:**

- `Grep` - `radioGroupExitBehavior` followed by `orientation="horizontal"` in the landscape file.
- `Grep` - the three `radioExitBehavior*` ids still present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `.\a.ps1 fr` passes.
- [ ] `spinnerSortMode` (dropdown) and `etSlideshowInterval` (input) stay full-width (WIDE rule).
- [ ] `Grep -n "=\"#"` returns zero hardcoded hex colors; file < 1500 LOC; portrait unchanged.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the modified file.

---

## Handoff Notes to Next Phase

Playback landscape densified. Radio groups can go horizontal when labels are short - same judgement applies elsewhere.

---

## Rollback Plan

Revert the phase commit - landscape-only XML.
