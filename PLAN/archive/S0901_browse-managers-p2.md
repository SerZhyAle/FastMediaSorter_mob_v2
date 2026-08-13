# S0901 - Browse managers: unbounded cache, job sync, hot warm-up collectors (P2 cluster)

**Ticket:** S0901
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:242 - inline_audio disk cache has no size bound or eviction: every played/prefetched SMB track is stored in full, forever, until a manual whole-cache wipe or OS storage pressure
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt:60 - playerWarmupJob / audioMetadataEnrichmentJob / lastWarmupSignature written on IO dispatcher but cancelled/read from main thread without synchronization - cancel edge can miss the live job
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt:553 - Warm-up collectors on settings and destinations Flows use bare lifecycleScope.launch{collect} - DataStore and Room upstreams stay actively collected the entire time BrowseActivity sits stopped in the back stack (baselined, still live)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt:153 - Post-stop save pipeline runs on the host's lifecycleScope: teardown after stop silently drops the captured recording and orphans the temp file

## 1. Goal (RU)

Четыре независимых дефекта в Browse-менеджерах: неограниченный дисковый кэш inline-audio, гонка видимости job-полей, горячие коллекторы Flow при остановленной Activity, и потеря записи с диктофона при teardown сразу после stop.

## 2. Constraints

- No schema/DI-scope changes beyond `@Inject`-wiring an existing `@ApplicationScope CoroutineScope` into `BrowseActivity` and threading it to `BrowseMicRecordingManager`.
- Happy-path behavior unchanged; the mic-save UI feedback (snackbar/dialog) is guarded so it never touches a destroyed activity.
- Cache eviction never deletes the just-downloaded file; oldest-first pruning down to a fixed cap.

## 3. Phases

### Phase 1 - `BrowseLoadingAuxManager` job field visibility (finding 60)

- Step 1.1: Mark `playerWarmupJob`, `audioMetadataEnrichmentJob`, `lastWarmupSignature` `@Volatile` so a write on IO (warm-up scheduled after a scan) is visible to `cancelAll`/`cancelPlayerWarmup` on main - the cancel can no longer miss a live job through a stale read (mirrors S0871).
  - Verification: grep - the three fields carry `@Volatile`.

### Phase 2 - `BrowseManagerInitializer` lifecycle-aware collectors (finding 553)

- Step 2.1: Replace the two bare `lifecycleScope.launch { flow.collect { .. } }` (settings, destinations) with `activity.collectOnLifecycle(flow) { .. }` (repeatOnLifecycle STARTED). Import `com.sza.fastmediasorter.utils.collectOnLifecycle`. `activity` is a `BrowseActivity` (LifecycleOwner).
  - Verification: grep - no bare `lifecycleScope.launch { .. collect` for settings/destinations; `collectOnLifecycle` used. `unsafe-collect` neuroslop count drops (below baseline).

### Phase 3 - `BrowseInlineAudioManager` bounded cache (finding 242)

- Step 3.1: Add a private `evictInlineAudioCacheIfNeeded(keep: java.io.File)` that sums the `inline_audio` dir, and if over `INLINE_AUDIO_CACHE_CAP_BYTES` (256 MB companion const) deletes oldest files (by `lastModified`) down to the cap, skipping `keep`. Uses a `for` loop with `break`/`continue` (<=2 returns for detekt `ReturnCount`).
- Step 3.2: Call `evictInlineAudioCacheIfNeeded(cacheFile)` in the `downloadSmbAudioToCache` `SmbResult.Success` branch (already on IO), before returning the path.
  - Verification: grep - helper present; called in the Success branch; companion const defined. Playing file's descriptor is already open (MediaPlayer), so eviction of its cache entry is harmless.

### Phase 4 - `BrowseMicRecordingManager` save survives teardown (finding 153)

- Step 4.1: Add `private val appScope: CoroutineScope` (an `@ApplicationScope` scope) to the constructor. Route the two save launches (`stopRecording`, `showNameDialog` positive button) through `appScope` so activity teardown right after stop cannot cancel `micRecordingSaver.save` (drop recording / orphan temp).
- Step 4.2: Guard UI feedback: in `stopRecording`, if the activity is finishing/destroyed skip the name dialog and save with the default name (never lose the recording); in `save()`, wrap the `withContext(Dispatchers.Main) { snackbar/notifier/onFileSaved }` block in an `!activity.isDestroyed` guard.
- Step 4.3: `BrowseActivity` - `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope`; pass `appScope = applicationScope` to the `BrowseMicRecordingManager(..)` constructor (was `coroutineScope = lifecycleScope`, kept for the recorder/UI lifecycle).
  - Verification: grep - saves launched on `appScope`; UI feedback guarded; `BrowseActivity` injects `@ApplicationScope` and passes it.

### Phase 5 - Build gate

- Step 5.1: `standard debug` compiles (`a.ps1 fk`). Detekt-clean on the touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings on the four managers + `BrowseActivity`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0896 (Browse-manager focus findings), S0909 (BrowseInlineAudioManager generation guard - same file, sequenced after this).

## Related

- S0878 (audit tail container - triage source).
- S0909 (BrowseInlineAudioManager generation guard - same file, handled next).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** BlockNeedUserTest.

All four findings implemented; `standard debug` Kotlin compile PASS (incl. Hilt/kapt for the new `@ApplicationScope` inject); detekt-clean on the touched files.

- **`BrowseLoadingAuxManager` (finding 60)** - `playerWarmupJob`, `audioMetadataEnrichmentJob`, `lastWarmupSignature` now `@Volatile`. A warm-up scheduled on IO is visible to `cancelAll`/`cancelPlayerWarmup` on main, so the cancel edge can no longer miss a live job via a stale read (mirrors S0871). Static.
- **`BrowseManagerInitializer` (finding 553)** - the two warm-up collectors (settings, destinations) now use `activity.collectOnLifecycle(..)` (repeatOnLifecycle STARTED) instead of bare `lifecycleScope.launch { collect }`; DataStore/Room upstreams stop collecting while BrowseActivity is stopped in the back stack and re-collect on restart. `unsafe-collect` neuroslop count drops. Static.
- **`BrowseInlineAudioManager` (finding 242)** - new `evictInlineAudioCacheIfNeeded(keep)` prunes the `inline_audio` cache dir oldest-first to `INLINE_AUDIO_CACHE_CAP_BYTES` (256 MB), called in `downloadSmbAudioToCache` Success (already on IO), never deleting the just-written file. Playing file's fd is held open by MediaPlayer, so evicting its entry is harmless. Static.
- **`BrowseMicRecordingManager` + `BrowseActivity` (finding 153)** - the save pipeline (`stopRecording`, name-dialog OK) now launches on an injected `@ApplicationScope` scope, so activity teardown right after stop cannot cancel `micRecordingSaver.save` (drop recording / orphan temp). UI feedback guarded: name dialog skipped (save with default name) when the activity is finishing/destroyed; the `save()` main-thread block early-returns on `activity.isDestroyed`.

**Device gate.** Finding 153 changes runtime lifecycle (save after teardown) and adds UI-guard paths; probe `S0901: mic save on appScope`. Verify via `/spec-sweep`:
- Record a short voice note into an SMB destination, press stop, then immediately swipe BrowseActivity from recents (or navigate away) -> the recording still lands at the destination (no orphaned temp file), no crash from post-teardown UI feedback (logcat `S0901: mic save on appScope`).
- With "ask filename" enabled, stop then leave immediately -> the recording is saved with the default name rather than lost.

**Evidence rung:** static + compile + detekt (P2). Findings 60/242/553 are static (visibility / disk hygiene / collector lifecycle); finding 153 is device-observable lifecycle - deferred to `/spec-sweep`.

### Manual device test (finding 153)

**Date:** 2026-07-07. **Device:** emulator-5554, API 37 (x86_64), standard-debug. **Verdict: PASS.**

Setup: enabled "Voice recorder" in Settings, RECORD_AUDIO granted, destination left at default (Downloads). No SMB destination is configured on the emulator, so the save was exercised against the local Downloads fallback; the appScope-survival mechanism and the `S0901` probe are destination-agnostic (SMB vs local only changes destination resolution inside `micRecordingSaver.save`, not whether the save survives teardown), so this validates finding 153's core path.

- Run 1 (ask-filename ON, dialog path). Hold mic ~2.5s, release -> "Save Recording" dialog appeared (proves a valid recording, >`MIN_VALID_RECORDING_BYTES`), tap OK.
  - expected: probe `S0901: mic save on appScope` fires; file saved to destination; no orphaned temp | actual: probe fired once; `Download/REC_20260707_012611.m4a` (32096 B) written; app external `files/Music` temp dir empty; list scrolled to the saved file (`onFileSaved` feedback ran while activity alive). PASS.
- Run 2 (ask-filename OFF, teardown race). Hold mic ~2.5s, release, then immediately `KEYCODE_BACK` -> BrowseActivity finished to Main (fires `onPause -> micRecordingManager.release()`) before the save could be assumed complete.
  - expected: recording still lands, no orphaned temp, no crash from post-teardown UI feedback, probe present | actual: probe fired again; `Download/REC_20260707_012934.m4a` (31813 B) written despite the teardown; temp dir clean; no `FATAL EXCEPTION` / ANR / app-process exception in the window; audio focus request/abandon symmetric for both recordings; FMS process stayed alive; LeakCanary reported 0 distinct leaks. PASS.

Probe count: exactly 2 (`S0901: mic save on appScope`), one per recording. Evidence: `temp/S0901/logcat.txt`.

Note: `micRecordingManager.release()` runs from `onPause` while `pendingTempFile` is still set until the appScope `save()` calls `clearPendingSession`; on the emulator (fast local save) the save completed before the release deleted the temp, so no loss was observed. In-scope of this finding, did not reproduce - not parked.
