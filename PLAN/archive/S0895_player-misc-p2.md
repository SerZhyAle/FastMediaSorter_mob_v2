# S0895 - Player subsystem misc: dead paths, scope races, singleton clobber (P2 cluster)

**Ticket:** S0895
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: разное по player-подсистеме - мёртвые пути, гонки на скоупах, single-slot клоббер.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:414 - onDestroy final-position save is cancelled by serviceScope.cancel() on the next statement - the destroy-edge save is dead in practice
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt:40 - PlayerEntryCoordinatorImpl is dead routing code - Hilt-bound but has no production caller
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt:100 - BD-TS local path builds ExoPlayer inline, skipping createPlayer() bookkeeping (duplicate creation path)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt:227 - Orphan creation path: releaseResources() constructs a brand-new VideoPlayerManager during Activity.onDestroy when none exists (dead catch never fires)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt:124 - Unguarded suspend settings write between successful VR dispatch and finishAndRemoveTask() - failure path skips 2D-host teardown and crashes via uncaught exception
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt:172 - 500 ms position poll keeps running in background while sheet host is stopped - keyed to isPlaying only, never to view visibility
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt:568 - Three bare `lifecycleScope.launch{...collect}` sites drive view-bound work (GL video effects, Glide image re-display) with no repeatOnLifecycle - keeps collecting and re-rendering while the Activity is stopped (baselined in unsafe-collect gate, still live)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt:183 - Stream error-recovery jobs (up to 5 s / 16 s delayed) act on whatever exoPlayer is current, not the errored instance - yanks the next file's restored position
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt:309 - release() unconditionally calls MemoryEnduranceTracker.endScenario(), clobbering foreign scenarios (e.g. VID-playback) since the tracker is a single-slot singleton

## Related

- S0878 (audit tail container - triage source); S0893/S0894 (sibling player clusters).
- Dead-code items (:40, :227) - grep PLAN/ for scaffolding tickets before deleting (feedback_dead_code_vs_active_tickets).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878, S0893, S0894

<!-- auto-approved by /spec-all - 2026-07-03 -->

**Tactical plan:** `PLAN/S0895_player-misc-p2/INDEX.md`

## Last Audit

**2026-07-03** - all 9 findings CONFIRMED against live code (full re-verification detail in `PLAN/S0895_player-misc-p2/INDEX.md` pre-flight section) and fixed. No stale findings in this ticket.

Files touched:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` - destroy-edge save moved to a dedicated `SupervisorJob` scope that survives `serviceScope.cancel()`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt` - deleted (confirmed dead: grep found zero injection sites outside the Hilt binding and its own file; grepped `PLAN/` for scaffolding references, only the source triage ticket S0878 and this ticket itself referenced it).
- `app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerContractsModule.kt` - removed the dead `bindPlayerEntryCoordinator` binding, kept `bindStereoDetectionFacade`.
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinatorTest.kt` - deleted (its only subject, `PlayerEntryCoordinatorImpl`, no longer exists).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` - `createPlayer()` gained an optional `mediaSourceFactory` override parameter (backward compatible - the one pre-existing call site is unaffected).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt` - BD-TS branch now calls `createPlayer(..)` instead of hand-building a second `ExoPlayer`; also fixes a latent silent-no-op when `currentPlayerView` was null.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` - `releaseResources()` uses `if (activity._videoPlayerManager != null)` instead of a try/catch that never fired.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt` - `markPromptDismissed()` wrapped in try/catch so `finishAndRemoveTask()` always runs after a successful VR dispatch.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt` - position poll now gated on `hostStarted && isPlaying` via `onHostStart()`/`onHostStop()`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingBottomSheetFragment.kt` - forwards `onStart()`/`onStop()` to the ViewModel.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` - 3 bare collectors wrapped in `repeatOnLifecycle(Lifecycle.State.STARTED)`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` - `onPlayerError()` captures the errored player instance and guards both delayed-recovery branches against staleness.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt` - `release()` only ends the `MemoryEnduranceTracker` scenario this instance actually started (`ownsEnduranceScenario` flag).

Evidence:
- Build: `.\a.ps1 fc` (standard debug, code+resources) - PASS, no new warnings attributable to this change.
- Test compile: `.\gradlew.bat :app_v2:compileStandardDebugUnitTestKotlin` - PASS after deleting the orphaned `PlayerEntryCoordinatorTest.kt`; `PlayerLifecycleManagerNearEndTest.kt` covers a different helper (`PlaybackCompletionDetector`), unaffected by this ticket's `PlayerLifecycleManager` edit.
- Gates: `assert-neuroslop.ps1`, `assert-deprecated-pm-flags.ps1`, `assert-listener-symmetry.ps1` scoped to all touched files - PASS, 0 new occurrences on every dimension.
- All 9 fixes are structural/logic corrections (null-check idiom matching an established pattern, scope independence, try/catch guaranteeing a downstream call, boolean-flag gating, reference-identity staleness guard, official `repeatOnLifecycle` idiom) provable by code inspection and build - unlike S0896's audio-focus/multi-window findings, none require OS-level runtime interaction (audio focus arbitration, split-screen window management) that can only be confirmed on a physical device. No `BlockNeedUserTest` gate applied.

**Verdict:** Verified.

**Parked:** none. No out-of-scope findings surfaced during this ticket.
