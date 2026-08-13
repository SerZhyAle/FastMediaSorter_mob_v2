# S1302 - Browse inline-play INFINITE ObjectAnimator keeps pumping vsync frames after onStop - stop-state rebind dispatched after collectors die

**Ticket:** S1302
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): handlers-timers-2.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: Browse inline-play INFINITE ObjectAnimator keeps running after the screen stops - the stop-state rebind is dispatched after collectors are cancelled

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt:685`
- Symptom: While inline audio plays, the row's note icon spins via an ObjectAnimator with repeatCount=INFINITE (InlinePlaybackAnimator.kt:19-24, started at MediaFileAdapter.kt:653). The only things that cancel it are onViewRecycled and a PAYLOAD_PLAYBACK_STATE rebind driven by collectOnLifecycle(viewModel.inlinePlayerState) in BrowseObserverManager.kt:63, which is repeatOnLifecycle(STARTED)-scoped. BrowseActivity.onStop calls super.onStop() FIRST (cancelling that collector) and only then viewModel.inlineStop(), so the resulting stopped-state emission is never delivered to the adapter and nothing else stops holder animators (onDestroy at line 693-700 has no adapter/animator teardown). The infinite animator therefore keeps requesting Choreographer frames at vsync rate for the whole time the activity is stopped; after a Back-press destroy it keeps ticking until GC clears ObjectAnimator's weak target and it self-cancels.
- Failure scenario: User starts inline audio playback in Browse (note icon spinning), then presses Home and leaves the phone for hours. Audio stops (inlineStop) but the INFINITE rotation animator is never cancelled: the main thread wakes on every vsync (~60-120 Hz) running the animation pump for the entire background period, draining battery with the screen off. On Back-press exit during playback the zombie animator additionally keeps ticking on a destroyed activity's view until the next GC happens to collect the target.
- Fix sketch: Call viewModel.inlineStop() before super.onStop() so the collector still delivers the rebind, and add a hard teardown: cancel row animators on view detach (OnAttachStateChangeListener inside InlinePlaybackAnimator) or an adapter-level stopAllAnimations() invoked from BrowseActivity.onStop/onDestroy.
- Verifier rationale: Confirmed. collectOnLifecycle is repeatOnLifecycle(STARTED) (LifecycleExtensions.kt:33-43); BrowseActivity.onStop calls super.onStop() at line 681 (collector cancelled) before viewModel.inlineStop() at 685, so the idle-state emission reaches a dead collector and no PAYLOAD_PLAYBACK_STATE rebind runs. The note animator is INFINITE (InlinePlaybackAnimator.kt:19-24, started MediaFileAdapter.kt:653) and the only cancel paths in the entire codebase are the payload rebind and onViewRecycled (MediaFileAdapter.kt:504-516); BrowseActivity.onDestroy (693-700) has no animator/adapter teardown. So the animator keeps pumping Choreographer frames while the activity is stopped, and after Back-press destroy until GC collects ObjectAnimator's weak target. Kept at P2 rather than P1 because the defect self-heals on return (StateFlow replays the idle state to the restarted collector on onStart) and OS cached-process freezing bounds the multi-hour background drain on modern Android; no retained Activity leak (weak target).

Evidence excerpt:

```
override fun onStop() {
    super.onStop()                      // cancels the STARTED-scoped inlinePlayerState collector
    if (!isChangingConfigurations) {
        MemoryEnduranceTracker.endScenario()
        viewModel.inlineStop()          // line 685: state change emitted AFTER the collector died
    }
// InlinePlaybackAnimator.kt: noteAnimator = ObjectAnimator.ofFloat(target, "rotation", 0f, 360f)
//     .apply { repeatCount = ObjectAnimator.INFINITE; ... start() }
```

