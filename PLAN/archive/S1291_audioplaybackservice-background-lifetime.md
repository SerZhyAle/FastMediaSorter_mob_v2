# S1291 - AudioPlaybackService background lifetime: unbounded stream retry + orphaned PositionSaveLoop leaks

**Ticket:** S1291
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): services-1, hang-paths-3, services-3, handlers-timers-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: S1146 (seekToNext rebuffer - different defect in the same service), S1219 (background-playback left panel feature draft touching the same service). Findings services-1/hang-paths-3 describe the same defect from two dimensions, as do services-3/handlers-timers-1 - both kept for their complementary evidence.

## Finding 1: AudioPlaybackService retries a failed source forever once any track reached READY - service never stops, endless network/wake churn

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:699`
- Symptom: After the first STATE_READY of a session, recordCurrentStreamSuccess() sets streamHasSuccessfulPlayback = true unconditionally (line 773) for ANY audio, not just radio streams. From then on every IO-range PlaybackException (2000..2008, which includes ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, TIMEOUT and even FILE_NOT_FOUND) passes canRetryStream(), and retryCurrentStream() only gives up when !streamHasSuccessfulPlayback - so the retry loop (prepare()+playWhenReady=true every 2-8 s, backoff capped at MAX_STREAM_BACKOFF_SHIFT=2 -> 8 s) repeats forever. onPlayerError also removes the auto-stop runnable (line 432), the skip-to-next branch is unreachable (retry returns first), and stopSelf() is never reached. The foreground service with WAKE_MODE_LOCAL stays alive reconnecting indefinitely.
- Failure scenario: User plays SMB/SFTP audio or an internet radio station with background playback ON, then leaves home Wi-Fi (NAS/stream permanently unreachable) and pockets the phone. Every ~8 s the service re-opens the connection, fails with an IO error, and reschedules - all night. The foreground service, its notification and the ExoPlayer wake mode never go away; the battery drains and the 'playing' notification is stuck until the user force-stops or reopens the app. The documented fatal-error path (FILE_NOT_FOUND after cache eviction 'lands here on purpose', line 449-450) is also dead, because retry intercepts it first.
- Fix sketch: Bound retries when playback cannot be re-established: count consecutive failed retry attempts since the last STATE_READY and stopSelf() (or fall through to the skip/fatal path) after N attempts or T minutes. Also set streamHasSuccessfulPlayback only for actual stream-catalog URLs (move the assignment inside the repository-hit branch) so ordinary file playback keeps the original fatal-error semantics.
- Verifier rationale: Confirmed. recordCurrentStreamSuccess() (line 771-773) sets streamHasSuccessfulPlayback=true unconditionally for ANY media item with a URI, before the stream-repository lookup. canRetryStream() then accepts every IO error (2000..2008, incl. FILE_NOT_FOUND) forever, and retryCurrentStream() only stops when !streamHasSuccessfulPlayback - no attempt counter or time cap exists anywhere (only the delay is capped at 8s via MAX_STREAM_BACKOFF_SHIFT=2/MAX_RETRY_DELAY_MS=8000). The documented FILE_NOT_FOUND fatal path (comment lines 449-450) is dead once any track reached READY. It is worse than claimed: retryCurrentStream() sets playWhenReady=true each cycle, defeating notification pause, and onTaskRemoved (line 582-592) then sees playWhenReady=true and refuses to stop the service on app swipe-away - only force-stop ends the loop. Minor finder inaccuracy: the skip branch is not unreachable for 3xxx/4xxx parse/decode errors (disjoint from the IO range), but the core unbounded-retry claim stands. P1: foreground service with wake mode churning network/CPU indefinitely, effectively an unreleased heavy resource the user cannot stop by normal means.

Evidence excerpt:

```
private fun canRetryStream(error: PlaybackException): Boolean {
    val ioErrorRange = PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    val withinRetryWindow = streamHasSuccessfulPlayback || ...
    return error.errorCode in ioErrorRange && withinRetryWindow
}
...
private fun retryCurrentStream() {
    if (!streamHasSuccessfulPlayback && elapsed >= DIALOG_TIMEOUT_MS) { stopSelf(); return }
    currentPlayer.prepare(); currentPlayer.playWhenReady = true
}
// recordCurrentStreamSuccess() (STATE_READY, any media):
streamHasSuccessfulPlayback = true
```

## Finding 2: AudioPlaybackService retries a failed radio stream every <=8 s forever once the station ever played

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:699`
- Symptom: Foreground audio service loops prepare()+playWhenReady against a dead stream URL every 2-8 s with no upper bound, no connectivity gating, and independent of the 'smart stream buffering' setting - sustained network churn and repeated ExoPlayer WAKE_MODE_LOCAL wakelock acquisition (held during every BUFFERING attempt) for hours until the user manually stops the notification.
- Failure scenario: User taps a favorite internet-radio station that played fine last week; today the host is down (or the user walks out of Wi-Fi coverage mid-listen). onPlayerError fires with an IO error code, canRetryStream is permanently true because lastPlayedAt is set in the DB, so scheduleStreamRetry re-arms every 8 s (backoff capped at MAX_STREAM_BACKOFF_SHIFT=2). Each retry calls prepare() with playWhenReady=true, putting ExoPlayer into BUFFERING where WAKE_MODE_LOCAL holds a partial wakelock+wifilock for the duration of the failed connect attempt. Phone sits in the pocket for hours with the 'playing' notification: continuous connect attempts + intermittent wakelock churn drain the battery until the user notices and stops the service by hand. The retry-forever design is documented only for the smart-buffering loader policy (RadioStreamBufferConfig, default OFF), but this service-level loop runs regardless of that setting.
- Fix sketch: Bound the loop: track consecutive failed retries and give up (stopSelf or pause with a 'stream unavailable' notification) after N attempts or M minutes without reaching STATE_READY in the current session; reset the counter on in-session success rather than on persisted lastPlayedAt. Optionally gate re-arming on NetworkStateMonitor connectivity so no-network periods do not spin the connect loop.
- Verifier rationale: Confirmed. canRetryStream (lines 696-703) returns true for IO-range errors whenever streamHasSuccessfulPlayback is set, and resetStreamRecovery seeds that flag from persisted DB history (line 733: getByUrl(streamUrl)?.lastPlayedAt != null), not this session's success. scheduleStreamRetry backoff verified: BASE_RETRY_DELAY_MS=2000 shl streamRetryAttempt capped at MAX_STREAM_BACKOFF_SHIFT=2 and MAX_RETRY_DELAY_MS=8000 - retries every <=8 s with no attempt cap, elapsed-time cap, or connectivity gate. retryCurrentStream (713-724) only stopSelf()s on the no-history path; with persisted history it re-arms prepare()+playWhenReady forever. Wake mode is C.WAKE_MODE_LOCAL when WAKE_LOCK is granted (lines 342-344), so each BUFFERING attempt holds a partial wakelock, and onTaskRemoved keeps the service alive while playWhenReady is true. Unbounded loop is real; P2 not P1 because the retry doubles as intended auto-resume when connectivity returns, impact is battery/network churn (no crash/leak/data loss), and the user can stop it from the notification. Bounding the loop and gating on connectivity is a small localized change.

Evidence excerpt:

```
private fun canRetryStream(error: PlaybackException): Boolean {
    ...
    val withinRetryWindow = streamHasSuccessfulPlayback ||               // line 699
        SystemClock.elapsedRealtime() - streamConnectionStartedAtMs < RadioStreamBufferConfig.DIALOG_TIMEOUT_MS
    return error.errorCode in ioErrorRange && withinRetryWindow
}
// streamHasSuccessfulPlayback is seeded from PERSISTED history, not this session (line 733):
//   streamHasSuccessfulPlayback = streamSourceRepository.getByUrl(streamUrl)?.lastPlayedAt != null
// retryCurrentStream (713-724) only stopSelf()s when !streamHasSuccessfulPlayback after 15 s; otherwise prepare()+play forever
```

## Finding 3: AudioPlaybackService leaks one PositionSaveLoop per rebuffer: orphaned 15 s Handler loops keep ticking forever and retain the released ExoPlayer

- Severity: P1, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:642`
- Symptom: startPositionSaving() is called on every entry into STATE_READY (line 384) and overwrites the positionSaveLoop field with a new PositionSaveLoop without stopping the previous one (PositionSaveLoop.start() only clears its OWN runnable). STATE_BUFFERING does not call stopPositionSaving(), so every READY -> BUFFERING -> READY cycle (network stall rebuffer, seek, non-gapless track change) orphans the previous loop. Each orphan re-posts its runnable on the main Looper every 15 s indefinitely - stopPositionSaving()/onDestroy stop only the newest instance - and its closure retains the ExoPlayer reference (getPositionMs = { p.currentPosition }). While the service is alive, every live orphan also issues its own Room position write every 15 s.
- Failure scenario: User streams SMB/SFTP audio over flaky Wi-Fi overnight with background playback ON. Dozens of rebuffer cycles create dozens of orphaned save loops: while playing, N loops each perform a Room write every 15 s (constant DB churn, battery); after the service is destroyed the orphaned runnables keep re-posting on the main Looper for the rest of the process lifetime, calling currentPosition on the released player and pinning the released ExoPlayer object graph in the heap. Repeated audio sessions accumulate more orphans.
- Fix sketch: Call stopPositionSaving() at the top of startPositionSaving() (or early-return if positionSaveLoop != null for the same media item). Additionally make PositionSaveLoop defensive: guard saveNow/tick with a released/stopped flag so a stopped loop can never re-post.
- Verifier rationale: Confirmed. startPositionSaving() (line 640-652) overwrites positionSaveLoop with a fresh PositionSaveLoop without stopping the previous instance; PositionSaveLoop.start()/stop() (PositionSaveLoop.kt:32-52) manage only their own runnable on their own Handler, so an overwritten instance keeps re-posting on the main Looper every 15s with no external cancellation path. STATE_READY calls startPositionSaving() (line 384) while STATE_BUFFERING (388-392) does not stop it, so every rebuffer/seek READY re-entry orphans one loop; stopPositionSaving (STATE_ENDED/IDLE/onDestroy) stops only the newest. While the service lives, each orphan passes its per-instance lastSaved guard (position advances) and issues its own Room write every 15s. After onDestroy, serviceScope cancellation kills the writes, but the orphaned runnables keep re-posting for the rest of the process lifetime and their closures ({ p.currentPosition }) pin the released ExoPlayer graph. Unbounded accumulation + retained released player = P1; fix is one stopPositionSaving() call at the top of startPositionSaving().

Evidence excerpt:

```
private fun startPositionSaving() {
    val p = player ?: return
    positionSaveLoop = PositionSaveLoop(   // previous loop NOT stopped first
        ... getPositionMs = { p.currentPosition }, ...)
    positionSaveLoop!!.start()
}
// Player.Listener: STATE_READY -> startPositionSaving();  STATE_BUFFERING -> (no stopPositionSaving)
// PositionSaveLoop.start(): stop() only removes THIS instance's runnable
```

## Finding 4: AudioPlaybackService orphans a self-reposting PositionSaveLoop on every BUFFERING->READY transition

- Severity: P1, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:642`
- Symptom: Each re-entry into Player.STATE_READY (seek, network rebuffer, stream-retry recovery) calls startPositionSaving(), which overwrites the positionSaveLoop field with a new PositionSaveLoop without stopping the previous one. Each PositionSaveLoop owns its own Handler with a runnable that unconditionally re-posts itself every 15 s (PositionSaveLoop.kt:36-39), so every orphaned loop keeps ticking on the main looper forever - even after service onDestroy, since stopPositionSaving() only stops the last instance. Orphans capture the ExoPlayer (`val p = player`) and the service's repository, retaining the released player and destroyed Service, and for network-audio sessions every live orphan performs a duplicate Room write every 15 s.
- Failure scenario: User listens to a network audiobook or flaky radio stream overnight via the background audio service. Every seek from the notification and every network rebuffer/watchdog re-prepare passes READY->BUFFERING->READY and orphans one more 15-second ticker; after hours there are dozens of zombie Handler loops each firing every 15 s and (for network files) each writing the position row to Room, multiplying IO and battery cost. When the service is finally destroyed the orphans survive process-long, retaining the released ExoPlayer and the destroyed Service instance until process death.
- Fix sketch: Mirror the S0854 guard from PlaybackPositionHelper.kt: call stopPositionSaving() (or positionSaveLoop?.stop()) as the first statement of AudioPlaybackService.startPositionSaving() before constructing the new loop.
- Verifier rationale: Confirmed. startPositionSaving() (lines 640-652) assigns a new PositionSaveLoop over the old field without calling stop() on the previous instance; PositionSaveLoop.start() only clears its OWN runnable, and each instance owns a private Handler whose runnable unconditionally re-posts itself every interval (PositionSaveLoop.kt:36-39). STATE_READY (line 384) calls startPositionSaving() unconditionally and the STATE_BUFFERING branch (lines 388-392) performs no stop, so every READY->BUFFERING->READY re-entry (seek, network rebuffer) orphans one self-reposting main-looper loop. stopPositionSaving() (654-658) and the onDestroy path (606) stop only the last instance, so orphans survive service destruction, retaining the destroyed Service and the captured released ExoPlayer for process lifetime; while playback continues each orphan passes its per-instance skip-if-unchanged guard and duplicates the Room position write every 15 s. serviceScope cancellation neuters the writes post-destroy but not the Handler re-posting or the retention. The sibling video-player helper already guards exactly this, confirming the pattern is a known hazard here.

Evidence excerpt:

```
Player.STATE_READY -> { ... startPositionSaving() ... }  // line 384; no stop in STATE_BUFFERING branch
private fun startPositionSaving() {
    val p = player ?: return
    positionSaveLoop = PositionSaveLoop(   // line 642: previous loop overwritten, never stop()ped
        ...
    positionSaveLoop!!.start()
}
// The video-player twin already fixed exactly this (PlaybackPositionHelper.kt:23-26, S0854):
// "stop the previous loop before dropping the reference - ... overwriting positionSaveLoop without
// stopping it first leaves the old runnable self-reposting forever (orphaned, retains ...)"
```

