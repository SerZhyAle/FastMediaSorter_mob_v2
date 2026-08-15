# S0241 Phase 03 - Extract Main-Side XR and Render Helpers

Ticket: S0241
Phase status: In Progress
Goal: remove legacy XR-only helpers and shared VR-only player-setting surfaces from `src/main` while keeping the flavor/source-set layout available for the rewrite.

## Completed In This Pass

- [x] Removed the `MainActivity` XR window-resize hook that persisted Quest panel size from `src/main`.
- [x] Deleted the dead `core/xr/XrDeviceDetector.kt` helper after its last main-side call site disappeared.
- [x] Deleted the dead `core/xr/VrPanelSizePreference.kt` helper after its last main-side call site disappeared.
- [x] Removed the dead entry-side `VrTaskTransition` API (`shouldEnterImmersiveTask` / `enterImmersive`) while keeping the vr-side exit helpers that the current VR build still needs.
- [x] Deleted the obsolete JVM tests for `XrDeviceDetector` and `VrPanelSizePreference`, then narrowed `VrTaskTransitionTest` to the surviving exit contract.
- [x] Removed the unused `resumePlayerIntent` branch from `VrTaskTransition.exitImmersiveToPanel`; the surviving VR runtime only returns to `MainActivity` through that helper.
- [x] Removed VR-only content-type, rendering-mode, and IPD controls from the shared `PlaybackControlDialog`, then deleted the matching rendering-mode row from shared video settings layouts in both portrait and landscape.
- [x] Deleted the dead EN/RU/UK string resources and arrays that only backed the removed shared VR-only player-setting controls.
- [x] Removed the dead shared `vrAutoDetectFormat` setting surface, then made `VrSessionLifecycleManager` keep launch auto-detection enabled locally so `vrDebug` keeps compiling without reintroducing the deleted shared toggle.
- [x] Temporarily aligned `vr` and `noLegal` flavor runtime flags with the standard player path (`SUPPORT_VR_PLAYER=false`, `PLAYER_ACTIVITY_CLASS=PlayerActivity`) so the VR-flavored builds stay installable while the dedicated VR runtime is being rewritten.
- [x] Fixed VR/noLegal build and install scripts to use the shared `com.sza.fastmediasorter(.debug)` package policy instead of the stale `.vr` / `.nolegal` package names.

## Remaining Phase 03 Work

- [ ] Strip the remaining shared VR-only settings and HUD surfaces from `src/main` (`vrAutoDetectFormat`, immersive-only messaging, and adjacent player/settings copy) without changing the rewrite target source-set layout.
- [ ] Audit `ui/player/render/stereoscopic/*` and adjacent main-side helpers for legacy XR-only abstractions that are no longer needed by flat single-eye stereo.
- [ ] Re-check which shared persistence keys and vr-side exit plumbing still need to stay in place for the rewrite, accepting temporary `vr` / `noLegal` red builds when a VR-only surface is being extracted.

## Validation So Far

- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `./gradlew.bat testNoLegalDebugUnitTest --tests "com.sza.fastmediasorter.ui.player.entry.VrTaskTransitionTest"`
- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `./gradlew.bat :app_v2:assembleVrDebug -Pchaquopy.enabled=false`
- PASS: `./gradlew.bat :app_v2:assembleNoLegalDebug`

## Notes

- This phase does not remove the VR flavor source sets or the project structure.
- The target is the VR-only code that still lives in `src/main`, even if that temporarily makes `vr` / `noLegal` unbuildable during the extraction window.
- Until the rewrite lands, `vr` and `noLegal` now ship the standard shared runtime path and keep only the VR-flavored shell / source-set overlays that are still needed for packaging and visuals.
- `ui/player/render/stereoscopic/*` was re-checked during this pass and left in place because the current VR runtime still imports that surface.