# Research 01 - Default gesture-binding seeding (install vs upgrade)

**Spec:** S0662
**§6 item:** 1
**Status:** Resolved
**Date:** 2026-06-24

## Question

How to preconfigure the three left-edge gesture bindings on a fresh install without overwriting existing users who already configured (or intentionally cleared) their gesture actions on upgrade.

## Current state (codebase findings)

Persisted gesture state lives in DataStore, read/written via `ScreenshotSettingsStore` (`app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt`). Keys + absent-key fallbacks:

- `gesture_overlay_enabled` -> `false`
- `screenshot_gesture_action_down` -> `SILENT_SCREENSHOT`
- `screenshot_gesture_action_right` -> `DO_NOT_USE`
- `screenshot_gesture_action_up` -> `DO_NOT_USE`

Action vocabulary `ScreenshotGestureAction` (`domain/model/ScreenshotGestureAction.kt`): `SILENT_SCREENSHOT, OPEN_IN_PLAYER, OPEN_IN_DRAW, OCR_TRANSLATE, SEND_TO_RECIPIENTS, SHARE, OPEN_APP, OPEN_PANEL, DO_NOT_USE`.

Direction enum `ScreenshotGestureDirection`: `DOWN, RIGHT, UP`.

Mapping requested by owner -> existing actions:

- UP = "вызов диалога запуска (меню)" -> `OPEN_PANEL` (opens the app-launch panel; `OPEN_PANEL` is a pre-capture action that skips capture).
- RIGHT = "скриншот редактирование" -> `OPEN_IN_DRAW` (capture, then open the captured image in the draw editor).
- DOWN = "скриншот тихий (с тостом)" -> `SILENT_SCREENSHOT` (capture, save, toast; no further UI).

Delta vs current absent-key fallbacks: DOWN already equals the target; only RIGHT and UP differ (`DO_NOT_USE` -> `OPEN_IN_DRAW` / `OPEN_PANEL`).

## Options considered

1. Change the static absent-key fallbacks in `ScreenshotSettingsStore` (and the mirrored `AppSettings` field defaults). Simplest, but retroactively changes behavior for every upgrade user who never explicitly wrote these keys - their UP/RIGHT silently become active actions. Violates "preserve existing users' choices".
2. Seed the three keys once at first run only (write actual DataStore values when no prior gesture configuration exists). Existing installs already have written values (or are detectable as upgrades), so they are left untouched.
3. No seeding - gestures stay `DO_NOT_USE` until manually configured. Fails the "useful out of the box" goal.

## Decision

Option 2 - first-run seeding. Matches the owner phrasing «при инсталяции» (on install), keeps upgrade users' configuration intact, and makes the Welcome toggle immediately useful on a fresh install.

The fresh-install gate should key off an explicit first-run/onboarding signal rather than "key absent", because an upgrade user who never opened the gesture card also has the keys absent - relying on absence alone would mis-seed them. The onboarding flow already tracks first-run state, which is the natural gate.

## Open follow-ups for /spec-tech

- Decide the exact first-run signal used to gate seeding (onboarding-not-completed flag vs a dedicated one-shot "gestures seeded" marker) so re-running onboarding does not re-seed over later user edits.
- Confirm seeding writes only the three direction actions and does not force `gesture_overlay_enabled=true` (enabling stays driven by the explicit toggle + permission grant).
