# Diagnostic Specification: Log Analysis & Investigation Roadmap

**Date:** 2026-04-13  
**Status:** Investigation Phase  
**Severity:** ⚠️ Medium (affects startup performance & caching)

---

## Executive Summary

Analysis of `logs/current.log` (462 lines, 12-second session) reveals **TWO SUSPICIOUS PATTERNS**:

1. **App killed at 21:19:09.446** (`SIGKILL, signal 9`) after 9 seconds of normal operation
2. **Immediate restart at 21:19:10** (PID 18509 restarted), exhibiting **identical startup behavior**
3. **Persistent Glide cache initialization failure** in BOTH sessions (cache directory doesn't exist)
4. **Cache path mismatch warning** during file open (subfolder mismatch detected)

This is either:
- A **deliberate kill-and-restart test** (user expectation: normal)
- A **system-triggered crash** (user expectation: investigate)
- A **memory pressure scenario** (user expectation: optimize)

---

## Section 1: Critical Findings

### 1.1 Pattern: Double Startup

| Metric | Value |
|--------|-------|
| **First session duration** | 21:19:00 → 21:19:09 (9 seconds) |
| **Kill signal** | Line 503: `Process Sending signal. PID: 18509 SIG: 9` |
| **Second session start** | 21:19:10 (+1 second after kill) |
| **Second session visible until** | 21:19:12 (end of log) |
| **Session count** | 2 (same PID 18509, process recycled) |

**Q1: Is this expected?** → Needs clarification from user.

**Q2: What triggered the kill?** → Log doesn't show Android lifecycle events (no `onDestroy`, no `onPause` completion). This is a **hard kill**, not an app-initiated exit.

---

### 1.2 **CRITICAL: Glide Disk Cache Missing on Every Startup**

**Log evidence (both sessions):**
```
FastMediaSorter  W  === GLIDE DISK CACHE STATUS AT STARTUP ===
FastMediaSorter  W  Cache directory does NOT exist: /data/user/0/com.sza.fastmediasorter/cache/image_cache
FastMediaSorter  W  This means no thumbnails were cached from previous sessions!
```

**Impact:**
- ❌ No cached thumbnails from previous app sessions
- ❌ Every app restart forces full Glide image pipeline re-initialization
- ⚠️ Performance: thumbnail loading on Media Browse screen will be **slow on first load**
- ⚠️ UX: Users see blank placeholders for 1–3 seconds per image while loading from disk

**Root cause candidates:**
- Glide cache directory not created in `FastMediaSorterApp.kt` or `GlideAppModule.kt`
- Cache path is hardcoded; device may be using different path on subsequent boots
- Cache directory deleted by system (cleanup task, memory pressure, package update)
- `SharedPreferences` or application data reset between sessions

**Code locations to check:**
1. `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` — app initialization
2. `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GlideAppModule.kt` — Glide config
3. Search for: "cache_image" or "cache_dir" in codebase
4. Room DB initialization (if cache metadata is stored in DB)

---

### 1.3 **WARNING: Cache Path Mismatch on File Open**

**Log evidence (line 423, session 1):**
```
FastMediaSorter  W  Cache does not contain initialFilePath=
  /storage/emulated/0/Download/FastMediaSorter_Test/Audio/test_audio_flac.flac
  (subfolder mismatch), reloading from /storage/emulated/0/Download/FastMediaSorter_Test/Audio
```

**Interpretation:**
- When opening a file, the app checks if it's in the cache (likely `BrowseViewModel` state)
- Cache lookup key includes the full file path
- **Subfolder mismatch**: The cache was populated with path `/Audio/...` but lookup is for `/Audio/...` → paths don't align
- App **recovers** by reloading from the parent directory

**Impact:**
- ⚠️ Logic error in cache key generation (likely comparing absolute vs. relative paths, or normalizing slashes inconsistently)
- ⚠️ Doesn't crash, but indicates **inefficient cache strategy**
- ⚠️ User taps file → app wastes time reloading directory instead of using cache

**Code locations to check:**
1. `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` — cache logic
2. `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/` — any file cache manager
3. Search for: "initialFilePath", "subfolder mismatch", cache key generation
4. Check: How are file paths normalized? (Windows slashes vs. Unix?)

---

### 1.4 **System-Level Issues (Not Our Code)**

#### Chrome / WebView ClassNotFoundException
```
ClassNotFoundException: Didn't find class "android.webkit.TracingController"
(3 times, lines 157, 257, 357)
```
- **Cause**: Chrome APK on Android 8.1 emulator is old; missing WebView APIs
- **Impact**: None (Chrome loads anyway, ignores tracing)
- **Action**: Ignore or update emulator GMS

#### Google Play Services Out of Date
```
Google Play services out of date for com.sza.fastmediasorter.
Requires 203400000 but found 202414022
```
- **Cause**: Emulator's GMS is old
- **Impact**: ⚠️ May affect Cast feature, Drive API, Location APIs
- **Action**: Update emulator GMS or mock/test offline

#### MediaMetadata.getEmbeddedPicture Failed
```
MediaMetad...trieverJNI  E  getEmbeddedPicture: Call to getEmbeddedPicture failed.
```
- **Cause**: Media file (`test_audio_flac.flac`) has no embedded artwork, or framework can't decode it
- **Impact**: Album art won't show for this file
- **Action**: Normal; library gracefully handles missing metadata

#### Input Dispatcher Channel Broken
```
InputDispatcher  E  channel '6523ae0 com.sza.fastmediasorter/com.sza.fastmediasorter.ui.main.MainActivity' 
  ~ Channel is unrecoverably broken and will be disposed!
```
- **Cause**: Input event delivery channel to Activity was severed (happens on app kill/close)
- **Impact**: None (Android cleanup)
- **Action**: Normal lifecycle event

---

## Section 2: Questions for User / Test Plan

### 2.1 What is the test scenario?

- [ ] **Test A: Normal user flow** — open app → browse → close app
  - **Expectation**: One startup, one clean exit
  - **Reality**: Two startups, hard kill between them
  - **Action**: Why does second startup occur in the log?

- [ ] **Test B: Quick kill test** — open app → immediately kill from shell/adb
  - **Expectation**: One startup, hard kill
  - **Reality**: Matches log (second startup might be from re-opening)
  - **Action**: Document as test artifact; ignore second session

- [ ] **Test C: Memory pressure / crash recovery** — open app → system kills due to memory → auto-restart
  - **Expectation**: System kills app, framework may restart it
  - **Reality**: Matches log
  - **Action**: Profile memory, check if app is OOM-prone

---

### 2.2 Reproducibility checklist

Run this to gather consistent logs:

```bash
# Pull fresh logs from device
.\scripts\utils\extract-device-logs.ps1

# Clear app data to force full restart
adb shell pm clear com.sza.fastmediasorter

# Open app, wait 30 seconds, close app cleanly
# (Don't use adb kill, just close the app via back/system UI)

# Extract logs again
.\scripts\utils\extract-device-logs.ps1

# Compare logs/current.log with previous version
```

**Expected outcome of clean run:**
- Single startup → user interactions → single clean exit
- No Glide cache warning (cache created on first run, exists on second run)
- No cache path mismatches

---

## Section 3: Detailed Investigation Checklist

### 3.1 Glide Cache Initialization (PRIORITY: HIGH)

**Files to inspect:**

1. **`app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`**
   - Look for: cache directory setup in `onCreate()`
   - Check: Are permissions requested before cache init?
   - Check: Is cache init guarded by API level (scoped storage)?

2. **`app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GlideAppModule.kt`**
   - Look for: `@Provides fun provideGlideInstance()` method
   - Check: Cache size formula (should be ~10% of heap)
   - Check: Cache directory path — is it hardcoded or dynamic?
   - Check: Is cache directory created before Glide init?

3. **`gradle/libs.versions.toml` or `app_v2/build.gradle.kts`**
   - Look for: Glide version declaration
   - Note: Current version should be 4.15.1 per CLAUDE.md

4. **Search all files:**
   ```bash
   grep -r "cache/image_cache" .
   grep -r "image_cache" app_v2/src/main
   grep -r "cacheDir\|getCacheDir" app_v2/src/main
   ```

**Testing steps:**
1. Add debug log in `FastMediaSorterApp.onCreate()`: print cache directory path
2. Check: Does directory exist immediately after init?
3. Verify: Cache persists across app restart (clear app data, restart, check)

**Root cause hypothesis:**
- Cache initialization happens on **background thread**, but Glide tries to use it on **main thread** before creation completes
- OR cache directory path is context-dependent and becomes invalid between sessions

---

### 3.2 Cache Path Mismatch Warning (PRIORITY: MEDIUM)

**Files to inspect:**

1. **`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`**
   - Look for: cache initialization, file path handling
   - Check: How is cache key generated from file path?
   - Check: Are paths normalized before cache lookup? (e.g., `File.absolutePath` vs. manual path building)

2. **`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/`** (all files)
   - Look for: "initialFilePath", "cache", "subfolder"
   - Check: File path comparison logic

3. **Search:**
   ```bash
   grep -r "subfolder mismatch\|initialFilePath" app_v2/src/main
   grep -r "cache.*contains\|cache.*lookup" app_v2/src/main
   ```

**Root cause hypothesis:**
- Cache lookup uses `"/storage/emulated/0/Download/FastMediaSorter_Test/Audio"` as key
- But cache was stored with key `"/storage/emulated/0/Download/FastMediaSorter_Test"` (parent)
- Path normalization issue: maybe trailing slashes, or relative paths, or URI encoding

**Testing:**
1. Add debug log: print exact cache key being used
2. Compare keys from two different file opens in same directory
3. Verify: Does cache work if file is in root directory (no subfolders)?

---

### 3.3 Process Kill Investigation (PRIORITY: MEDIUM)

**Questions:**
- Was the kill intentional (user test)?
- Or did system kill the app?

**Diagnosis:**
1. Check if there are any `ActivityManager` kills in the full log:
   ```bash
   .\scripts\utils\search-log.ps1 -LogFile "logs/current.log" -Pattern "ActivityManager.*killed" -AppOnly
   ```

2. Check for low-memory kills:
   ```bash
   .\scripts\utils\search-log.ps1 -LogFile "logs/current.log" -Pattern "lowmem|OutOfMemory|killed due to low memory" -AppOnly
   ```

3. Check startup banner memory stats from first session:
   - Heap Total: 2 MB (very low)
   - Heap Max: 512 MB (normal for this device)
   - Available RAM at startup: 1356 MB (healthy)
   - **Verdict**: Not OOM-related

**Action:**
- If kill is intentional (user test): document in test plan and ignore
- If system kill: profile memory usage and identify leak

---

## Section 4: Architecture Compliance Checks

### 4.1 Glide Module Should Follow Hilt Pattern

**Requirement:** `GlideAppModule.kt` must be:
- Declared as `@GlideModule` (not `@Module`)
- Provide cache config in `applyOptions(Context, GlideBuilder)`
- NOT provide cache directory during app init (Glide handles it)

**Verification:**
```kotlin
// GOOD pattern:
@GlideModule
class GlideAppModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setMemoryCache(MemorySizeCalculator.Builder(context).build().getMemoryCacheSize())
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE))
        // Cache dir is managed by Glide, NOT app
    }
}

// BAD pattern (what might be causing the issue):
@Module
@InstallIn(SingletonComponent::class)
object GlideAppModule {
    @Provides
    fun provideGlide(context: Context): RequestManager {
        // Trying to create cache dir manually → may not exist yet
        val cacheDir = File(context.cacheDir, "image_cache")
        cacheDir.mkdirs()  // ← Might fail if called off main thread
        return Glide.with(context)
    }
}
```

---

### 4.2 ViewModel Cache Management Should Respect Data Flow

**Requirement:** Cache logic must follow `UI → ViewModel → UseCase → Repository → DataSource`

**If cache is in BrowseViewModel:**
- ✅ OK: Cache stores UI state (what user last viewed)
- ❌ Wrong: Cache stores file metadata (belongs in Repository)
- ❌ Wrong: Cache stores Bitmap/drawable data (belongs in Glide, not ViewModel)

**Verification:**
```kotlin
// GOOD: ViewModel caches navigation state
class BrowseViewModel : ViewModel() {
    private val _selectedDirectory = MutableStateFlow<String?>(null)
    val selectedDirectory: StateFlow<String?> = _selectedDirectory
}

// BAD: ViewModel caches actual file data
class BrowseViewModel : ViewModel() {
    private val fileCache = mutableMapOf<String, List<MediaFile>>()  // ← Belongs in Repository
}
```

---

## Section 5: Testing & Validation Plan

### 5.1 Manual Test Cases

**TC-1: Clean cold start (cache miss scenario)**
```
Steps:
1. adb shell pm clear com.sza.fastmediasorter
2. Open FastMediaSorter app
3. Wait 5 seconds for UI to settle
4. Check logcat:
   - Should see "Cache directory does NOT exist" once (first app run)
   - Should NOT see it again if app is restarted without clear
5. Close app cleanly (back button → system exit)
6. Reopen app
7. Check logcat:
   - Should NOT see "Cache directory does NOT exist" (cache persists)

Expected: Glide cache is created on first run, reused on second run
```

**TC-2: Browse file, check cache consistency**
```
Steps:
1. Open app, let it initialize
2. Navigate to /storage/emulated/0/Download/FastMediaSorter_Test/Audio
3. Check logcat for "Cache does not contain initialFilePath"
4. Navigate to parent directory, then back to Audio
5. Check logcat: should NOT see mismatch warning again

Expected: No subfolder mismatch on second visit to same directory
```

**TC-3: Process kill recovery**
```
Steps:
1. Open app normally
2. adb shell kill -9 $(adb shell pidof com.sza.fastmediasorter)
3. Check logcat: should see "Sending signal. PID: ... SIG: 9"
4. Reopen app
5. Verify UI recovers without data loss (check ViewModel state restoration)

Expected: App restarts cleanly; cache is valid; ViewModel state is restored
```

**TC-4: Memory profile**
```
Steps:
1. Open app
2. adb shell dumpsys meminfo com.sza.fastmediasorter > temp/mem_startup.txt
3. Browse 50+ images in high-resolution folder
4. Wait 30 seconds
5. adb shell dumpsys meminfo com.sza.fastmediasorter > temp/mem_browsing.txt
6. Diff: Check if memory grows unbounded or stabilizes

Expected: Heap usage grows initially, then stabilizes at ~150–200 MB
```

### 5.2 Automated Checks (Maestro E2E)

Add to `maestro/critical/cache_persistence.yaml`:
```yaml
appId: com.sza.fastmediasorter
---
- launchApp
- assertVisible:
    text: Browse
- clearAppData: com.sza.fastmediasorter
- assertVisible:
    text: "Glide Disk Cache"  # Warning should appear
- closeApp
- launchApp
- assertNotVisible:
    text: "Glide Disk Cache"  # Warning should NOT appear (cache exists)
- assertVisible:
    text: Browse
```

---

## Section 6: Files to Review (Sorted by Priority)

| Priority | File | Why |
|----------|------|-----|
| 🔴 HIGH | `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | App init, logging startup info |
| 🔴 HIGH | `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/GlideAppModule.kt` | Glide config, cache setup |
| 🟠 MED | `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | File cache, path logic |
| 🟠 MED | `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/` | All files in this dir |
| 🟡 LOW | `app_v2/build.gradle.kts` | Glide version, cache-related configs |
| 🟡 LOW | `gradle/libs.versions.toml` | Dependency versions |

---

## Section 7: Remediation Checklist

**After investigation is complete, implement fixes in this order:**

- [ ] **Step 1**: Verify Glide cache initialization in `FastMediaSorterApp.kt`
  - [ ] Cache directory is created on main thread before first Glide call
  - [ ] Cache directory path is stable across app restarts
  - [ ] Add guard: `if (!cacheDir.exists()) cacheDir.mkdirs()`
  - [ ] Add debug log: print cache directory and existence check

- [ ] **Step 2**: Fix cache path mismatch in `BrowseViewModel` or managers
  - [ ] Normalize all file paths using `File(path).absolutePath`
  - [ ] Use consistent key generation for cache lookup
  - [ ] Add unit test: verify cache key is stable for same directory

- [ ] **Step 3**: Add Glide cache metrics to startup banner
  - [ ] Print: cache directory exists? (T/F)
  - [ ] Print: cache size on disk
  - [ ] Print: cache hit rate (if tracked)

- [ ] **Step 4**: Document process kill scenario in test plan
  - [ ] Clarify: is two-startup pattern expected or a bug?
  - [ ] If bug: investigate why second startup happens immediately after kill

- [ ] **Step 5**: Run dev log for each modified file:
  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/.../GlideAppModule.kt" "GlideAppModule" "Fixed cache initialization"
  .\scripts\add_to_dev_log.ps1 "app_v2/.../BrowseViewModel.kt" "BrowseViewModel" "Fixed cache path mismatch"
  ```

---

## Section 8: Acceptance Criteria

✅ **Investigation complete when:**
1. Glide cache directory successfully persists across app restart (TC-1 passes)
2. No "subfolder mismatch" warnings on repeated directory navigation (TC-2 passes)
3. App recovers cleanly from hard kill (TC-3 passes)
4. Memory usage stabilizes below 250 MB (TC-4 passes)
5. Maestro cache persistence test passes
6. All files checked, root causes documented
7. Fixes implemented and tested

✅ **Release complete when:**
- [ ] All fixes merged to `main`
- [ ] Dev log updated for all modified files
- [ ] Test results documented
- [ ] User confirms expected behavior in next session

---

## Section 9: Next Steps

1. **User confirmation**: Is the two-startup pattern expected (test artifact) or a bug?
2. **Code review**: Inspect files listed in Section 6
3. **Root cause analysis**: Document findings in `temp/investigation_results.md`
4. **Implement fixes** using this spec as checklist
5. **Retest** using manual and automated test cases
6. **Document** remediation in next dev log entry

