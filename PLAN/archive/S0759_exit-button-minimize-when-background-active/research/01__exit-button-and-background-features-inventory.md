# S0759 research 01 - Exit button mechanics + background-feature inventory

**Date:** 2026-06-28
**Scope:** app_v2, MainActivity exit path + foreground services
**Goal:** Establish what the top-left exit does today, what "minimize" would be, and inventory the
background-capable features that could flip the button from close to minimize. The behavioural rule and
UX remain owner decisions.

---

## Current exit behaviour

- Top-left exit -> `MainActivity.finishAffinity()` (`ui/main/MainActivity.kt:389` and `:865`).
- Before exiting, the app **stops `AudioPlaybackService`** on purpose: the inline note at
  `MainActivity.kt:1236` states that without stopping the foreground service, `finishAffinity()` +
  `Process.killProcess()` makes the OS restart the process within ~1s (a double-startup bug). So today's
  exit is a full teardown that deliberately kills background audio.
- `PlayerVrLaunchManager.kt:125` uses `finishAndRemoveTask()` for the VR hand-off (separate path).

## Minimize primitive already exists

`WelcomeActivity.kt:106` calls `moveTaskToBack(true)`. That is the natural "minimize" = send the task to
the background, keep the process and any foreground services alive, return the user to the home screen /
previous task. No new API needed for the minimize action itself.

## Key interaction / risk

The current exit path is specifically designed to **stop** `AudioPlaybackService` to avoid OS restart.
"Minimize but keep the music playing" must take a different path that does **not** stop that service. So
the minimize branch diverges from the close branch precisely at foreground-service teardown - this is the
core technical change, and it must be correct or it reintroduces the double-startup bug (close) or leaks
the service (minimize).

## Background-capable feature inventory (candidates for the rule)

Foreground services declared in `AndroidManifest.xml`:
- `.ui.player.AudioPlaybackService` - `mediaPlayback` (background music; **owner-named**) - `:354`.
- `.widget.QuickAudioRecorderService` - `microphone` (active recording, S0349) - `:446`.
- `androidx.work.impl.foreground.SystemForegroundService` - `mediaPlayback|dataSync` (scheduled file
  operations via WorkManager, Android 14+) - `:513`.
- `.widget.FavoritesWidgetService` / `.widget.ScheduledTasksWidgetService` - widget remote views
  (`:340,351`) - probably not "keep app foregrounded" triggers.

Other long-lived capabilities (not services, owner-named or known):
- **Edge gestures** (left-edge swipe / Quick Launch Panel) - **owner-named**; a setting + overlay, not a
  service. Note `AppLaunchPanelTileService` is a QS tile entry point (`:369`).
- TTS read-aloud (`TtsReadAloudManager`, used by the text viewer) - active speech.
- Streaming playback (may route through `AudioPlaybackService`).
- Slideshow / auto-advance (if running).

## Owner decisions (product / UX - cannot be inferred from code)

1. **Trigger set (Q1).** Which of the above flip the button to minimize? (Owner named bg music + edge
   gestures; the rest - recording, in-progress file ops, TTS, streams - need an explicit call.)
2. **Rule (Q2).** Setting-enabled vs feature-actually-active at press time? (e.g. "background music is
   enabled in settings" vs "music is playing right now").
3. **Button UX (Q3).** Keep the same exit button silently, or change icon/label/tooltip when it will
   minimize? Owner flagged `/ui-clarify`.
4. **Real-close path (Q4).** How does the user still fully quit - long-press, menu item, confirm dialog,
   or rely on the system app-switcher swipe?
5. **Minimize semantics (Q6).** `moveTaskToBack(true)` (recommended, primitive exists) vs launching the
   home intent vs other.
6. **Scope (Q5).** Only the top-left main-window button, or also other back/close/exit entry points?
7. **Indication (Q7).** Tell the user the app is still running in background (toast/snackbar/notification)
   or rely on the existing foreground-service notification(s)?

## Touched files (likely, any variant)

- `ui/main/MainActivity.kt` (exit handler at `:389`/`:865`, service-stop at `:1236`).
- Wherever the active-feature state is queried (audio service state, edge-gesture setting, WorkManager
  running ops) to evaluate the rule.
- Possibly a new small helper (e.g. `MainExitDecisionManager`) per the UI-delegate convention.
