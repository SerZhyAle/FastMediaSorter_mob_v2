---
name: am-start-refused-for-non-exported
description: On the Samsung test device adb shell am start CANNOT launch a non-exported activity - it throws SecurityException, so trampoline entry points must be driven through their real trigger
metadata:
  type: feedback
---

`adb shell am start -n <pkg>/<non-exported activity>` is **refused** on `RFCR110NBQJ`
(Samsung SM-G996U1, Android 15):

```
SecurityException: Permission Denial: starting Intent { cmp=.../.widget.PhotoCaptureLaunchActivity }
from null (pid=..., uid=2000) not exported from uid 10371
```

Do not plan a device test around starting a trampoline or launch activity directly. Drive the real
trigger instead - the gesture, the widget, the shortcut - or accept that the path is untestable and
say so.

**Why:** On 2026-08-11 I briefed a device run claiming the shell uid holds `START_ANY_ACTIVITY` so a
non-exported activity would start fine. It does not on this handset. The runner's first five
"shots" through `am start` were silent no-ops, and it caught them only because it counted the files
in the target folder instead of trusting the command's exit code. Five fabricated data points came
within one careless step of entering a verdict. The general lesson is the sharper one: a device
action is proven by its **effect** (a new file, a changed UI node, a log line), never by the exit
code of the command that requested it.

**How to apply:** When an entry point is `android:exported="false"`, plan for the real trigger from
the start and tell the runner which one. If the trigger is flavor-gated, check whether it is
compiled into the installed build before assuming it is absent - the standard flavor gates its edge
overlay behind the gradle property `fms.edgeGestureOverlay`, which defaults to off, yet the build in
use on 2026-08-11 had it on. Verify, do not infer from the default.
Related: [[reference_test_device_galaxy_s21]], [[reference_trigger_widget_only_features_on_emulator]].
