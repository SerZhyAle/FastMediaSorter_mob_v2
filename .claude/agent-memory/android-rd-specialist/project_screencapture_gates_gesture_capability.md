---
name: screencapture-gates-gesture-capability
description: fms.screenCapture=off (default) compiles the standard gesture-overlay capability out of the standard APK; S0621/S0622/S0623 device tests need -P fms.screenCapture=on
metadata:
  type: project
---

The default standard debug build ships with `gradle.properties` `fms.screenCapture=off`, which unmounts the `standardScreenCapture` source set. With it off, the left-edge **gesture-overlay** capability (Settings > Operations gesture group, MediaProjection screenshot path, app-launch panel) is **absent from the standard APK** - the controller class is in no dex, the Operations gesture group does not render, and zero device-test probes fire.

**Why:** S0630 split the Play-safe gesture controller into `src/standardScreenCapture`, gated by `fms.screenCapture`. The default-off keeps the gesture feature out of a normal standard build.

**How to apply:** When device-testing any gesture-overlay ticket on a standard build - **S0621** (hotfix-standard-gesture-settings), **S0622** (left-edge-gestures-open-app), **S0623** (app-launch-panel-dialog), and relatives - first rebuild with `-P fms.screenCapture=on`, otherwise the UI under test is simply compiled out and the run is INCONCLUSIVE (not FAIL). Whether the standard **Play release** ships with this flag on is an owner/release-config decision - confirm before reframing these tickets. The noLegal flavor has the capability via its accessibility path regardless.
