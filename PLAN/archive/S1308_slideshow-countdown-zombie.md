# S1308 - SlideshowController: pending showCountdown lambda survives pause/stop - stuck '1..' countdown badge

**Ticket:** S1308
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): handlers-timers-3.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: SlideshowController: pending showCountdown lambda survives pause/stop and leaves a stuck '1..' countdown badge

- Severity: P3, effort: trivial.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt:268`
- Symptom: scheduleNextSlide() posts an untracked anonymous lambda `countdownHandler.postDelayed({ showCountdown() }, countdownDelay)` (line 268). pauseSlideshow() (line 180) and stopSlideshow() (line 209) remove only `countdownRunnable` via removeCallbacks(countdownRunnable) - the pending anonymous lambda is not removed (only restartTimer/onPause/cleanup use removeCallbacksAndMessages(null)). The zombie lambda later fires, posts countdownRunnable, and ticks 3-2-1 on a paused/stopped slideshow. The countdown ends at seconds==1 without ever emitting 0 (the clearing onCountdownTick(0) lives in slideShowRunnable, which WAS removed), and PlayerDialogAndUiStateManager.updateCountdownDisplay (line 531-535) only hides the badge on seconds==0, so '1..' stays visible.
- Failure scenario: User runs an image slideshow with a 10 s interval and taps 'stop slideshow' (or pause) 2 s into the interval. ~5 s later - slideshow already stopped - the zombie lambda fires: the player overlay counts 3.. 2.. 1.. and then the '1..' badge stays stuck over the image indefinitely (until the next slideshow start, file change to a suppressed type, or activity pause). Same for updateInterval(), which also leaves the old lambda pending.
- Fix sketch: Use countdownHandler.removeCallbacksAndMessages(null) in pauseSlideshow()/stopSlideshow()/updateInterval() (matching restartTimer and the lifecycle hooks), or store the showCountdown lambda in a field and remove it explicitly wherever countdownRunnable is removed.
- Verifier rationale: Confirmed. scheduleNextSlide() posts an untracked anonymous lambda (line 268); pauseSlideshow (line 180) and stopSlideshow (line 209) remove only slideShowRunnable and countdownRunnable - only restartTimer/onPause/cleanup use removeCallbacksAndMessages(null), and updateInterval (226) removes only slideShowRunnable. The zombie lambda fires after stop/pause and posts countdownRunnable, whose run() (126-138) gates only on lifecycle STARTED, not isActive/isPaused, so it ticks 3-2-1 on a stopped slideshow. It never emits 0: the clearing onCountdownTick(0) lives in the removed slideShowRunnable (line 98), and updateCountdownDisplay (PlayerDialogAndUiStateManager.kt:531-535) hides tvCountdown only when seconds==0; grep shows no other code path hides tvCountdown outside audio-photo/library modes. Result: stray ticks plus a '1..' badge stuck over the image until the next start/pause/mode change. Cosmetic and bounded (no growth, no leak) - P3, one-line fix per removal site.

Evidence excerpt:

```
// scheduleNextSlide():
countdownHandler.postDelayed({ showCountdown() }, countdownDelay)   // line 268 - untracked lambda
// stopSlideshow():
handler.removeCallbacks(slideShowRunnable)
countdownHandler.removeCallbacks(countdownRunnable)   // line 209 - does NOT remove the pending lambda
// updateCountdownDisplay(): tvCountdown.text = "$seconds.."; isVisible = true; hidden only when seconds == 0
```

