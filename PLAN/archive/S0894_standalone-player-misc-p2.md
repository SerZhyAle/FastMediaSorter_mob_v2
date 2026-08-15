# S0894 - Standalone player hosts: Glide targets, rename restart, teardown edges (P2 cluster)

**Ticket:** S0894
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: standalone-хосты - утечки Glide-таргетов и кривые teardown/rename-пути.

- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:283 - onResume() force-sets playWhenReady=true, overriding a user's manual pause across background/foreground
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:293 - Glide targets never cleared on teardown and loads bound to applicationContext - in-flight request retains ImageView/Activity past onDestroy (contract item 9)
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt:301 - Save-position-on-release coroutine launched into lifecycleScope from onDestroy - dead code on API 29+ (scope already cancelled), and lastSavedPosition is marked before the write commits
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt:519 - SAF rename restarts audio playback from position 0 (and leaks the just-swapped controller): path change from onRenameComplete re-triggers viewManager.show() because lastShownPath is not updated on rename
- app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt:549 - onDestroy force-initializes the entire lazy TextViewerManager graph when the activity dies before deferred setupViews() ran

## Related

- S0878 (audit tail container - triage source); S0893 (release-edges sibling cluster).

## Last Audit

**Date:** 2026-07-03
**Mode:** full (independent second-reviewer re-verification of a prior agent's implementation)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Findings re-verified against live code

1. **[PASS]** `StandaloneViewManager.onPause()` now captures `resumeVideoOnResume`/`resumeAudioOnResume` from the live `playWhenReady` state before force-pausing; `onResume()` restores exactly that captured value instead of hardcoding `true` - a manual pause survives background/foreground.
2. **[PASS]** `showImage`/`reloadImage`/`showGif` bind Glide via `Glide.with(safeViews.photoView)` (View/Activity-lifecycle-scoped, not `applicationContext`); `release()` explicitly calls `Glide.with(safeViews.photoView).clear(safeViews.photoView)` on teardown.
3. **[PASS]** `persistPositionOnRelease` runs on an independent `CoroutineScope(Dispatchers.IO + NonCancellable)`, not the by-then-cancelled `lifecycleScope` (dead on API 29+ per the original finding). `lastSavedPosition` is written only after the repository call returns inside `saveCurrentPosition()`'s try block - no longer marked ahead of the commit. `persistPositionOnRelease` itself does not touch `lastSavedPosition` (correct - the manager is tearing down, no further comparisons follow).
4. **[PASS]** `AudioStandaloneActivity.handleRenameComplete()` sets `lastShownPath = newUri.toString()` before calling `viewModel.onRenameComplete(newUri, newName)`; confirmed `StandalonePlayerViewModel.onRenameComplete()` writes `mediaFile.path = newUri.toString()` (identical string form), so the `file.path != lastShownPath` guard in `observeData()` no longer fires - `show()` is not re-invoked, the just-updated audio controller (already re-pointed via `updateAudioMediaItem`) is not orphaned, playback does not restart from 0.
5. **[PASS]** `TextStandaloneActivity.onDestroy()` guards both `textViewerManagerDelegate.isInitialized()` and `translationManagerDelegate.isInitialized()` before touching either lazy - an activity death before `setupViews()` ran no longer force-initializes the graph.

No rule violations found (layer discipline, Player/Glide ownership contract, Timber-only logging, no broad/empty catch, no neuroslop).

### Manual / on-device

- [ ] SAF rename of a currently-playing audio file: confirm playback audibly continues from the same position (functional confirmation beyond the static path-matching proof above).

### Build evidence

`.\a.ps1 fc` (standard debug, code+resources) - PASS. `compileStandardDebugKotlin` / full `BUILD SUCCESSFUL`.
