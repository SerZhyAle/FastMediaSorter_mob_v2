# S0581 - Friendly "stream unavailable" dialog with remove-from-list action

**Status:** Archived

## 1. Problem

A stream that does not respond fails ungracefully:

- Inline audio on the Трансляции screen (`StreamInlineAudioManager` via `AudioServiceController`) has no error listener - the failure surfaces only as `AudioPlaybackService: fatal playback error - stopping service` in the log and the UI stays silent.
- The fullscreen player surfaces a raw playback-error message.

The user wants a friendly dialog when a stream is unreachable, offering to remove that stream from the local list, instead of a silent stop or a raw error.

## 2. Context

- Every stream visible in the list is already a persisted `StreamSourceEntity` row (catalog import and user-add both write to the DB, even for a non-working URL). So a failing stream always has a DB row to remove - no "not in DB" branch is needed.
- `StreamsViewModel.onRemove(StreamSourceEntity)` already wraps `RemoveStreamSourceUseCase`.
- Related fixes shipped alongside (separate dev-log entries, not part of this ticket):
  - video stream URLs were forced to `ResourceType.LOCAL` and failed `File.exists()`.
  - the background audio factory (`NetworkAwareMediaSourceFactory`) lacked cross-protocol redirects, so an Icecast/Shoutcast 301 across http<->https was fatal.

## 3. Decisions

- Dialog buttons: Повторить / Удалить / Отмена.
  - Повторить: re-attempt playback of the same source.
  - Удалить: `StreamsViewModel.onRemove(source)` and dismiss.
  - Отмена: dismiss, leave the row.
- Trigger: a source/connection playback error (no response, 30x not followed, 404/410, DNS/refused, timeout) on a stream source. Decoder/format errors keep the existing generic error path.
- Title carries the stream display name so the user knows which stream failed.

## 4. Scope

- Inline audio surface (DONE): `StreamInlineAudioManager` gains an `onError(StreamSourceEntity)` callback fired from `Player.Listener.onPlayerError`; `StreamsActivity.showStreamUnavailable` shows the Retry/Remove/Cancel dialog. Strings EN/RU/UK added (`streams_unavailable_title`, `streams_unavailable_message`).
- Fullscreen player surface (DONE): a source error on a VIDEO/RTSP stream now resolves the originating `StreamSourceEntity` by URL and shows the same dialog.

## 4.1 Implementation notes

- `StreamInlineAudioManager.onPlayerError` stops inline playback and forwards the failed source.
- Removal reuses the existing `StreamsViewModel.onRemove` -> `RemoveStreamSourceUseCase`.
- The inline dialog lives in `StreamsActivity`; no business logic added beyond forwarding the three choices.
- Player surface: `PlayerPlaybackCallbackImpl.onPlaybackError` routes stream-scheme URLs (http/https/rtsp) to `PlayerViewModel.onStreamPlaybackFailed`, which resolves the row via the new `GetStreamSourceByUrlUseCase` (`StreamSourceDao.getByUrl`) and emits `PlayerEvent.ShowStreamUnavailable`; `PlayerEventHandler` renders the dialog. A URL with no stored row falls back to the generic error, preserving the old skip behavior for arbitrary http media.
- Retry replays the URL (`PlayerActivity.playVideo`); Remove calls `PlayerViewModel.removeStreamSource` then finishes the player; Cancel finishes.

## 5. Out of scope

- Auto-removal or auto-skip of dead streams.
- Liveness pre-checking before playback.
- Bulk "remove all dead" maintenance.
