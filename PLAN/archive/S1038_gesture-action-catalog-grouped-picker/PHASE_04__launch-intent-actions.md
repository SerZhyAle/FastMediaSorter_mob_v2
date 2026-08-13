# Phase 04 - launch-intent-actions

**Goal:** Add launch/intent actions (all gesture flavors) via a `LaunchActionHandler`, with package-visibility and safe degradation.

## Steps

- [x] **4.1** New enum values + catalog entries (group LAUNCH): `OPEN_ASSISTANT`, `OPEN_GEMINI`, `CREATE_KEEP_NOTE`, `OPEN_URL`, `SET_ALARM`, `SET_TIMER`, `NEW_CALENDAR_EVENT`. Update the exhaustive `when`. Verify: enum + catalog + when cover all; compiles.
- [x] **4.2** `LaunchActionHandler` intents (all wrapped in `runCatching { startActivity(.. FLAG_ACTIVITY_NEW_TASK) }.onFailure { Timber.w }` - trampoline via a transparent same-app Activity as the dispatcher runs in a Service):
  - assistant: `Intent(ACTION_ASSIST)` (system default).
  - Gemini: launch the Gemini app package; degrade to no-op if absent.
  - Keep note: try the create-note intent -> fallback launch Keep -> safe no-op.
  - URL: `ACTION_VIEW Uri.parse(payload)` using the Phase 02 per-slot payload; empty payload -> no-op.
  - alarm: `AlarmClock.ACTION_SET_ALARM`; timer: `ACTION_SET_TIMER`; calendar: `CalendarContract.Events` insert intent.
  Verify: handler compiles; each intent guarded.
- [x] **4.3** Package-visibility: add `<queries>` for Keep/Gemini/assistant/alarm/calendar target packages/intents to `AndroidManifest.xml` (check existing `<queries>` first; Android 11+). Verify: manifest merges; `a.ps1 fr` PASS.
- [x] **4.4** URL slot input: a simple input dialog next to the slot writing the per-slot payload (Phase 02). Wire from `OperationsGesturesManager` when `OPEN_URL` is selected. Verify: URL persists per slot; `a.ps1 dq` PASS.

## Done criteria
- [x] Launch/intent actions present with degradation; URL per-slot input works; standard debug builds green.

## Step Log

- 2026-07-19 - Steps 4.1-4.4 done. Enum +7 LAUNCH; catalog +7 LAUNCH entries; runPostSave when kept exhaustive. New `LaunchActionHandler` (assistant ACTION_ASSIST; Gemini `com.google.android.apps.bard` via getLaunchIntentForPackage; Keep note = ACTION_CREATE_NOTE on API 33+ then fallback to Keep launch; URL = ACTION_VIEW with bare-host https normalisation, empty -> no-op; alarm/timer via AlarmClock; calendar via ACTION_INSERT Events). All launches guarded by runCatching + FLAG_ACTIVITY_NEW_TASK. `handlePreCaptureAction` is now `suspend` and takes zone+direction so OPEN_URL resolves its per-slot payload via `payloadFor`; both callers (OverlayHostService, ScreenshotAccessibilityService) updated. Manifest `<queries>` extended (Gemini package + ASSIST/CREATE_NOTE/SET_ALARM/SET_TIMER/calendar-INSERT intents; Keep + assistant already present). URL entry wired in `EdgeGestureConfigManager` (S1035 moved the picker host there from OperationsGesturesManager) via `promptUrl` + `applyPayload`. Strings +16 x3. Verification: `a.ps1 fc` standard + `a.ps1 fkn` noLegal both BUILD SUCCESSFUL; `check_strings_localized -KeyPrefix gesture_` OK 43/43; dialog-cancel-style delta 0.
- Note: research/tactical referenced `OperationsGesturesManager` as the picker host, but S1035 relocated the edge-gesture detail UI (incl. the picker) to `EdgeGestureConfigManager`; wired there instead (derivable from code, not a guess).
