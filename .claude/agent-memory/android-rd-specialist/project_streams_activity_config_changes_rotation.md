---
name: streams-activity-config-changes-rotation
description: StreamsActivity (and key activities) use configChanges=orientation; orientation-dependent layout must recompute in onConfigurationChanged, not only on recreate
type: feedback
---

Key activities in this app declare `android:configChanges="orientation|screenSize|keyboardHidden"` in the
manifest (e.g. `StreamsActivity`, the player family), so they are NOT recreated on rotation - they handle
the config change themselves. Any orientation-dependent layout decision (RecyclerView column span,
list/grid layout-manager, toolbar label visibility) computed only in `setupViews`/`applyMode` will be stale
after a rotation and the change appears to "not work" while compiling and looking correct in one orientation.

**Why:** S0692 (landscape multi-column stream list) compiled and unit-passed but showed one column in
landscape on device - because `StreamGridModeManager.applyMode` (where the span is computed) only re-runs on
a display-mode toggle or first state, never on rotation. Found only by rotating the emulator.

**How to apply:** When a spec is "different layout in landscape vs portrait", check the target Activity's
manifest `configChanges`. If it includes `orientation`, add an `onConfigurationChanged` override that
recomputes/re-applies the orientation-dependent layout (mirror `StreamGridModeManager.onConfigurationChanged`).
Always device-test the rotation explicitly - a passing build + correct portrait view is not enough. Note: the
emulator `sdk_gphone16k` is nearly square (2076x2152), so landscape only marginally widens; column math still
resolves to >1, but verify on it.
