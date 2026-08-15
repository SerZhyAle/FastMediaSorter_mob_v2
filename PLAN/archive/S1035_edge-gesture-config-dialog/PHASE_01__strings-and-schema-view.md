# Phase 01 - Strings and interactive schema view

**Strategic spec:** [`../S1035_edge-gesture-config-dialog.md`](../S1035_edge-gesture-config-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Produce the trilingual strings and the standalone `EdgeGestureSchemaView` custom view (renders four edge zones with three swipe directions each, grey = available/unassigned, red = enabled/assigned, and reports taps) that later phases consume. No wiring into settings yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt` | New | ≤ 300 |

> `EdgeGestureSchemaView` lives in `src/main` and is capability-agnostic (drawing only); the feature gate stays at the launcher/dialog level (Phase 04). No `BuildConfig` flavor guard here.

---

## Steps

### Step 01.1 - Add trilingual strings for the dialog surface

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the new user-visible strings across EN/RU/UK in one lockstep call. Keys and EN copy:
> `setting_edge_gesture_config_button` = "Configure gestures"; `edge_gesture_config_dialog_title` = "Edge gestures"; `edge_gesture_tab_left_top` = "Left top"; `edge_gesture_tab_left_bottom` = "Left bottom"; `edge_gesture_tab_right_top` = "Right top"; `edge_gesture_tab_right_bottom` = "Right bottom"; `edge_gesture_general_group_title` = "General gesture settings"; `edge_gesture_schema_content_description` = "Edge gesture map: tap a zone direction to assign an action"; `edge_gesture_schema_state_assigned` = "assigned"; `edge_gesture_schema_state_available` = "available".
> Provide natural RU/UK translations (RU uses ё where grammatical; `..` not `...`; plain hyphen). Use `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"` per key (parity-enforced).

**Verification:**

- `Grep` - each new key name matches exactly once in `res/values/strings.xml`.
- `Grep` - each new key name matches exactly once in `res/values-ru/strings.xml`.
- `Grep` - each new key name matches exactly once in `res/values-uk/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "edge_gesture_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-07-13 - Verification 5/5 PASS. Added 10 keys (setting_edge_gesture_config_button + 9 edge_gesture_*) across EN/RU/UK via set-android-string add; parity 10/10/10; localization audit exit 0. Plain labels, policy-compliant.

---

### Step 01.2 - Create EdgeGestureSchemaView (state rendering)

**Files:** `ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `class EdgeGestureSchemaView(context, attrs) : View`. Draw a phone-screen outline with four edge bands (left-top, left-bottom, right-top, right-bottom) positioned like the real overlay (left/right edges, upper 10-40% and lower 60-90% of the safe height), and inside each active band three swipe-direction arrows (up / inward / down) with a finger-motion hint. Expose `fun setState(state: SchemaState)` where `SchemaState` is a data class describing, per zone: enabled flag and, per direction, whether an action is assigned. Colour rule: a band/direction that is available but off/unassigned draws in a grey (`?attr/colorOutline` / a grey `@color` token - never a hardcoded hex), an enabled zone and each assigned direction draw in red (`@color/...` red token already used by the overlay strip, reuse it). Cache `Paint` objects as fields (no allocation in `onDraw`). No tap handling yet (next step).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureSchemaView.kt` exists.
- `Grep` - `class EdgeGestureSchemaView` matches exactly once (declaration).
- `Grep` - `fun setState(` present.
- `Grep -n "#[0-9a-fA-F]{6}"` in the new file returns zero hits (no hardcoded hex).
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 01.3 - Add tap hit-testing and assignment listener

**Files:** `ui/settings/gesture/EdgeGestureSchemaView.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add hit-testing so a tap on a direction arrow reports `(ScreenshotGestureZone, ScreenshotGestureDirection)` and a tap on a band body (not on an arrow) reports the zone. Expose `fun setOnDirectionTapListener(listener: (ScreenshotGestureZone, ScreenshotGestureDirection) -> Unit)` and `fun setOnZoneTapListener(listener: (ScreenshotGestureZone) -> Unit)`. Use the model enums from `domain/model/ScreenshotGestureZone.kt` and `ScreenshotGestureDirection.kt`. Keep the view keyboard/D-pad reachable (`isFocusable = true`) and set `contentDescription` from `edge_gesture_schema_content_description`; individual zone/direction meaning is also conveyed by the tab labels + rows (colour is not the sole signal).

**Verification:**

- `Grep` - `fun setOnDirectionTapListener(` present.
- `Grep` - `fun setOnZoneTapListener(` present.
- `Grep` - `ScreenshotGestureDirection` referenced in the file.
- `Grep` - `contentDescription` set in the file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`EdgeGestureSchemaView` + all `edge_gesture_*` strings exist. Phase 02 embeds the view in the dialog layout and references the strings for tab titles / group headers.

---

## Rollback Plan

Revert phase commit(s) - new view + additive strings only, no data or user-facing surface changed yet.
