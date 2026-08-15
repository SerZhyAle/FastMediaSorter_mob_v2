---
name: launcher-desktop-device-test-setup
description: Reaching the launcher desktop - check enabledComponents first, because once launcher mode has ever been on, am start opens the desktop with no HOME role; only a virgin device needs the onboarding walk
metadata:
  type: feedback
---

Before assuming a launcher-desktop test is expensive or blocked, **read the component state**:

```
dumpsys package com.sza.fastmediasorter.debug   ->  enabledComponents:
    com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
    com.sza.fastmediasorter.LauncherPlaceShare
```

`LauncherRoleManager.isModeEnabled()` reads exactly this, so their presence means launcher mode is ON
even when the HOME role belongs to another launcher. `LauncherHomeActivity` is `exported="true"`, so
once enabled it opens directly:

```
am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
```

**No HOME role grant, no chooser, no onboarding.** Verified 2026-08-14 on `RFCR110NBQJ` during the
S1616/S1585 sweep, where the brief forbade role changes - the whole test ran anyway.

`launcher_role_prefs.xml` is **not** the mode flag: it only holds the onboarding role-request
bookkeeping and is routinely empty (`<map />`) while the mode is on. Reading it as "mode off" is the
trap that makes this look blocked.

**Only when the components are absent** does the expensive path apply: the activity ships
`android:enabled="false"`, `adb shell pm enable` is refused (`Shell cannot change component state`),
and the sole route is in-app - welcome page 1 "Use as home screen" (or Settings > General) ->
finish onboarding -> accept the system Home chooser. Cancelling the chooser leaves the component
disabled and costs a restart of the whole sequence. The permission-controller activity can wedge on
top and survives `force-stop` of our package; clear it with
`am force-stop com.google.android.permissioncontroller`.

**Two desktop-reading gotchas found in the same run:**
- `uiautomator dump` fails with `ERROR: could not get idle state` - the animated wallpaper never
  idles. Screenshots work; take bounds from the grid instead. Cells lay out on a fixed pitch
  (landscape: origin x=109, 265 px per column/row), so `rowIndex`/`colIndex` from `launcher_cells`
  converts straight to tap coordinates.
- Desktop sections collapse, and a collapsed section renders its header with **zero** cells beneath.
  A cell that exists in `launcher_cells` but is missing on screen usually means a collapsed section
  or an overlap with a wider gadget - not a bug. Tap the section title to expand.

Related: [[feedback_never_grant_system_roles_on_owner_phone]], [[project_spec_all_concurrent_tree_red]].
