# Phase 02 - Dialog layout (portrait + landscape)

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Author the full-screen dialog layout hosting the schema view, a TabLayout with four per-zone content containers (each: enable toggle, strip-visibility toggle, three action pickers), and one collapsible "General gesture settings" group (screenshot destination, clipboard, edit-app-panel, accessibility rows, permission entry) - in both portrait and landscape.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (schema view + strings exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_edge_gesture_config.xml` | New | ≤ 400 |
| `app_v2/src/main/res/layout-land/dialog_edge_gesture_config.xml` | New | ≤ 400 |

> Landscape variant is created in this phase (Step 02.3); no portrait-only gap. Reuse existing row widgets (`SettingsToggleRow`, `SettingsSelectionRow`, `CollapsibleSectionHeader`) and dimen/attr tokens; no hardcoded hex colours.

---

## Steps

### Step 02.1 - Portrait dialog skeleton (header, schema, tabs, general group)

**Files:** `res/layout/dialog_edge_gesture_config.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the portrait full-screen dialog root (scrollable column). Top: a header row with the title (`@string/edge_gesture_config_dialog_title`) and an icon-only close button `btnClose` (mirror `dialog_default_apps.xml`). Below the header: `com.sza.fastmediasorter.ui.settings.gesture.EdgeGestureSchemaView` `@+id/edgeGestureSchema` (fixed sensible height, `match_parent` width). Below the schema: a `com.google.android.material.tabs.TabLayout` `@+id/tabsEdgeGestureZones` (four tabs added in code) and a `@+id/containerZoneTabContent` frame that holds the four zone content blocks (Step 02.2). Below that: the general collapsible group - a `CollapsibleSectionHeader` `@+id/headerEdgeGestureGeneral` (title `@string/edge_gesture_general_group_title`) + a `@+id/containerEdgeGestureGeneral` LinearLayout. Do not populate the zone blocks or general group rows yet.

**Verification:**

- `Glob` - `res/layout/dialog_edge_gesture_config.xml` exists.
- `Grep` - `@+id/edgeGestureSchema` present.
- `Grep` - `@+id/tabsEdgeGestureZones` present.
- `Grep` - `@+id/headerEdgeGestureGeneral` and `@+id/containerEdgeGestureGeneral` present.
- `Grep -n "#[0-9a-fA-F]{6}"` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 02.2 - Populate zone tab blocks + general group rows

**Files:** `res/layout/dialog_edge_gesture_config.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inside `containerZoneTabContent` add four zone blocks (`@+id/blockZoneLeftTop`, `blockZoneLeftBottom`, `blockZoneRightTop`, `blockZoneRightBottom`), only one visible at a time (tab-driven, code in Phase 03). Each block reproduces the existing per-zone rows with the SAME id names already used by the current settings layout so the moved manager logic keeps working: `rowZone<Name>Enabled`, `rowZone<Name>StripVisible`, `rowGesture<Name>Up`, `rowGesture<Name>Right`, `rowGesture<Name>Down` (see `fragment_settings_destinations.xml` lines ~1020-1058 for the exact id set and string attrs). Inside `containerEdgeGestureGeneral` add the relocated general rows with their existing ids: `rowGestureOverlayEnabled` is NOT here (stays in the settings tab as master); place `rowCopyScreenshotToClipboard`, `btnSelectScreenshotDestination` + `tvScreenshotDestination`, `btnEditAppPanel`, `tvAccessibilityShortcutHint`, `btnOpenAccessibilitySettings`. Keep ids identical to the current layout to minimise manager churn.

**Verification:**

- `Grep` - `@+id/rowZoneLeftTopEnabled`, `@+id/rowZoneRightBottomDown`… (all four zones' enable + Up/Right/Down ids) present in the file.
- `Grep` - `@+id/rowCopyScreenshotToClipboard` present.
- `Grep` - `@+id/btnSelectScreenshotDestination` and `@+id/btnEditAppPanel` present.
- `Grep` - `@+id/btnOpenAccessibilitySettings` present.
- `Grep` - `@+id/rowGestureOverlayEnabled` returns zero hits (master stays in the settings tab).

**Status:** `[ ]` not done

---

### Step 02.3 - Landscape variant (multi-column)

**Files:** `res/layout-land/dialog_edge_gesture_config.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create the landscape counterpart with the same ids. Use the width: place the schema view and the tab/zone content side-by-side (two columns) rather than a tall single column - e.g. schema on the left, active zone block on the right; the general group spans full width below or in the second column. Every id from the portrait layout must exist here too so view binding resolves in both orientations.

**Verification:**

- `Glob` - `res/layout-land/dialog_edge_gesture_config.xml` exists.
- `Grep` - `@+id/edgeGestureSchema`, `@+id/tabsEdgeGestureZones`, `@+id/containerEdgeGestureGeneral` all present in the land file.
- `Grep` - `@+id/rowGestureLeftTopUp` and `@+id/rowGestureRightBottomDown` present in the land file.
- `Grep -n "#[0-9a-fA-F]{6}"` returns zero hits in the land file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug) - `DialogEdgeGestureConfigBinding` generates for both orientations.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`DialogEdgeGestureConfigBinding` exists with all zone/general/schema ids. Phase 03 inflates it in the fragment and binds the manager against these ids.

---

## Rollback Plan

Revert phase commit(s) - new layout files only; not referenced until Phase 03.
