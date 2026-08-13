# Research 01 - Widget tap targets vs launcher commands

**Ticket:** S1170
**Date:** 2026-07-27
**Method:** read-only source inventory of `app_v2/src/main/java/com/sza/fastmediasorter/widget/`, `core/panel/`, `domain/model/launcher/`, `src/launcherEnabled/ui/launcher/`.

Establishes what each of the 14 catalog widgets does on tap and how much of that the launcher's existing command model already expresses. This decides the phase split: routes must exist before the gadgets that call them.

---

## 1. What exists

`LauncherCellCommand` (`domain/model/launcher/LauncherCellCommand.kt`) - sealed, 6 cases:

- `App(packageName)` -> `app:<pkg>`
- `Feature(routeKey)` -> `fn:<routeKey>`
- `Resource(resourceId, mode)` -> `res:<id>:<BROWSE|SLIDESHOW|PLAY>`
- `Stream(streamId)` -> `stream:<id>`
- `OsShortcut(targetKey)` -> `os:<targetKey>`
- `ScheduledOp(operationId)` -> `op:<id>`

Dispatch: `ExecuteLauncherCommandUseCase.launch(command)`; gadget-facing entry `LauncherGadgetHost.run(command)`, implemented by `LauncherHomeViewModel.run`.

`InternalRouteCatalog` (`core/panel/InternalRouteCatalog.kt`) exposes 14 route keys, intents in `core/panel/AppLaunchPanelRouteIntents.kt`: `app_launch_panel, calculator, game, ocr, streams, favorites, quick_camera, quick_voice, screen_recording, link_download, take_photo_send_to, take_photo_edit, take_photo_ocr_translate, start_video_recording`.

`OsShortcutCatalog` keys are OS system screens only - no route there can open one of our own screens.

**Constraint found:** `ExecuteLauncherCommandUseCase` only ever calls `startActivity`. A command cannot start a service or send a broadcast.

---

## 2. Per-widget tap targets

| Provider | Tap target(s) |
|---|---|
| `CalculatorWidgetProvider` | `CalculatorActivity.createIntent(fromWidget = true)` |
| `CameraOcrTranslateWidgetProvider` | `MainActivity` action `ACTION_CAMERA_OCR_TRANSLATE` |
| `CaptureOcrPanelWidgetProvider` | two: `MainActivity` `ACTION_CAMERA_PHOTOS`; `MainActivity` `ACTION_CAMERA_OCR_TRANSLATE` |
| `CameraLaunchWidgetProvider` | `CameraLaunchActivity` action `ACTION_LAUNCH` (photo mode), data `fms://cam-launch/<widgetId>` |
| `CameraPhotosWidgetProvider` | `MainActivity` `ACTION_CAMERA_PHOTOS` -> Browse on `LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS` |
| `CameraQuickCaptureWidgetProvider` | configured -> `CameraQuickCaptureActivity` `ACTION_CAPTURE`; unconfigured -> `CameraQuickCaptureConfigActivity` |
| `ContinueReadingWidgetProvider` | `MainActivity` `ACTION_START_SLIDESHOW` on the last-used resource |
| `GameLaunchWidgetProvider` | enabled -> `GameActivity`; disabled -> `SettingsActivity.openProgramsSectionIntent` |
| `RandomPhotoFrameWidgetProvider` | three: config activity; `PlayerActivity.createPanelIntent(initialFilePath = snapshot)`; `BrowseActivity.createIntent` |
| `RandomMusicWidgetProvider` | `MainActivity` `ACTION_RANDOM_MUSIC` |
| `AudioNowPlayingWidgetProvider` | body -> `MainActivity`; prev/play-pause/next -> `startService` on `AudioPlaybackService`; favourite -> self-broadcast |
| `QuickAudioRecorderWidgetProvider` | `QuickAudioRecorderActivity` action `ACTION_TOGGLE` |
| `FavoritesWidgetProvider` | container -> `MainActivity` + `open_favorites`; rows -> `PlayerActivity` per row |
| `ScheduledTasksWidgetProvider` | no main activity tap; run-all / pause are self-broadcasts; everything else -> `SettingsActivity` + `EXTRA_OPEN_SCHEDULED` |

---

## 3. Verdict that drives the phase split

**Expressible today, no new route (6):** calculator -> `fn:calculator`; camera OCR translate -> `fn:ocr`; quick capture -> `fn:quick_camera`; quick voice recorder -> `fn:quick_voice`; favourites main tap -> `fn:favorites`; game -> `fn:game` (its disabled fallback is already modelled by the route's `settingsIntent`).

**Needs a new `InternalRouteCatalog` key + intent (5):** camera photos; launch camera in photo mode; continue reading; random music; the scheduled-tasks settings deep link (`SettingsActivity` + `EXTRA_OPEN_SCHEDULED`) - note `os:` cannot express an in-app screen.

**Not command-shaped at all - must be a real gadget (3):**

- `RandomPhotoFrame` - three state-dependent targets plus a live photo snapshot; no command carries `initialFilePath`/`skipAvailabilityCheck`.
- `AudioNowPlaying` - transport buttons are `startService`, favourite is a broadcast; the command dispatcher only starts activities.
- `ScheduledTasks` - run-all and toggle-pause are broadcasts, and the list is per-row.

`Favorites` is dual: its container tap is a command, but reproducing the row list needs its own rendering over the same data source.

**Consequence for ordering:** the five new routes are a prerequisite for the simple gadgets that call them, so routes come before gadgets. The three non-command gadgets carry their own behaviour and depend only on the gadget contract.

---

## 4. Two corrections this research forces on the strategic spec

Both are folded into §3 of the spec (2026-07-27).

- The original grouping described 13 of 14 entries: `AudioNowPlayingWidgetProvider` was missing.
- "Own state" named the photo frame and the OCR panel. In code, per-`appWidgetId` state belongs to `RandomPhotoFrameWidgetProvider` (`RandomPhotoFrameSnapshotStore`) and `CameraQuickCaptureWidgetProvider` (`cam_capture_target_*` + its own config activity). `CaptureOcrPanelWidgetProvider` holds no state.

Also: `HomeWidgetEntry` carries no string key - identity is `providerClass`. A launcher gadget key must be added explicitly rather than derived from the class name, which R8 and renames would break.
