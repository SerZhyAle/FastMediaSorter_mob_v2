---
name: app-self-pin-tests-launcher-pin-host
description: FastMediaSorter pins its own shortcuts, so the launcher's CONFIRM_PIN_SHORTCUT host is testable end to end on an emulator with no third-party app
metadata:
  type: project
---

The app issues real pin requests itself, through `ShortcutManagerCompat.requestPinShortcut`:
`widget/ResourceShortcutPinManager.kt` (reached from the MainActivity resource menu, "Add to Home
Screen") and `ui/streams/helpers/StreamShortcutPinManager.kt`. When FastMediaSorter also holds the home
role, that request is delivered straight back to its own
`LauncherPinRequestActivity` (`src/launcherEnabled/.../ui/launcher/pin/`).

**Why:** S1205 was written expecting a third-party publisher (Google Maps "add to home screen") and was
parked in `BlockNeedUserTest` as untestable without one. On 2026-08-06 the self-pin path exercised the
whole loop on emulator-5554 - accept, place, resolve, launch - and took the ticket to `Verified` with no
physical device and no Play services.

**How to apply:** for anything touching the launcher's pin-hosting path, drive the app's own
"Add to Home Screen" entry instead of hunting for a third-party publisher. It is the same platform path.
The one thing it does not prove is a genuinely foreign publisher's request. Check the home role first -
`adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN`
- because the whole flow is inert unless it names our `LauncherHomeActivity`.
