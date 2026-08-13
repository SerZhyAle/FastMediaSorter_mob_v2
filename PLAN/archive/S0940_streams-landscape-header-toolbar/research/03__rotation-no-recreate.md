# Research 03 - Streams window does not recreate on rotation

Strategic §6 item: 3 (state on rotation) - resolves ADR-1.

## Question

Does the streams window recreate on rotation, so a `layout-land` resource variant would auto-apply, and does search/filter/sort state survive?

## Findings (from code, 2026-07-04)

- Manifest declares the activity with `android:configChanges="orientation|screenSize|keyboardHidden"`.
  - `app_v2/src/main/AndroidManifest.xml` - `<activity android:name=".ui.streams.StreamsActivity" .. android:configChanges="orientation|screenSize|keyboardHidden" ..>`.
- `StreamsActivity.onConfigurationChanged` calls `super` and only `gridModeManager.onConfigurationChanged()` (recomputes grid column span). No `setContentView`, no re-inflate, no recreate. Comment cites S0692: the activity handles orientation itself so the span is recomputed rather than recreating.
- Consequence 1: on rotation of an already-open window the resource variant (`res/layout` vs `res/layout-land`) is NOT re-selected - the view hierarchy inflated at launch stays. A `layout-land/activity_streams.xml`-only edit would only take effect when the window is launched directly in landscape.
- Consequence 2: because there is no recreate, the search text and filter/sort selections survive rotation automatically - the same view instances remain in memory. No SavedState plumbing needed.
- Motivation for no-recreate: streams have active playback (mini-control, off-screen capture host). Recreate on every rotation would tear down the live stream - the exact regression S0692 removed.

## Decision

- Keep `configChanges` (no recreate) - do NOT remove `orientation|screenSize`.
- Relocate the search/filter/sort control group programmatically between two slots (below-toolbar for portrait, in-header for landscape) via a dedicated placement manager, invoked at activity setup and inside `onConfigurationChanged`.
- Both `res/layout/activity_streams.xml` and `res/layout-land/activity_streams.xml` must carry the identical two-slot host structure, since whichever is inflated at launch (by launch orientation) must let the manager place controls into either slot.
- Owner confirmed scope "Full: applies on rotation too" (2026-07-04).

## Impact on plan

- ADR-1 rewritten: programmatic relocation, not resource-variant swap.
- Introduces a new `StreamsControlsPlacementManager` in `ui/streams/helpers/` (public API change - catalog regen in cleanup phase).
- `StreamsActivity` gains manager instantiation + a call from `onConfigurationChanged`.
