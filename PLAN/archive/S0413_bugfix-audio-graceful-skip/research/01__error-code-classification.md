# Research 01 - ExoPlayer error code classification (skippable vs fatal)

Strategic §6 item 1.

## Question

Which `PlaybackException.errorCode` values mark a per-file problem (skip the track) vs a fatal session problem (stop the service)?

## Findings

Media3 1.2.1 `PlaybackException` error codes are grouped by thousands:

- `1xxx` - runtime/unspecified (`ERROR_CODE_UNSPECIFIED`, `ERROR_CODE_FAILED_RUNTIME_CHECK`, `ERROR_CODE_TIMEOUT`, `ERROR_CODE_REMOTE_ERROR`, `ERROR_CODE_BEHIND_LIVE_WINDOW`).
- `2xxx` - IO family (`ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, `..TIMEOUT`, `ERROR_CODE_IO_BAD_HTTP_STATUS`, `ERROR_CODE_IO_FILE_NOT_FOUND`, `ERROR_CODE_IO_NO_PERMISSION`, ..).
- `3xxx` - parsing family (`ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001`, `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 3003`, manifest variants).
- `4xxx` - decoding family (`ERROR_CODE_DECODER_INIT_FAILED`, `ERROR_CODE_DECODING_FAILED`, `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED`, ..).

The observed crash: `errorCode=3003` with cause `UnrecognizedInputFormatException` (none of the bundled extractors could read the stream). That maps to `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`. Confirmed empirically by the user's own log line emitted from `AudioPlaybackService.onPlayerError`.

Project precedent for thousand-range classification already exists:

- `BackgroundMusicManager` treats `error.errorCode in 2000..2999` as IO error.
- `StandaloneViewManager` maps `ERROR_CODE_PARSING_CONTAINER_MALFORMED` to the existing string `R.string.error_invalid_format`.
- `SlideshowResourceAvailabilityManager` keeps a "playback unavailable" set centered on IO/timeout codes.

## Decision

- **Skippable (per-file)** = `errorCode in 3000..4999` (parsing + decoding). The defect is intrinsic to one file's bytes/format; the rest of the queue is fine.
- **Fatal (stop session)** = everything else, notably the `2xxx` IO family. `ERROR_CODE_IO_FILE_NOT_FOUND` is the cache-eviction case the current `onPlayerError` comment already calls out - it can signal the whole `unified_network_cache` was wiped, so it must keep today's stop behavior rather than be skipped silently.

Rationale: range check is consistent with the existing `2000..2999` precedent and needs no per-constant enumeration; new parsing/decoding codes added by future Media3 versions are auto-covered.

## Status

Resolved.
