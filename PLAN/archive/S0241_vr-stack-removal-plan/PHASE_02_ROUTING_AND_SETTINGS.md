# S0241 Phase 02 - Remove Main-Side VR Routing and Transition Hooks

Ticket: S0241
Phase status: Done
Goal: stop `src/main` code from routing users into the immersive task or depending on removed VR UI gates, while leaving the VR build structure in place for a later rewrite.

## Completed In This Pass

- [x] Removed the `MainActivity` branch that forced landscape on XR headsets.
- [x] Removed the direct browse-to-immersive launch path from `BrowseEventHandler`; browse playback now always opens the flat player and still forwards the detected stereo hint.
- [x] Removed the remaining main-side `VrTaskTransition` entry points from `MainActivity` and `BrowseManagerInitializer`; slideshow, random play, and draw-overlay now launch the flat player directly.
- [x] Removed the dead `BrowseRoutingDecision` helper and its obsolete focused unit test once browse routing became unconditionally panel-only.
- [x] Removed the main-side `disable3dVr` playback-dialog plumbing; stereo controls now depend only on VR flavor support or detected stereo content.

## Deferred Follow-Up

- Shared VR settings persistence (`AppSettings` / `SettingsRepositoryImpl` / backup mapping) stays intact for now because the VR build and project structure remain in place.
- `VrTaskTransition.kt` stays in place because the remaining vr-side exit flows (`VrPlayerActivity` / `VrSessionLifecycleManager`) still depend on it.

## Validation So Far

- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt" "S0241 Phase 02" "Removed the VR-headset forced-landscape branch from MainActivity"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt" "S0241 Phase 02" "Removed the direct browse-to-immersive launch path and always route Browse playback into the flat player"`
- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `./gradlew.bat testNoLegalDebugUnitTest --tests "com.sza.fastmediasorter.ui.browse.managers.BrowseRoutingDecisionTest"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" "S0241 Phase 02" "Moved slideshow, random play, and draw-overlay entry points to flat-player-only launches"`
- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `./gradlew.bat :app_v2:assembleDebug`

## Notes

- `VrForcedFormatResolver` was intentionally left in place during this pass because it still feeds flat stereo-format resolution in player-side code.
- `VrTaskTransition.kt` can no longer launch immersive entry from main-side code, but it still backs vr-side exit routing; deleting the file now would break that return path.
- The removal of persisted VR settings was intentionally deferred after the scope clarification: we are extracting legacy main-side code first, not collapsing the VR build surface.