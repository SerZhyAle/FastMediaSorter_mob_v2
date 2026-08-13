# S0922 - Camera overlay labels upside-down in landscape

**Status:** Archived
**Priority:** 90
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)

## 1. Problem

The camera capture host is portrait-locked (S0754), so overlay controls and labels are counter-rotated by device angle to stay upright. When the phone is turned to either landscape side, every label/control appears rotated 180 degrees (upside-down) instead of upright. Inverted portrait renders correctly. Reported by owner during S0801 rotate device-test.

## 2. Root cause

`OrientationEventListener.onOrientationChanged()` reports the device's clockwise rotation from the user's point of view. To keep a portrait-locked overlay upright, the applied `View.rotation` (also clockwise-positive) must be the negative of that device angle.

In `CameraOrientationManager.dispatch()` the two landscape branches carried the wrong sign:

- device at +90 deg CW (`ROTATION_270` bucket) applied +90 -> net 180 -> upside-down.
- device at -90 deg CW (`ROTATION_90` bucket) applied -90 -> net -180 -> upside-down.
- inverted portrait (`ROTATION_180`) applied 180 -> net 0 -> correct (why only landscape was wrong).

The CameraX `onTargetRotationChanged` mapping is the canonical snippet and stays untouched; only the icon-rotation `when` was inverted. Predates S0801 (which only moved lifecycle wiring, not the angle math).

## 3. Fix

Swap the two landscape icon-rotation values in `CameraOrientationManager.dispatch()`:

- `Surface.ROTATION_90 -> 90f` (was `-90f`)
- `Surface.ROTATION_270 -> -90f` (was `90f`)
- `ROTATION_180 -> 180f` and `else -> 0f` unchanged.

## 4. Done criteria

1. Rotating the camera to either landscape side keeps all overlay labels/controls upright.
2. Inverted portrait and natural portrait remain upright (no regression).
3. CameraX capture output orientation unchanged.

## Reference

- OrientationEventListener returns clockwise device rotation: https://developer.android.com/develop/devices/chromeos/learn/camera-orientation
