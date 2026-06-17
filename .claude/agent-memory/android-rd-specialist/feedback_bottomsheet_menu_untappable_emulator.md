---
name: bottom-sheet menu items untappable on emulator
description: ResourceOperationsMenu / bottom-sheet items don't register taps via mobile-mcp or adb on the AVD during /spec-test-device
type: feedback
---

During `/spec-test-device`, the Browse **Resource Operations Menu** bottom-sheet items (e.g. "Capture with camera", "Record video") cannot be triggered on the emulator: taps register on neither `mobile_click_on_screen_at_coordinates` nor `adb shell input tap`, across sessions and both input paths. Symptoms: foreground stays on the host activity, the spec's `Sxxxx:` probe never fires, mobile-mcp sometimes returns a stale element tree and an occasional all-black frame.

**Why:** burned two full device-test runs on S0469 trying to drive the in-app camera capture through this menu; the capture flow (Browse → "Capture with camera" → CameraCaptureActivity → CameraCaptureSaver) is only reachable via that bottom sheet, so the runtime path stayed unverified.

**How to apply:** for any flow gated behind a bottom-sheet/popup menu item, don't loop on coordinate taps — after one mobile-mcp + one adb attempt that leave `topResumedActivity` unchanged, declare the runtime path INCONCLUSIVE (tooling wall, not a code defect), verify what you can statically + via the settings UI, and keep the ticket `BlockNeedUserTest` for a real device. For repeatable menu-item drives prefer a Maestro flow with a text matcher, or a real device. Settings screens and resource-list rows DO tap fine via mobile-mcp; only the bottom-sheet menu is the wall.
