# Стратегическая спецификация: S0878 - Mass audit 2026-07-02: хвост (P2-триаж, довер-верификация, непокрытые слои)

**Ticket:** S0878
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - mass audit follow-up 2026-07-02
**Tactical spec:** `PLAN/S0878_audit-mass-2026-07-02-followup/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Mass audit 2026-07-02 (wf_34a4d99d-fbf): Find phase complete (119 unique findings), adversarial verify phase killed by session limit (only 15/~80 verdicts). P0/P1 findings parked as individual bugfix drafts (see catalog, same date). This ticket holds the tail:

1. P2 appendix (34 findings) - triage below, promote or drop each.
2. Verification debt: CLEARED 2026-07-02 (sequential one-skeptic-per-ticket campaign, ~21 agents). All 25 bugfix drafts (S0853-S0877) now carry a verdict in their section 0. Outcome: 23 tickets fully confirmed (43 findings), severity escalations - S0863 error-listener start() and S0875 ImageReader race raised to P0; downgrades - S0867 -> P2 (bounded self-healing lag), S0871 state-race item -> P3 (log-only consumers), S0877 -> P2 (panel); zero tickets archived (one attempted refutation of S0854 was overruled by tie-break code read - see feedback_verify_full_evidence memory). Cross-links recorded: S0854<->S0865 (same root cause: unserialized playVideo coroutine - one fix, plus family sweep of Smb/Sftp/Cloud helpers per S0865 note), S0866 -> S0624 (plausible scan-hang root cause). Remaining debt here: P2 appendix triage below + protocol layers 5/6/7 (startup/perf/R8) never swept + runtime evidence (LeakCanary on confirmed leak tickets) still pending.
3. Coverage note (verbatim): Swept: listener-symmetry, player/Glide ownership, Room main-safety, concurrency + 15 player-host contract units + critic round. NOT swept: protocol layers 5 (startup), 6 (perf), 7 (R8/minified) - static-review dimensions only, no runtime evidence (LeakCanary/benchmarks) gathered.
4. Contract matrix per player host (contract_ok items) - in attachment 02, key contractMatrix.

**P2 appendix (unverified static findings):**

- app_v2/src/main/java/com/sza/fastmediasorter/core/AudioToggleTileService.kt:78 - MediaController built asynchronously can land after onStopListening; next listen cycle overwrites it without release, leaking the controller and its Player.Listener
- app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt:146 - MediaExtractor released only inside the use-block happy path - leaks native extractor when openFileDescriptor returns null or setDataSource/getTrackFormat throws
- app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt:214 - extractPdfInfo leaks the ParcelFileDescriptor when the PdfRenderer constructor throws (corrupt or password-protected PDF)
- app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt:55 - insertAllAsOverrides is non-transactional and its hasOverrides() guard makes a partial apply permanent
- app_v2/src/main/java/com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt:119 - invalidateConnection ignores poolMutex.tryLock() result and unconditionally unlock()s - would corrupt another coroutine's critical section or throw IllegalStateException; latent only (no production subclass)
- app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt:132 - Idle-pool machinery is dead code: connectionPool is never populated, so cleanupIdleFtpConnections() is a permanent no-op that FtpClient still arms on every acquire
- app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt:29 - M3U playlist import writes N rows as N separate transactions (no withTransaction), unlike the sibling mergeCatalog
- app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportFavoritesUseCase.kt:104 - Favorites import inserts row-by-row without a transaction
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:86 - Inline audio plays without ever requesting audio focus, so release has nothing to abandon and playback ignores focus loss (contract item 5)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:242 - inline_audio disk cache has no size bound or eviction: every played/prefetched SMB track is stored in full, forever, until a manual whole-cache wipe or OS storage pressure
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt:60 - playerWarmupJob / audioMetadataEnrichmentJob / lastWarmupSignature written on IO dispatcher but cancelled/read from main thread without synchronization - cancel edge can miss the live job
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt:553 - Warm-up collectors on settings and destinations Flows use bare lifecycleScope.launch{collect} - DataStore and Room upstreams stay actively collected the entire time BrowseActivity sits stopped in the back stack (baselined, still live)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt:90 - Focus-denied early return leaves audioFocusListener set and the denied request never abandoned; stopRecording's pending-guard returns before abandonAudioFocus
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt:153 - Post-stop save pipeline runs on the host's lifecycleScope: teardown after stop silently drops the captured recording and orphans the temp file
- app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt:212 - audioFocusListener field set before the focus request and never cleared when the request is denied
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:414 - onDestroy final-position save is cancelled by serviceScope.cancel() on the next statement - the destroy-edge save is dead in practice
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt:40 - PlayerEntryCoordinatorImpl is dead routing code - Hilt-bound but has no production caller
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:120 - Video decode can start/resume while the host activity is invisible - start paths are gated only on audio isPlaying, driven by a Player.Listener that is not lifecycle-unbound
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:154 - No onStop release edge - prepared video MediaPlayer (hardware codec) is retained from onPause until onDestroy while the activity is backgrounded
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:242 - SurfaceTextureListener asymmetry: hide() leaves the listener installed, and onSurfaceTextureDestroyed returns false while no code path ever calls SurfaceTexture.release()
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:310 - startMediaPlayer catch block orphans the just-constructed MediaPlayer when the apply{} initializer throws - only the Surface is released
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt:309 - release() unconditionally calls MemoryEnduranceTracker.endScenario(), clobbering foreign scenarios (e.g. VID-playback) since the tracker is a single-slot singleton
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:32 - Multi-window: first-destroyed PlayerActivity releases the shared singleton player out from under the surviving window and permanently unhooks its UI listeners (contract item 1: host ownership not exclusive)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:97 - No audio-focus handling: player built without setAudioAttributes(attrs, handleAudioFocus=true), unlike every other player host in the app (contract item 5, acquisition half)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:372 - Broad catch (e: Exception) swallows CancellationException from loadPlaylistJob.cancel(), logging normal teardown cancellation as an error
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt:100 - BD-TS local path builds ExoPlayer inline, skipping createPlayer() bookkeeping (duplicate creation path)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt:227 - Orphan creation path: releaseResources() constructs a brand-new VideoPlayerManager during Activity.onDestroy when none exists (dead catch never fires)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt:124 - Unguarded suspend settings write between successful VR dispatch and finishAndRemoveTask() - failure path skips 2D-host teardown and crashes via uncaught exception
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:283 - onResume() force-sets playWhenReady=true, overriding a user's manual pause across background/foreground
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:293 - Glide targets never cleared on teardown and loads bound to applicationContext - in-flight request retains ImageView/Activity past onDestroy (contract item 9)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:301 - Save-position-on-release coroutine launched into lifecycleScope from onDestroy - dead code on API 29+ (scope already cancelled), and lastSavedPosition is marked before the write commits
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt:183 - Stream error-recovery jobs (up to 5 s / 16 s delayed) act on whatever exoPlayer is current, not the errored instance - yanks the next file's restored position
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt:702 - TTS keeps speaking after viewer close and file swap - stop() is wired only to page/chapter navigation, not to any close/swap path
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TtsReadAloudManager.kt:86 - Failed TTS init permanently bricks the state machine in INITIALIZING - no retry or reset path short of host release()
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/NowPlayingViewModel.kt:172 - 500 ms position poll keeps running in background while sheet host is stopped - keyed to isPlaying only, never to view visibility
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt:568 - Three bare lifecycleScope.launch{...collect} sites drive view-bound work (GL video effects, Glide image re-display) with no repeatOnLifecycle - keeps collecting and re-rendering while the Activity is stopped (baselined in unsafe-collect gate, still live)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt:519 - SAF rename restarts audio playback from position 0 (and leaks the just-swapped controller): path change from onRenameComplete re-triggers viewManager.show() because lastShownPath is not updated on rename
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt:1013 - Player released on onDestroy instead of the API24+ onStop edge, with no documenting comment for the deviation - codecs held while the host sits stopped in background
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt:549 - onDestroy force-initializes the entire lazy TextViewerManager graph when the activity dies before deferred setupViews() ran
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt:445 - Video ExoPlayer released only in onDestroy - prepared player and codecs held for unbounded time while the activity is stopped in background (contract item 2)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:20 - releasePlayer() does not stop the positionSaveLoop - idle 15 s Handler tick survives video-to-document switches
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:34 - Only playerListener is removed at release - PauseAwareLoadControl and the per-stream listener are never removed
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:35 - releasePlayer() releases the ExoPlayer while it is still attached to PlayerView - no setVideoSurface(null)/player detach before release (asymmetric with onDestroy)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerLifecycleHelper.kt:52 - Player never released on the background edge - onPause only pauses, no onStop release/onStart recreate, so codecs + buffered media are retained while the app is backgrounded
- app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:32 - No WebView.onPause()/onResume() forwarding - JS-enabled media page keeps running (timers, playback, network) while host is backgrounded
- app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:255 - Account-name AlertDialog is not lifecycle-managed - WindowLeaked and lost user input on config change
- app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt:321 - webView.destroy() called while the WebView is still attached to the dialog window
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt:103 - cancelAll() cannot stop queued captures: drainOne polls the url before suspending on the semaphore, so parked drainOnes launch full ExoPlayer captures after onStop / leaving GRID
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt:195 - catch (t: Throwable) in capture() swallows CancellationException and logs cancelAll-driven cancels as 'Stream snapshot failed' WARNs
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt:68 - onComplete is never invoked on cancellation - withContext(Dispatchers.Main) in the finally of a cancelled job throws before running the block, contradicting the KDoc
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt:118 - catch (t: Throwable) swallows CancellationException and logs every user-driven sweep cancel as a WARN failure with stack trace
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:136 - OFF-mode local ExoPlayer requests no audio focus and no becoming-noisy handling, unlike its service-mode twin
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:183 - stop() leaves playWhenReady=true and the playlist loaded on the service player, defeating AudioPlaybackService.onTaskRemoved's no-active-playback stopSelf heuristic
- app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt:75 - Enable-all sequence stalls permanently after recreation/process death: stage-completion callback is instance-bound and restored inProgress=true has no resume path
- app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt:178 - isFinalizing latch is never reset - a recording started during finalization cannot be stopped and is silently killed and stranded
- app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxer.kt:58 - remux() leaks a native MediaExtractor when setDataSource fails on a segment file
- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:112 - False load-bearing comment on windowFocusedDeferred: claims a new instance is created in maybeStartRenderThread, but no recreation exists - the focus gate is permanently open for any second render-thread start within one Activity instance
- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:286 - Dead HUD canvas buffers: ~4 MB (hudBitmap 1024x512 ARGB_8888 + hudRgbaBytes) allocated per activity with zero consumers since the S0290 ray-tick HUD path was removed
- app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt:1124 - shutdownRenderThreadSync abandons a still-alive render thread that strongly references the Activity and the live native EGL/OpenXR session on join timeout
- wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt:68 - Contract item 5: player never requests audio focus - builder omits setAudioAttributes(attrs, handleAudioFocus=true)
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt:48 - Dead album-art path: albumArtRepository and preferencesRepository injected but never used; MediaMetadataRetriever import unused; albumArtUrl UI branch can never render
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt:94 - Contract item 5 (playWhenReady reset): audio STATE_ENDED seeks to 0 without pausing - track restarts and loops indefinitely
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:156 - First-run video never auto-starts: dismissBatteryWarning does not set playWhenReady although the load path defers autoplay to dismissal
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:207 - SMB playback temp files are re-downloaded every open and never deleted - cache dirs grow without app-side bound on watch storage
- wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt:333 - Contract item 2: no onStop handling - video/audio playback keeps running while the host activity is stopped; onCleared is the only teardown edge

**Вложения:**
- full recovered findings JSON (119) - `PLAN/S0878_audit-mass-2026-07-02-followup/attachments/01__findings_recovered.json`
- first run structured result (killed list, p2Appendix, contractMatrix, criticMissed) - `PLAN/S0878_audit-mass-2026-07-02-followup/attachments/02__first_run_result.json`

---

## 1. Проблема

Хвост массового аудита 2026-07-02 держался одним контейнером: 65 несортированных P2-находок, непокрытые слои протокола 5/6/7 и долг по runtime-evidence. Пока хвост не разложен по исполняемым тикетам, находки не видны планированию и не двигаются.

---

## 2. Цели

1. Каждая находка P2-appendix получает диспозицию: тематический тикет, fold в существующий тикет или обоснованный drop.
2. Непокрытые слои и runtime-evidence оформлены отдельным исполняемым тикетом.

**Non-goals:** верификация/исправление самих находок - это работа кластерных тикетов.

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0853-S0877 (P0/P1 той же кампании), S0892

---

## 4. Контекст текущей архитектуры

Не применимо - тикет-контейнер (триаж), кода не трогает.

---

## 5. Предлагаемый подход

Кластеризация по подсистеме/теме, thin-драфты со вербатим-находками в §0 (паттерн /spec-draft), verify-per-finding откладывается в жизненный цикл каждого кластера.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

Кластер может оказаться смесью реальных дефектов и ложных срабатываний (static-review без скептика) - каждый кластерный тикет верифицирует свои находки до фикса.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (триаж-контейнер).

---

## 9. Архитектурные решения (ADR)

ADR нет.

---

## 10. Связи с другими спеками

- Породил: S0893-S0905 (кластеры + coverage-tail).
- Fold: 3 WebView-находки -> S0892.
- Верификационная кампания: S0853-S0877 (закрыта 2026-07-02, см. §0).

---

## 11. Критерии готовности (strategic-level)

1. 65/65 находок P2-appendix имеют диспозицию, зафиксированную в спеке.
2. Кластерные тикеты зарегистрированы в каталоге.
3. Слои 5/6/7 + runtime-evidence оформлены исполняемым тикетом.

---

## Triage disposition 2026-07-03

- S0893 player-release-edges-p2 (10): AudioEmptyStateController :120/:154/:242/:310, VideoPlayerLifecycleHelper :20/:34/:35/:52, StandalonePlayerActivity :445, PhotoVideoStandaloneActivity :1013.
- S0894 standalone-player-misc-p2 (5): StandaloneViewManager :283/:293/:301, AudioStandaloneActivity :519, TextStandaloneActivity :549.
- S0895 player-misc-p2 (9): AudioPlaybackService :414, PlayerEntryCoordinator :40, LocalPlaybackHelper :100, PlayerLifecycleManager :227, PlayerVrLaunchManager :124, NowPlayingViewModel :172, PlayerManagerInitializer :568, StreamPlaybackHelper :183, AudioServiceController :309.
- S0896 audio-focus-contract-sweep-p2 (8): BackgroundMusicManager :32/:97/:372, BrowseInlineAudioManager :86, BrowseMicRecordingManager :90, MainVoiceCaptureManager :212, StreamInlineAudioManager :136, wear/WearAppModule :68.
- S0897 tts-lifecycle-p2 (2): TextViewerManager :702, TtsReadAloudManager :86.
- S0898 room-import-transactions-p2 (3): InputBindingRepository :55, StreamSourceRepository :29, ImportFavoritesUseCase :104.
- S0899 native-fd-leaks-p2 (3): SafUriExtractor :146/:214, MediaMuxerRemuxer :58.
- S0900 streams-helpers-p2 (5): StreamFrameSnapshotManager :103/:195, StreamHealthProbeManager :68/:118, StreamInlineAudioManager :183.
- S0901 browse-managers-p2 (4): BrowseInlineAudioManager :242, BrowseLoadingAuxManager :60, BrowseManagerInitializer :553, BrowseMicRecordingManager :153.
- S0902 wear-p2 (5): AudioPlayerViewModel :48/:94, VideoPlayerViewModel :156/:207/:333.
- S0903 vr-diagxr-p2 (3): DiagnosticXrActivity :112/:286/:1124.
- S0904 infra-misc-p2 (5): AudioToggleTileService :78, BaseConnectionPool :119, FtpExoPlayerPool :132, WelcomePermissionsManager :75, ScreenVideoRecordingService :178.
- Fold -> S0892 (3): WebViewAuthDialogFragment :32/:255/:321 (:321 с NB о противоречии live-grep).
- S0905 audit-layers-567-runtime-evidence: слои 5/6/7 + LeakCanary/benchmark долг.
- Dropped: 0 - все находки static-unverified; drop без code-read был бы потерей сигнала, верификация в кластерах.

Итого: 62 в кластерах + 3 fold = 65/65.

---

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- Deliverable тикета - триаж, не фиксы: 65/65 находок P2-appendix диспозиционированы (см. Triage disposition).
- 13 тикетов S0893-S0905 зарегистрированы в каталоге (insert.ps1, все exit 0), thin-драфты с вербатим-находками в §0.
- 3 WebView-находки влиты в S0892 (дополнен §0), включая NB о противоречии одной находки live-grep-доказательствам.
- Верификационный долг S0853-S0877 закрыт ещё 2026-07-02 (см. §0, п.2).
- Валидация: select по S0893..S0905 - expected: 13 Draft-строк | actual: 13 Draft-строк.

---

