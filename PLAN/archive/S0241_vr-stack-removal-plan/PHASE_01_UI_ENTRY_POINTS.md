# S0241 Phase 01 - Remove VR UI Entry Points

Ticket: S0241
Phase status: Done
Goal: remove user-visible immersive launch affordances from the flat player UI before deeper VR code removal starts.

## Completed In This Pass

- [x] Removed `btnApplyAnd3D` from `dialog_playback_control.xml` and `layout-land/dialog_playback_control.xml`.
- [x] Removed the `Apply and 3D` click path from `PlaybackControlDialogFragment.kt`.
- [x] Removed the dedicated `btn3dVrCmd` button from both `activity_player_unified.xml` variants.
- [x] Removed the shared `btn3dVrCmd` binding path from `PlayerBindingSafeViews.kt` and `CommandPanelController.kt`.
- [x] Removed the remaining command-panel VR overflow entry from `overflow_menu_player.xml`, `CommandPanelLayoutPlanner.kt`, `CommandPanelController.kt`, and `PlayerCommandPanelCallbackImpl.kt`.
- [x] Adapted `VrToggleButtonManager.kt` and `VrPlayerActivity.kt` so the legacy VR flavor still compiles while the shared button is being phased out.
- [x] Removed orphaned plain-player immersive helper methods from `PlayerActivity.kt` and the matching override from `VrPlayerActivity.kt`.
- [x] Removed the immersive-only settings toggles from `PlaybackSettingsFragment` / `VideoSettingsFragment` and their portrait/landscape layouts.
- [x] Deleted the now-unused EN/RU/UK strings for `settings_vr_auto_immersive*`, `settings_vr_disable_3d*`, and `settings_vr_show_fps*`.

## Validation So Far

- PASS: `git push origin refs/heads/archive/vr-stack-2026-05:refs/heads/archive/vr-stack-2026-05 refs/tags/vr-stack-2026-05-final`
- PASS: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0241 -Status Tactical`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt" "PlaybackControlDialogFragment" "Removed the Apply and 3D immersive shortcut from the playback control dialog"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "S0241 Phase 1" "Removed the dedicated VR command-bar button and decoupled the legacy VR toggle manager from the shared binding"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt" "S0241 Phase 1" "Removed the remaining VR command-panel and overflow entry points from planner and callback flow"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "S0241 Phase 1" "Removed orphaned plain-player immersive helper methods after deleting shared VR UI entry points"`
- PASS: `pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt" "S0241 Phase 1" "Removed immersive-only VR toggles from playback/video settings screens and deleted their localized strings"`
- PASS: `./gradlew.bat :app_v2:assembleDebug`
- PASS: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_vr_auto_immersive"`
- PASS: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_vr_disable_3d"`
- PASS: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_vr_show_fps"`

## Notes

- The command-panel and stereo-dialog immersive entry points are now gone from the shared player UI.
- Phase 01 is complete; the next local slice is Phase 02 main-side routing, transitions, and settings persistence cleanup.