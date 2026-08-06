---
name: launcher-desktop-device-test-setup
description: Reaching the launcher desktop on a device costs a full onboarding walk - the HOME activity ships disabled and adb cannot enable it; budget for it before promising a launcher screenshot
metadata:
  type: feedback
---

Any device test of a launcher-desktop screen (cell editor, gadgets, taskbar, contact cells) needs launcher mode turned on **in-app first**, and that is a multi-step walk - not a one-liner. Budget for it, or say up front that the shot will be deferred.

**Why:** `LauncherHomeActivity` is declared `android:enabled="false"` in `src/launcherEnabled/AndroidManifest.xml`; only the app itself flips it, via `LauncherRoleManager.enableMode`. `adb shell pm enable <pkg>/...LauncherHomeActivity` is refused - `SecurityException: Shell cannot change component state` - so there is no shell shortcut, and `am start` on it answers `Activity class ... does not exist` until the app has enabled it. On 2026-08-06 an S0428 device run burned a long sequence of turns discovering this and still never reached the screen.

**How to apply:**
- The only path is: launch → welcome page 1 switch "Use as home screen" (or Settings > General > "Make this app the home screen") → finish onboarding → accept the system Home-app chooser. Toggling the switch and then cancelling the chooser leaves the component disabled, and the chooser lists the app only once the component is enabled - so cancelling once costs a restart of the whole sequence.
- The permission-controller activity opened by that flow can wedge itself on top of the app's own task and survives `force-stop` of our package; clear it with `am force-stop com.google.android.permissioncontroller`.
- When a phase's UI gate wants a screenshot of a launcher screen and this walk is not budgeted, write the deferral and its reason into the Step Log rather than skipping it silently - see [[feedback_verify_full_evidence]].
- A shared emulator makes this worse: a sibling session can reset app data mid-run and put the app back at first-run onboarding. See [[project_spec_all_concurrent_tree_red]].
