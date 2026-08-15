# Research 02 - Writing to the clipboard from the capture service context

**Strategic item:** §6.2
**Status:** Resolved

## Question

Is `setPrimaryClip` allowed from the screenshot capture service (a started foreground service running MediaProjection), and is there a UI-thread requirement?

## Finding

- Android 10 (API 29) restricted clipboard **reads** from background (`getPrimaryClip` returns null unless the app is foreground or the default IME). It did **not** restrict **writes**: `setPrimaryClip` from a foreground service succeeds.
- `ScreenCaptureService` is a started foreground service (`startForeground` with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`) and is the active capture owner at the moment of writing, so the write path is valid.
- `ClipboardManager.setPrimaryClip` is safe to call from the main thread; the service's `serviceScope` already runs on `Dispatchers.Main.immediate`, so the call site is on the main looper. The bitmap-to-PNG compression should run off the main thread; only the final `setPrimaryClip` need touch main.

## Consequence for the plan

- Perform PNG compression to cache on an IO/Default dispatcher inside `ImageClipboardWriter`, then issue `setPrimaryClip` (cheap) - the existing main-thread service scope satisfies any thread expectation.
- No extra permission and no foreground-promotion work is needed beyond what the capture service already does.

## Sources

- Android 10 privacy changes - clipboard data access (developer.android.com): read-only restriction.
- In-repo: `screencapture/ScreenCaptureService.kt` (foreground service, `Dispatchers.Main.immediate` scope, existing toast on main thread).
