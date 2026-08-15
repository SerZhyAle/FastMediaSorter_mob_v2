**Status:** Archived

# S0606 - 3D/VR detail groups not rendered (empty to bottom) when enabled, landscape, on Quest

## Problem

- Device: Meta Quest 3 (XR present), noLegal build, landscape.
- Settings -> Media -> 3D-VR section expanded, 3D/VR master toggle ON.
- Reporter: the expanded group "takes all the space down to the very bottom and is of course empty there"; reproduces ONLY when 3D/VR is enabled (master ON -> detail groups shown).

## Confirmed on device + emulator

- Quest screenshots (master ON): the "Format detection" sub-group (5 toggles, 2 columns) renders, but the lower sub-groups ("Default mode" spinners, "Immersive & diagnostics") do NOT render - replaced by a large empty area down to the panel bottom, plus a grey gradient artifact where the spinner row should start.
- Emulator (Pixel_6, noLegal, landscape, detail groups forced visible via temp patch): the SAME 2-column landscape layout renders fully and correctly - all sub-groups incl. the three spinners (Mono / Flat / Cinema). No empty area, no gradient.
- Conclusion: the landscape XML ([layout-land/fragment_vr_settings_block.xml]) has NO static defect. The failure is Quest-specific rendering of the lower part of `vrDetailGroupsContainer` once it becomes visible.

## Rejected hypothesis

- Original guess: `ViewPager2` capturing the page's vertical scroll on the VR laser pointer -> page won't scroll. Implemented `NestedScrollableHost` wrapper. REVERTED: the bug is not scroll-capture (content is short with empty space below, lower sub-groups missing - not below-the-fold), and it renders fine on a phone. The wrapper did not and could not address this.

## Open hypotheses (need on-device data)

- AppCompatSpinner inside the weighted 2-column rows fails to render / measures to ~0 on the Quest compositor (grey gradient = its half-drawn state). The detection sub-group has no spinner and renders; the first sub-group that fails is the one introducing a spinner.
- A measurement/redraw miss when `vrDetailGroupsContainer` flips GONE -> VISIBLE on the Quest: the new lower content is laid out at 0 height / off-surface.
- Stretch-overscroll (Android 12+) stuck state on the Quest panel.

## Next step

- Instrument `VrSettingsBlockFragment` to log measured heights of `vrDetailGroupsContainer` and each sub-group (and each spinner) right after the master toggle turns the detail groups VISIBLE on the Quest. Build noLegal, have the owner reproduce on the Quest, harvest the log (Quest panel is not introspectable via uiautomator - it reports only `com.oculus.vrshell`; screencap is stereo). The measured heights pinpoint which sub-group/spinner collapses.

## Paused 2026-06-22 (resume here)

- Waiting on the owner's Quest log; the probe step is built and deployed.
- Probe build: noLegal debug v2.60.6220.130 at `c:\GD\WORK\FastMediaSorter\FastMediaSorter_nolegal_debug.apk` (+ Drive .zip) and `DOWNLOADS\`.
- Instrumentation lives uncommitted in the working tree: `VrSettingsBlockFragment.applyState` logs `S0606:` geometry of `vrDetailGroupsContainer` + sub-groups/spinners + ancestors when detail groups become visible. Do NOT revert while BlockNeedUserTest (tag invariant).
- Resume: owner reproduces on Quest (landscape, Settings -> Media -> 3D-VR, master ON -> empty-to-bottom) and provides the log; read the `S0606:` lines to find which sub-group/spinner collapses to ~0 height or off-surface; write the targeted fix; strip the probe instrumentation.

## Side finding (parked separately)

- `SettingsActivity.setupConnectedTabs` posts a runnable ([SettingsActivity.kt:285]) that reads `binding` with no lifecycle guard -> `IllegalStateException: Binding is only valid between onCreateView and onDestroyView` on a launch/recreate race (hit repeatedly when direct-launching SettingsActivity). Unrelated to this ticket.
