# Phase 03 - Widget provider + resources + strings

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-06-20
**Completed:** 2026-06-20 (commit ab3f5d02)

---

## Objective

Add the config-less app-widget provider and all its resources: layout, accent drawable, widget-info XML, and the two new trilingual strings (label + description) - everything the merged-manifest receiver needs in Phase 04.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`CameraLaunchActivity` exists with `ACTION_LAUNCH`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/drawable/ic_widget_camera_launch_accent.xml` | New | ≤ 60 |
| `app_v2/src/main/res/layout/widget_camera_launch.xml` | New | ≤ 40 |
| `app_v2/src/main/res/xml/camera_launch_widget_info.xml` | New | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetProvider.kt` | New | ≤ 90 |

> Landscape parity: app-widget layouts use a single `RemoteViews` layout; no `res/layout-land/widget_camera_launch.xml` counterpart is needed (consistent with the other camera widgets).

---

## Steps

### Step 03.1 - Add trilingual widget strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys in lockstep across EN/RU/UK with one call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key widget_camera_launch_label -En "Camera" -Ru "Камера" -Uk "Камера"`
> and
> `... -Action add -Key widget_camera_launch_description -En "Open the camera to take a photo or record video" -Ru "Открыть камеру для съёмки фото или видео" -Uk "Відкрити камеру для зйомки фото або відео"`.
> Wording must pass `docs/COMMUNICATION_POLICY.md` §2 (label/description formula) and §6 (tone checklist): plain, action-first, no hype. Keep the label distinct enough from the existing quick-capture widget label to be tellable apart in the picker if needed.

**Verification:**

- `Grep` - `widget_camera_launch_label` present in all three `strings.xml` files.
- `Grep` - `widget_camera_launch_description` present in all three.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_camera_launch"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Add the accent drawable

**Files:** `app_v2/src/main/res/drawable/ic_widget_camera_launch_accent.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a vector drawable `ic_widget_camera_launch_accent.xml` for the widget icon/preview. Base it on `ic_widget_camera_quick_capture_accent.xml` but make it visually distinct (e.g. a plain camera glyph without the quick-capture flash accent), using theme attributes / `@color/*` for tint - no hardcoded `#hex` fills beyond what the source vector already uses for the accent shape. Keep `android:tint`/path colors consistent with the other camera widget accents.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_widget_camera_launch_accent.xml` exists.
- `Grep` - `<vector` present in the file.

**Status:** `[x]` done

---

### Step 03.3 - Add the widget layout

**Files:** `app_v2/src/main/res/layout/widget_camera_launch.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `widget_camera_launch.xml` mirroring `widget_camera_quick_capture.xml`: a clickable container `@+id/widget_camera_launch_container` wrapping an `ImageView @+id/widget_camera_launch_icon`. Use `?attr/`/`@color/` for any color; no hardcoded `#hex`. The icon's `src` is set in the provider, but reference `@drawable/ic_widget_camera_launch_accent` as the layout default.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/widget_camera_launch.xml` exists.
- `Grep` - `@+id/widget_camera_launch_container` present.
- `Grep` - `@+id/widget_camera_launch_icon` present.
- `Grep -n "#"` shows no hardcoded color literals in the layout.

**Status:** `[x]` done

---

### Step 03.4 - Add the widget-info XML (config-less)

**Files:** `app_v2/src/main/res/xml/camera_launch_widget_info.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `camera_launch_widget_info.xml` mirroring `widget_camera_quick_capture_info.xml` but WITHOUT the `android:configure` attribute (config-less, ADR-2). Set `initialLayout=@layout/widget_camera_launch`, `description=@string/widget_camera_launch_description`, `previewImage=@drawable/ic_widget_camera_launch_accent`, `minWidth/minHeight=48dp`, `targetCellWidth/Height=1`, `resizeMode=none`, `widgetCategory=home_screen`, `updatePeriodMillis=0`, `theme=@style/Widget.FastMediaSorter`.

**Verification:**

- `Glob` - `app_v2/src/main/res/xml/camera_launch_widget_info.xml` exists.
- `Grep` - `@layout/widget_camera_launch` present.
- `Grep` - `android:configure` returns zero hits in the file (config-less).

**Status:** `[x]` done

---

### Step 03.5 - Create CameraLaunchWidgetProvider

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetProvider.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Create `CameraLaunchWidgetProvider : AppWidgetProvider()`. In `onUpdate`, for each id call a `companion` `updateAppWidget` that inflates `R.layout.widget_camera_launch`, sets the icon `R.drawable.ic_widget_camera_launch_accent`, and sets an `onClick` PendingIntent on `R.id.widget_camera_launch_container` targeting `CameraLaunchActivity` with `action = CameraLaunchActivity.ACTION_LAUNCH`, `flags = FLAG_ACTIVITY_NEW_TASK`, unique `data = Uri.parse("fms://cam-launch/$appWidgetId")`, and `PendingIntent.getActivity(..., FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`. Config-less: no `onDeleted` prefs, no configure intent branch. Flavor availability is decided by the merged manifest (Phase 04); read no `BuildConfig.SUPPORT_*` here (Rule 15).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetProvider.kt` exists.
- `Grep` - `class CameraLaunchWidgetProvider : AppWidgetProvider` matches once.
- `Grep` - `CameraLaunchActivity.ACTION_LAUNCH` present.
- `Grep` - `BuildConfig` returns zero hits in the file.
- `/build` - `standard debug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - validated in commit ab3f5d02 (`standard debug`).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_camera_launch"` exits 0 (re-verified 2026-06-21).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The provider class + `camera_launch_widget_info.xml` + the two strings exist. Phase 04 registers the receiver (referencing `@string/widget_camera_launch_label`, `@drawable/ic_widget_camera_launch_accent`, `@xml/camera_launch_widget_info`) and the trampoline activity in the main manifest.

---

## Rollback Plan

Revert phase commit(s) - resources + provider are not referenced by the manifest until Phase 04, so reverting leaves no dangling registration.
