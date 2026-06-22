---
name: quest-panel-not-introspectable
description: Quest 2D app panels can't be inspected via uiautomator/screencap; reproduce XR-gated UI on a phone emulator instead
type: reference
---

Meta Quest renders the app's 2D panel inside its own VR compositor, so standard device tooling cannot inspect it:
- `uiautomator dump` returns only `com.oculus.vrshell` with `[0,0][1,1]` bounds - never the app's view tree.
- `adb exec-out screencap` returns the stereo eye-buffer (dual fisheye), not a flat panel - layout is unreadable.

So for an XR-only / XR-gated UI bug (e.g. the 3D-VR settings detail groups that only show when `xrPresent && masterOn`):
- Reproduce the layout on a **phone emulator** with the `noLegal` flavor. XR detection there is `NONE`, so to force the XR-gated UI, temporarily patch the gate in code (e.g. `XrEnvironmentDetectorImpl.detect()` or the fragment's `xrPresent`/`showDetails`), build, inspect with clean 2D screenshots + `uiautomator`, then revert the patch.
- Caveat: forcing the full `XrEnvironment.VR_QUEST` on an emulator activates XR-dependent components that crash on non-XR hardware - force the narrowest flag (just the UI-visibility gate), not the whole environment.
- For genuinely Quest-only rendering differences (renders on phone, breaks on Quest), the only channel is on-device `Timber` logging - instrument measured view heights, have the owner reproduce on the Quest, harvest the log.

**Why:** S0606 - a "3D-VR group fills empty to the bottom in landscape" bug reproduced only on Quest with the master toggle ON; the 2-column layout rendered perfectly on a phone, isolating it to Quest-specific rendering.
**How to apply:** Before trying to drive/screenshot the app on a Quest, expect introspection to fail; pivot to emulator-with-forced-gate or on-device logging.
