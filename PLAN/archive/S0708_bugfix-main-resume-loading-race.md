# Draft: S0708 - Main resume-loading indicator races ViewModel state collector

**Ticket:** S0708
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix, low)
**Source:** Parked by S0703 shared-state mutation audit (stage 2 adjudication, confirmed REAL, low severity).

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Raw finding (audit evidence)

On the main screen, `navigationProgressLayout.isVisible` and `tvNavigationMessage.text` are written by two un-coordinated owners:
- `MainResumePlaybackHelper` writes directly (`isVisible = true` + `text = resume_checking`) at resume start and clears on dismiss (`MainResumePlaybackHelper.kt:74-75, 185`).
- `MainActivity` state collector writes both from ViewModel state (`navigationProgressLayout.isVisible = state.isNavigating`, `tvNavigationMessage.text = state.navigationMessage`) (`MainActivity.kt:976-981`).

Both run on Main via `lifecycleScope.launch`; if the ViewModel emits `isNavigating=false` while the helper is mid resume-flow, the state collector hides the indicator prematurely (one-frame inconsistency).

## 1. Problem

The resume-loading indicator has two writers (direct helper vs ViewModel-state collector) that are not synchronized, so a state emission can prematurely hide it for a frame.

## 2. Direction (rough)

Make `MainResumePlaybackHelper` drive the indicator through ViewModel state (single source) instead of writing the views directly, or gate the collector while resume is active. Detail in /spec-tech.

## 3. Implementation

- `MainResumePlaybackHelper` exposes `isResumeLoadingActive` (true from overlay-show until `dismissResumeLoading`).
- The MainActivity `viewModel.state` collector early-returns while `isResumeLoadingActive` is set, so it can no longer write `navigationProgressLayout`/`tvNavigationMessage` mid resume-flow.
- Helper stays the single writer during resume; collector resumes ownership once the flow dismisses or navigates away.

## Related

- Parent audit: S0703.

## Last Audit

### Manual / on-device

Outcome: PASS - 2026-06-26, emulator-5554 (standard debug 2.60.6261.106). Resume-loading indicator stays single-owned by the helper through the full resume check; collector defers, no premature hide/flicker.

- [x] Cold launch via ACTION_MAIN (force-stop then `am start -a android.intent.action.MAIN -c LAUNCHER -n .../MainActivity`) with a fresh `resume_state_prefs_main` (local mkv, within 48h TTL) and resumeOnNextLaunch=true.
- [x] `S0708: resume loading shown, gating nav-state collector` logged exactly once at resume start (11:14:08.768) - helper claims `isResumeLoadingActive`.
- [x] Resume state loaded (11:14:08.770) and flow navigated to PlayerActivity (11:14:09.147), ~379 ms window.
- [x] No interleaved `navigationProgress`/`isNavigating` collector write between show and navigate - indicator not hidden mid-flow (the collector early-return held).
- [x] Player opened on the resumed file (video_large_200mb.mkv 1/1) - end-to-end resume succeeded. Evidence: temp/s0708_logcat.txt, temp/s0708_02_resume_indicator.png.

Note: availability check was a fast local `File.exists()`, so the visible indicator window was short; the race-guard invariant (helper is the sole writer while active, collector defers) is what is verified here and held.
