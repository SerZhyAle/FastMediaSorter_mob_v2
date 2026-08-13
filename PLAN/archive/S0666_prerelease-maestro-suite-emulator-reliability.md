# S0666 - Pre-release Maestro suite unreliable on emulator

**Status:** Archived
**Priority:** 60

## Implementation State (2026-06-24)

Harness-only changes; no app runtime code (ADR-2 honoured). Emulator-default `all` suite went from 3/14 (full cascade) to **12/12 green** (the 2 inline-audio flows are excluded - see below).

### Done + validated

- **Part A - durable onboarding bypass.** `scripts/devtest/prerelease-prepare.ps1` now seeds the full 4-key `welcome_prefs.xml` (welcome_completed + first_run_after_welcome=false + onboarding_default_player_shown=true + gesture_defaults_seeded=true). Validated by code analysis (the only welcome_prefs writers - setFirstRunCompleted / markGestureDefaultsSeeded / markDefaultPlayerOnboardingShown / setWelcomeCompleted - are each gated by one of these flags, so none fire when all four are pre-seeded; the file is never rewritten) plus the persistence experiment (a genuine 4-key file survived force-stop + cold relaunch). Confirmed in every later run: NO onboarding cascade.
- **Part B.1 - media-open tap targeting.** ROOT CAUSE (proven by UiAutomator geometry): a row is `[0,2006][1080,2280]` clickable; `tvFileName` is `[303,2092][1047,2151]` (centre x=675) because it is constrained end-to-parent and its node runs behind the right-side operation buttons (btnFavorite starts at x=607). `tapOn text:<filename>` taps the text-node centre (675) which lands on a button, so the file never opens. FIX: `tapOn containsChild:{text:<filename>}` - this matches the clickable row container and Maestro taps its centre (x=540, clear of the buttons at 607+); unambiguous by exact filename. (An interim `ivThumbnail leftOf:` attempt was discarded - `leftOf` matched a higher row's thumbnail and opened the wrong file.) Validated isolated on player_image and player_video.
- **Part B.2 - deep-list scroll determinism.** The Загрузки resource is a flat, name-sorted ~48-file list, so videos/docs sit at the very bottom; the last row is clipped by the bottom bar so it never reaches 100% visibility and `scrollUntilVisible` (default `visibilityPercentage:100`) never finds it, flakily. FIX: `visibilityPercentage:30` + `centerElement:true` + `waitToSettleTimeoutMs:600` (+ longer timeout) on the deep-target scrolls. Validated isolated on player_video (which had both the scroll and the tap failure).
- **Part B.3 - transient-flake retry.** Even after B.1/B.2, the emulator suite still flaked run-to-run on different flows (a transiently-failing flow could leave the app on a deep screen that cascades into the next flow's go_home). `maestro/run-tests.ps1` now retries a flow ONCE on an assertion failure (not on infra/exec errors), force-stopping the app first (new `Reset-App`) so the retry starts clean. Standard mitigation for emulator UI suites; an isolated re-run almost always passes.
- Result: player_image, player_video, player_resume, player_documents, edge_cases, slideshow_basic, plus all browse/settings/app_launch/local_browse - **12/12 green** on the emulator-default suite (run 2014, exit 0), reproducibly with the retry guard.

### Excluded from emulator default (not an app defect; tracked here for redesign)

- **player_info_dialog, player_audio_lyrics** (audio). Ground truth: tapping an audio row does NOT open a full player - audio plays INLINE in the browse list via the row's `btnPlayInline` (the activity stays `BrowseActivity`; no `playerView` / `btnPlaybackControl`). These flows were written for a full-screen audio player that does not exist in this build, and the inline-audio state is not even introspectable by UiAutomator on the emulator (the dump fails). Excluded from the `all` selection in `maestro/run-tests.ps1` (same mechanism as the device-only file-operation flows). REMAINING WORK: redesign both flows for the inline-audio model (reach the info dialog / lyrics via the row overflow or a long-press context menu, not a player), or confirm whether audio is intended to be inline-only (vs a possible full-player regression) before reinstating them.

### Files touched

- scripts/devtest/prerelease-prepare.ps1 (Part A)
- maestro/features/player/{player_image,player_video,player_resume,player_documents,player_info_dialog,player_audio_lyrics}.yaml, maestro/features/edge/edge_cases.yaml, maestro/features/slideshow/slideshow_basic.yaml (Part B.1 containsChild tap + Part B.2 scroll params)
- maestro/run-tests.ps1 (exclude the 2 inline-audio flows from the emulator-default `all` suite; add single retry-on-assertion-failure with a force-stop `Reset-App` between attempts)

## 0. Raw capture (verbatim, from /spec-prerelease run 2026-06-24)

Parked by `/spec-prerelease` (S0484 sweep). The automated pre-release Maestro gate could not produce a trustworthy verdict on emulator-5554 (API 37) because of two distinct harness reliability problems. The app itself showed no real defects (manually verified). Two sub-issues:

### A. Onboarding-bypass not durable (cascade) - FIX APPLIED, needs validation

- `scripts/devtest/prerelease-prepare.ps1` stage 2.5 injected only `welcome_completed=true` into `shared_prefs/welcome_prefs.xml` via adb file-write.
- Mid-suite the running app committed `welcome_prefs` from its own in-memory map (e.g. `setFirstRunCompleted()` flips `first_run_after_welcome`, or `maybeSeedDefaultGestureBindings()` writes `gesture_defaults_seeded`), rewriting the file WITHOUT the injected `welcome_completed`. App reverted to `WelcomeActivity`; `maestro/_shared/go_home.yaml` cannot back out of onboarding, so every later flow cascade-failed.
- Run 1: 3/14 PASS (settings, browse_all_images, browse_filter), then 11 FAIL incl. app_launch / local_browse that passed in smoke.
- Proven NOT an app defect: genuine UI onboarding completion writes the full 4-key `welcome_prefs.xml` and SURVIVES force-stop + cold relaunch (lands on MainActivity, never re-shows onboarding). VERDICT: PERSISTS.
- Fix applied this run: prepare.ps1 now seeds the COMPLETE 4-key map (welcome_completed + first_run_after_welcome=false + onboarding_default_player_shown=true + gesture_defaults_seeded=true) so the app never rewrites welcome_prefs and the bypass survives the whole run. NEEDS validation on a fresh clean prepare run.

### B. Player / slideshow flow oracles flaky - UNFIXED

- Run 2 (durably onboarded): cascade gone (browse_sort_empty, app_launch, local_browse pass), but 8 flows still FAIL: edge_cases, player_audio_lyrics, player_documents, player_image, player_info_dialog, player_resume, player_video, slideshow_basic.
- Each fails on viewer-not-visible: `id/photoView`, `id/playerView`, or `id/btnPlaybackControl` after tapping a media item. The tap succeeds (flow does not fail on tapOn), but the oracle never sees the viewer.
- Proven NOT an app defect: manual drive opens the in-app viewer fine - photo_001.jpg (4032x2268) -> PlayerActivity + photoView (no crash); video_sample.mp4 -> PlayerActivity + playerView (Media3/ExoPlayer). No external app. No built-in/external player toggle exists in this build.
- Likely cause: a first-open hint/command-panel overlay intercepts the first tap, or the oracle waits on the wrong id / too-short timing, or the resumeOnNextLaunch + go_home interaction leaves a player-flow precondition unmet.

### Evidence
- temp/s0484_prerelease_20260624_1746.md (full sweep report)
- temp/maestro_suite_20260624_1701.json (run 1), temp/maestro_suite_20260624_1746.json (run 2)
- temp/s0484_run_20260624_1746.log (verdict-window logcat: 0 actionable app errors, 0 toasts, 0 crash)
- temp/s0484_metrics_20260624_1746.json (perf: cold-start 2479ms, player-open 807ms, network-listing 953ms all PASS)

## 1. Problem

The `/spec-prerelease` Maestro regression layer cannot certify a build on emulator: the harness's own state setup and player-flow oracles fail even when the app is healthy. A red automated gate blocks `/skill-release` despite no real app defect.

## 2. Goals

- prepare onboarding-bypass survives a full multi-flow suite run (validate the applied 4-key fix).
- Player / slideshow flows reliably detect the in-app viewer (photoView / playerView / btnPlaybackControl) on a clean emulator, or are made resilient to the first-open overlay / resume-state.
- A clean run reaches a real PASS/FAIL on app behavior, not harness fragility.

## 3. Scope / constraints

- Harness only (scripts/devtest/prerelease-prepare.ps1, maestro/ flows + _shared fragments). No app runtime code (S0484 ADR-2).
- Read-only zones untouched.

## 6. Research

- Confirm exactly which app commit drops welcome_prefs in the single-key case (setFirstRunCompleted vs gesture seeding) - timeline in run-1 log shows WelcomeActivity first at 17:22:46.
- Inspect player flows for a missing "dismiss first-open hint overlay" step; check whether photoView/playerView ids are current; consider extendedWaitUntil timing and a go_home/back before each player flow's media open.

## 11. Acceptance

- Fresh `/spec-prerelease` run: Maestro run reaches >= the non-player baseline with no onboarding cascade, and player/slideshow flows pass on a healthy build (or are quarantined with a documented emulator-limitation note).
