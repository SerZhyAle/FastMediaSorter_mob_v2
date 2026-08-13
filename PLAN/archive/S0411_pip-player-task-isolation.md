**Status:** Archived

# S0411 — Isolate PiP standalone-player task from the main task

## Problem

When an external standalone player is in Picture-in-Picture and the user navigates back into the
app (e.g. overflow → "Open in FMS" on an external file), `MainActivity` (Browse) renders inside the
leftover PiP window instead of fullscreen.

Observed flow (device log, 2026-06-13):
- External video opens in `PhotoVideoStandaloneActivity`, user sends it to PiP (window keeps playing).
- External image (`frame_video_large_*.jpg`) opens fullscreen in a second `PhotoVideoStandaloneActivity`.
- User taps overflow → "Open in FMS". The image is a MediaStore file not inside any FMS resource, so
  `StandaloneFileOperationsHandler.openInFms()` falls back to `launchMainActivity()`.
- `MainActivity` appears in the small PiP window (854×480), showing Browse, not a media viewer.

## Root cause

- `MainActivity`, `PlayerActivity`, `StandalonePlayerActivity`, `PhotoVideoStandaloneActivity` and the
  external VIEW aliases all share the default task affinity (the application package).
- `launchMainActivity()` starts `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
- `NEW_TASK` reuses the existing task with a matching affinity. The only live task with that affinity
  is the standalone player's task, which is currently in PiP, so `MainActivity` inherits the PiP
  window geometry.

## Goal

`MainActivity` / Browse always opens in the main task (fullscreen), never inside the standalone
player's PiP window, regardless of an active PiP session.

## Scope

In scope:
- A dedicated task affinity for the external standalone player surfaces and their VIEW aliases.

Out of scope:
- The in-app `PlayerActivity` task model. In-app launches go through `navigateSlideAnim`
  (`startActivity` without `NEW_TASK`), so the player stays in the main task and the affinity attribute
  is inert there. No reported defect on that path.
- Changing "Open in FMS" fallback semantics for unresolvable external files (still Browse root). Tracked
  separately if desired.
- Preventing an external image launch from covering an in-PiP external video (different concern).

## Approach

- Give the standalone player task a distinct affinity `${applicationId}.player`, set on both the target
  activities and the aliases that launch them (an `activity-alias` uses its own affinity, defaulting to
  the package — not the target activity's, so the aliases must carry it explicitly).
- Apply to the PiP-capable external surfaces:
  - `activity .ui.player.StandalonePlayerActivity`
  - `activity .ui.player.standalone.PhotoVideoStandaloneActivity`
  - `activity-alias .StandaloneVideoPlayer`
  - `activity-alias .StandaloneImagePlayer`
- `MainActivity` keeps the default affinity. `launchMainActivity()`'s `NEW_TASK` can no longer match the
  `${applicationId}.player` task, so Browse resolves to the main task and renders fullscreen.

## Why this is safe

- Task affinity only redirects a launch when combined with `NEW_TASK` (or `singleTask`/reparenting).
  In-app navigation to the player uses plain `startActivity`, so the player still rides the current
  (main) task and back/recents behaviour is unchanged.
- The "open in new window" path (`NEW_TASK | MULTIPLE_TASK`) still creates a fresh task because
  `MULTIPLE_TASK` forces it regardless of affinity.
- External launches already arrive as `NEW_TASK`; they now land in the `${applicationId}.player` task
  instead of the package-default task.

## Verification

- Build: standard debug (manifest change) green.
- Device (BlockNeedUserTest):
  - Open an external video → enter PiP → open an external image → overflow → "Open in FMS".
  - Expect: Browse opens fullscreen in its own window, not inside the PiP rectangle.
  - Regression: normal in-app open file → player → back to list still works as one recents entry.
  - Regression: external single-file open still shows the correct fullscreen viewer.

## Last Audit

**Date:** 2026-06-15
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Static: 4 PiP surfaces (`StandalonePlayerActivity`, `PhotoVideoStandaloneActivity`,
`StandaloneVideoPlayer`, `StandaloneImagePlayer`) carry `taskAffinity="${applicationId}.player"`;
`MainActivity` has no `taskAffinity` (default); `launchMainActivity()` uses `NEW_TASK|CLEAR_TOP`.
Device (emulator-5554, API 37): `MainActivity` launched with those exact flags against an active
`.player` task lands in a separate default-affinity fullscreen task, never the player task. External
single-file open shows fullscreen viewer; in-app player rides the current task. No crash/ANR.
FEATURES trilingual EXEMPT (no user-facing copy change). Debug tag removed on the Verified flip.

### Manual / on-device

- [ ] Optional real-device pass with an actual PiP gesture + a SAF-only/network file to exercise the
  `OpenInFmsTarget.NotResolvable` → Browse fallback path directly (unreachable from local MediaStore
  VIEW intents; routing verified by equivalent direct launch).

## Revision History

- **2026-06-15** — by `/spec-test-device` (emulator-5554, Android API 37)
  - Scenario: temp/S0411_mobile_test_scenario_20260615_2215.md · PASS/INCONCLUSIVE/NOT-EXERCISED · Errors in log: 0
  - Task-affinity routing verified: standalone player surfaces resolve with `.player` affinity; `MainActivity` launched with `NEW_TASK|CLEAR_TOP` lands in a separate default-affinity fullscreen task, never the player task.
  - S0411 Timber probe + literal PiP-rectangle symptom not exercised (fallback needs non-local/SAF URI; no PiP trigger in player UI) — superseded by the direct routing proof.
