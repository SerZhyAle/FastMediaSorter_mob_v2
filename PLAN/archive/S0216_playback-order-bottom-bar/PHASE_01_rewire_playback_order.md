# Phase 1: Rewire Playback Order UI

- [x] Add a shared playback-order UI mapper for icon and label resources.
- [x] Route `exo_repeat` to `PlayerActivity.onPlaybackOrderClicked()`.
- [x] Update the bottom-bar button icon and content description from `PlayerState.playbackOrderMode`.
- [x] Remove the top command-panel playback-order button and adaptive planner entry.
- [x] Validate with `./gradlew.bat :app_v2:compileStandardDebugKotlin`.