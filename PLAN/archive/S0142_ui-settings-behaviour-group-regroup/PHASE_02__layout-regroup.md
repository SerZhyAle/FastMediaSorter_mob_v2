# Phase 02 — Layout Regroup (portrait + landscape)

**Strategic spec:** [`../S0142_ui-settings-behaviour-group-regroup.md`](../S0142_ui-settings-behaviour-group-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Restructure the `containerBehaviour` block in both portrait and landscape `fragment_settings_playback.xml`: introduce two divider-delimited sub-sections (`INCOMING LINKS`, `CAMERA CAPTURE`) with bold caps sub-headers, move `row_saved_authorizations` into the `INCOMING LINKS` block, and give that row a leading help icon, a trailing chevron, and a selectable background. No Kotlin changes in this phase.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — new string keys exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 600 |

> Landscape variant exists — both files MUST receive equivalent changes in this phase.

---

## Reference patterns (already in the codebase)

- Divider: `<View android:layout_width="match_parent" android:layout_height="@dimen/settings_divider_height" android:layout_marginTop="@dimen/margin_small" android:layout_marginBottom="@dimen/margin_small" android:background="?attr/colorOutlineVariant" />` (see `fragment_settings_general.xml`).
- Bold caps sub-header: `<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/..." android:textSize="@dimen/text_size_small" android:textStyle="bold" android:textAllCaps="true" android:textColor="?attr/colorPrimary" android:layout_marginBottom="@dimen/margin_small" />` (mirrors `touch_zones_legend_title`).
- Sub-screen nav row: `rowControlsKeybindings` at the bottom of `fragment_settings_playback.xml` — `android:background="?attr/selectableItemBackground"`, trailing `ImageView` with `@drawable/ic_chevron_right`.
- Help icon: existing `iconHelpCameraCapture` — `ImageButton`, `@drawable/ic_help_outline_24`, `?attr/selectableItemBackgroundBorderless`, `app:tint="@color/text_color_secondary"`, `android:contentDescription` set.

---

## Steps

### Step 02.1 — Portrait: wrap the link auto-download items into the `INCOMING LINKS` sub-section

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml` (landscape counterpart `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` updated in Step 02.4 — same phase)
**Depends on:** — start of phase

**Prompt for developer:**

> Inside `containerBehaviour`, immediately before the `switch_link_autodownload_enabled` row, insert a divider `View` then a bold-caps sub-header `TextView` with `android:text="@string/settings_subcategory_incoming_links"`. Keep the existing three rows in order: master switch (`switch_link_autodownload_enabled`), destination resource row (`row_link_autodownload_resource`), open-in-player switch (`switch_link_autodownload_open_in_player`). Then move the `row_saved_authorizations` LinearLayout so it sits as the last item of this sub-section (directly after `switch_link_autodownload_open_in_player`'s row, before the next divider). After the saved-authorizations row, insert a closing divider `View` so the block is visually bounded from `switchResumeOnNextLaunch` below it. Do not change the IDs or the children of the moved rows except as specified in Step 02.3.

**Verification:**

- `Grep -n "settings_subcategory_incoming_links"` — exactly 1 hit in `layout/fragment_settings_playback.xml`.
- `Grep -n "row_saved_authorizations"` — exactly 1 hit; appears in source order *after* `switch_link_autodownload_open_in_player` and *before* `switchResumeOnNextLaunch`.
- `Grep -n "colorOutlineVariant"` — at least 2 hits inside the Behaviour card region (opening divider before `INCOMING LINKS`, closing divider after `row_saved_authorizations`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. `layout/fragment_settings_playback.xml`: divider+caps header inserted before `switch_link_autodownload_enabled` (lines 340/342); `row_saved_authorizations` already followed `switch_link_autodownload_open_in_player` (kept in place); closing divider added after it (line 391). Dev log recorded in phase wrap-up.

---

### Step 02.2 — Portrait: wrap the camera-capture items into the `CAMERA CAPTURE` sub-section

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml` (landscape counterpart updated in Step 02.4 — same phase)
**Depends on:** Step 02.1

**Prompt for developer:**

> Immediately before `layoutCameraCapture`, insert a divider `View` then a bold-caps sub-header `TextView` with `android:text="@string/settings_subcategory_camera_capture"`. Immediately after `layoutCameraCapture`, insert a closing divider `View` so the block is bounded from `switchShowBlackScreenButton` below it. Do not change the children or IDs of `layoutCameraCapture`.

**Verification:**

- `Grep -n "settings_subcategory_camera_capture"` — exactly 1 hit in `layout/fragment_settings_playback.xml`.
- `Grep -n "layoutCameraCapture"` — still exactly 1 declaration hit (`@+id/layoutCameraCapture`).
- Source order: `settings_subcategory_camera_capture` header appears before `@+id/layoutCameraCapture`; a divider `View` appears between `layoutCameraCapture`'s closing tag and `switchShowBlackScreenButton`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. `layout/fragment_settings_playback.xml`: divider+caps header `settings_subcategory_camera_capture` inserted before `layoutCameraCapture` (lines 418/420); closing divider added after it (line 442). Dev log recorded in phase wrap-up.

---

### Step 02.3 — Portrait: turn `row_saved_authorizations` into a sub-screen nav row with help icon + chevron

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml` (landscape counterpart updated in Step 02.4 — same phase)
**Depends on:** Step 02.1

**Prompt for developer:**

> Rework the `row_saved_authorizations` LinearLayout to a horizontal row (keep its `@+id/row_saved_authorizations`, `android:clickable="true"`, `android:focusable="true"`, `android:minHeight="@dimen/settings_item_min_height"`): add `android:background="?attr/selectableItemBackground"`. As the first child add an `ImageButton android:id="@+id/iconHelpSavedAuthorizations"` using the same attributes as `iconHelpCameraCapture` (`@drawable/ic_help_outline_24`, `?attr/selectableItemBackgroundBorderless`, `app:tint="@color/text_color_secondary"`, `android:contentDescription="@string/tooltip_saved_authorizations_title"`, size/margins per `@dimen/settings_help_icon_size` / `@dimen/settings_help_icon_margin`). The middle child is a vertical `LinearLayout` (`layout_width="0dp"`, `layout_weight="1"`) holding the existing title `TextView` (`@string/setting_saved_authorizations_title`) and summary `TextView` (`@string/setting_saved_authorizations_summary`). As the last child add an `ImageView` with `android:src="@drawable/ic_chevron_right"`, `android:layout_width="24dp"`, `android:layout_height="24dp"`, `android:importantForAccessibility="no"` — same as the chevron in `rowControlsKeybindings`.

**Verification:**

- `Grep -n "iconHelpSavedAuthorizations"` — exactly 1 hit (the `@+id/` declaration) in `layout/fragment_settings_playback.xml`.
- `Grep -n "ic_chevron_right"` — at least 2 hits in `layout/fragment_settings_playback.xml` (one in `rowControlsKeybindings`, one in `row_saved_authorizations`).
- `Grep -n "selectableItemBackground\"" ` — `row_saved_authorizations` carries `?attr/selectableItemBackground`.
- `Grep -n "setting_saved_authorizations_title"` and `setting_saved_authorizations_summary` — still present exactly once each.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. `layout/fragment_settings_playback.xml`: `row_saved_authorizations` reworked to horizontal nav-row with leading `iconHelpSavedAuthorizations` ImageButton, weighted title/summary column, trailing `ic_chevron_right` ImageView, `?attr/selectableItemBackground` on the row (lines 377-388). Dev log recorded in phase wrap-up.

---

### Step 02.4 — Landscape: apply Steps 02.1–02.3 to `layout-land/fragment_settings_playback.xml`

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> The landscape variant has the same single-column structure for the Behaviour group. Apply the identical changes from Steps 02.1, 02.2 and 02.3 in `layout-land/fragment_settings_playback.xml`: two divider+sub-header pairs, `row_saved_authorizations` moved into the `INCOMING LINKS` block, the help icon + chevron + selectable background on that row. Keep all IDs identical to the portrait file.

**Verification:**

- `Grep -n "settings_subcategory_incoming_links"` — exactly 1 hit in `layout-land/fragment_settings_playback.xml`.
- `Grep -n "settings_subcategory_camera_capture"` — exactly 1 hit in `layout-land/fragment_settings_playback.xml`.
- `Grep -n "iconHelpSavedAuthorizations"` — exactly 1 hit (`@+id/`) in `layout-land/fragment_settings_playback.xml`.
- `Grep -n "row_saved_authorizations"` — exactly 1 hit; source order after `switch_link_autodownload_open_in_player`, before `switchResumeOnNextLaunch`.
- `Grep -n "ic_chevron_right"` — at least 1 hit inside the Behaviour region of `layout-land/fragment_settings_playback.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS. `layout-land/fragment_settings_playback.xml`: applied the same divider+caps headers, `row_saved_authorizations` nav-row rework and closing dividers as the portrait variant; IDs identical. Dev log recorded in phase wrap-up.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (standard debug, v2.60.5101.424).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `aapt`/lint sees no missing-resource errors for the new `@string`/`@drawable`/`@dimen` references (covered by build).
- [x] Dev log entry added for both layout files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`row_saved_authorizations` now contains a child `ImageButton` with id `iconHelpSavedAuthorizations` in both layouts → view binding exposes `binding.iconHelpSavedAuthorizations`. Phase 03 wires its click listener. The row's click listener (`binding.rowSavedAuthorizations.setOnClickListener`) and its enabled-gating (`binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled`) already exist in `PlaybackSettingsFragment.kt` and keep working — verify they still resolve after the layout change.

---

## Rollback Plan

Revert phase commit(s) — layout-only changes, no data migration or persisted state touched.
