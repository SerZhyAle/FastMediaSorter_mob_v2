# Phase 03 — Adaptive Background

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Provide a Material You-themed widget background on Android 12+ via dynamic system-color resources and a high-contrast dark fallback with a 1dp border for Android 8..11; raise the contrast of the inner Favorites item card under the new background.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Pre-implementation blocker on Material You launcher test plan resolved (developer documents 2 launchers for hands-on check).
- [ ] `app_v2/src/main/res/values-v31/themes.xml` exists (already present per audit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/widget_background.xml` | Modified | ≤ 15 |
| `app_v2/src/main/res/drawable-v31/widget_background.xml` | New | ≤ 15 |
| `app_v2/src/main/res/values-v31/themes.xml` | Modified | +6 |
| `app_v2/src/main/res/drawable/widget_item_background.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/xml/widget_favorites_info.xml` | Modified | +1 |
| `app_v2/src/main/res/xml/widget_resource_launch_info.xml` | Modified | +1 |
| `app_v2/src/main/res/xml/widget_camera_photos_info.xml` | Modified | +1 |
| `app_v2/src/main/res/xml/widget_random_music_info.xml` | Modified | +1 |
| `app_v2/src/main/res/xml/widget_continue_reading_info.xml` | Modified | +1 |

---

## Steps

### Step 03.1 — Tighten base widget background and item background contrast

**Files:** `app_v2/src/main/res/drawable/widget_background.xml`, `app_v2/src/main/res/drawable/widget_item_background.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `widget_background.xml`: replace the solid `#CC000000` with `#E6121212`, raise corners to 24dp, add a `<stroke android:width="1dp" android:color="#33FFFFFF" />` element. In `widget_item_background.xml`: replace `#33FFFFFF` with `#4DFFFFFF`, raise corners to 12dp. Both files remain a single `<shape>` rectangle.

**Verification:**

- `Grep` — `#E6121212` matches exactly once in `app_v2/src/main/res/drawable/widget_background.xml`.
- `Grep` — `<stroke` matches exactly once in `app_v2/src/main/res/drawable/widget_background.xml`.
- `Grep` — `android:radius="24dp"` matches exactly once in `app_v2/src/main/res/drawable/widget_background.xml`.
- `Grep` — `#4DFFFFFF` matches exactly once in `app_v2/src/main/res/drawable/widget_item_background.xml`.
- `Grep` — `android:radius="12dp"` matches exactly once in `app_v2/src/main/res/drawable/widget_item_background.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS. Files: drawable/widget_background.xml (rewritten 9 LOC), drawable/widget_item_background.xml (rewritten 7 LOC). Dev log recorded.

---

### Step 03.2 — Add Material You background drawable for API 31+

**Files:** `app_v2/src/main/res/drawable-v31/widget_background.xml`
**Depends on:** — independent of 03.1

**Prompt for developer:**

> Create new resource directory `drawable-v31/` if absent. Add `widget_background.xml` as a `<shape android:shape="rectangle">` with `<solid android:color="@android:color/system_neutral1_900">` (this auto-flips with light/dark theme on Pixel-style launchers via system theme overlay), corners 24dp, and `<stroke android:width="1dp" android:color="@android:color/system_accent1_200">` for a subtle Material You accent border.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable-v31/widget_background.xml` exists.
- `Grep` — `@android:color/system_neutral1_900` matches exactly once in the new file.
- `Grep` — `@android:color/system_accent1_200` matches exactly once in the new file.
- `Grep` — `android:radius="24dp"` matches exactly once in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: drawable-v31/widget_background.xml (new, 9 LOC). Dev log recorded.

---

### Step 03.3 — Declare `Widget.FastMediaSorter` theme in values-v31

**Files:** `app_v2/src/main/res/values-v31/themes.xml`
**Depends on:** — independent of 03.1, 03.2

**Prompt for developer:**

> Append a new `<style name="Widget.FastMediaSorter" parent="@android:style/Theme.DeviceDefault.DayNight">` block inside the existing `<resources>` root. Set `<item name="android:colorBackground">@android:color/system_neutral1_900</item>` and `<item name="android:textColorPrimary">@android:color/system_neutral1_50</item>`. Do not modify the existing `Theme.FastMediaSorter` style.

**Verification:**

- `Grep` — `<style name="Widget.FastMediaSorter"` matches exactly once in `app_v2/src/main/res/values-v31/themes.xml`.
- `Grep` — `Theme.DeviceDefault.DayNight` matches exactly once in the same file.
- `Grep` — `<style name="Theme.FastMediaSorter"` still matches exactly once in the same file (existing style untouched).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: values-v31/themes.xml (+6 LOC). Dev log recorded.

---

### Step 03.4 — Reference `Widget.FastMediaSorter` theme from all widget metadata

**Files:** `app_v2/src/main/res/xml/widget_favorites_info.xml`, `app_v2/src/main/res/xml/widget_resource_launch_info.xml`, `app_v2/src/main/res/xml/widget_camera_photos_info.xml`, `app_v2/src/main/res/xml/widget_random_music_info.xml`, `app_v2/src/main/res/xml/widget_continue_reading_info.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add the attribute `android:theme="@style/Widget.FastMediaSorter"` to each `<appwidget-provider>` root element in the five widget metadata files. The attribute is honored only on API 31+ and silently ignored on lower API levels — fallback path is the base `drawable/widget_background.xml` from Step 03.1.

**Verification:**

- `Grep` — `android:theme="@style/Widget.FastMediaSorter"` matches exactly five times across `app_v2/src/main/res/xml/widget_*_info.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 1/1 PASS (theme attr present in all 5 widget_*_info.xml). Files: 5 widget_*_info.xml (+1 LOC each). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (33s, standard flavor; combined Phase 03+04 verification).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Background visuals adapt to the launcher theme on modern Android and remain readable on older Android. Phase 04 changes the Favorites widget content surface to handle empty state — uses the same item background contrast established in Step 03.1.

---

## Rollback Plan

Revert phase commit(s). Background reverts to the legacy solid black; no data or behavioural change.
