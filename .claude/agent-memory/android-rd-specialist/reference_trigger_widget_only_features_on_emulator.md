---
name: trigger-widget-only-features-on-emulator
description: Device-test technique - how to trigger widget/gesture-only features (quick audio recorder etc.) on an emulator when am start is export-blocked
metadata:
  type: reference
---

Widget-only / gesture-only features (e.g. Quick Audio Recorder S0349, its trampoline `QuickAudioRecorderActivity` + `QuickAudioRecorderService`) can ONLY be triggered on-device via the app's own self-uid PendingIntent (the widget tap), not from adb shell.

**Why:** those trampoline activities and foreground services are `exported="false"`. On API 37 both `am start -n .../Activity` and `am start-foreground-service -n .../Service` fail with `SecurityException: not exported from uid <appUid>`. `run-as <pkg> am start` also fails - `am` reports its calling package as `com.android.shell`, so `assertPackageMatchesCallingUid` rejects it. The widget works because `setOnClickPendingIntent` fires a PendingIntent created by the app itself (self-uid), which bypasses the export check and is BAL-allowed (`BAL_ALLOW_VISIBLE_WINDOW`).

**How to apply (device test):**
- Place the widget via the launcher widget picker, then tap it. Prior runs that "gave up" on this are beatable.
- Reliable widget placement on Pixel launcher: open picker (long-press home -> Widgets), use its Search field to find the app, tap the app row to expand, then `adb shell input draganddrop <srcX> <srcY> <dstX> <dstY> 2500` from the widget preview center to an empty home cell. A plain `input swipe` just scrolls the list - it does not pick up the widget; `draganddrop` dwells at the source long enough to satisfy the long-press pickup.
- After placing, dismiss the resize frame (tap empty area), then tap the widget container (`id/widget_quick_audio_recorder_container`) to fire the toggle.
- Related: [[emulator-verifies-mediaprojection-screenshot]], [[bottomsheet-menu-untappable-emulator]].
