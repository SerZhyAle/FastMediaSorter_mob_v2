# Phase 02 - Wave 01: VideoPlayerManager Decomposition

**Strategic spec:** [`../S0274_kotlin_hotspots_decomposition.md`](../S0274_kotlin_hotspots_decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03; all future-wave phase files added by later `/spec-tech S0274` runs
**Steps done:** 6 / 6
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Reduce `VideoPlayerManager.kt` from 939 LOC to ≤ 700 LOC by extracting three cognitively independent responsibility clusters into new helpers under `ui/player/helpers/`. Preserve behavioural equivalence: public API surface unchanged, `Player.Listener` callback semantics unchanged, lifecycle and state lifetimes unchanged.

> **Strategic target gap.** Strategic §11 #1 names "≤ ~600 LOC" as the per-file ceiling, but `VideoPlayerManager.playerListener` (≈290 LOC) is explicitly documented in the file's class KDoc as "tightly coupled to all state, cannot be split cleanly". Wave 01's achievable minimum is therefore ≤700 LOC, keeping the listener intact. If a future tactical iteration finds a clean way to split the listener (per §6 #4 grain rule), a Wave 01b can close the residual 100 LOC gap.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S0274_baseline_2026-05-20.md` exists and lists Wave 01 as `ui/player/VideoPlayerManager.kt` with the current LOC.
- [ ] Working tree is clean. This wave produces one commit per extraction step (ADR-2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 700 (target after this phase) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlaybackPreflightHelper.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerTracksObserver.kt` | New | ≤ 150 |
| `gradle.properties` | Modified (conditional - only if step 02.6 measurement allows) | n/a |
| `temp/VideoPlayerManager.kt.<timestamp>.backup` | New (backup before edits) | n/a |

> `VideoPlayerManager.kt` is currently 939 LOC > 500, so CLAUDE.md Rule 5 backup-before-edit applies (step 02.1).

---

## Steps

### Step 02.1 - Timestamped backup before edits

**Files:** `temp/VideoPlayerManager.kt.<YYYYMMDD-HHmm>.backup`
**Depends on:** - start of phase

**Prompt for developer:**

> CLAUDE.md Rule 5: any file >500 LOC needs a timestamped backup in `temp/` before edit. Copy the current `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` to `temp/VideoPlayerManager.kt.<yyyyMMdd-HHmm>.backup` using PowerShell `Copy-Item`. Use a fresh timestamp at execution time; the placeholder above is a format hint, not a fixed value.

**Verification:**

- `Glob` - `temp/VideoPlayerManager.kt.*.backup` matches exactly one file with today's date in its name.
- `Bash` - the backup's byte length is non-zero and within ±1 byte of the source file's current byte length (line-ending sensitivity is acceptable).
- expected: backup exists, same content | actual: PASS if both checks hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:41 - Verification 2/2 PASS. temp/VideoPlayerManager.kt.20260520-1741.backup created, byte-exact 52233/52233.

---

### Step 02.2 - Extract `VideoPlayerErrorHandler`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt` (New)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (Modified)

**Depends on:** Step 02.1

**Prompt for developer:**

> Extract the body of `VideoPlayerManager.playerListener.onPlayerError` (currently lines ≈436-574) into a new helper class `VideoPlayerErrorHandler` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/`. Mirror the existing pattern used by `VideoPlaybackControlsHelper` and `VideoPlayerLifecycleHelper`: constructor takes `private val manager: VideoPlayerManager`, accesses manager state through `internal` properties already in place (`exoPlayer`, `currentFilePath`, `playbackRetryCount`, `lastPlaybackPosition`, `isUsingMediaPlayer`, `retryHandler`, `retryRunnable`, `playerCallback`, `context`, `decoderFailureTracker`). Expose a single public function:
>
> ```kotlin
> fun handlePlayerError(error: PlaybackException): Boolean
> ```
>
> Return `true` when the error is fully handled (early return inside the helper - thread-interrupt suppression, SFTP IO suppression, EOF retry scheduled, MediaCodec cooldown mark, audio-renderer fallback Variant B, MediaPlayer fallback, BD-TS / VOB network-container routing, ERROR_CODE_TIMEOUT branch). Return `false` when the caller should propagate to `playerCallback.onBuffering(false)` + `playerCallback.onPlaybackError(error)`.
>
> Move the `MAX_EOF_RETRIES` constant into the helper's companion (keep `internal const val` so existing buffer constants in `VideoPlayerManager` remain undisturbed). In `VideoPlayerManager`, add a lazy helper field:
>
> ```kotlin
> private val errorHandler by lazy(LazyThreadSafetyMode.NONE) {
>     VideoPlayerErrorHandler(this)
> }
> ```
>
> Replace the existing `onPlayerError` body with:
>
> ```kotlin
> override fun onPlayerError(error: PlaybackException) {
>     if (errorHandler.handlePlayerError(error)) return
>     playerCallback.onBuffering(false)
>     playerCallback.onPlaybackError(error)
> }
> ```
>
> Preserve every existing Timber call **verbatim** (level, message, args). No behavioural change.
>
> **Logging discipline (CLAUDE.md):** `Log.d(` must remain absent in both files - `Timber` only.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerErrorHandler.kt` exists.
- `Grep` - `class VideoPlayerErrorHandler` matches exactly once in that file.
- `Grep` - `fun handlePlayerError(error: PlaybackException): Boolean` matches exactly once in that file.
- `Grep` - `Log\.d\(` returns zero hits in both touched files.
- `Grep` - `override fun onPlayerError` in `VideoPlayerManager.kt` has at most 5 lines of body before its closing brace (delegation pattern).
- `Build` - `.\a.ps1 dq` (quiet standardDebug) exits 0.
- expected: build PASS, delegation pattern in place | actual: PASS if all of the above hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:48 - Verification 6/6 PASS. VideoPlayerErrorHandler.kt created (~210 LOC). onPlayerError reduced to 4-line delegate. MAX_EOF_RETRIES moved to helper companion. Widened to internal: playbackRetryCount, lastPlaybackPosition, retryPlayback. Removed orphan import ExoPlaybackException. Build: BUILD SUCCESSFUL in 2m 46s (after one flaky-kapt retry).

---

### Step 02.3 - Extract `VideoPlaybackPreflightHelper`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlaybackPreflightHelper.kt` (New)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (Modified)

**Depends on:** Step 02.2

**Prompt for developer:**

> Extract the pre-flight block of `VideoPlayerManager.playVideo` (currently lines ≈724-796 - everything before the `managerScope.launch` block) into a new helper class `VideoPlaybackPreflightHelper` in `ui/player/helpers/`. Constructor takes `private val manager: VideoPlayerManager`. Expose a single public function:
>
> ```kotlin
> fun runPreflight(path: String, resourceType: ResourceType): String
> ```
>
> Return value is the `scenarioTag` (currently derived inline). The helper performs, in this exact order, the equivalent of: `playerCallback.onBeforeVideoLoad(path)`, `panelStereoSingleEyeNotifier.resetForNewSession()`, `PanelStereoCropApplier.reset(currentPlayerView)`, scenarioTag derivation, `memoryProfileCoordinator.enter(playbackScenario)`, audio-scenario Glide clearMemory, `memoryProbe.record(PRE_PLAY, scenarioTag)`, reset `videoSizeKnown`/`pendingEffectsApply`, assign `currentFilePath = path`, `VideoPlaybackFailureSessionCache.hasFailure` warning toast, `posterExtractor.reset()`, reset `lastCompletedPath` / `audioUnsupportedShownForPath`, `stopPositionSaving()`, ExoPlayer recreation accounting (`trackChangesSinceRecreate` increment, threshold check, `releasePlayer()` call), low-native-heap Glide eviction + `Runtime.getRuntime().gc()` (S0168 §5.3).
>
> Move the related private companion constants `PLAYER_RECREATE_INTERVAL`, `NATIVE_HEAP_RECREATE_THRESHOLD_BYTES`, `NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES` into the new helper's companion. Keep `DEFAULT_BRIGHTNESS_PROGRESS` and the buffer constants in `VideoPlayerManager` (they are read by other helpers).
>
> In `VideoPlayerManager`, add a lazy field:
>
> ```kotlin
> private val preflightHelper by lazy(LazyThreadSafetyMode.NONE) {
>     VideoPlaybackPreflightHelper(this)
> }
> ```
>
> Rewrite the head of `playVideo` to:
>
> ```kotlin
> fun playVideo(
>     path: String,
>     resourceType: ResourceType,
>     credentialsId: String?,
>     playWhenReady: Boolean = true,
>     onComplete: () -> Unit = {}
> ) {
>     Timber.d("VideoPlayerManager: playVideo - path=$path, type=$resourceType")
>     if (exoPlayer == null) MemoryEnduranceTracker.startScenario("VID-playback")
>     val scenarioTag = preflightHelper.runPreflight(path, resourceType)
>     managerScope.launch { /* unchanged dispatch + savedPosition restore block */ }
> }
> ```
>
> Preserve every Timber call verbatim. Do not change the `managerScope.launch { ... }` body in this step.

**Verification:**

- `Glob` - `VideoPlaybackPreflightHelper.kt` exists at the path above.
- `Grep` - `class VideoPlaybackPreflightHelper` matches exactly once.
- `Grep` - `fun runPreflight(path: String, resourceType: ResourceType): String` matches exactly once.
- `Grep` - inside `VideoPlayerManager.kt`, `preflightHelper.runPreflight(` matches exactly once.
- `Grep` - inside `VideoPlayerManager.kt`, `NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES` returns zero hits (constant moved).
- `Grep` - `Log\.d\(` returns zero hits in both touched files.
- `Build` - `.\a.ps1 dq` exits 0.
- expected: helper extracted, delegation in place, constants moved | actual: PASS if all hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:58 - Verification 7/7 PASS. VideoPlaybackPreflightHelper.kt created (~125 LOC). playVideo head shrunk to 4 lines + managerScope.launch (pre-flight delegated). 3 constants (PLAYER_RECREATE_INTERVAL, NATIVE_HEAP_RECREATE_THRESHOLD_BYTES, NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES) moved to helper companion. Widened to internal: posterExtractor, lastCompletedPath, audioUnsupportedShownForPath, trackChangesSinceRecreate, scenarioTagFor. Removed orphan imports: android.os.Debug, com.bumptech.glide.Glide. Build: BUILD SUCCESSFUL in 3m 10s.

---

### Step 02.4 - Extract `VideoPlayerTracksObserver`

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerTracksObserver.kt` (New)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (Modified)

**Depends on:** Step 02.3

**Prompt for developer:**

> Extract the body of `VideoPlayerManager.playerListener.onTracksChanged` (currently lines ≈601-645) into a new helper class `VideoPlayerTracksObserver` in `ui/player/helpers/`. Constructor takes `private val manager: VideoPlayerManager`. Expose one public function:
>
> ```kotlin
> fun onTracksChanged(tracks: Tracks)
> ```
>
> The helper handles: extracting the selected video track format, the `VR_QUALITY_DEBUG` log line (kept verbatim), the stereo detection coroutine launch via `manager.managerScope.launch { withContext(Dispatchers.IO) { manager.stereoDetector.detectForVideo(...) } }`, and the M2TS audio-unsupported-tracks toast logic (using `manager.audioUnsupportedShownForPath` for the one-shot guard).
>
> Add a lazy field in `VideoPlayerManager`:
>
> ```kotlin
> private val tracksObserver by lazy(LazyThreadSafetyMode.NONE) {
>     VideoPlayerTracksObserver(this)
> }
> ```
>
> Replace the existing `onTracksChanged` body with:
>
> ```kotlin
> override fun onTracksChanged(tracks: Tracks) {
>     tracksObserver.onTracksChanged(tracks)
> }
> ```
>
> Preserve every Timber call verbatim. `stereoDetector` and `audioUnsupportedShownForPath` must become `internal` in `VideoPlayerManager` if they are not already (check before flipping visibility - changing `private` to `internal` is acceptable only for these two; do not widen any other visibility).

**Verification:**

- `Glob` - `VideoPlayerTracksObserver.kt` exists.
- `Grep` - `class VideoPlayerTracksObserver` matches exactly once.
- `Grep` - `fun onTracksChanged(tracks: Tracks)` matches exactly once (the helper's public method).
- `Grep` - inside `VideoPlayerManager.kt`, `tracksObserver.onTracksChanged(tracks)` matches exactly once.
- `Grep` - `Log\.d\(` returns zero hits in both touched files.
- `Grep` - `VR_QUALITY_DEBUG` matches exactly once across the helper (verbatim move).
- `Build` - `.\a.ps1 dq` exits 0.
- expected: helper extracted, delegation in place | actual: PASS if all hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:03 - Verification 6/6 PASS (`VR_QUALITY_DEBUG` predicate relaxed to ≥1 from "exactly once" - the original code carried both the WHY-comment and the Timber call, so verbatim move preserves 2 occurrences). VideoPlayerTracksObserver.kt created (~75 LOC). onTracksChanged reduced to 3-line delegate. Widened to internal: stereoDetector. Removed orphan import androidx.media3.common.C. Build: BUILD SUCCESSFUL in 1m 37s.

---

### Step 02.5 - Verify LOC budget and insert Timber test tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Measure `VideoPlayerManager.kt` LOC via the catalogue: run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to refresh, then read the `loc` field for `VideoPlayerManager` from `dev/CATALOG/app_v2.jsonl`. The value must be ≤ 700. If it is between 700 and 750, the wave is acceptable with a note in step 02.6 documenting why the gap remains (likely `playerListener` cannot be further split). If > 750, do not proceed - this is a hard fail of the wave; revisit extractions 02.2-02.4 for additional candidates (e.g. `playVideo` post-preflight cleanup, lifecycle-related `init` block).
>
> Insert exactly one debug verification tag at the entry of the most-exercised changed flow (the `onPlayerError` delegation in `VideoPlayerManager.kt`):
>
> ```kotlin
> override fun onPlayerError(error: PlaybackException) {
>     Timber.d("S0274: VideoPlayerManager.onPlayerError delegated to VideoPlayerErrorHandler")
>     if (errorHandler.handlePlayerError(error)) return
>     playerCallback.onBuffering(false)
>     playerCallback.onPlaybackError(error)
> }
> ```
>
> Per CLAUDE.md "Debug Verification Tags": this tag exists while S0274 is in `BlockNeedUserTest` and is removed when the status leaves that state. The tag is the operator's logcat probe for Wave 01 device-test.

**Verification:**

- `Grep` - `Timber.d("S0274: VideoPlayerManager.onPlayerError delegated to VideoPlayerErrorHandler")` matches exactly once in `VideoPlayerManager.kt`.
- `Bash` - `dev/CATALOG/app_v2.jsonl` shows `"class":"VideoPlayerManager"` with a `loc` value ≤ 750 (hard ceiling) and ideally ≤ 700.
- `Build` - `.\a.ps1 dq` exits 0.
- expected LOC: ≤ 700 (≤ 750 with documented reason) | actual: 706 LOC (6 above soft target, well below hard 750). Reason: `playerListener` left intact per file's class KDoc invariant "cannot be split cleanly"; remaining residual is mutable-state declarations + Player.Listener body + lifecycle/init blocks + public API delegations. Acceptable per phase intro.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:08 - Verification 3/3 PASS. VideoPlayerManager.kt now 706 LOC (was 939, -233). Timber.d("S0274: VideoPlayerManager.onPlayerError delegated to VideoPlayerErrorHandler") inserted on the first line of onPlayerError - operator's logcat probe for the BlockNeedUserTest round. Build: BUILD SUCCESSFUL.

---

### Step 02.6 - Decide on `gradle.properties` reminder removal

**Files:** `gradle.properties` (conditional)
**Depends on:** Step 02.5

**Prompt for developer:**

> Strategic §6 #3: measure the impact of Wave 01 on Kotlin daemon heap pressure. Do not change `gradle.properties` blindly. Two acceptable outcomes:
>
> 1. **Keep heap unchanged.** Edit only the comment block (currently lines 43-46) to reflect the new state. Replace `VideoPlayerManager.kt is 1700+ lines with many coroutines` with the current LOC value and an updated rationale, e.g. `VideoPlayerManager.kt is now ~<loc> lines after S0274 Wave 01; heap kept at 4g while remaining hotspots (PlayerActivity 1726, TextViewerManager 1486, ..) are still in the S0274 backlog.` Do **not** lower the heap budget in this scenario. Keep the `TODO: Split` line replaced by `TODO: Continue S0274 backlog waves`.
>
> 2. **Try lower heap.** If the developer has a clean Gradle cache and time to validate, attempt `-Xmx3g` (or smaller) in `kotlin.daemon.jvm.options`, then run `.\a.ps1 dq` and `.\a.ps1 d` (full standardDebug) consecutively. If both succeed and Kotlin daemon does not OOM, commit the lower heap. If either fails, revert to `-Xmx4g` and take the "Keep heap unchanged" branch above. **Do not silently swallow a daemon OOM.**
>
> Record the chosen outcome in `temp/S0274_baseline_2026-05-20.md` under a new `## Wave 01 Result` section: the achieved `VideoPlayerManager.kt` LOC, whether the heap was lowered, and the deciding rationale. This artefact feeds the spec's `## Last Audit` block when `/spec-check` runs.

**Verification:**

- `Grep` - inside `gradle.properties`, `kotlin.daemon.jvm.options` is present and the comment block above it no longer references `1700+ lines` verbatim.
- `Grep` - inside `temp/S0274_baseline_2026-05-20.md`, `## Wave 01 Result` matches exactly once.
- `Build` - `.\a.ps1 dq` exits 0.
- expected: gradle.properties either kept at 4g with refreshed comment, or lowered with passing build | actual: kept at 4g (Option 1) with refreshed reminder block. Lowering deferred to a post-Wave-03+ revisit per the new comment.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 18:11 - Verification 3/3 PASS. gradle.properties reminder block rewritten (no more "1700+ lines" text, references the new 706 LOC + remaining backlog list). temp/S0274_baseline_2026-05-20.md `## Wave 01 Result` section appended. Build: BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `VideoPlayerManager.kt` LOC is ≤ 700 (hard ceiling 750 with documented gap).
- [ ] Three new helper files exist under `ui/player/helpers/`, each ≤ its line budget.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2` (the regeneration in step 02.5 covers this).
- [ ] `.\a.ps1 dq` exits 0 after the last extraction step.
- [ ] One `Timber.d("S0274:` tag exists in `.kt` code (CLAUDE.md "Debug Verification Tags" - inserted in step 02.5, removed by `/spec-check` when ticket leaves `BlockNeedUserTest`).
- [ ] Spec status moved to `BlockNeedUserTest` via `update.ps1 -Id S0274 -Status BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

- Phase 03 picks up to run the docs-catalog-cleanup ritual: trilingual feature docs are **skipped** per strategic §8, but functionality log decision and catalogue sync still run.
- The owner is then expected to exercise the player on device with a video that triggers each error branch (EOF retry, audio-renderer fallback, BD-TS dialog) and confirm logcat shows the `S0274:` tag. After the device test passes, `/spec-check S0274` flips status to `Partial` (because 15 more waves remain in the backlog) and removes the `S0274:` Timber tag.

---

## Rollback Plan

- Per-step granularity: each extraction is its own commit (ADR-2). Reverting step 02.4 alone is safe; reverting step 02.3 also requires reverting step 02.4 because step 02.4 might depend on the lazy-field placement from 02.3.
- Full-wave rollback: revert the four commits in reverse order. The backup from step 02.1 is the safety net if a revert goes sideways.
- `gradle.properties` change in step 02.6 is independent and can be reverted on its own.
