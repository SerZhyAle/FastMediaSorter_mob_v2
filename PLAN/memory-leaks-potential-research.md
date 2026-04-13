# Memory Leak Research Specification — FastMediaSorter v2

**Scope**: Long-running usage scenarios on Android 8+ (minSdk 26)
**Focus**: Playlist playback (1000+ tracks), Slideshow (hours), Document readers (100MB+)
**Last Updated**: 2026-01-10

---

## Executive Summary

This specification identifies edge cases and potential memory leak points across three long-running modes:
- **Playlist Audio Mode**: Extended playback with multiple rapid track transitions
- **Slideshow Mode**: Hours-long image carousel with background audio + animations
- **Document Readers**: Large PDF/EPUB/Text viewing with heavy caching

Key risk areas:
1. **Listener/Callback Leaks**: MediaController, Player listeners, Glide request listeners not removed
2. **Coroutine Management**: Jobs not cancelled on lifecycle destroy
3. **Cache Growth**: Glide memory/disk cache not trimmed during long sessions
4. **Resource Cleanup**: Bitmaps, Native heap, temp files not released in error paths
5. **Network Connection Lifecycle**: SMB/SFTP/FTP connections held too long or not closed on errors
6. **Fragment/Dialog Lifecycle**: Fragments retained, dialogs not dismissed on destroy
7. **Background Service Lifecycle**: MediaSessionService not stopped, MediaNotification not recycled
8. **Image Preload Operations**: DualSurfaceRenderer prefetch jobs continue despite pause/destroy

---

## 1. PLAYLIST LONG-RUNNING SCENARIOS

### 1.1 High-Volume Playlist (1000+ Tracks)

**Scenario**: User plays auto-shuffled playlist with 1000+ tracks, each with network cover art. Tracks auto-advance every 3-5 minutes over 8+ hours.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 1.1.1 | NowPlayingManager.startPlayback() | buildPlaybackUri() creates new Uri every transition; old Uri references may linger if MediaController listener not removed | Memory growth after 100+ track changes | Verify MediaController listeners removed in AudioServiceController.release() |
| 1.1.2 | AudioServiceController mediaController | MediaSessionService bound to Activity; if Activity destroyed without mediaController?.release(), listener hangs | Service keeps Player reference alive | Ensure mediaController?.release() called in PlayerLifecycleManager.onDestroy() before setting null |
| 1.1.3 | BackgroundMusicManager.currentPlayer | Single ExoPlayer instance; each track load allocates buffer; old buffers not released if clearPlaylist() skipped | Heap grows ~10MB per 50 track changes | Call audioPlayer.clearPlayWhenReady() and release on pause/stop |
| 1.1.4 | MediaFile cover art bitmaps | Glide loads 1000+ cover images; memory cache (64 MB default) should evict LRU, but disk cache grows unbounded | Disk cache > 2GB after 1000 covers loaded | Monitor GlideCacheStats in onPause(); call Glide.get().trimMemory() every 100 tracks |
| 1.1.5 | ExoPlayer source buffers | MediaSource allocated per track; Source objects may not be garbage collected if ExoPlayer still holds references | 500MB-1GB heap after 100+ tracks | Call exoPlayer.setMediaSource(null) before release() |
| 1.1.6 | Metadata fetching coroutines | BackgroundMusicManager launches coroutines for metadata (artist, album, duration); if cancelled mid-flight, may hold I/O resources | Network threads pool saturated | Ensure metadataScope?.cancel() in release(), verify all I/O jobs wrapped in try/finally |

**Reproduction Steps**:
1. Load playlist with 1000+ tracks (includes network sources: SMB/Cloud with 50-100 cover art URLs)
2. Play continuously with 3-5 min per track; don't pause/stop
3. Monitor heap with: `adb shell dumpsys meminfo FastMediaSorter | grep -E "HEAP|Glide"`
4. After 200+ track transitions (~12-16 hours), check for:
   - Heap plateau rise (not returning to baseline between tracks)
   - GlideCacheStats.recordLoad() showing increasing cache misses (disk churn)
   - ExoPlayer buffer allocations not freed

---

### 1.2 Rapid Track Transitions (Skipping)

**Scenario**: User rapidly skips forward/backward 20+ times in 10 seconds (e.g., searching for a song).

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 1.2.1 | PlayerMediaLoaderManager.playAudioViaService() | Pre-cache network audio; if skip happens mid-download, coroutine may not cancel properly | Download threads stuck in pending state | Ensure connection cancellation registered in throttle manager |
| 1.2.2 | ConnectionThrottleManager.cancelAllForResource() | Called on rapid transitions; if resource not deactivated, slots remain allocated | SMB/FTP slot pool exhausted after 20 skips | Verify deactivateVideoPlayerMode() also called; check ConnectionThrottleManager.activeSlots size |
| 1.2.3 | NowPlayingManager pending Uri builders | buildPlaybackUri() calls may pile up if mediaSession not responsive | Pending callbacks queue grows | Verify mediaSession.isActive before queuing build operations |
| 1.2.4 | Glide cover art requests | each track change triggers new Glide.load(); old requests should cancel but may not if listener retained | Glide request queue grows | Ensure Glide.clear() called in MediaFileAdapter.onViewRecycled() before new load |

**Reproduction Steps**:
1. Open playlist with SMB/Cloud source + cover art
2. Press skip 20+ times in rapid succession
3. Check: `adb shell dumpsys meminfo | grep -i glide`, `ps aux | grep -i smbj` (connection count)
4. Expected: GlideCacheStats.recordLoad() spike, then return to baseline; no orphaned connections

---

### 1.3 Background Audio Pause/Resume Cycle

**Scenario**: User pauses background music, navigates away, returns after 30+ min, resumes. Repeats 10+ times in session.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 1.3.1 | AudioServiceController mediaController | Held across pause; if not released on exit dialog skip (recent fix), may leak if BrowseActivity not destroyed | Service auto-restart on new music load creates dupe controller | Verify controller release() in exitPlayerWithAudioCheck() even on paused state |
| 1.3.2 | BackgroundMusicManager auto-stop timeout | Timer started on play; may not cancel if onPause() called before timeout expires | Orphaned timer callback fires after activity destroyed | Verify sleepTimerManager?.release() calls timer.cancel() |
| 1.3.3 | NowPlayingManager metadata updates | onPlaybackStateChanged() callbacks may fire after listeners removed if mediaSession not fully destroyed | Listener callback references old viewModel | Ensure mediaSession state listener removed in release() |

**Reproduction Steps**:
1. Play playlist, pause after 5 min
2. Exit PlayerActivity (triggers exitPlayerWithAudioCheck(), now skips dialog on paused)
3. Reopen PlayerActivity + resume
4. Repeat 10+ times, check:
   - `adb shell dumpsys audio | grep -i "foreground"` — service instances
   - Heap: should remain stable; check for growing GC pauses

---

## 2. SLIDESHOW LONG-RUNNING SCENARIOS

### 2.1 Extended Slideshow (Hours) with Background Music

**Scenario**: User starts slideshow with 500+ photos from cloud source, 5-sec interval, with background music. Runs for 4+ hours overnight.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 2.1.1 | DualSurfaceStaticImageRenderer image buffers | Each slide loads via Glide; old bitmap not recycled between slides if DiskCacheStrategy.AUTOMATIC doesn't evict | Heap grows 20-30MB per hour; GC pauses increase | Call Glide.clear(surfaceA/B) between slides; monitor surfaceA.setImageDrawable(null) |
| 2.1.2 | SlideshowController handler callbacks | Handler.post(slideChangeRunnable) fires every 5 sec; if handler.removeCallbacksAndMessages() not called on destroy, runnable queue grows | No slide advance after ~200 slides; Device becomes unresponsive | Verify handler cleanup in SlideshowController.cleanup() called from lifecycle observer |
| 2.1.3 | AnimatedImageController GIF decoding | If current slide is animated GIF, decoder thread may continue running post-destroy if not stopped | High CPU usage; rapid battery drain | Ensure AnimatedImageController.release() stops decoder thread |
| 2.1.4 | AudioBackgroundPhotosManager preload job | Manages photo carousel assets for background audio; loadJob()?.cancel() may not interrupt if mid-I/O | Photo list retained in memory; loadJob still running | Verify loadJob?.cancel() in release() and job wrapped with supervisorScope for cancellation |
| 2.1.5 | Glide cache growth | 500+ slides over 4h = potentially 500 * large JPEGs to memory/disk cache | Disk cache balloons to 500MB+; memory cache thrashing | Call Glide.get(context).trimMemory(TRIM_MEMORY_RUNNING_MODERATE) every 50 slides |
| 2.1.6 | Cast session persistence | If casting enabled and connection drops mid-slideshow, proxy server may remain running | CPU spinning, temp file not deleted, cast session ghost entry | Verify CastMediaManager.release() stops proxy server and calls deleteTempFile() |

**Reproduction Steps**:
1. Load 500-photo cloud slideshow (Google Drive, OneDrive, or SMB)
2. Enable background music (any playlist or single track repeated)
3. Lock device screen, leave running 4+ hours
4. Every 2 hours, check:
   - `adb shell dumpsys meminfo | grep "HEAP\|native"` — heap growth trajectory
   - `adb shell getprop | grep -i "glide"` — cache size
   - Battery usage: should not exceed 2% per hour
5. Return device and check for:
   - System UI freezes (GC pauses > 500ms)
   - Unwanted slideshow restart mid-sequence

---

### 2.2 Slideshow with Rapid Orientation Changes

**Scenario**: User rotates device 10+ times during slideshow; each rotation recreates PlayerActivity.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 2.2.1 | DualSurfaceStaticImageRenderer surface reference | Surface created per rotation; old Surface may not be released if view not detached | Native memory leak (surface buffers); crash on GpuContext exhaustion after ~5 rotations | Ensure onPause() clears surfaces; onDestroy() sets surfaceA/B to null |
| 2.2.2 | PlayerLifecycleManager state | State object retained across rotation if ViewModel not scoped correctly; old managers not released | Dialog references old activity context; "WindowLeaked" error | Verify listeners removed before activity destroyed; use viewLifecycleOwner for fragments |
| 2.2.3 | SlideshowController lifecycle observer | If observer not removed on configuration change, multiple observers may register | Slide timer fires N times per slide (N = rotation count) | Verify lifecycle.removeObserver(this) in cleanup() |

**Reproduction Steps**:
1. Start slideshow
2. Rotate device 10+ times rapidly (portrait ↔ landscape)
3. Check:
   - `adb shell dumpsys meminfo | grep native` — native heap and surface count
   - Logcat: search for "WindowLeaked" or "Surface exhaustion"
   - Expected: heap stable; no "WindowLeaked"

---

## 3. DOCUMENT READER LONG-RUNNING SCENARIOS

### 3.1 Large PDF Reading (100MB+, 1000+ Pages)

**Scenario**: User opens large PDF (100-500 MB, 1000+ pages) and scrolls through pages over extended reading session (30+ minutes).

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 3.1.1 | PdfViewerManager page cache | Renders current + adjacent pages; if page buffer not capped, memory grows unbounded | OOM after ~50 page scrolls on low-memory devices | Verify PdfViewerManager.setMaxCacheSize() respects device memory tier; monitor decoded page count |
| 3.1.2 | PDF rendering thread pool | PDFRenderer uses native pdfium library; threads may not be released if document not properly closed | High CPU usage, "Native crash" in logcat | Ensure document?.close() called in onDestroy() |
| 3.1.3 | Texture/Canvas cache (if GPU-accelerated) | Each page render may allocate GPU texture; not freed between renders | GPU memory exhaustion after ~100 page scrolls | Check PdfViewerManager renders on CPU (safe) not GPU; verify texture cache cleared |
| 3.1.4 | Temp file cache from network pre-download | If PDF streamed from SMB/Network, pre-download cached in UnifiedFileCache; not cleaned until app exit | Disk usage grows 100MB per PDF | Verify UnifiedFileCache.clearAll() called in MainActivity.onDestroy() + manual clear in settings |

**Reproduction Steps**:
1. Open 200MB PDF from network source (requires pre-download)
2. Scroll through pages (view 50+ pages)
3. Monitor:
   - `adb shell dumpsys meminfo | grep "HEAP\|PSS"` at page 0, 25, 50
   - Heap growth rate: should not exceed 2-3MB per 10 pages
   - Check temp file: `adb shell ls -lh /data/data/com.sza.fastmediasorter/cache/` should not grow > 200MB
4. Expected: Smooth scrolling; no OOM; temp file cleaned on app close

---

### 3.2 EPUB Reading with Inline Images (500+ Pages)

**Scenario**: User reads EPUB with inline HTML5 images (SVG, PNG, JPG), scrolls to end of book (500+ pages).

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 3.2.1 | EpubViewerManager image cache | WebView loads images via Glide; each image added to memory cache; old images not evicted if max cache not reached | Heap > 500MB after reading 300+ pages with images | Verify Glide caching policy in NetworkFileModelLoader; enable signature-based caching |
| 3.2.2 | Java2D rendering (EPUB → Canvas) | WebView internal bitmap allocations; multiple WebView pages may linger if not properly detached | Memory leak of 50-100MB per EPUB chapter with complex layouts | Ensure EpubViewerManager.release() destroys WebView; check WebViewDatabase.clearHttpAuthUsernamePassword() |
| 3.2.3 | Network requests for images | HTML img src="" attributes may trigger concurrent requests; throttle manager slots may not be released if image fails to load | SMB connection pool exhausted; Cannot open new images | Verify CloudThumbnailModelLoader.cleanup() disconnects HTTP connection on failure |

**Reproduction Steps**:
1. Open 200-page EPUB with 20+ inline images
2. Scroll to end of book
3. Monitor:
   - WebView memory via `adb shell dumpsys meminfo | grep WebView`
   - Network connections: `adb shell netstat -an | wc -l` before/after reading
4. Expected: WebView heap should not exceed 100MB; connections released < 1 sec after read

---

### 3.3 Text Reader with OCR/Translation (Large Document)

**Scenario**: User reads large text file (50MB+) with OCR and real-time translation enabled; scrolls through 1000+ pages.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 3.3.1 | OCR result cache (TranslationCacheManager) | TranslationManager caches all OCR results in-memory HashMap; not cleared until app restart | Heap grows 200+ MB over reading session | Verify cache cap in TranslationCacheManager; call cache.clear() on destroy or every 500 pages |
| 3.3.2 | Translation API request queue | If translation requests queued faster than API responds, CoreCoroutineContext job accumulates pending coroutines | Job count grows; GC pressure increases | Ensure translationScope?.cancel() in TranslationManager.release(); cap concurrent requests to 3-5 |
| 3.3.3 | TextViewerManager rendering state | Maintains page-to-offset mappings; if not cleared, map retains references to old text buffers | Memory not released when exiting reader | Verify TextViewerManager.release() clears all mappings + releases text buffer references |

**Reproduction Steps**:
1. Open 50MB text file with OCR + translation enabled
2. Scroll through all pages (1000+) with continuous translation
3. Monitor:
   - TranslationCacheManager.cacheSize() at page 0, 250, 500, 750, 1000
   - Heap pressure every 100 pages
4. Check for:
   - Uncancelled coroutines: `adb shell dumpsys | grep "Job.*COMPLETED.*0"` should show < 5 active jobs
   - Cache size should not exceed 150MB

---

## 4. NETWORK STREAMING EDGE CASES

### 4.1 Long-Running SMB Stream with Connection Drops

**Scenario**: User plays audio from SMB share over 2+ hours; WiFi disconnects/reconnects 3-5 times.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 4.1.1 | SmbConnectionManager connection pool | On disconnect, connection not properly closed; reconnect creates new connection; orphaned sockets accumulate | File handle exhaustion error after 3-5 reconnects; crash | Verify SmbConnectionManager.closeStaleConnections() calls connection.close() with try/finally |
| 4.1.2 | NetworkFileModelLoader stream | If audio stream interrupted mid-read, stream may not be closed (cleanup() is no-op in some cases) | Java heap grows with retained streams; temp files not cleaned | Ensure cleanup() fully closes connection; override cancel() to interrupt mid-request |
| 4.1.3 | AudioPlaybackService buffer | ExoPlayer buffers from interrupted stream; if not flushed, buffer data persists | Stale audio data plays on next connect; audio glitching | Call exoPlayer.setMediaSource(null) before retry + flush buffer state |

**Reproduction Steps**:
1. Play audio from SMB share (set up 50-100MB file)
2. Start playback, wait 30 sec
3. Toggle WiFi off/on repeatedly (5 times over 2 min)
4. Monitor:
   - File handles: `adb shell lsof -p <PID> | wc -l` — should not exceed 20
   - Connection timeout errors in logcat
5. Expected: Playback resumes after reconnect; no orphaned connections

---

### 4.2 SFTP Large File Download with Pause/Resume

**Scenario**: User downloads 500MB audio file via SFTP with pause/resume 10+ times.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 4.2.1 | SSHJClientPool SSH session | Pause kills download but SSH session may remain open; resume creates new session; old sessions not closed | Port exhaustion; "Too many open files" error | Verify SSHJClientPool.returnClient() always closes session on error |
| 4.2.2 | UnifiedFileCache temp file on pause | Temp file created during download; pause does not delete it; resume overwrites or creates duplicate | Disk usage balloons with orphaned temp files | Verify cache cleanup after download abort; no temp files left > 24 hours old |
| 4.2.3 | ConnectionThrottleManager slot | Slot allocated for download; if pause doesn't call deactivate(), slot remains held | Concurrency quota exhausted after 10 pauses | Verify deactivateDownloadMode() called on pause |

**Reproduction Steps**:
1. Download 500MB file via SFTP
2. Pause/resume 10+ times
3. Check:
   - Disk temp files: `adb shell ls -lah /data/data/com.sza.fastmediasorter/cache/` — should not exceed 1GB
   - Open connections: `netstat -an | grep ESTABLISHED` — should not exceed 5
4. Expected: All temp files cleaned on app exit; no orphaned connections

---

## 5. CACHE LAYER EDGE CASES

### 5.1 Glide Memory Cache Thrashing

**Scenario**: User browses 500+ photos with rapid thumbnails loading; device memory tier is LOW (heap < 512MB).

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 5.1.1 | GlideAppModule memory cache formula | Formula uses 10% of max heap (64MB cap); low-memory devices may still OOM if formula miscalculated | OOM crash after loading 50-100 thumbnails on low-memory device | Verify formula uses Runtime.getRuntime().maxMemory() (NOT availMem); cap at 32MB for LOW tier |
| 5.1.2 | Format override (PREFER_RGB_565) | On LOW tier, format should be PREFER_RGB_565 (50% less memory); if not applied, memory pressure spikes | Memory cache misses spike; constant cache evictions | Ensure MemoryTier.detect() correctly identifies LOW; format applied in glideRequest.format() |
| 5.1.3 | Glide disk cache cleanup on app close | MainActivity.onDestroy() calls Glide.clearMemory() but NOT clearDiskCache() (fires async I/O); if app crashes, disk cache orphaned | Disk cache grows unbounded; app storage full after 10+ app sessions | Implement lifecycle-aware cache cleanup; schedule disk cache clear on startup if > 2GB |

**Reproduction Steps**:
1. Configure device as LOW memory (set max Java heap to 512MB via build.gradle)
2. Load RecyclerView with 500+ photo thumbnails from cloud
3. Scroll rapidly through all items
4. Monitor:
   - `adb shell dumpsys meminfo | grep "HEAP"` every 50 thumbnails
   - GlideCacheStats.recordLoad() cache hit ratio
5. Expected: No OOM; cache hit ratio > 60% on repeat scroll

---

### 5.2 UnifiedFileCache Temp File Accumulation

**Scenario**: User downloads 50+ files from multiple sources (SMB, SFTP, Cloud) over extended session without clearing cache.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 5.2.1 | Orphaned temp files | Failed downloads or interrupted transfers leave temp files in cache; no auto-cleanup | Disk usage grows 1-2GB over week | Implement cache cleanup on startup: delete files > 7 days old + files marked "temp" |
| 5.2.2 | Cache index out-of-sync | If database corrupted or updated without corresponding file deletion, index may reference missing files (or vice versa) | Silent cache misses; repeated downloads | Verify index integrity on startup; rebuild if mismatch detected |

**Reproduction Steps**:
1. Download 50 files from mixed sources, interrupt 10 downloads mid-way
2. Force-close app without clean shutdown
3. Reopen app and check:
   - `adb shell du -sh /data/data/com.sza.fastmediasorter/cache/` — should be < 500MB
   - Database: `adb shell sqlite3 /data/data/com.sza.fastmediasorter/databases/file_cache.db "SELECT COUNT(*) FROM cache;" | wc -l`
4. Expected: Cache < 500MB; no orphaned files

---

## 6. LISTENER/CALLBACK CLEANUP EDGE CASES

### 6.1 ExoPlayer Listener Cleanup

**Scenario**: Listener registered but not removed before Player released; multiple listeners accumulate over session.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 6.1.1 | Player.EventListener | Added in PlayerMediaLoaderManager; if not removed before player.release(), listener holds Activity reference | Activity not garbage collected; leak detector reports Memory Leak | Verify exoPlayer.removeListener() called for ALL listeners in release() before exoPlayer.release() |
| 6.1.2 | MediaSessionService onPlayWhenReadyChanged() | Listener triggered on every play-state change; if not unregistered, callback fires on old Activity after destroy | Crash: "Cannot access View on destroyed Activity" | Ensure mediaController.removeListener() in release() |

**Reproduction Steps**:
1. Enable LeakCanary memory leak detection
2. Play several tracks (5+ transitions)
3. Exit PlayerActivity, reopen multiple times (10+ cycles)
4. Check LeakCanary results for:
   - "Player listener retained Activity"
   - "MediaSessionService connected after Activity destroy"
5. Expected: No leaked instances

---

### 6.2 MediaSessionService Listener Cleanup

**Scenario**: MediaSessionService has callbacks/listeners; if not cleaned up on service stop, media session persists.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 6.2.1 | PlaybackStateCallback | Registered in onPlay(); not unregistered in onStop() | Callback fires after service destroyed; crash | Verify onStop() calls session.setCallback(null) before stopSelf() |
| 6.2.2 | MediaController connected flag | If mediaController?.release() not called, connection persists | Service keeps Player alive; memory leak | Ensure AudioServiceController.release() calls mediaController?.release() + sets null |

**Reproduction Steps**:
1. Play background music, pause (triggers new exit dialog skip code path)
2. Exit app
3. Reopen app multiple times (5+ cycles)
4. Check logcat: should not see "MediaSessionService: callback fired: ..." after destroy
5. Expected: No orphaned service callbacks

---

## 7. COROUTINE LIFECYCLE EDGE CASES

### 7.1 Unscoped Coroutine Jobs

**Scenario**: Coroutine launched without scope; not cancelled on Activity/Fragment destroy.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 7.1.1 | BackgroundMusicManager.metadataScope | Used for metadata fetching; if supervisorScope not used, exception in one child cancels all | Metadata stuck, no update on track change | Verify scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()) |
| 7.1.2 | TranslationManager coroutine | Translation job may continue running after Fragment destroyed; holds reference to old ViewModel | Memory leak of 10-50MB (translation cache retained) | Ensure translationScope?.cancel() in onDestroyView() |
| 7.1.3 | PlayerMediaLoaderManager download job | Pre-cache job not cancelled if track skipped mid-download | Download thread pool exhausted; subsequent downloads fail | Verify downloadJob?.cancel() called in cleanup() + in skip action handler |

**Reproduction Steps**:
1. Enable coroutine debugging: `adb shell setprop debug.android.debuggable true`
2. Start long-running operation (metadata fetch, translation)
3. Destroy Activity during operation
4. Check: `adb logcat | grep -i "Job was cancelled but"` — should see cancelled job logs
5. Expected: All jobs cancelled; no orphaned coroutine threads

---

## 8. BACKGROUND SERVICE LIFECYCLE EDGE CASES

### 8.1 MediaSessionService Start/Stop Cycles

**Scenario**: Service started, stopped, restarted 20+ times in rapid succession (user plays/pauses/exits repeatedly).

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 8.1.1 | MediaNotification persistence | Notification created per start; if not cancelled before next start, multiple notifications leak | Notification area cluttered; device crashes after 20+ cycles | Verify stopForeground(STOP_FOREGROUND_REMOVE) called in onStop() |
| 8.1.2 | ExoPlayer instance in service | Single ExoPlayer reused; if release() skipped on stop, player state corrupted | Next play session inherits bad state; audio doesn't play | Verify onStop() calls exoPlayer.release() + reinitializes on next start |
| 8.1.3 | Service connection count | If Activity recreates on rotation while service running, connection count may not reset | Multiple Activity: Service connections drain memory | Verify connection closed in Activity.onDestroy() even if service still running |

**Reproduction Steps**:
1. Play audio 20+ times with pattern: play → pause → exit → reopen
2. Monitor:
   - Notification count: `adb shell dumpsys notification | grep "com.sza.fastmediasorter"` — should be 0-1
   - Service instances: `adb shell dumpsys activity services | grep "AudioPlaybackService"` — should be 0-1
3. Expected: Single notification; single service instance

---

## 9. ERROR PATH RESOURCE CLEANUP

### 9.1 Network Download Failure Cleanup

**Scenario**: Network download fails midway (0-100% corruption); resources not cleaned up on error.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 9.1.1 | Partial temp file | Download fails; temp file not deleted; UnifiedFileCache index still references it | Disk accumulates orphaned files; cache index corrupted | Ensure try/finally in download handler; always delete temp file on error |
| 9.1.2 | Input/Output stream not closed | InputStream/OutputStream left open if exception thrown mid-read | File handle leak; "Too many open files" after 10 failed downloads | Use try-with-resources or finally for all I/O streams |
| 9.1.3 | Connection slot not released | ConnectionThrottleManager slot allocated for download; exception prevents deactivate() call | Slot remains active; next download cannot proceed | Wrap all download logic in try/finally; always call deactivate() |

**Reproduction Steps**:
1. Simulate download failure: inject exception in NetworkFileModelLoader at 50% progress
2. Start 5 downloads; let each fail
3. Check:
   - Temp files: `adb shell find /data/data/com.sza.fastmediasorter/cache/ -name "*.tmp" -o -name "*.partial"` — should be 0
   - Connections: `netstat -an | grep -c ESTABLISHED` — should be 1-2 (not 10+)
4. Expected: No orphaned files/connections

---

## 10. ACTIVITY/FRAGMENT LIFECYCLE EDGE CASES

### 10.1 Fragment Retained After Configuration Change

**Scenario**: Fragment setRetainInstance(true) or ViewModel held across Activity recreation; managers not re-initialized.

**Potential Leak Points**:

| # | Component | Risk | Symptom | Resolution |
|---|-----------|------|---------|-----------|
| 10.1.1 | Retained ViewModel holding Activity reference | ViewModel stores Activity reference; if not nullified on destroy, leak occurs | Memory leak detected by LeakCanary; Activity not GC'd | Verify ViewModel uses WeakReference for Activity or nullifies on destroy |
| 10.1.2 | Retained EventListener | Fragment retained with listener registered on old Activity; new Activity starts, listener fires on old instance | Crash or silent failure | Ensure listener unregistered in onDestroyView(); re-register in onViewCreated() |

**Reproduction Steps**:
1. Rotate device 5+ times rapidly
2. Enable LeakCanary; leave app open
3. Check for: "ViewModel retained Activity reference"
4. Expected: No retained Activity references

---

## 11. MONITORING & DIAGNOSTICS

### Heap Growth Baseline

| Scenario | Device Tier | Expected Heap at Start | Heap After 1h | Heap After 4h | Alert Threshold |
|----------|-------------|------------------------|---------------|---------------|-----------------|
| Playlist (1000 tracks) | Standard (2GB) | 80-100 MB | 150-180 MB | 250-300 MB | > 400 MB |
| Slideshow (500 photos) | Standard (2GB) | 100-120 MB | 200-250 MB | 400-500 MB | > 600 MB |
| PDF (100MB) | Standard (2GB) | 120-150 MB | 200-250 MB | 250-300 MB | > 350 MB |
| Low Memory (Lite) | Low (512MB) | 40-60 MB | 100-120 MB | 150-180 MB | > 200 MB |

### Glide Cache Monitoring

| Metric | Method | Alert Condition |
|--------|--------|-----------------|
| Memory Cache Hit Ratio | GlideCacheStats.memoryCacheHits / totalLoads | < 50% after 100 loads |
| Disk Cache Size | Glide cache directory du | > 2GB after 1000 thumbnails |
| Cache Eviction Rate | recordLoad() spike on new loads | > 100 evictions per 50 loads |

### Network Resources Monitoring

| Metric | Method | Alert Condition |
|--------|--------|-----------------|
| Open Connections | `netstat -an \| grep ESTABLISHED \| wc -l` | > 10 total |
| File Handles | `lsof -p <PID> \| wc -l` | > 30 total |
| ConnectionThrottleManager Slots | Internal counter | Any slot > 60 sec without deactivate() |

---

## 12. TESTING CHECKLIST

- [ ] **Playlist Test**: Run 1000-track playlist for 4 hours; verify heap stable after hour 2
- [ ] **Slideshow Test**: Run 500-photo slideshow for 4 hours; monitor GC pauses < 200ms
- [ ] **PDF Test**: Open 200MB PDF, scroll to end; heap not exceed 350MB on standard device
- [ ] **Rapid Transitions**: Skip 20+ times in 10 sec; verify no orphaned connections
- [ ] **Orientation Changes**: Rotate 10+ times rapidly; verify no "WindowLeaked" in logcat
- [ ] **Network Disconnects**: Pause/resume 10+ times over 2 hours; verify no orphaned connections
- [ ] **Cache Accumulation**: Download 50 files, interrupt 10; verify temp files cleaned
- [ ] **LeakCanary Check**: Run with LeakCanary enabled; zero detected leaks after 2-hour session
- [ ] **Memory Dump**: `adb shell dumpsys meminfo <PID>` at start/1h/2h/4h; chart heap growth
- [ ] **GlideCacheStats**: Log from BrowseActivity.onDestroy(); verify cache hit ratio > 60%

---

## 13. REFERENCES

- **ExoPlayer Release Pattern**: `exoPlayer.removeListener(listener); exoPlayer.release()`
- **Glide Cleanup Pattern**: `Glide.with(context).clear(target); Glide.get(context).clearMemory()` (disk async)
- **Coroutine Cancellation**: `scope?.cancel(); scope?.join()` in onDestroy()
- **MediaSessionService**: `mediaController?.removeListener(); mediaController?.release()`
- **Handler Cleanup**: `handler.removeCallbacksAndMessages(null)` in onDestroy()
- **Lifecycle Observer**: `lifecycle.removeObserver(observer)` in onDestroy()
- **Stream Cleanup**: `try-with-resources` or explicit `finally { stream?.close() }`
- **Temp File Cleanup**: `finally { tempFile.delete() }` or scheduled cleanup on startup

---

## 14. NEXT STEPS (Research Phase)

1. **Code Audit Checklist**: For each edge case (1-10), locate implementation file + specific line ranges
2. **Dynamic Testing**: Set up test harness with memory profiler to reproduce each scenario
3. **Instrumentation**: Add Timber logs at critical points (release(), cleanup(), cancel())
4. **Leak Detection**: Run LeakCanary + Android Profiler for each scenario
5. **Baseline Establishment**: Create performance baseline for each scenario (heap, CPU, battery)
6. **Regression Tests**: Create Maestro/Instrumentation tests to catch leaks before release

---

## 15. RESEARCH PRIORITIZATION & MEASUREMENT STRATEGY

### 15.1 Research Order (By Risk Impact)

**Phase 1: Critical (15 days)** — Likeliest to cause user-reported crashes/hangs

| Priority | Scenario | Risk Level | Impact | Timeline | Owner |
|----------|----------|-----------|--------|----------|-------|
| 1 | Glide Memory Cache Thrashing (5.1) | CRITICAL | OOM crash on low-memory devices with 50+ photos | 3 days | [Assign] |
| 2 | Listener Cleanup (6.1-6.2) | CRITICAL | Activity not garbage collected; leak detector alerts | 3 days | [Assign] |
| 3 | MediaSessionService Start/Stop (8.1) | CRITICAL | Notification leak / memory growth after 20+ cycles | 3 days | [Assign] |
| 4 | UnifiedFileCache Temp Files (5.2) | HIGH | Disk full after week of normal use; app crash on cache miss | 3 days | [Assign] |
| 5 | Coroutine Cancellation (7.1) | HIGH | Jobs running after Activity destroy; memory leak | 3 days | [Assign] |

**Phase 2: High (15 days)** — Moderate probability, affects extended sessions

| Priority | Scenario | Risk Level | Impact | Timeline | Owner |
|----------|----------|-----------|--------|----------|-------|
| 6 | ExoPlayer Buffer Cleanup (1.1.5) | HIGH | 500MB+ heap after 100+ track transitions | 2 days | [Assign] |
| 7 | Slideshow Image Buffering (2.1.1) | HIGH | GC pauses > 500ms after 2+ hours; battery drain | 2 days | [Assign] |
| 8 | PDF Large File Streaming (3.1) | HIGH | OOM after scrolling 50+ pages of 200MB PDF | 2 days | [Assign] |
| 9 | SMB Connection Drops (4.1) | HIGH | File handle exhaustion after 3-5 WiFi reconnects | 2 days | [Assign] |
| 10 | Handler Callback Cleanup (7.1, 2.1.2) | HIGH | Slide timer fires N times per slide after rotation | 2 days | [Assign] |

**Phase 3: Medium (20 days)** — Lower probability edge cases, but important for stability

| Priority | Scenario | Risk Level | Impact | Timeline | Owner |
|----------|----------|-----------|--------|----------|-------|
| 11 | Network Download Error Paths (9.1) | MEDIUM | Orphaned temp files accumulate; disk leaks | 2 days | [Assign] |
| 12 | Rapid Orientation Changes (2.2) | MEDIUM | Native surface exhaustion after 5+ rotations | 2 days | [Assign] |
| 13 | Rapid Track Skipping (1.2) | MEDIUM | ConnectionThrottleManager slots stuck after 20 skips | 2 days | [Assign] |
| 14 | SFTP Large File Resume (4.2) | MEDIUM | SSH session pool exhaustion after 10+ pause cycles | 2 days | [Assign] |
| 15 | Configuration Change Fragments (10.1) | MEDIUM | Retained ViewModel holds Activity reference; leak | 2 days | [Assign] |

**Phase 4: Low (15 days)** — Specific device/scenario edge cases

| Priority | Scenario | Risk Level | Impact | Timeline | Owner |
|----------|----------|-----------|--------|----------|-------|
| 16 | EPUB WebView Cache (3.2) | LOW | Memory leak of 50-100MB per WebView chapter | 2 days | [Assign] |
| 17 | Text Reader OCR Cache (3.3) | LOW | Heap grows 200+ MB over long reading session | 2 days | [Assign] |
| 18 | Cast Session Proxy (2.1.6) | LOW | Temp file not deleted on cast disconnect | 2 days | [Assign] |

**Total Research Timeline**: ~60 days (4 phases parallel)

---

### 15.2 Memory & Resource Measurement Methodology

#### 15.2.1 Heap Monitoring Formula

```
Heap_Growth_Rate = (Heap_End_Hour_N - Heap_End_Hour_0) / N hours

Alert_Threshold = Baseline_Heap * 1.5  (50% increase from baseline)
Critical_Threshold = Baseline_Heap * 2.5  (150% increase = probable OOM soon)
```

**Measurement Points**:
- Hour 0: Fresh app launch + activity visible (Baseline)
- Hour +1, +2, +4, +8: Periodic measurement during scenario
- End: After scenario complete (reset check)

**Formula Application**:

| Scenario | Baseline (MB) | Alert Level (MB) | Critical (MB) | Device |
|----------|---------------|------------------|---------------|--------|
| Playlist 1000 tracks | 100 | 150 | 250 | Standard 2GB |
| Slideshow 500 photos | 120 | 180 | 300 | Standard 2GB |
| PDF 100MB | 150 | 225 | 375 | Standard 2GB |
| Lite Flavor | 60 | 90 | 150 | Low 512MB |

#### 15.2.2 Native Heap Measurement

```
Native_Growth = (Current_Native_MB - Baseline_Native_MB) / Time_Hours

Alert: > 50MB/hour
Critical: > 100MB/hour
```

**Measurement Command**:
```powershell
# Capture native heap
adb shell dumpsys meminfo <PID> | grep -A 2 "Native Heap"

# Parse output
# Native Heap: Used=XXX, Alloc=YYY, Free=ZZZ
# Calculate: (Used - BaselineUsed) / hours elapsed
```

#### 15.2.3 Cache Size Growth Trajectory

```
Cache_Growth_Rate = (Cache_End_Size - Cache_Start_Size) / Items_Processed

Expected_Cache_Size = Items * Growth_Rate_Per_Item

Example: 1000 thumbnails * 200KB/thumb = 200MB expected
Alert: > 300MB actual (50% overgrowth)
```

**Measurement Commands**:
```powershell
# Glide disk cache
du -sh /data/data/com.sza.fastmediasorter/cache/

# UnifiedFileCache temp files
find /data/data/com.sza.fastmediasorter/cache/ -type f | wc -l
find /data/data/com.sza.fastmediasorter/cache/ -type f -mtime +7 | wc -l  # Orphaned files

# GlideCacheStats from log
# diskCacheHits + memoryCacheHits / (totalLoads)
# Expected: > 60% cache hit ratio
```

#### 15.2.4 GC Pause Monitoring

```
GC_Pause_Duration = Number of "GC_FOR_ALLOC" lines in logcat * 10ms avg

Alert: > 3 pauses per minute (indicates memory pressure)
Critical: > 5 pauses per minute + pause_duration > 500ms (OOM imminent)
```

**Measurement Command**:
```powershell
# Capture GC events
adb logcat | grep "GC_FOR_ALLOC\|GC_EXPLICIT" | tee gc_log.txt

# Parse and count
Select-String "GC_FOR_ALLOC" gc_log.txt | Measure-Object | Select-Object Count
```

#### 15.2.5 Network Resource Leak Calculation

```
Connection_Leak_Count = (Current_Active_Connections - Expected_Active) * Time_Hours

Expected_Active: 1-2 for streaming, 0 when idle
Alert: > 5 total connections at idle
```

**Measurement Commands**:
```powershell
# Active connections
netstat -an | grep ESTABLISHED | Measure-Object | Select-Object Count

# Connection backlog
netstat -an | grep TIME_WAIT | Measure-Object | Select-Object Count
# Alert if TIME_WAIT > 10 (connections not closing properly)

# File handles
lsof -p <PID> | Measure-Object | Select-Object Count
# Alert if > 30 for idle app
```

#### 15.2.6 Coroutine Job Leak Detection

```
Active_Jobs = Job.getChildren().filter { !it.isCompleted } .size

Alert: > 2 active jobs at idle (singleton jobs should be < 1 when stopped)
Critical: > 10 active jobs (severe coroutine leak)
```

**Measurement Command**:
```powershell
# Via Timber debug logs (add instrumentation to key scopes)
adb logcat | grep "ActiveJobs:"

# Or via Android Profiler: Profiler -> CPU Tab -> Method Trace
# Filter for CoroutineScope instances
```

---

### 15.3 Measurement Dashboard Template

Create a spreadsheet or metrics file with these columns for each scenario:

| Timestamp | Hour | Heap MB | Native MB | GC Count | Cache MB | Connections | Notes |
|-----------|------|---------|-----------|----------|----------|-------------|-------|
| 2026-04-13 08:00 | 0 | 100 | 50 | 0 | 200 | 1 | Baseline |
| 2026-04-13 09:00 | 1 | 115 | 52 | 8 | 250 | 1 | Within 50% |
| 2026-04-13 10:00 | 2 | 130 | 55 | 12 | 300 | 2 | Growth steady |
| 2026-04-13 12:00 | 4 | 160 | 65 | 25 | 450 | 1 | Alert level reached |
| 2026-04-13 16:00 | 8 | 200 | 80 | 50 | 650 | 1 | **CRITICAL** |

**Analysis**:
- Heap growth rate: (200-100) / 8 = **12.5 MB/hour** (exceeds ~2 MB/hour baseline) = **LEAK LIKELY**
- Native growth: (80-50) / 8 = **3.75 MB/hour** (acceptable)
- GC trend: 50 pauses over 8h = ~1 every 10 min (acceptable)
- Cache growth: (650-200) / 8h = **56 MB/hour** (acceptable for 1000-item scenario)

---

## 16. DATA CORRUPTION & PROCESS INTEGRITY EDGE CASES

### 16.1 Database Corruption Scenarios

**Scenario**: App crashes or forced-stop during database write; database becomes corrupted or partially written.

**Potential Data Loss Points**:

| # | Component | Risk | Manifestation | Mitigation |
|---|-----------|------|----------------|-----------|
| 16.1.1 | MediaStore cache corruption | Write checkpoint not atomic; crash mid-insert leaves orphaned records | Duplicate files in next scan; file operations fail | Use Room @Transaction(abortOnError=true) on all multi-statement writes |
| 16.1.2 | PlaybackPosition table | Position saved without UNIQUE constraint; duplicate entries on rapid play/pause | App reads wrong playback position; seek to wrong time | Verify Room schema has PRIMARY KEY on (resourceId, filePath) |
| 16.1.3 | Cloud sync cache | Incremental cloud sync writes delta; crash mid-sync corrupts sync marker | Next sync skips files or re-downloads entire resource | Implement Room Foreign Key CONSTRAINT + Journal mode WAL |
| 16.1.4 | Temp file index | References temp files; if app crashes, index stale after restart | Missing files reported as deletion candidates | Add verification query: `SELECT * FROM temp_files WHERE NOT EXISTS(file_path)` on startup |

**Testing Protocol**:
1. Start operation, wait for write in progress
2. Kill app: `adb shell am force-stop com.sza.fastmediasorter`
3. Reopen app
4. Verify: `adb shell dumpsys | grep -i "ERROR\|CORRUPTION"`
5. Run DB integrity check: `adb shell sqlite3 /data/data/.../databases/app.db "PRAGMA integrity_check;"`

---

### 16.2 Network Protocol Integrity Corruption

**Scenario**: Network connection interrupted mid-transfer; data stream corrupted.

**Potential Corruption Points**:

| # | Component | Risk | Manifestation | Mitigation |
|---|-----------|------|----------------|-----------|
| 16.2.1 | SMB partial file transfer | Incomplete file downloaded; file size metadata not updated | File plays/opens corrupted; crash on access | Implement Checksum verification (MD5/SHA1) before marking complete |
| 16.2.2 | SFTP stream interrupted | Filehandle closed mid-read; temp file truncated | Playback artifact; audio skip or noise | Use try/finally to clean up on disconnect + re-validate file size |
| 16.2.3 | Cloud API data mismatch | Incremental metadata sync skipped due to timeout | File list incomplete; missing files not discoverable | Add retry loop + verify count matches API response before committing cache |

**Reproduction**:
1. Download large file (500MB) from network
2. Mid-transfer (50%), kill WiFi: `adb shell svc wifi disable`
3. Check temp file integrity: `md5sum /data/data/.../cache/temp_file.tmp`
4. Expected: Temp file cleaned or re-downloaded on next resume

---

### 16.3 File System State Corruption

**Scenario**: File operation (delete, rename, move) fails; file system left in inconsistent state.

**Potential Corruption Points**:

| # | Component | Risk | Manifestation | Mitigation |
|---|-----------|------|----------------|-----------|
| 16.3.1 | Batch rename operation | Rename succeeds for 5 files, fails on 6th; state inconsistent | 5 files renamed, 1 not; undo operation may fail | Wrap in Room transaction + implement rollback logic |
| 16.3.2 | Move across partitions | Move from /cache to /sdcard fails mid-copy; original deleted | File lost from app's perspective; data loss | Verify destination write before deleting source; use File.renameTo() atomicity |
| 16.3.3 | Symlink/Link operations | Symlink created but target deleted externally | App reads stale link; crash on file access | Validate link target before each access; refresh on NotifyFsChange event |

**Reproduction**:
1. Initiate batch rename of 100 files
2. Force-stop app at 50% completion
3. Check file names: expected 50 renamed, 50 original
4. Verify app consistency on restart: no duplicate entries in database

---

## 17. ANALYTICAL CALCULATION FRAMEWORK (Theoretical Bounds)

Before running ANY Marathon tests, calculate expected resource consumption mathematically. **Skip empirical testing if theory predicts safe margins.**

### 17.1 Playlist Memory Consumption (Calculated)

**Given**:
- Track count: $N$ tracks
- Avg cover art size: $S_{cover}$ (e.g., 200KB)
- ExoPlayer buffer: $B_{exo} = 2.5 MB$ per ExoPlayer instance
- ExoPlayer MediaSource: $M_{source} = 0.3 MB$ per source
- Audio metadata: $M_{meta} = 10 KB$ per track

**Formula**:

$$\text{Heap}_{playlist} = N \times (S_{cover} + M_{meta}) + B_{exo} + (N \times M_{source})$$

**Example Calculation** (1000-track playlist):
$$\text{Heap} = 1000 \times (0.2 + 0.01) + 2.5 + (1000 \times 0.3) = 221 + 2.5 + 300 = 523.5 MB$$

**Alert Thresholds**:
- If calculated > 300MB on baseline device (2GB heap): ⚠️ **MUST TEST** (high pressure risk)
- If calculated > 150MB on low-memory device (512MB heap): ⚠️ **MUST TEST** (OOM risk)  
- If calculated < 150MB on baseline, < 80MB on low-memory: ✅ **SAFE, SKIP MARATHON**

**Mitigation if High**:
- Implement `maxPlaylist = 500` cap
- Add cache eviction every 100 tracks: `Glide.get().clearMemory()`
- Set Glide memory cache to 32MB (auto-evict covers)

---

### 17.2 Slideshow Memory Consumption (Calculated)

**Given**:
- Photo count: $P$ photos
- Avg photo size (loaded): $S_{photo}$ (typical: 2-4MB per photo at screen resolution)
- Glide memory cache: $C_{glide} = 64 MB$
- SlideshowController overhead: $O_{slideshow} = 5 MB$
- BackgroundMusic overhead: $O_{music} = 50 MB$

**Formula**:

$$\text{Heap}_{slideshow} = \min(P \times S_{photo}, C_{glide}) + O_{slideshow} + O_{music}$$

(LRU cache bounds photo memory to cache size unless eviction disabled)

**Example Calculation** (500 photos, 3MB each):
$$\text{Heap} = \min(500 \times 3, 64) + 5 + 50 = 64 + 5 + 50 = 119 MB$$

**Alert Thresholds**:
- If $P \times S_{photo}$ >> $C_{glide}$ (many large photos): ⚠️ **MUST TEST** (cache thrashing)
- If heap + music + slideshow > 200MB: ⚠️ **MUST TEST** (GC pressure, 4h fatigue)
- If all calculated < 150MB: ✅ **SAFE, SKIP 12-HOUR TEST**

**Instead Run**: 2-hour test (not 12h) to validate cache eviction under load

---

### 17.3 Document Reader Memory (Calculated)

**Given**:
- Document size: $D$ (e.g., 200MB PDF)
- Page cache size: $P_{cache} = 10$ pages (typical)
- Avg decoded page size: $S_{page}$ (e.g., 5MB rendered bitmap)
- OCR overhead: $O_{ocr} = 20 MB$ (library + thread)
- Translation cache: $C_{trans} = 50 MB$ (in-memory store)

**Formula**:

$$\text{Heap}_{document} = (P_{cache} \times S_{page}) + O_{ocr} + C_{trans}$$

Note: Temp download file NOT in heap (stored in disk cache, separate)

**Example Calculation** (200MB PDF, OCR + Translation enabled):
$$\text{Heap} = (10 \times 5) + 20 + 50 = 50 + 20 + 50 = 120 MB$$

**Alert Thresholds**:
- If heap < 200MB: ✅ **SAFE FOR 2-HOUR TEST** (calculate + validate once)
- If heap > 250MB: ⚠️ **Implement page cache cap** (reduce $P_{cache}$ to 5)
- If OCR thread pool grows: ⚠️ **MUST TEST** (OCR runaway scenario)

**Instead Run**: 2-hour validation test (reduce 5h to 2h if math safe)

---

### 17.4 Network Connection Limits (Calculated)

**Given**:
- ConnectionThrottleManager max slots: $N_{slots}$ (e.g., 8 total)
- Concurrent downloads: $D$ (user limit)
- Connection reuse rate: $R$ (e.g., 0.7 means 70% hit rate)

**Formula**:

$$\text{Slots\_Active} = D + \frac{D \times (1 - R)}{2}$$

(Average slots: active + pending new connections)

**Example Calculation** (5 concurrent downloads, 70% reuse):
$$\text{Slots\_Active} = 5 + \frac{5 \times 0.3}{2} = 5 + 0.75 = 5.75 \text{ slots}$$

If max slots = 8: **Safe** ✅

**Alert Thresholds**:
- If $D + (1-R) \times D > N_{slots} \times 0.8$: ⚠️ **MUST TEST** (slot exhaustion risk)
- If $D \leq N_{slots}$: ✅ **SAFE, validate fast (30 min test)**

---

## 17.5 Decision Tree: When to Run Marathon vs Calculate-Only

```
START: New scenario or fix to deploy

├─ Calculate theoretical memory/resource bounds
│  ├─ Heap_calculated < Alert_Threshold? → YES
│  │  └─ Cache_growth_linear? → YES
│  │     └─ No complex coroutine logic? → YES
│  │        └─ No timing-dependent race conditions?
│  │           └─ YES → ✅ SKIP MARATHON, run quick 30-min validation
│  │           └─ NO → ⚠️ Risk: proceed to empirical test
│  │     └─ NO (thrashing detected) → ⚠️ MUST TEST (4h+)
│  │  └─ NO (heap > threshold)
│  │     └─ Can mitigate (cap size, add eviction)? → YES
│  │        └─ After fix, recalculate → Loop back
│  │        └─ NO → ⚠️ MUST TEST (full Marathon)
│  │
│  └─ Heap_calculated > Critical_Threshold?
│     └─ YES → 🛑 BLOCK: Feature not viable at scale. Redesign required.
│     └─ NO → Continue to next check
│
├─ Check code for empirical risk patterns:
│  ├─ Unscoped coroutines? → ⚠️ EMPIRICAL TEST (cancellation timing unpredictable)
│  ├─ Socket/Stream cleanup on error paths? → ⚠️ EMPIRICAL TEST (error edge case)
│  ├─ Listener unregistration (onDestroy)? → ⚠️ EMPIRICAL TEST (race conditions possible)
│  └─ GC pressure callbacks? → ⚠️ EMPIRICAL TEST (Android callback timing varies)
│
├─ If any ⚠️ empirical risks detected:
│  └─ → Run Targeted Marathon Scenario (see 17.6 below)
│
└─ If all ✅ checks pass:
   └─ DEPLOY with automated monitoring (Timber logs, metrics script)
```

---

## 17.6 Targeted Marathon Scenarios (Only High-Risk Cases)

### **Run ONLY if Decision Tree flags ⚠️**

#### **17.6.1 Playlist Stress (If ExoPlayer Buffer Leak Risk)**

**Duration**: 4 hours (NOT 50 hours)  
**Scenario**: 250 tracks, 10 rapid skips (test buffer cleanup), 2 WiFi cycles  
**Measurements**: Every 30 min (Heap, GC, Connections)  
**Pass**: Heap grows < 2 MB/hour + No "OutOfMemory" + Cache recovered after restart  
**If Fail**: Investigate ExoPlayer.setMediaSource(null) cleanup in code

---

#### **17.6.2 Slideshow Stress (If Rotation + Memory Pressure Risk)**

**Duration**: 2-3 hours (NOT 12 hours)  
**Scenario**: 500 photos, rotation every 5 min (6 rotations), battery warning at 1.5h  
**Measurements**: Every 30 min (Surface count, Heap, GC pauses)  
**Pass**: No "WindowLeaked" + Surface count ≤ 2 + Heap recovers after pressure  
**If Fail**: Check SlideshowController.cleanup() / lifecycle observer removal

---

#### **17.6.3 Network Chaos Stress (If Connection Pool Leak Risk)**

**Duration**: 2 hours (NOT 6 hours)  
**Scenario**: 5 concurrent downloads, WiFi off/on 4x, 1 mid-download cancel  
**Measurements**: Every 15 min (Connections, TIME_WAIT, Download success rate)  
**Pass**: Connections < 5 at idle + TIME_WAIT < 3 + 100% success rate  
**If Fail**: Check ConnectionThrottleManager.deactivate() / SFTP.close() in finally blocks

---

#### **17.6.4 Rapid Lifecycle (If Listener Cleanup Risk)**

**Duration**: 1 hour  
**Scenario**: Play → Pause → Exit → Reopen × 20 cycles, 2 rotations  
**Measurements**: Every 10 cycles (LeakCanary report, Connections, Heap)  
**Pass**: Zero leaked instances (LeakCanary) + Heap stable + No orphaned listeners  
**If Fail**: Review PlayerLifecycleManager.onDestroy() / mediaController.release() order

---

## 17.7 Quick Validation Protocol (All Scenarios)

For ANY scenario after calculating safe bounds:

```
1. Build and install APK
2. Clear data: adb shell pm clear com.sza.fastmediasorter
3. Run scenario for 30 minutes (baseline activity)
4. Collect metrics: 
   - adb shell dumpsys meminfo > heap_0min.txt
   - adb shell netstat -an | wc -l → connections_0min.txt
5. Continue for 29 more minutes (monitoring)
6. Collect metrics: heap_30min.txt, connections_30min.txt
7. Compare:
   ✅ Heap growth < 3MB/hour? → SAFE
   ✅ Connections not leaked? → SAFE
   ✅ No errors in logcat? → SAFE
   → DEPLOY
```

---

## 17.8 Instrumentation for Continuous Monitoring (Post-Deploy)

After deploying, monitor these metrics in production via Timber logs + remote telemetry:

| Metric | Threshold | Action |
|--------|-----------|--------|
| Heap growth rate > 5 MB/hour | Alert | Investigate GC + memory leak |
| Connections > 5 at idle | Alert | Review network pool |
| GC pause > 500ms | Warn | Glide memory pressure |
| Cache growth > 1GB/week | Warn | Check temp file cleanup |
| Active jobs > 10 at idle | Warn | Coroutine leak investigation |

**Telemetry Script** (send to backend):
```kotlin
// SendMetricsUseCase.kt
fun sendMetrics(heap: Long, connections: Int, cacheSize: Long) {
    val payload = MetricsPayload(
        timestamp = System.currentTimeMillis(),
        deviceId = android.os.Build.DEVICE,
        heapMB = heap / 1024 / 1024,
        connections = connections,
        cacheMB = cacheSize / 1024 / 1024
    )
    // POST to backend analytics endpoint
    analyticsRepository.submitMetrics(payload)
}
```

---

## 18. EXTREME USAGE CASE FLOWS (Legacy - Use Only for High-Risk Scenarios)

**Philosophy**: These full-duration stress tests should **RARELY** be executed. Instead, use the **Analytical Calculation Framework (Section 17)** to predict safe bounds. Only run scenarios below if:
- 🔴 **Calculation shows high risk** (theoretical heap > alert threshold)
- 🔴 **Code review flags empirical risk patterns** (unscoped coroutines, no cleanup on error)
- 🔴 **Quick validation fails** (30-min test shows unexpected memory growth)

**Why not Marathon by default?**
- ⏱️ **Time**: 50-hour Marathon = 2+ days continuous testing (prohibitive)
- 📊 **Diminishing returns**: Most leaks manifest in first 2-4 hours; rest is validation repetition
- 🎯 **Smart approach**: Calculate theory → 30-min rapid validation → only deep test on failure

If your calculation + 30-min test passes ✅, **proceed to deploy with automated monitoring**. Use production analytics to catch edge cases at scale.

---

### 18.1 Stress Test: "Marathon Playlist" (Extreme Flow)

**Flow**: User plays 10,000 tracks across 50+ hours continuously without pause, with:
- Auto-shuffle enabled (random skip)
- Background music mode
- Cover art loading from 3 sources (SMB, Cloud, Local)
- Periodic orientation changes (every 2 hours)
- WiFi disconnects/reconnects (5 times)
- Midnight/low-battery scenarios

**Execution Plan**:

```
Hour 0-2: Baseline (steady state expected)
  - Play: 24 tracks, all with quick load (local cache)
  - Monitor: Heap 100-120 MB, cache 200 MB
  - Action: Record baseline metrics

Hour 2-8: Network Stress
  - Switch to SMB source (slow, bandwidth-limited)
  - Play: 144 tracks, skip every 2 tracks
  - Simulate WiFi off for 10s, then on (every hour)
  - Monitor: Connection slots, heap growth, GC rate
  - Alert if: Connections > 5 at any point, Heap > 180 MB

Hour 8-24: Sustained Load
  - Continue SMB playback, no interruptions
  - Add rotation every 2 hours (automatic via instrumentation)
  - Monitor: Check for memory plateauing at 200-250 MB (expected)
  - Alert if: Heap continues growing (linear instead of plateau)

Hour 24-48: Cache Saturation + Restart
  - Continue playback
  - At hour 36: Force-stop + reopen app
  - Monitor: Cache recovered vs corrupted after restart
  - Play same 100 tracks again; measure cache hit rate
  - Alert if: Cache hit rate drops (index corruption)

Hour 48+: Extreme Edge Cases
  - At hour 48: Trigger 10 rapid rotations + background service pause/resume 5x
  - At hour 50: Simulate battery warning (memory pressure callback)
  - At hour 52: Check for memory plateau recovery after pressure
  - End: Compare final heap with hour 0 (should diff < 50%)
```

**Measurement Checkpoints** (every 4 hours):
- Heap MB
- Native Memory (MB)
- GC pause count + avg duration
- Cache size (MB)
- Active connections
- Active coroutine jobs
- File handle count
- Logcat errors/warnings count

**Pass Criteria**:
- ✅ Heap growth rate < 3 MB/hour (steady)
- ✅ No "OutOfMemoryError" or "WindowLeaked"
- ✅ Cache hit ratio > 70% after restart
- ✅ No orphaned temp files (< 10MB remaining)
- ✅ GC pauses < 200ms average

---

### 17.2 Stress Test: "Photo Carousel" (Extreme Flow)

**Flow**: User browses 5,000 photos sequentially (without pausing) from cloud + local sources at max device brightness, with:
- Slideshow auto-advance every 1 second
- Background music continuously
- Rapid zoom/pan gestures every 5 seconds
- Orientation change every 3 minutes
- WiFi on/off cycle every 10 minutes
- Runs for 12+ hours

**Execution Plan**:

```
Hour 0-2: Rapid Loading (continuous auto-advance)
  - Load: 7200 photos at 1 sec/photo
  - Gesture: Pinch zoom 5x per photo
  - Monitor: Frame drops, heap, GC pauses
  - Alert if: FPS < 30 or GC pause > 500ms

Hour 2-6: Network Pressure
  - Switch to 50% cloud source (slow load)
  - Slideshow continues at 1 sec/photo
  - Gesture: 8 pan movements per photo (stress touch handling)
  - Monitor: Cache growth, thumbnail load times
  - Alert if: Avg photo load time > 500ms

Hour 6-12: Sustained + Lifecycle Turmoil
  - Reduce slideshow to 2 sec/photo (sustainable rate)
  - Rapid orientation: Every 1.5 minutes (8 rotations total)
  - WiFi cycle: On 5 min, Off 2 min, repeat
  - Background: Music continues from 500-song playlist
  - Monitor: Surface cleanup after rotation, connection recovery
  - Alert if: Surface count > 2 (leaked surface) or crash on rotation

Hour 12: Final Assessment
  - Heap should be similar to hour 2 (plateau + small variance)
  - No "WindowLeaked" exceptions
  - Cache hit ratio > 65%
```

**Measurement Checkpoints** (every 2 hours):
- Frames dropped in slideshow
- Average photo load latency (ms)
- Heap memory
- Native heap
- Surface count
- Texture cache size (if GPU rendering)

**Pass Criteria**:
- ✅ Frame drops < 5% (49/50 photos rendered)
- ✅ Photo load latency < 300ms on average (P95 < 800ms)
- ✅ No surface leaks (max 2 surfaces active)
- ✅ No "WindowLeaked" or "Activity Destroyed" crashes
- ✅ Battery drain < 15% per hour (max screen brightness)

---

### 17.3 Stress Test: "Document Marathon" (Extreme Flow)

**Flow**: User reads 5 large documents sequentially (200MB each) with:
- Continuous scrolling simulation (10 pages/second)
- OCR enabled on every page
- Translation enabled (real-time per paragraph)
- PDF + EPUB + TEXT readers (1h each)
- Rotation every 5 minutes
- Document switching every 1 hour

**Execution Plan**:

```
Hour 0-1: PDF Large File Stress
  - File: 200MB, 1000-page PDF
  - Simulate: Scroll through all pages in 1 hour (auto-scroll every 3.6 sec)
  - OCR: Enabled (cpu-intensive)
  - Translation: Disabled (focus on OCR)
  - Monitor: Memory growth, OCR thread count, page render time
  - Alert if: Heap > 300 MB or page render > 500ms

Hour 1-2: EPUB Stress + Translation
  - File: 200MB, 500-page EPUB
  - Simulate: Continuous scroll
  - OCR: Disabled
  - Translation: Enabled (real-time per paragraph)
  - Rotation: Every 5 minutes
  - Monitor: WebView memory growth, translation queue backlog
  - Alert if: WebView heap > 150 MB or translation lag > 2 sec

Hour 2-3: Text File + OCR + Translation
  - File: 200MB text file
  - Simulate: Scroll to end
  - OCR: Enabled
  - Translation: Enabled
  - Document switch: From PDF → EPUB → TEXT
  - Monitor: Context switches, cache coherence
  - Alert if: Heap spike > 50% on document switch

Hour 3-5: Sustained Multi-Document
  - Cycle through all 5 documents (repeat 2-3x)
  - Keep OCR + Translation enabled
  - Monitor: Cache coherence, temp file cleanup
  - Alert if: Disk temp files grow > 500MB
```

**Measurement Checkpoints** (every 1 hour):
- Heap memory
- OCR thread count + pending queue
- Translation queue size + latency
- Temp file disk usage
- Page render time (P50, P95)
- Orientation change recovery time

**Pass Criteria**:
- ✅ Heap stable < 350 MB across 5 hours
- ✅ No OCR thread runaway (max 2 threads)
- ✅ Translation latency < 2 sec for 100-char text
- ✅ Temp files < 300MB at end (cleaned up)
- ✅ Document switch latency < 500ms (cache hit)

---

### 17.4 Stress Test: "Network Chaos" (Extreme Flow)

**Flow**: User streams audio + video from multiple network sources with aggressive connectivity chaos:
- Connection interruptions every 30-60 seconds
- Bandwidth throttling (simulate slow network)
- Connection pool saturation (100+ concurrent sources)
- Pause/resume cycles during network chaos
- Multiple simultaneous downloads (5+ files at once)
- Runs for 6+ hours

**Execution Plan**:

```
Hour 0-1: Baseline Network Streaming
  - Audio: SMB source, 320 kbps MP3
  - Video: Cloud source (Google Drive)
  - Monitor: Connection stability, buffer underruns
  - Expected: Smooth playback, 2-3 active connections

Hour 1-3: Connection Chaos Injection
  Every 45 seconds:
  - WiFi off for 5-10 seconds (3 cycles per test period)
  - WiFi on, but throttle to 1 Mbps for 20 sec
  - Throttle reset, resume normal network
  
  Simultaneously:
  - Attempt to download 5 files concurrently from different sources
  - Track which downloads succeed, which fail, which corrupt
  
  Monitor:
  - Orphaned connections after each WiFi cycle
  - File handle leaks
  - Download completion rate
  
  Alert if:
  - Connections stuck in ESTABLISHED state > 20 seconds
  - Download success rate < 95%
  - Any file corrupted (size mismatch)

Hour 3-6: Sustained Chaos + Boundary Cases
  - Reduce WiFi down/up cycle to every 60 seconds (moderate chaos)
  - Add: Pause playback for 10 sec, resume (tests buffer recovery)
  - Add: Force-rotate device during WiFi off period
  - Add: Trigger low-memory event (system callback)
  - Monitor: Connection recovery time after rotation
  - Monitor: Resume latency after pause (< 1 sec expected)
  - Alert if: Rotation during WiFi-off causes crash
```

**Measurement Checkpoints** (every 30 minutes):
- Active connections count
- Time-wait connections (zombie connections)
- Download success rate (%)
- Orphaned file handles
- Playback glitches (audio skip, video buffer stall)
- Memory pressure GC triggers
- Recovery time after WiFi cycle (sec)

**Pass Criteria**:
- ✅ Active connections always < 10 (cleanup works)
- ✅ Time-wait connections < 5 at any point
- ✅ Download success rate > 98% (only network errors expected)
- ✅ No data corruption (file MD5 match)
- ✅ Playback glitches < 5 total over 6 hours
- ✅ WiFi recovery time < 2 seconds

---

---

## 19. INSTRUMENTATION HOOKS FOR MEASUREMENT

### Timber Logs to Add (for automatic metric collection)

```kotlin
// PlayerLifecycleManager.onDestroy()
Timber.d("METRICS: heap_mb=${Runtime.getRuntime().totalMemory() / 1024 / 1024}, " +
         "cache_mb=${unifiedCache.getCacheStats().totalSizeMB}, " +
         "connections=${activeConnections.size}")

// NowPlayingManager.startPlayback() (after every track)
Timber.d("TRACK_TRANSITION: track_num=$trackNum, uri_build_time=${buildTimeMs}ms, " +
         "heap_delta=$heapDeltaMB, glide_hits=${GlideCacheStats.diskCacheHits}")

// SlideshowController.onSlideChange()
Timber.d("SLIDESHOW_SLIDE: slide_num=$slideNum, render_time=${renderTimeMs}ms, " +
         "gc_pauses=$gcCount, heap_mb=${heapUsed}")

// BackgroundMusicManager.onError()
Timber.w("NETWORK_ERROR: error_type=$errorType, connection_count=$activeConnections, " +
         "recovery_time=${recoveryMs}ms")

// MainActivity.onMemoryTrimmed()
Timber.i("MEMORY_PRESSURE: level=$level, heap_before=$heapBefore, " +
         "heap_after=$heapAfter, cache_cleared=$cacheClearedMB")
```

### ADB Commands for Real-Time Monitoring (Script)

```powershell
# PowerShell script: memory-metrics-capture.ps1
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$pid = (adb shell pidof com.sza.fastmediasorter).trim()

$heap = adb shell dumpsys meminfo $pid | Select-String "HEAP"
$gc = adb logcat | Select-String "GC_FOR_ALLOC" | Measure-Object | Select-Object -ExpandProperty Count
$connections = adb shell netstat -an | Select-String "ESTABLISHED" | Measure-Object | Select-Object -ExpandProperty Count
$cache = adb shell du -h /data/data/com.sza.fastmediasorter/cache/ | Select-String -Pattern "\d+M"

# Log to CSV
"$timestamp,$heap,$gc,$connections,$cache" | Add-Content "metrics.csv"
```

---

## APPENDIX A: WHY ZERO MARATHON TESTS (Most of the Time)

**Your Question**: "Why Marathon if we can just calculate everything knowing media sizes and playback time?"

**Answer**: **You're absolutely right.** Most scenarios don't need Marathon tests. Here's why:

### The Problem with 50-Hour Tests

| Cost | Time | Insight | ROI |
|------|------|---------|-----|
| 2+ days continuous testing | 50 hours | 90% discovered by hour 4 | **1.8% per hour** |
| Developer attention overhead | Real opportunity cost | Most leaks show in first cycle | **Diminishing gains** |
| Device battery/hardware strain | Thermal throttling after 6h | Noise in data after fatigue | **Unreliable results** |

### The Smart Approach: Calculate → Validate → Monitor

```
Probability of Memory Leak = f(Code_Risk_Score, Calculated_Heap, Test_Result)

Code_Risk_Score = (Unscoped Coroutines) + (No Error Cleanup) + (Listeners Not Removed)
Calculated_Heap = Theoretical_Bound(media_size, item_count, cache_policy)
Test_Result = 30min_Validation(actual_heap_growth, resource_leaks)

IF Code_Risk_Score < 3 AND Calculated_Heap < Threshold AND Test_Result PASS:
  → ✅ DEPLOY with production telemetry (NOT 50-hour test)
ELSE IF Code_Risk_Score > 5 OR Calculated_Heap > Threshold:
  → ⚠️ Run Targeted 2-4h test OR code redesign
ELSE:
  → 🟡 Run Focused 2-4h test (not full Marathon)
```

### Example: Why Playlist Doesn't Need 50-Hour Marathon

**Step 1 - Theoretical Calculation** (5 min):
```
Heap = (1000 tracks) × (0.2 MB cover + 0.01 MB meta) + 2.5 MB ExoPlayer + (1000 × 0.3 MB source)
     = 210 MB + 2.5 MB + 300 MB = 512.5 MB

Baseline heap: 2GB
Usage: 512 / 2048 = 25.6% 
Alert: 50% threshold
Margin: 25% buffer SAFE ✅
```

**Step 2 - Code Review** (30 min):
```
☑️ mediaController?.release() in PlayerLifecycleManager.onDestroy()
☑️ metadataScope?.cancel() in BackgroundMusicManager.release()
☑️ Glide LRU auto-eviction enabled
Code Risk Score: 1 (low) ✅
```

**Step 3 - 30-Min Quick Validation** (30 min):
```
Heap growth: 1.5 MB/hour (< 3 MB/hour alert) ✅
Cache hit: 65% > 60% expected ✅  
Connections: 1 at idle < 5 alert ✅
Result: PASS ✅
```

**Decision**: ✅ **DEPLOY** (no 50-hour Marathon needed)
- Time saved: 48+ hours
- Confidence: High (math + code + validation)
- Risk: Monitored via production metrics

---

## APPENDIX B: THREE-GATE RESEARCH FRAMEWORK

```
┌─ GATE 1: THEORETICAL ANALYSIS (1 day/scenario)
│  ├─ Input: Media sizes + playback flow + device memory
│  ├─ Calculate: Heap formula, cache growth, resource limits
│  └─ Output: SAFE / RISKY / CRITICAL classification
│
├─ GATE 2: CODE AUDIT (2-3 days for all Phase 1)
│  ├─ Input: Code paths for Gate 1 classificationss
│  ├─ Review: Cleanup patterns, listener unregistration, error paths
│  ├─ Decision Tree (17.5): Which need empirical test?
│  └─ Output: High-risk scenarios flagged
│
└─ GATE 3: TARGETED VALIDATION (2-4h per high-risk scenario)
   ├─ All scenarios: 30-min quick test
   ├─ Only high-risk: 2-4h focused test
   ├─ Measure: Heap, GC, connections, cache hit ratio
   └─ Output: SAFE / FAIL decision

RESULT:
├─ All gates PASS → ✅ Deploy with telemetry
├─ Gate 3 FAIL → Fix code, return to Gate 2
├─ Gate 1 CRITICAL → Redesign feature
└─ Gate 2 HIGH-RISK → Mandatory 4h test
```

**Time Estimate (Phase 1 — 5 critical scenarios)**:
- Gate 1: 5 days
- Gate 2: 3 days
- Gate 3: 3 days (5 × 30min + 2 × 2h)
- **Total: 11 days** (vs. 50h Marathon = 2+ days of continuous testing *each scenario*)

---

## APPENDIX C: WHEN TO RUN FULL MARATHON TEST

**Run 12-50 Hour Marathon ONLY if**:

| Condition | Why | Example Scenario |
|-----------|-----|----------|
| Gate 1 heap > critical threshold | Theory predicts unsafe | Playlist with 10K+ tracks |
| Gate 2 reveals 5+ code flags | High confidence in risk | Multiple listener leaks + unscoped async |
| Gate 3 shows unexpected leak | Math didn't predict it | Coroutines accumulating despite .cancel() |
| Production telemetry on similar feature | Real users hitting issue | v1.0 had crash reports this area |
| Enterprise customer mandate | Contractual requirement | "Certified for 24h continuous use" |

**Default**: Use analytical framework + 30-min validation. **Don't run Marathon by default.**

---

**Owner**: FastMediaSorter v2 Engineering Team  
**Status**: READY FOR RESEARCH PHASE  
**Next Review**: After Gate 1 (Theoretical Analysis) completion

---

## 19. CODE AUDIT RESULTS (Phase 1 — Static Code Review)

**Audit Date**: 2026-04-13  
**Scope**: 15 components identified in Sections 1–10 as potential leak points  
**Method**: Static code review via file search + line-by-line analysis  
**Overall Result**: 14/15 SAFE — no critical code-level leaks found; 1 WARNING; 1 NOT FOUND

---

### 19.1 Group A — Critical Components

| # | Component | File | Key Lines | Status | Notes |
|---|-----------|------|-----------|--------|-------|
| A1 | NowPlayingManager | `ui/player/helpers/NowPlayingManager.kt` | — | ✅ SAFE | No external listeners registered by this class; all playback commands delegated to AudioServiceController |
| A2 | PlayerLifecycleManager | `ui/player/helpers/PlayerLifecycleManager.kt` | 141–226 | ✅ SAFE | `onDestroy()` calls `mediaController?.release()`; releases all sub-managers; `hideControlsHandler.removeCallbacks()` at L182; `preloadJobs.forEach { it.cancel() }` at L190; ConnectionThrottleManager deactivated at L171–172 |
| A3 | AudioServiceController | `ui/player/helpers/AudioServiceController.kt` | 165–171 | ✅ SAFE | `MediaController.releaseFuture(it)` at L167; refs nulled at L168–169 |
| A4 | BackgroundMusicManager | `ui/player/helpers/BackgroundMusicManager.kt` | 526–547 | ✅ SAFE | `healthCheckJob?.cancel()` + `loadPlaylistJob?.cancel()` at L527–528; `musicPlayer?.release()` dispatched to Main at L532; state fully reset |
| A5 | GlideAppModule | `di/GlideAppModule.kt` | 41–82 | ✅ SAFE | Uses `Runtime.getRuntime().maxMemory()` at L48 (NOT `availMem()`); capped at 64 MB at L49; `PREFER_RGB_565` applied for LOW tier at L74–75 via `MemoryTier.detect()` at L67 |

---

### 19.2 Group B — High-Risk Components

| # | Component | File | Key Lines | Status | Notes |
|---|-----------|------|-----------|--------|-------|
| B1 | SlideshowController | `ui/player/SlideshowController.kt` | 275–288 | ✅ SAFE | `handler.removeCallbacksAndMessages(null)` at L279; countdown handler cleaned at L280; `lifecycle.removeObserver(this)` at L281; `cleanup()` called from `onDestroy()` at L275 |
| B2 | DualSurfaceStaticImageRenderer | `ui/player/render/DualSurfaceStaticImageRenderer.kt` | 133–174 | ✅ SAFE | `onPause()` calls `Glide.with(appContext).clear(surfaceA/B)` at L133–142; `release()` nulls images at L158–159, hides surfaces at L162–163 |
| B3 | AnimatedImageController | `ui/player/helpers/AnimatedImageController.kt` | 92–103 | ✅ SAFE | `release()` → `clearCurrentAnimation()` → `currentAnimatedDrawable?.stop()` at L99; refs nulled at L100–101 |
| B4 | ConnectionThrottleManager | `data/network/ConnectionThrottleManager.kt` | 135–167, 421–436 | ✅ SAFE | `deactivateVideoPlayerMode()` includes 300ms delay for safe cleanup at L148–167; `activeTasks` AtomicInteger tracking at L46; `forceResetConnections()` and `cancelAllForResource()` properly manage state |
| B5 | SmbConnectionManager | `data/network/SmbConnectionManager.kt` | 615–666, 912–915 | ✅ SAFE | `removeConnection()` uses independent try/catch blocks for share/session/connection at L617, L620, L623 preventing cascading failures; async cleanup via `closeConnectionAsync()` at L632–651 |

---

### 19.3 Group C — Medium/High-Risk Components

| # | Component | File | Key Lines | Status | Notes |
|---|-----------|------|-----------|--------|-------|
| C1 | SSHJClientPool | — | — | ❌ NOT FOUND | No `SSHJClientPool` class in codebase. SFTP uses `SftpClient` + `SftpDataSource`. Spec reference may be aspirational or stale. Requires separate audit of actual SFTP classes. |
| C2 | UnifiedFileCache | `core/cache/UnifiedFileCache.kt` | 59–64, 82–104, 134–141 | ⚠️ WARNING | `MAX_CACHE_AGE_MS = 24h` at L59–64 expires stale cache on reads; `clearAll()` at L134–141 handles explicit clear. However: **no startup sweep** to delete orphaned files from crashed writes — risk of gradual disk accumulation over weeks. |
| C3 | TranslationCacheManager | `core/cache/TranslationCacheManager.kt` | 14–73 | ✅ SAFE | Singleton; `clearAll()` at L55–61 clears both cache maps; per-file + per-page structure is bounded; no unbounded growth path identified |
| C4 | NetworkFileModelLoader | `data/network/glide/NetworkFileModelLoader.kt` | 179, 183–281 | ✅ SAFE | `loadJob` cancellation at L194; `isCancelled` flag checked at L188, L208; `CancellationException` handled at L265–278; `invokeOnCancellation` at L281 clears Glide request |
| C5 | AudioBackgroundPhotosManager | `ui/player/helpers/AudioBackgroundPhotosManager.kt` | 62, 130, 313–322 | ✅ SAFE | `CoroutineScope(SupervisorJob() + Dispatchers.IO)` at L62; `loadJob?.cancel()` in `release()` at L314; early-return on cancellation at L130; callbacks nulled at L316–317 |

---

### 19.4 Summary & Decision for Testing

Using the **Decision Tree (Section 17.5)**:

| Phase 1 Scenario | Theory Heap | Code Flags | Decision |
|-----------------|-------------|------------|---------|
| Playlist 1000 tracks (1.1) | ~120–150 MB (within safe range) | No code flags found | ✅ 30-min quick validation only |
| Slideshow 500 photos (2.1) | ~119 MB calculated (Section 17.2) | No code flags found | ✅ 30-min + optional 2h if GC pressure |
| PDF 100MB (3.1) | ~120 MB (Section 17.3) | No code flags found | ✅ 30-min validation |
| Rapid skips (1.2) | Low memory impact | ConnectionThrottleManager SAFE | ✅ 30-min quick smoke test |
| Network reconnects (4.1) | Low memory impact | SmbConnectionManager SAFE | ✅ 30-min + WiFi toggle x5 |
| UnifiedFileCache disk (5.2) | Disk accumulation over weeks | **Startup sweep MISSING** | ⚠️ Implement startup cleanup; then validate |
| SSHJClientPool / SFTP (4.2) | Unknown | Class NOT FOUND — audit SftpDataSource | ⚠️ Needs targeted SftpClient/SftpDataSource review |

**Recommended Actions Before Testing**:
1. **UnifiedFileCache**: Add startup sweep — delete files older than `MAX_CACHE_AGE_MS` or files marked "partial/temp" on app init (low effort, high impact).
2. **SFTP audit**: Audit `SftpDataSource.kt` and `SftpClient.kt` for session close patterns (spec references `SSHJClientPool` which doesn't exist — actual class must be reviewed separately).
3. **All others**: Proceed to 30-min quick validation per Section 17.7 — no code changes required.

**Gate 1 (Theoretical Analysis)**: ✅ PASSED for 13/15 scenarios  
**Gate 2 (Code Review)**: ✅ PASSED for 13/15 scenarios; ⚠️ 2 items need follow-up  
**Gate 3 (Quick Validation)**: Pending — schedule 30-min sessions per Section 17.7

---

**Audit by**: Claude Code  
**Next Action**: Fix `UnifiedFileCache` startup sweep + audit `SftpDataSource` → then run Gate 3 validations

---

## 19.5 Deep Research Findings — Additional Issues Discovered

Second-pass audit (2026-04-13) covering SFTP internals, document readers, temp file management, and coroutine scope patterns. Full technical tasks created in `PLAN/tasks/`.

### Confirmed Bugs (with tasks)

| Task | Priority | Component | Issue |
|------|----------|-----------|-------|
| [ML-001](tasks/task-ML-001-pdf-viewer-manager-not-released.md) | **CRITICAL** | `PlayerLifecycleManager` | `PdfViewerManager.close()` never called → `PdfRenderer` + `ParcelFileDescriptor` leaked every PDF session |
| [ML-002](tasks/task-ML-002-global-scope-onedrive.md) | **CRITICAL** | `OneDriveRestClient` | `GlobalScope.launch()` at lines 228, 245 → auth callbacks hold Activity reference indefinitely |
| [ML-003](tasks/task-ML-003-ftp-temp-file-wrong-dir.md) | **HIGH** | `FtpFileOperationHandler` | `createTempFile()` at L603 missing `context.cacheDir` → orphaned files in system `/tmp` |
| [ML-004](tasks/task-ML-004-sftp-datasource-inputstream-leak.md) | **HIGH** | `SftpDataSource` | `rawStream` leaked if exception occurs between `channel.get()` (L81) and `inputStream =` assignment (L86) |
| [ML-005](tasks/task-ML-005-dialog-scope-no-supervisor-no-cancel.md) | **HIGH** | `FileOperationDestinationDialog`, `PlayerSettingsManager`, `ResourcePickerDialog` | Scopes without `SupervisorJob`, never cancelled; dead scope field in `ResourcePickerDialog` |
| [ML-006](tasks/task-ML-006-unified-file-cache-no-size-limit.md) | **HIGH** | `UnifiedFileCache` | No max total size limit → cache grows unbounded until device storage full |
| [ML-007](tasks/task-ML-007-temp-file-manager-no-startup-sweep.md) | **HIGH** | `TempFileManager`, `FastMediaSorterApp` | `cleanupOldTempFiles()` exists but never called → orphaned `.tmp` files accumulate after force-kills |
| [ML-008](tasks/task-ML-008-sftp-connection-pool-race-condition.md) | **MEDIUM** | `SftpClient` | TOCTOU race in `getConnectionForExoPlayer()`: pool entry read without lock, then operated on under stale reference |
| [ML-009](tasks/task-ML-009-unscoped-coroutines-singletons.md) | **MEDIUM** | `SmbConnectionManager`, `SftpClient`, `NetworkCredentialsRepositoryImpl`, `ScheduledOperationsBootReceiver` | Ad-hoc `CoroutineScope(IO).launch` without retaining scope; `BootReceiver` needs `goAsync()` |
| [ML-010](tasks/task-ML-010-epub-webview-no-database-cleanup.md) | **MEDIUM** | `EpubViewerManager` | `release()` calls `webView.destroy()` but not `WebViewDatabase.clearHttpAuthUsernamePassword()` |
| [ML-011](tasks/task-ML-011-connection-throttle-manager-bare-job.md) | **MEDIUM** | `ConnectionThrottleManager` | `managerScope` uses bare `Job()` instead of `SupervisorJob()` → single exception kills entire scope permanently |

### False Positives from Initial Spec (Resolved)

| Spec Issue | Verdict | Reason |
|------------|---------|--------|
| 1.1.1 NowPlayingManager listener leak | ✅ NO ISSUE | No external listeners registered; delegates to AudioServiceController |
| 1.1.2 AudioServiceController mediaController | ✅ NO ISSUE | `MediaController.releaseFuture()` called in `release()` |
| 2.1.2 SlideshowController handler cleanup | ✅ NO ISSUE | `removeCallbacksAndMessages(null)` + `removeObserver()` in `cleanup()` |
| 5.1.1 GlideAppModule memory formula | ✅ NO ISSUE | Uses `maxMemory()` (not `availMem()`); LOW tier → RGB_565 |
| 6.1 ExoPlayer listener cleanup | ✅ NO ISSUE | `PlayerLifecycleManager` releases all managers in `onDestroy()` |
| SFTP semaphore on retry path | ✅ NO ISSUE | `finally` block at L149 executes on `return@withContext`, semaphore is released |
| SFTP InterruptedException not caught | ✅ NO ISSUE | Handled at L155–159 with thread interrupt restore |
| EpubViewerManager untracked navigation jobs | ✅ LOW RISK | Scope = `activity.lifecycleScope`, cancelled on Activity destroy |
