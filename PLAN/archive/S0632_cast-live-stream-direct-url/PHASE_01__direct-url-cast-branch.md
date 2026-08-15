# Phase 01 - Direct-URL cast branch for live streams

**Strategic spec:** `PLAN/S0632_cast-live-stream-direct-url.md`
**Status:** Done
**Depends on:** none

## Objective

Add a third source path to the Cast send flow: a live-stream branch that hands the stream URL to
the receiver directly (live stream type, content-type by extension), skipping temp download and the
local proxy. Reject RTSP. Leave the local/network/cloud file path untouched.

## Steps

### Step 1 - Pure cast-stream decision resolver

Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastStreamResolver.kt`.

- Sealed result `CastStreamDecision`: `NotAStream`, `UnsupportedProtocol`, `Direct(contentType: String)`.
- `CastStreamResolver` with `resolve(path: String): CastStreamDecision`:
  - Reuse `StreamMediaKindClassifier.isSupportedScheme(path)` (`domain/usecase/streams/StreamMediaKindClassifier.kt`); false -> `NotAStream` (local/smb/sftp/ftp/cloud unchanged).
  - `path` lowercased starts with `rtsp://` -> `UnsupportedProtocol`.
  - else (http/https) -> `Direct(contentType)` where content-type maps by URL extension: `m3u8` -> `application/x-mpegurl`, `mpd` -> `application/dash+xml`, `mp4`/`mov` -> `video/mp4`, `webm` -> `video/webm`, `ts` -> `video/mp2t`, unknown -> `application/x-mpegurl` (HLS is the dominant live case; WHY-comment this default).
- Default-construct the classifier (`StreamMediaKindClassifier()`); it is `@Inject`-constructible with no deps and `CastMediaManager` is not a Hilt object.

**Verification:**

- Glob `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastStreamResolver.kt` exists.
- Grep `CastStreamResolver.kt` for `sealed` and `UnsupportedProtocol` and `Direct(` - all present.
- Grep `CastStreamResolver.kt` for `isSupportedScheme` - classifier reused, no duplicated scheme list.

### Step 2 - Live-stream loader on receiver

In `CastMediaManager.kt` add `private fun loadStreamOnReceiver(file: MediaFile, url: String, contentType: String)`.

- Mirror `loadMediaOnReceiver` but: `MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_LIVE).setContentType(contentType).setMetadata(MediaMetadata(MEDIA_TYPE_MOVIE){ KEY_TITLE = file.name }).build()`.
- `MediaLoadRequestData` autoplay true; on failure show `R.string.cast_error_load` (existing).

**Verification:**

- Grep `CastMediaManager.kt` for `STREAM_TYPE_LIVE` and `loadStreamOnReceiver` - both present.
- Grep `CastMediaManager.kt` for `setContentType` - present in the stream loader.

### Step 3 - Route resolveAndSend through the resolver first

In `CastMediaManager.kt`:

- Add `private val castStreamResolver = CastStreamResolver()`.
- At the top of `resolveAndSend(file)`, branch on `castStreamResolver.resolve(file.path)`:
  - `UnsupportedProtocol` -> main-thread Toast `R.string.cast_stream_unsupported_protocol`; return.
  - `Direct(contentType)` -> main-thread `loadStreamOnReceiver(file, file.path, contentType)`; return.
  - `NotAStream` -> fall through to the existing local/network/cloud logic unchanged.

**Verification:**

- Grep `CastMediaManager.kt` for `castStreamResolver.resolve` - present in `resolveAndSend`.
- Grep `CastMediaManager.kt` for `cast_stream_unsupported_protocol` - referenced.
- Read `resolveAndSend`: existing `isLocalFile` / `downloadToTemp` / `proxyServer.serveFile` path still present and reached for `NotAStream`.

### Step 4 - KDoc update

Update the `CastMediaManager` class KDoc: document the live-stream direct path (no proxy, no download, live stream type) and the RTSP rejection.

**Verification:**

- Grep `CastMediaManager.kt` KDoc for `live` / `stream` mention added.

## Phase Done Criteria

1. `CastStreamResolver.kt` exists with the 3-way sealed decision and reuses `StreamMediaKindClassifier`.
2. `CastMediaManager.resolveAndSend` consults the resolver before the local/network classification; non-stream path unchanged.
3. `loadStreamOnReceiver` builds a `STREAM_TYPE_LIVE` MediaInfo with content-type and casts the URL directly.
4. `.\a.ps1 fk` (Kotlin compile) passes for the touched files.
