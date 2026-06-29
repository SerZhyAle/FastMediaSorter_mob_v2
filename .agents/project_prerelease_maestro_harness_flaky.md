---
name: prerelease-maestro-harness-flaky
description: /spec-prerelease Maestro suite fails on emulator from harness bugs (onboarding-bypass decay + player-flow oracles), not app defects; verify manually before blaming the app
type: project
---

`/spec-prerelease` Maestro full suite goes red on the emulator from TWO harness problems, not app defects (tracked by S0666; prepare.ps1 part fixed 2026-06-24).

**Why:** A 2026-06-24 sweep went Maestro 3/14 then 6/14. Both red runs were 100% harness; the app was healthy (in-app player, perf, logcat all clean, 0 error toasts).

**How to apply:**
- Onboarding cascade: `prerelease-prepare.ps1` adb-injected only `welcome_completed=true` into `welcome_prefs.xml`; the running app's next SharedPreferences commit (setFirstRunCompleted / gesture-seeding) rewrote the file WITHOUT it -> app reverted to WelcomeActivity mid-suite -> `go_home.yaml` can't exit onboarding -> every later flow cascade-fails (incl. app_launch/local_browse that passed in smoke). Fix applied: prepare now seeds the full 4-key map (welcome_completed + first_run_after_welcome=false + onboarding_default_player_shown=true + gesture_defaults_seeded=true). Genuine UI onboarding completion is durable across restart (proven) - so a mid-suite revert = harness, never an app onboarding bug.
- Player/slideshow flows (edge_cases, player_*, slideshow_basic) fail on viewer-not-visible (id photoView/playerView/btnPlaybackControl) even when durably onboarded. The in-app player WORKS when driven manually (tap media -> PlayerActivity + photoView/playerView, no external app, no "built-in player" toggle exists). Treat these as flaky oracles (first-open hint overlay intercepts first tap / wrong-id/timing), not app defects - still unfixed in S0666.
- Verdict gotcha: log-audit reports ~80+ "actionable" clusters on the emulator that are all system/Maestro noise (RoleControllerServiceImpl, SmsApplication, HCPackageInfoUtils dev.mobile.maestro, persistent_data_block, android.xr flags, GFXSTREAM egl, Maestro gRPC-netty). Trust `toastCount` (0 = no user-facing error) + crashBlocks over the raw actionable count. Aggregator FAIL was Maestro-only; log+perf were clean.
- Never propose `/skill-release` on this red gate (ADR-1). Manually verify the exercised surfaces, park harness findings, recommend fixing the harness + re-running.
