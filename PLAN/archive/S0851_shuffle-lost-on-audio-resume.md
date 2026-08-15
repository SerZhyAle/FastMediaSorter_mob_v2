# S0851 - Shuffle order not applied when resuming music playback

**Ticket:** S0851
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-01
**Tier:** unset

<!-- discovered by /log-reader - 2026-07-01 (user-reported, corroborated by session logs) -->

## 0. Raw capture

User report (RU, verbatim): "не работает shufle при возобновлении проигрывания музыки".

Symptom: SHUFFLE playback-order mode is not honoured when audio playback is *resumed* (returning to the player / reconnecting to the running `AudioPlaybackService`) - tracks advance in list order instead of shuffled order.

## 1. Evidence

- `AudioServiceController.applyPlaybackOrderMode` ([app_v2/.../ui/player/helpers/AudioServiceController.kt:273](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt#L273)) sets `player.shuffleModeEnabled` only for a live `mediaController` and only when `mediaItemCount > 1` (S0549 single-item guard: `shuffleModeEnabled = !singleItem`).
- `connectForStatus` ([same file:246](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt#L246)) reconnects to the service for status only - it does **not** re-apply the playback-order mode.
- Re-apply is driven from `PlayerActivity.applyPlaybackOrderModeToActivePlayer` ([PlayerActivity.kt:1153](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt#L1153)), called from `updateTrackButtonsVisibility` ([:911](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt#L911)) and `syncPlaybackOrderForCurrentResource` ([:1168](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt#L1168)). Suspected gap: on resume the mode is re-applied before the reconnected controller's timeline is populated (`mediaItemCount <= 1` -> `singleItem=true` -> `shuffleModeEnabled=false`), or not re-applied against the freshly reconnected `mediaController` at all.
- Session logs show repeated `NowPlayingManager: Initializing mini now playing bar listeners` (re-entry into player) with background `AudioPlaybackService` running - the resume/reconnect path is exercised but no shuffle re-arm is logged.

## 2. Open questions

- Exact meaning of "возобновление": resume after pause, re-open player over running service, or resume from notification/widget? Confirm reproduction path with owner.
- Interaction with the S0549 single-item degeneration: when audio is a single `MediaItem`, shuffle is delegated to app-level `PlayerNavigationCoordinator` order model - verify that model is restored on resume (see [PlayerNavigationCoordinator.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt)).

## 3. Fix (implemented - needs device verification)

Root cause confirmed in code: on resume over a running `AudioPlaybackService` a fresh `MediaController` first reports `mediaItemCount <= 1`, so `AudioServiceController.applyPlaybackOrderMode` (called during bind via `updateTrackButtonsVisibility`) reads "single item" and sets `shuffleModeEnabled = false`; nothing re-applied it once the timeline synced.

Implemented in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` (single file):
- Extended the existing `servicePlaybackListener` (no new listener - listener-symmetry unchanged) with `onTimelineChanged`, calling a one-shot re-arm helper.
- `reapplyServiceOrderModeOnTimelineReady()` re-applies the persisted `PlaybackOrderMode` once the reconnected timeline is populated (`mediaItemCount > 1`), guarded by a per-binding flag reset in `bindServicePlaybackListener` on player swap - fires at most once per reconnect, does not re-roll the shuffle order.
- Covers both resume paths (same-controller reattach and fresh reconnect) - both funnel through `bindServicePlayerToView`.
- Single-item timelines (S0549) are intentionally skipped; their app-level shuffle bag rebuilds fresh on relaunch (still shuffles - residual is only the exact prior sequence, self-healing).

Open questions (§2) resolution: the exact "возобновление" path no longer matters (re-arm covers all reconnect paths); single-item order-model restoration is self-healing, not the reported list-order symptom.

## 4. Device test (BlockNeedUserTest)

Real device/emulator, background audio ON: start a multi-track audio playlist, select SHUFFLE, recreate the player over the running service (background+return, or rotate/theme-change), then tap Next. Expect shuffled order, not list order. Logcat should show `Timber.d("S0851: re-armed order mode ..")` on resume. Not statically verifiable.

## Related

- S0549 (single-item timeline shuffle/loop degeneration - REPEAT_MODE_OFF + app-level advance).

## Last Audit

### Manual - 2026-07-09 - emulator-5554 (Android 13, x86_64), standard debug (com.sza.fastmediasorter.debug)

Verdict: PASS.

Setup: seeded a 6-track local audio resource `S0851_Audio` (/storage/emulated/0/Download/S0851_Audio, tracks 01_sheeran..06_adele), enabled SHUFFLE (dice icon), enabled "Фоновое воспроизведение" (Settings -> Плеер -> Background Playback = `enablePersistentAudioPlayback`) so local audio routes through `AudioPlaybackService` as a multi-item timeline.

Precondition discovered: the S0851 fix path only engages when Background Playback is ON. With it OFF, local audio plays per-file via `PlayerNavigationCoordinator` (single-item, S0549 self-healing path) - the re-arm probe never fires, though shuffle is still preserved on resume (verified separately: Next went 01 -> 05 -> 04, indices 0 -> 4 -> 3).

Resume test (Background Playback ON): playing 01_sheeran via service (playlist size=6), backgrounded via HOME (3 s, service kept playing), returned to app -> PlayerActivity rebound and `AudioServiceController` reconnected (fresh `MediaSession onConnect` + `service player bound`). Shuffle (dice) persisted across the resume. Tapped Next.

Result:
- expected: Next follows shuffled order, not sequential list order | actual: `nextFile: index 0 -> 4 / 6` (01_sheeran -> 05_blu "Blu Detiger - Figure It Out"), NOT sequential 02_ariana. PASS.
- expected: S0851 re-arm tag fires after the service reconnect | actual: `S0851: re-armed order mode SHUFFLE on timeline-ready, items=6` logged on the reconnect (also fired on the initial service bind). PASS.

Evidence: temp/S0851/ (logcat_service_check.txt = initial service bind + first re-arm; logcat_resume.txt = reconnect + re-arm + shuffled nextFile; logcat_full.txt = pre-setting per-file/coordinator path for contrast; screenshots via mobile-mcp).

No code, status, or commit changes made by this run.
