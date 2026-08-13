# S0896 - Audio focus contract sweep: hosts that never request or never abandon focus (P2 cluster)

**Ticket:** S0896
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: contract item 5 (audio focus) - хосты, которые не запрашивают фокус, не абандонят его на denied-пути или строят плеер без handleAudioFocus.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:32 - Multi-window: first-destroyed PlayerActivity releases the shared singleton player out from under the surviving window and permanently unhooks its UI listeners (contract item 1: host ownership not exclusive)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:97 - No audio-focus handling: player built without setAudioAttributes(attrs, handleAudioFocus=true), unlike every other player host in the app (contract item 5, acquisition half)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:372 - Broad catch (e: Exception) swallows CancellationException from loadPlaylistJob.cancel(), logging normal teardown cancellation as an error
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:86 - Inline audio plays without ever requesting audio focus, so release has nothing to abandon and playback ignores focus loss (contract item 5)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt:90 - Focus-denied early return leaves audioFocusListener set and the denied request never abandoned; stopRecording's pending-guard returns before abandonAudioFocus
- app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt:212 - audioFocusListener field set before the focus request and never cleared when the request is denied
- app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:136 - OFF-mode local ExoPlayer requests no audio focus and no becoming-noisy handling, unlike its service-mode twin
- wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt:68 - Contract item 5: player never requests audio focus - builder omits setAudioAttributes(attrs, handleAudioFocus=true)

## Related

- S0878 (audit tail container - triage source); S0902 (wear sibling cluster - WearAppModule item lives here as focus-theme).
- S0909 (parked during implementation - unrelated `_inlinePlayerState` generation-guard gap found next to this ticket's `BrowseInlineAudioManager` audio-focus fix, out of scope here).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878, S0902 (wear sibling - only the WearAppModule finding overlaps; rest of wear module is S0902's scope, not touched here)

<!-- auto-approved by /spec-all - 2026-07-03 -->

**Tactical plan:** `PLAN/S0896_audio-focus-contract-sweep-p2/INDEX.md`

## Last Audit

### Manual (device test 2026-07-10)

Device: emulator-5554 (Android 13, x86_64). Build 2.60.7092.225-DEBUG (standard, S0896 probes present). Evidence: `temp/S0896/logcat.txt`, `temp/S0896/focus_evidence.txt`.

**Flow 2 - Audio ducking / focus contract: PASS.**
- Browse inline audio now requests focus. Probe fired: `S0896: BrowseInlineAudioManager audio focus request for '01_sheeran.mp3' granted=true`, backed by framework `MediaFocusControl: requestAudioFocus() ... AA=USAGE_MEDIA/CONTENT_TYPE_MUSIC ... req=1`. Symmetric `abandonAudioFocus()` observed on stop.
- Full audio player ExoPlayer built with handleAudioFocus: framework `requestAudioFocus() ... clientId=...media3.exoplayer.AudioFocusManager$AudioFocusListener`.
- Genuine foreign interruption (simulated incoming call via `adb emu gsm call`): telecom requested focus (`AudioFocus_For_Phone_Ring_And_Calls req=2`), AudioManager dispatched `onAudioFocusChange(-2)` (transient loss) to the app's media3 player -> playback paused; on call cancel, `onAudioFocusChange(1)` (gain) dispatched -> resumed (UI position 00:52 -> 01:32). Expected: app pauses/ducks on foreign focus | Actual: paused then resumed. Match.

**Flow 1 - Multi-window ref-counting: INCONCLUSIVE.**
- Could not create two coexisting `PlayerActivity` hosts on this emulator. Player overflow has no "Open in new window"; `am start` VIEW with `FLAG_ACTIVITY_LAUNCH_ADJACENT` was intercepted by the system ResolverActivity (audio/* multi-handler) and, once FMS was chosen, routed into the single existing `PlayerActivity` instance (task t198) - `activeHostCount` never exceeded 1.
- The release-skip probe (`S0896: BackgroundMusicManager release() skipped - N other host(s) still active`, `BackgroundMusicManager.kt:618`) therefore never fired. Ref-count code is confirmed present and reached in the live build (`BackgroundMusic: Player initialized` logged once on player entry = `initialize()` -> `activeHostCount++`), but the surviving-window-keeps-playing behavior was not exercisable here. Needs a real split-screen / freeform device with two simultaneous player windows.
