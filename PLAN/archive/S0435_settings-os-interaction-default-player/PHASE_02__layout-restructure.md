# Phase 02 - Layout restructure

**Strategic spec:** [`../S0435_settings-os-interaction-default-player.md`](../S0435_settings-os-interaction-default-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Restructure the renamed "Operating system interaction" group (`groupSystemApps`) to hold, in order, the moved rotate checkbox (no header), a new default-player subgroup with four wrap-content buttons + hint, the moved incoming-links subgroup, and the existing screen-gestures subgroup - mirrored in portrait and landscape with identical view ids.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (all referenced strings exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 880 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 930 |

> **Landscape parity (MANDATORY).** Both files declare the SAME `FragmentSettingsPlaybackBinding`; any new id present in only one orientation becomes a nullable binding field. Every id added below MUST exist in both files so Phase 03 can reference non-null fields.
> No hardcoded `="#hex"` colors - use `?attr/` / `@color/` (existing rows already do).

---

## Target structure inside `containerSystemApps` (both orientations)

Order top to bottom:

1. `layoutFollowSystemRotation` (moved here) - rotate checkbox, no subgroup header.
2. `layoutDefaultPlayerSubgroup` (new) - caps title `@string/setting_subgroup_default_player_title`, hint `tvDefaultPlayerSettingsHint`, four buttons.
3. `layoutIncomingLinksSubgroup` (new wrapper around the moved incoming-links rows) - caps title `@string/settings_subcategory_incoming_links`.
4. `layoutScreenGesturesSubgroup` (new wrapper around the existing gesture rows) - caps title `@string/setting_subgroup_screen_gestures_title`.

New button ids (identical in both files): `btnSettingsDefaultPlayerImages`, `btnSettingsDefaultPlayerAudio`, `btnSettingsDefaultPlayerVideo`, `btnSettingsDefaultPlayerDocs`.

---

## Steps

### Step 02.1 - Portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Restructure the `groupSystemApps` card so `containerSystemApps` holds the four blocks in the order above.
> - Move the `layoutFollowSystemRotation` block (with its `rowFollowSystemRotation` child) out of `containerPlayerUI` to the top of `containerSystemApps`; drop its leftover surrounding wrapper in Player UI. Keep ids unchanged.
> - Add `layoutDefaultPlayerSubgroup`: a vertical `LinearLayout` with a caps `TextView` (`textAllCaps="true"`, `?attr/colorPrimary`, style matching the existing screen-gestures caps header) bound to `@string/setting_subgroup_default_player_title`; a `tvDefaultPlayerSettingsHint` `TextView` (text set in code from `welcome_default_player_hint`); then the four `MaterialButton`s with `style="@style/Widget.FastMediaSorter.SettingsButton.Outlined"`, `android:layout_width="wrap_content"`, `android:textAllCaps="false"`, stacked vertically and left-aligned, texts `@string/settings_default_player_btn_images` / `_audio` / `_video` / `_docs`.
> - Move the incoming-links rows (`settings_subcategory_incoming_links` caps header, `rowLinkAutodownloadEnabled`, `row_link_autodownload_resource` + `tv_link_autodownload_resource_value`, `rowLinkAutodownloadOpenInPlayer`) out of `containerBehaviour` into a new `layoutIncomingLinksSubgroup` wrapper inside `containerSystemApps`. Keep ids unchanged.
> - Wrap the existing gesture rows (`setting_subgroup_screen_gestures_title` caps header, `rowGestureOverlayEnabled`, `rowScreenshotGestureDown`, `rowScreenshotDestination`) in a new `layoutScreenGesturesSubgroup` wrapper, kept last. Keep ids unchanged.
> - Do not change the `headerSystemApps` `csh_title` ref - the renamed value comes from Phase 01.

**Verification:**

- `Grep` - each of `layoutDefaultPlayerSubgroup`, `tvDefaultPlayerSettingsHint`, `layoutIncomingLinksSubgroup`, `layoutScreenGesturesSubgroup`, `btnSettingsDefaultPlayerImages`, `btnSettingsDefaultPlayerAudio`, `btnSettingsDefaultPlayerVideo`, `btnSettingsDefaultPlayerDocs` matches once in `layout/fragment_settings_playback.xml`.
- `Grep` - `layoutFollowSystemRotation` no longer appears inside `containerPlayerUI` region (moved); appears inside `containerSystemApps` region.
- `Grep` - `rowLinkAutodownloadEnabled` appears inside `containerSystemApps` region, not `containerBehaviour`.
- `Grep` - `android:layout_width="wrap_content"` present on all four `btnSettingsDefaultPlayer*` declarations.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. 8 new ids x1; 4 buttons wrap_content; layoutFollowSystemRotation (L656) + rowLinkAutodownloadEnabled (L692) now inside containerSystemApps (L653); gestures wrapped in layoutScreenGesturesSubgroup (L722).

---

### Step 02.2 - Landscape layout (two-column buttons)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror Step 02.1 in the landscape file with the SAME ids. Landscape differences:
> - The rotate checkbox is currently paired with fullscreen in `containerFullscreenAndRotation`; remove `layoutFollowSystemRotation` from that row, leave `rowHideSystemUiInFullscreen` full-width there, and place `layoutFollowSystemRotation` at the top of `containerSystemApps`.
> - In `layoutDefaultPlayerSubgroup`, arrange the four buttons in two columns (two horizontal rows of two `wrap_content` buttons, start-aligned, or a `GridLayout` with `columnCount="2"`); keep the same four ids and `wrap_content` width.
> - Move the incoming-links rows from `containerBehaviour` into `layoutIncomingLinksSubgroup` inside `containerSystemApps`, preserving the existing landscape two-column resource/open-in-player row.
> - Wrap the gesture rows in `layoutScreenGesturesSubgroup`, kept last.

**Verification:**

- `Grep` - each of `layoutDefaultPlayerSubgroup`, `tvDefaultPlayerSettingsHint`, `layoutIncomingLinksSubgroup`, `layoutScreenGesturesSubgroup`, `btnSettingsDefaultPlayerImages`, `btnSettingsDefaultPlayerAudio`, `btnSettingsDefaultPlayerVideo`, `btnSettingsDefaultPlayerDocs` matches once in `layout-land/fragment_settings_playback.xml`.
- `Grep` - `rowFollowSystemRotation` appears inside `containerSystemApps` region in the land file.
- `Grep` - `rowLinkAutodownloadEnabled` appears inside `containerSystemApps` region in the land file.
- ID parity: the eight ids above each appear in BOTH layout files (re-run the Step 02.1 greps).

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. 8 ids x1 in land; layoutFollowSystemRotation (L683) + rowLinkAutodownloadEnabled (L727) inside containerSystemApps (L680); containerFullscreenAndRotation removed; buttons in 2-column rows. ID parity portrait/land confirmed.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (ViewBinding regenerates; new fields non-null because ids exist in both orientations).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both layout files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`FragmentSettingsPlaybackBinding` now exposes non-null fields for the four buttons, `tvDefaultPlayerSettingsHint`, and the three subgroup wrappers. The whole gesture block is isolated under `layoutScreenGesturesSubgroup` so Phase 03 can gate the subgroup (not the whole card). Moved rows (`rowFollowSystemRotation`, incoming-links rows) keep their ids, so existing fragment wiring stays valid.

---

## Rollback Plan

Revert phase commit(s) - layout-only change; no data migration. Binding regenerates from reverted XML.
