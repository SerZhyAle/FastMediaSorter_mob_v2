**Status:** Archived

## Fix

Root cause: `CastMediaManagerImpl.loadMediaOnReceiver` built the local-file `MediaInfo` without a
content type, so the default Cast receiver rejected the load with a null status message
(`load failed - null`). The live-stream path already set it; the local-file path did not.

Change (castEnabled source set):
- `LocalCastProxyServer.mimeType(File)` - extracted the MIME resolution to a shared companion
  function so the proxy bytes and the `MediaInfo` content type resolve identically.
- `loadMediaOnReceiver(..., contentType)` now calls `.setContentType(mimeType)`.

The `serve called but no file set` 404 is the receiver's symptom of the same failed load (it stops
requesting once load fails); no separate proxy-ordering change made.

Validation: `.\a.ps1 fk` -> BUILD SUCCESSFUL.

# S1155 - Cast of local file fails: proxy serves 404 "no file", receiver load returns null

## 0. Raw capture (verbatim)

Source: remote diagnostic drop `logs/fastmediasorter_logs.zip`, device POCO M2012K11AG (alioth), Android 13 / API 33, app 2.60.7191.740 (260719174), standard/release.

Session `fastmediasorter_20260723_190448.log`:
```
2026-07-23 19:07:26.174 W/App: LocalCastProxyServer: serve called but no file set
```

Session `fastmediasorter_20260723_190803.log`:
```
2026-07-23 19:08:19.289 W/UserAction: CastMediaManager: load failed - null
```

## 1. Symptom

Casting a local file to a Cast receiver fails. The receiver requests the media URL from the app's
`LocalCastProxyServer`, but `currentFile` is null/missing at serve time, so the proxy returns
`404 "no file"`. The receiver's `RemoteMediaClient.load()` then reports failure with a null
status message, surfacing as `CastMediaManager: load failed - null` and the `cast_error_load` toast.

## 2. Evidence

- `LocalCastProxyServer.serve()` guards on `currentFile == null || !file.exists()` and returns
  `Response.Status.NOT_FOUND` with body `"no file"`.
- `CastMediaManagerImpl.loadMediaOnReceiver()` logs `load failed - ${result.status.statusMessage}`
  (null here) and shows `R.string.cast_error_load` when `!result.status.isSuccess`.
- The two log lines are one session apart but describe the same local-file cast path: proxy has no
  file to serve at the moment the receiver connects.

## 3. Hypotheses (unverified)

- Ordering/race: `remoteClient.load(...)` is issued (or the proxy URL published) before
  `currentFile` is assigned, so the receiver's HTTP GET races ahead of file-set.
- File cleared between load request and serve (session teardown, next-file navigation).
- Local file path no longer exists at serve time (`!file.exists()`).

## 4. Notes

- App degrades gracefully (toast + warning log, no crash).
- Distinct from S1137 (FFmpeg AAC radio decode) - that is the FFmpeg audio renderer, this is the
  Cast local-file proxy path.
- Needs on-device repro with a real Cast receiver to confirm which hypothesis holds before design.
