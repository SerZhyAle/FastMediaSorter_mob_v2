# Research 02 - Surfacing a skip message from the background service

Strategic §6 item 2.

## Question

How to show a short non-blocking message about a skipped track when the Activity may be dead (service running in background)?

## Findings

- The whole point of `AudioPlaybackService` is background survival after Activity destruction, so the message channel must not assume a live Activity.
- A foreground service may post a text `Toast` on the main thread. Custom-view toasts are restricted from the background since Android 11, but plain text toasts are unaffected and remain the standard mechanism across API 23-35. The project already uses `Toast.makeText` broadly.
- An Activity-side observation path already exists: `PlayerMediaLoaderManager` observes audio-service playback errors when the player screen is alive. That path can give a richer in-app surface, but it is not guaranteed present in background.

## Decision

- Primary channel: post a plain text `Toast` from the service on `Looper.getMainLooper()`. Reliable whether or not the Activity is alive - matches the background-playback contract.
- Debounce: suppress repeats so a run of consecutive bad files does not spam identical toasts (strategic §3.1). Show at most one skip toast per short window.
- No new IPC/broadcast is introduced; the existing `PlayerMediaLoaderManager` error-observation path is left untouched (richer in-app surfacing can be layered later via §5.3 extension point).

## Status

Resolved.
