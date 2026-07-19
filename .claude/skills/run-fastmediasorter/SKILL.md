---
name: run-fastmediasorter
description: Build, install, launch, screenshot, and drive the FastMediaSorter Android app (standard-debug) on a connected emulator/device. Use when asked to run, start, build, install, launch, screenshot, smoke-test, or drive the app on a device.
---

# Run FastMediaSorter (standard-debug, on-device)

FastMediaSorter is an Android app (`app_v2`, flavor `standard`, build type `debug`,
applicationId `com.sza.fastmediasorter.debug`). It is **driven on a real emulator/device**
via adb - there is no headless mode. The agent handle is
`.claude/skills/run-fastmediasorter/smoke.ps1` (launch + screenshot + crash-scan) plus
`scripts/devtest/adb.ps1` for ad-hoc interaction (launch/tap/text/key/shot/log/current).

Environment: Windows dev machine, PowerShell 7, Android SDK at `%LOCALAPPDATA%\Android\Sdk`
(adb is **not** on PATH - the scripts auto-discover it). A booted emulator or attached device
is required. Paths below are relative to the repo root.

## Prerequisites

- Run the mandatory `document-registry` loop before the device operation and at handoff. For routine smoke runs, query `-ProductArea testing` and `-Trigger workflow`; review returned records without editing them unless the run exposes a documentation-impacting result.
- PowerShell 7 (`pwsh`), Android SDK platform-tools (adb auto-discovered), JDK/Gradle via the
  repo's `gradlew` wrapper (invoked by the builder script - never call gradle directly).
- A booted emulator or attached device. Verify:

```bash
pwsh -NoProfile -File scripts/devtest/adb.ps1 current
```

- Optional, only for rich UI walks (element tree, not needed for smoke): the **mobile-mcp**
  server (`.mcp.json` + Node/npx). See `/spec-test-device` for the mobile-mcp scenario pattern.

## Build + install

Builds the standard-debug APK and installs it on the connected device (set `ANDROID_SERIAL`
first when more than one device is online - the builder has no device flag):

```bash
pwsh -NoProfile -File scripts/builders/build-standard-device.ps1
```

## Run - agent path (driver)

One command: pre-flight → launch the explicit MainActivity → screenshot to `temp/scratch/` → scan the
launch window for FATAL/crash/ANR. Exit 0 = alive and rendering.

```bash
pwsh -NoProfile -File .claude/skills/run-fastmediasorter/smoke.ps1
```

Add `-Build` to build+install first, `-DeviceId <id>` to pin a device. Screenshot lands at
`temp/scratch/<device>_<TS>.png`.

Ad-hoc interaction afterwards via the project's adb swiss-army (resolve targets, don't guess
coordinates):

```bash
pwsh -NoProfile -File scripts/devtest/adb.ps1 launch          # explicit MainActivity
pwsh -NoProfile -File scripts/devtest/adb.ps1 shot            # screenshot -> temp/scratch/
pwsh -NoProfile -File scripts/devtest/adb.ps1 current         # focused activity
pwsh -NoProfile -File scripts/devtest/adb.ps1 log -Tail 200 -Grep "FATAL|Exception"
```

## Run - human path

Build+install as above, then launch and watch the window on the emulator:

```bash
pwsh -NoProfile -File scripts/devtest/adb.ps1 launch
```

## Gotchas

- **adb is not on PATH** on this machine. `adb.ps1` / `device-ready.ps1` auto-discover it at
  `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Calling bare `adb` fails.
- **Fresh install lands on the onboarding screen, and tapping through it is flaky.** A first-run
  install shows `WelcomeActivity`; launching `MainActivity` still routes there. Programmatic taps
  on «ENABLE ALL» / «NEXT» (both mobile-mcp and raw `adb input tap`, at correct coordinates) did
  **not** advance it in automation. Reliable bypass: set the completion pref while the app is
  stopped, then launch lands on `MainActivity`:

```bash
adb=~/AppData/Local/Android/Sdk/platform-tools/adb.exe
pkg=com.sza.fastmediasorter.debug
xml='<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="welcome_completed" value="true" /></map>'
b64=$(printf %s "$xml" | base64 -w0)
"$adb" -s emulator-5554 shell am force-stop $pkg
"$adb" -s emulator-5554 shell "run-as $pkg sh -c 'mkdir -p shared_prefs; echo $b64 | base64 -d > shared_prefs/welcome_prefs.xml'"
```

- **`adb shell screencap` intermittently returns an all-black PNG** on this emulator after a
  theme/locale change or an activity transition, even while the activity is `Awake` and focused.
  The UI is fine - the capture caught a bad frame. **Trust `mobile_list_elements_on_screen` (the
  a11y tree) over the screenshot** for verifying state; re-take the screenshot if you need a real
  image.
- **LeakCanary launcher trap.** The debug build registers LeakCanary's launcher activity, so a
  plain launcher-intent launch can open LeakCanary instead of the app. `adb.ps1 launch` starts
  the explicit `MainActivity` to dodge this - prefer it over a generic launch.
- **Component names need the code package, not the applicationId.** The applicationId is
  `com.sza.fastmediasorter.debug` but classes live in `com.sza.fastmediasorter` (no `.debug`).
  An `am start -n <pkg>/.ui...` short form expands the dot against the applicationId and fails
  ("Activity class ... does not exist"); use the FQCN
  `com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui...`.
- **Emulator NAT can't reach LAN.** SMB resources on `192.168.x` are unreachable from the
  emulator; public SFTP/FTP endpoints are reachable. Relevant when exercising network resources.

## Troubleshooting

- `device-ready` exit `2` (no online device) → boot an emulator / attach a device, re-run.
- exit `3` (multiple devices) → pass `-DeviceId <id>`.
- exit `4` (package not installed) → run `smoke.ps1 -Build`, or the builder script first.
- Builder fails with a transient "daemon stopped" → re-run the builder (known flaky).
