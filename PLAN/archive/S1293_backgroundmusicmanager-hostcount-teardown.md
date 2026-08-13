# S1293 - BackgroundMusicManager host-count teardown: recovery reinit wedges activeHostCount forever; early-return retains destroyed PlayerActivity

**Ticket:** S1293
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): player-family-1, singletons-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: S0896 (the activeHostCount guard this regression lives in was introduced by the S0896 multi-window contract).

## Finding 1: BackgroundMusicManager error-recovery reinit inflates activeHostCount, permanently disabling singleton teardown

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:200`
- Symptom: After one playback-error recovery reinit, the @Singleton manager's host reference count is off by +1 forever: release() at every subsequent PlayerActivity destroy sees activeHostCount > 0 and skips teardown, so the background-music ExoPlayer (codec/AudioTrack/HandlerThread), the 60-second health-check coroutine loop, and the listener lambdas capturing the destroyed PlayerActivity are retained for the rest of the process lifetime.
- Failure scenario: User runs a photo slideshow with background music for hours; one track raises a non-IO fatal ExoPlayer error and the auto-recovery skip path itself throws (the outer catch at line 191), so the last-resort branch runs releaseMusicPlayerInstance() + initialize(). activeHostCount goes 1 -> 2 while only one PlayerActivity exists. When the user leaves the player, PlayerLifecycleManager calls backgroundMusicManager.release(): count drops to 1, teardown is skipped ('S0896: ... other host(s) still active'). From then on, for the whole process lifetime: (a) a live ExoPlayer with its native codec/audio resources is never released, (b) healthCheckJob wakes every 60 s forever (battery drain while the app idles in background), and (c) onTrackChangedListener/onMusicErrorListener - set in PlayerManagerInitializer.ensureAudioBackgroundManagersConfigured() as closures over the PlayerActivity (activity.runOnUiThread { ... }) - keep the destroyed PlayerActivity (and its whole view tree) reachable, since the count-guarded early return also skips the 'Drop listener lambdas' cleanup at lines 636-637. Every later player session repeats init/release 2->1 without ever reaching zero.
- Fix sketch: Separate host acquisition from player construction: give the recovery path a private reinitializePlayer() that rebuilds the ExoPlayer without touching activeHostCount (and use the same non-counting path for the lazy 'if (musicPlayer == null) initialize()' inside updateState(), which has the same unmatched-increment shape). Alternatively decrement activeHostCount immediately before the recovery initialize() call so the acquire/release pairing stays 1:1 per host.
- Verifier rationale: Confirmed by reading the full file plus both glue classes. initializeInternal() increments activeHostCount unconditionally (line 110); the error-recovery last-resort branch (lines 194-200) calls releaseMusicPlayerInstance() + initialize() with no compensating decrement, so one recovery inflates the count 1->2 for a single host. Host-side pairing is otherwise strictly 1:1: the only host initialize() is PlayerManagerInitializer.ensureAudioBackgroundManagersConfigured() line 175 (guarded once per activity) and the only release() is PlayerLifecycleManager line 187. release() (lines 615-620) early-returns while count>0, skipping healthCheckJob cancel, ExoPlayer release, and the listener-lambda nulling at lines 636-637; those lambdas (set at PlayerManagerInitializer lines 176-183) close over the PlayerActivity, so the destroyed activity is retained by the @Singleton for process lifetime, the ExoPlayer is never released, and the 60s health-check loop runs forever. coerceAtLeast(0) cannot repair the inflated count, so every later session cycles 2->1 without reaching zero - the leak is permanent after a single occurrence. The same unmatched-increment shape exists at the lazy initialize() in updateState() line 270. Reachability needs a double fault (the auto-recovery skip itself throwing into the catch at line 191), which is rare - that, plus the guaranteed consequence being an unreleased heavy resource / wedged refcount, keeps it at P1 rather than P0. Fix is localized: a non-counting reinitializePlayer() for the recovery and lazy paths.

Evidence excerpt:

```
// Last resort: reinitialize player
try {
    this@BackgroundMusicManager.releaseMusicPlayerInstance()
    this@BackgroundMusicManager.isPlaying = false
    // Reinitialize
    this@BackgroundMusicManager.initialize()   // <- initializeInternal() does activeHostCount++ (line 110) with no matching release()
...
fun release() {
    activeHostCount = (activeHostCount - 1).coerceAtLeast(0)
    if (activeHostCount > 0) {
        // S0896: another window still owns this singleton - do not tear down its player/listeners.
        ... return
    }
```

## Finding 2: BackgroundMusicManager singleton retains destroyed PlayerActivity via UI listener lambdas when release() early-returns for a surviving multi-window host

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:616`
- Symptom: In split-screen/freeform with two PlayerActivity windows (the scenario the S0896 refcount was added for), closing the window that configured its listeners last leaves the @Singleton holding lambdas that capture the destroyed PlayerActivity - its binding, view tree, dialogAndUiStateManager and everything they reference - for as long as the other window stays open (potentially hours in a slideshow session). The nulling of onTrackChangedListener/onMusicErrorListener added specifically to fix this capture (comment: 'Drop listener lambdas: they capture the host PlayerActivity (S0726/S0715 P2)') is unreachable on the early-return path.
- Failure scenario: User opens PlayerActivity A, then PlayerActivity B in split-screen (both call ensureAudioBackgroundManagersConfigured -> initialize() increments activeHostCount to 2, B's lambdas overwrite A's in the singleton). User closes window B and keeps a slideshow running in window A for hours: B's onDestroy -> PlayerLifecycleManager.releaseResources() -> backgroundMusicManager.release() decrements to 1 and returns at line 616-620 BEFORE the listener nulling at lines 636-637, so the app-lifetime singleton keeps destroyed Activity B (multi-MB of views/bitmaps/players) reachable until window A also closes. Bonus functional defect: window A never receives track-name callbacks because the stored lambda still targets dead B.
- Fix sketch: Track listener registrations per host (e.g. store the registering host token with each lambda) and clear the stored lambdas when the host that owns them releases, even on the refcounted early-return path; simplest safe variant: in release(), if the departing host is the one whose lambdas are stored, null onTrackChangedListener/onMusicErrorListener before the activeHostCount > 0 return (the surviving window re-registers on its next updateState/configure pass).
- Verifier rationale: Confirmed. The @Singleton (line 35) stores activity-capturing lambdas set only once per activity in PlayerManagerInitializer.kt:176-187 (guarded by areAudioBackgroundManagersConfigured, so the surviving window never re-registers). release() decrements the S0896 refcount and early-returns at lines 615-620 BEFORE the listener nulling at 636-637; grep confirms no other code path clears these lambdas. PlayerLifecycleManager.kt:186-188 calls release() from onDestroy only when configured, so in dual-window split-screen the destroyed window that registered last stays reachable from the app-lifetime singleton (binding, view tree, dialogAndUiStateManager) until the other window also closes, plus its track-name callbacks target the dead activity. Rated P1 rather than P0 because the retention requires the narrow split-screen/freeform dual-PlayerActivity-with-background-music scenario and self-heals when the surviving window closes.

Evidence excerpt:

```
fun release() { ... activeHostCount = (activeHostCount - 1).coerceAtLeast(0)
 if (activeHostCount > 0) {
  // S0896: another window still owns this singleton - do not tear down its player/listeners.
  Timber.d(...); return
 }
 ...
 // Drop listener lambdas: they capture the host PlayerActivity (S0726/S0715 P2).
 onTrackChangedListener = null
 onMusicErrorListener = null
(listeners are set in PlayerManagerInitializer.kt:176-187: activity.backgroundMusicManager.setOnTrackChangedListener { trackName -> activity.runOnUiThread { ... } })
```

