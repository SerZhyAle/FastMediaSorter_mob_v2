# Phase 03 - Standalone video-host background release

**Strategic spec:** [`../S0893_player-release-edges-p2.md`](../S0893_player-release-edges-p2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01/02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Give `StandalonePlayerActivity` (deprecated, pending removal, still a live fallback target) and `PhotoVideoStandaloneActivity` (its current replacement) the same API24+ release-on-`onStop`/recreate-on-`onStart` edge for their video path, via one small addition to the shared `StandaloneViewManager` plus a matching `onStop`/`onStart` override in each host. `AudioStandaloneActivity`/`TextStandaloneActivity` are correctly out of scope - audio is expected to keep playing via `AudioPlaybackService` while backgrounded, and text has no player.

---

## Prerequisites

- [x] Confirmed zero existing `onStop`/`onStart` overrides in either target Activity.
- [x] Confirmed `StandaloneViewManager.releaseVideoPlayer()` already implements the Media3 1.2.1 GL-pipeline-safe teardown sequence (S0859) - the new `onStopVideo()` method reuses it as-is, no duplication needed.
- [x] Confirmed both hosts already track `viewModel.state.value.mediaFile`/`mediaType` independently, so rebuild-on-start can call the existing `viewManager.show(..)` entry point without any new state on `StandaloneViewManager`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 910 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1115 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1155 |

---

## Steps

### Step 03.1 - Add StandaloneViewManager.onStopVideo()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new public method next to `release()`:
> ```kotlin
> /**
>  * S0893: API24+ multi-window release edge for the video path only - the audio controller and
>  * document viewers are untouched (audio may legitimately continue via AudioPlaybackService;
>  * PDF/EPUB/text hold no comparable OS codec resource). Callers gate this call on
>  * Build.VERSION_CODES.N and rebuild via [show] from their own onStart - only the host Activity
>  * knows which MediaFile is currently active, so no resume state is tracked here.
>  */
> fun onStopVideo() {
>     if (exoPlayer != null) {
>         Timber.d("StandaloneViewManager: onStopVideo - releasing backgrounded video player")
>         releaseVideoPlayer()
>     }
> }
> ```

**Verification:**

- `Grep` - `fun onStopVideo\(\)` matches exactly once.
- `Grep` - `releaseVideoPlayer\(\)` called inside it (reuses the existing S0859-fixed teardown, no new release logic duplicated).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `StandaloneViewManager.kt` (+~13 LOC).

---

### Step 03.2 - Wire onStop/onStart in PhotoVideoStandaloneActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add two new overrides next to the existing `override fun onPause()` / `override fun onDestroy()` pair (in the "Lifecycle" region):
> ```kotlin
> override fun onStop() {
>     super.onStop()
>     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) viewManager.onStopVideo()
> }
>
> override fun onStart() {
>     super.onStart()
>     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && viewModel.state.value.mediaType == MediaType.VIDEO) {
>         viewModel.state.value.mediaFile?.let { file ->
>             viewManager.show(file, MediaType.VIDEO) { pv -> setupVideoControls(pv) }
>         }
>     }
> }
> ```
> `android.os.Build` is already imported in this file (used by `parseIncomingIntent()`'s SDK check) - reuse the existing import, no new import needed.

**Verification:**

- `Grep` - `override fun onStop\(\)` and `override fun onStart\(\)` each match exactly once.
- `Grep` - `viewManager.onStopVideo\(\)` present inside `onStop()`.
- `Grep` - `viewManager.show\(file, MediaType.VIDEO\)` present inside `onStart()`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: `PhotoVideoStandaloneActivity.kt` (+~13 LOC).

---

### Step 03.3 - Wire onStop/onStart in StandalonePlayerActivity (deprecated fallback host)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add two new overrides next to the existing `override fun onPause()` / `override fun onDestroy()` pair, using the same `::viewManager.isInitialized` late-init guard already documented at this class's `onDestroy()` (S0860 - `viewManager` is assigned late in `setupViews()`, so a probe short-circuit or first-frame destroy can run lifecycle callbacks before it exists):
> ```kotlin
> override fun onStop() {
>     super.onStop()
>     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ::viewManager.isInitialized) {
>         viewManager.onStopVideo()
>     }
> }
>
> override fun onStart() {
>     super.onStart()
>     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ::viewManager.isInitialized &&
>         viewModel.state.value.mediaType == MediaType.VIDEO) {
>         viewModel.state.value.mediaFile?.let { file ->
>             viewManager.show(file, MediaType.VIDEO) { pv -> setupVideoControls(pv) }
>         }
>     }
> }
> ```
> `android.os.Build` is already imported (used by `parseIncomingIntent()`/`resolveIncomingUri()`).

**Verification:**

- `Grep` - `override fun onStop\(\)` and `override fun onStart\(\)` each match exactly once.
- `Grep` - `::viewManager.isInitialized` present in both new overrides.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: `StandalonePlayerActivity.kt` (+~18 LOC).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` PASS (full-tree combined build across all 12 Phases 01-03 files, `BUILD SUCCESSFUL in 26s`).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for all 3 files via `post-change.ps1` (batched at phase end - see Phase 04).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. All three functional clusters (Phases 01-03) are independently reverted/verifiable; Phase 04 is documentation/catalog only.

---

## Rollback Plan

Low-risk: revert this phase's 3 files. `StandaloneViewManager.onStopVideo()` is additive (new method, no existing call site changed) - reverting the two Activity files alone is sufficient to fully disable this phase's behavior change without touching the shared manager.
