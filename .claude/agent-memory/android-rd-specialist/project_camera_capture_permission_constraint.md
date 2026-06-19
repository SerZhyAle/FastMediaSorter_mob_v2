---
name: camera-capture-permission-constraint
description: Why FastMediaSorter's camera capture is permission-free, and why adding any in-app camera forces CAMERA on the whole app (S0359 Variant 1 decision)
metadata:
  type: project
---

FastMediaSorter's existing photo capture is deliberately permission-free: both `BrowseCameraCaptureManager` (S0022, capture-to-resource) and `CameraOcrFlowManager` (camera-OCR-translate) delegate to the device camera via `MediaStore.ACTION_IMAGE_CAPTURE`, and the app does **not** declare `android.permission.CAMERA`.

**Why:** Android rule (confirmed on developer.android.com, enforced since API 23): if an app *declares* `CAMERA` in the manifest but it isn't granted at runtime, `ACTION_IMAGE_CAPTURE` throws `SecurityException`. So an app that only delegates to the system camera should NOT declare CAMERA - and FastMediaSorter doesn't, which is why capture works with zero permission prompts today.

**The trap for any future in-app camera work:** CameraX / Camera2 cannot open the camera device without `CAMERA` declared + granted. The moment you declare CAMERA to enable an in-app preview, the *existing* `ACTION_IMAGE_CAPTURE` flows also start requiring CAMERA. Therefore "no permission → system camera, has permission → in-app camera" is **impossible in one app** - declaring CAMERA poisons the permission-free fallback. There is no loophole (conditional manifest, flavor split, `uses-permission-sdk-23` all fail).

**How to apply:** Any spec proposing an in-app camera (CameraX) must accept that CAMERA becomes mandatory for ALL capture (OCR + capture-to-resource), and that this is a behaviour change for existing users (capture was permission-free). S0359 resolved this as "Variant 1" (owner, 2026-06-05): CAMERA mandatory, in-app CameraX is the sole capture path, the OEM Retry/OK confirmation screen is removed entirely, denial → rationale + no capture (no system-camera fallback). Don't re-propose a permission-gated fallback to the system camera - it cannot work.

Related: the OEM "Retry/OK" review screen the owner wanted removed is the *system camera app's* own confirmation after `ACTION_IMAGE_CAPTURE`, not app UI - only an in-app camera eliminates it.
