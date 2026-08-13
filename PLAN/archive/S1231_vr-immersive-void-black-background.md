# S1231 - Immersive background is blue-grey instead of void black

**Status:** Archived
**Priority:** 55

## 0. Raw capture

Owner, 2026-07-27, in the headset:

> "фон в иммерсив "серый"или "сизый". Должен быть VOID BLACK"

## 1. Cause

`app_v2/src/vr/cpp/xr_session.cpp`, per-eye clear before the scene is drawn:

```cpp
glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
```

That is RGB (13, 13, 20) of 255 - dark, but not black, and with the **blue channel highest**. In a headset, next to a bright film on the quad, a uniform blue-tinted field reads as a haze rather than as absence of light, which is exactly the "сизый" the owner reported.

The environment blend mode is `XR_ENVIRONMENT_BLEND_MODE_OPAQUE`, so this clear colour is what fills the entire field of view outside the media quad - it is the whole visual environment, not a small border.

## 2. Change

`glClearColor(0.0f, 0.0f, 0.0f, 1.0f)`.

No other clear call exists in the native slice, so this is the single source of the background colour.

## 3. Verification

- Build: `.\a.ps1 nd`.
- Device check owed: enter immersive on any media - the surround must be black, with no blue cast visible next to bright content.

## Last Audit

**Date:** 2026-07-28 (spec-next F5). **Verdict:** code+build verified; headset check owed -> BlockNeedUserTest.

- §2 exact: `glClearColor(0.0f, 0.0f, 0.0f, 1.0f)` at `xr_session.cpp:1326` with the S1231 WHY
  comment; the old `{0.05, 0.05, 0.08}` tint is gone (0 hits) and the call is the single
  `glClearColor` in `src/vr/cpp` (1 hit), matching §2's single-source claim.
- Build: `fkn` PASS + full `.\a.ps1 nd` PASS (19:53 - noLegal APK built and distributed by the
  builder), covering the native slice per §3.
- Probe-tag note: the change is native-only (C++); the `Timber.d("Sxxxx:")` probe convention
  applies to `.kt` flows and has no insertion point here - device evidence is the visual check.
- ALL_FEATURES: no record yet (0 hits) - to be written by `/spec-check` on the Verified flip
  (user-visible FIX, noLegal flavor).

## 4. Note for whoever revisits this

If a non-black surround is ever wanted again (e.g. a dim ambient tint to reduce eye strain on very bright content), keep the channels equal. The defect here was not the brightness but the tint: an unequal blue channel is what the eye reads as colour rather than as dark.
