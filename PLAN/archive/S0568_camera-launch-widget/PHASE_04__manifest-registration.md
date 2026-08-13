# Phase 04 - Manifest registration

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-06-20
**Completed:** 2026-06-20 (commit ab3f5d02)

---

## Objective

Register the widget receiver and the transparent trampoline activity in the main manifest so the widget appears in the home-screen picker. No flavor manifest edits - the widget ships in every flavor, mirroring the existing camera widgets (research 01).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (provider class, widget-info XML, label string, accent drawable all exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |

> Flavor placement: no `src/<flavor>/AndroidManifest.xml` overlay is added or changed. Research 01 confirms no flavor removes the camera widgets; the launch widget mirrors that (present everywhere, runtime degenerate gating).

---

## Steps

### Step 04.1 - Register receiver + trampoline activity

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, next to the `CameraQuickCaptureWidgetProvider` registration, add:
> - a `<receiver android:name=".widget.CameraLaunchWidgetProvider" android:exported="true" android:label="@string/widget_camera_launch_label" android:icon="@drawable/ic_widget_camera_launch_accent">` with an `intent-filter` for `android.appwidget.action.APPWIDGET_UPDATE` and `<meta-data android:name="android.appwidget.provider" android:resource="@xml/camera_launch_widget_info" />`.
> - an `<activity android:name=".widget.CameraLaunchActivity" android:exported="false" android:theme="@style/Theme.FastMediaSorter.Transparent" android:excludeFromRecents="true" android:taskAffinity="" android:noHistory="true" />` (mirrors `CameraQuickCaptureActivity`, so the user stays on the home screen during the camera handoff).
> Do not edit any flavor manifest.

**Verification:**

- `Grep` - `.widget.CameraLaunchWidgetProvider` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` - `@xml/camera_launch_widget_info` present in the main manifest.
- `Grep` - `.widget.CameraLaunchActivity` present with `android:noHistory="true"`.
- `Grep` - `CameraLaunchWidgetProvider` returns zero hits across `app_v2/src/{lite,photos,legacy,vr,noLegal}/AndroidManifest.xml` (no flavor overlay).
- `/build` - `standard debug` assembles (merged manifest valid).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Step 04.1 is `[x] done`.
- [x] Project compiles + manifest merges - validated in commit ab3f5d02 (`standard debug`).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the manifest change.

---

## Handoff Notes to Next Phase

The widget is now installable and tappable end-to-end. Phase 05 inserts the device-test probe, records the capability, regenerates the catalog, and closes the loop.

---

## Rollback Plan

Revert the manifest hunk - the widget disappears from the picker; the Phase 02/03 classes/resources become dormant but harmless.
