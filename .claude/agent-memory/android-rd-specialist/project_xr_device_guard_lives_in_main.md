---
name: xr-device-guard-lives-in-main
description: XrDeviceProbe (src/main) is the flavor-neutral "is this a VR headset" guard - use it, not the vr-flavor XrEnvironmentDetector, because Quest users sideload the standard APK too
metadata:
  type: project
---

To gate behaviour on "the app is running on a VR headset", use `XrDeviceProbe.isXrDevice(context)` in
`app_v2/src/main/java/com/sza/fastmediasorter/core/util/XrDeviceProbe.kt` - NOT the flavor-side
`XrEnvironmentDetector` / `XrDetectionFacade`.

**Why:** the two look interchangeable and are not. `XrEnvironmentDetectorImpl` lives in `src/vr/` and
therefore exists only in the `vr` and `noLegal` flavors. But **Quest users sideload the `standard`
APK as well**, where that detector does not exist - so a guard written against it silently does
nothing on exactly the devices it was meant to cover. `XrDeviceProbe` is a deliberate `src/main`
mirror for that case: pure `PackageManager.hasSystemFeature`, no Meta/Oculus SDK import, no
`BuildConfig` flavor guard, so it satisfies the no-flavor-gates-in-main rule (its own KDoc states all
of this).

Precedent - two existing consumers already use the "on an XR device, suppress what does not apply"
shape: `GmsAvailabilityChecker` (suppresses Google services) and `DetectionHelper` (device-profile
detection). A third consumer follows the same shape rather than inventing a mechanism.

**Related trap:** "VR" is ambiguous in this repo and the two meanings lead to opposite conclusions.
The `vr` **flavor** has no immersive rendering wired yet (epic S0773) and is not distributed, while a
VR **device** most often runs `noLegal` (sideload) or `standard`. S1236 was first researched against
the flavor and answered the wrong question; the owner clarified 2026-07-29 that he means the device.
Ask which one is meant before scoping any "in VR" ticket.

See [[vr-inclusion-hierarchy]] for the flavor mounting rules and [[quest-panel-not-introspectable]]
for why on-Quest verification has to go through `Timber` logs rather than screenshots.
