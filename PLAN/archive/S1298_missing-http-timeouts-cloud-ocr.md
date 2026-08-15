# S1298 - Missing connect/read timeouts: GoogleDrive/OneDrive HTTP clients and PaddleOCR model download hang forever on stalled network

**Ticket:** S1298
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-4, hang-paths-2.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: S1025 (transfer fail-fast on unreachable destination - complementary; this ticket adds socket-level timeouts).

## Finding 1: Cloud HTTP clients (Google Drive, OneDrive) set no connect/read timeouts - stalled network hangs playback and transfers indefinitely

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveHttpClient.kt:113`
- Symptom: Cloud video playback freezes in endless buffering (no error ever surfaces) and cloud copy/upload jobs hang forever when the connection stalls without RST; the only recovery is killing the app.
- Failure scenario: User plays a Google Drive video: CloudDataSource.open() -> GoogleDriveRestClient.getFileInputStream (line 937) -> GoogleDriveHttpClient.getFileInputStream, which opens an HttpURLConnection with default timeouts (0 = infinite). Wi-Fi drops silently mid-stream -> CloudDataSource.read() blocks in connection.inputStream.read() forever; unlike SMB there is no watchdog, so ExoPlayer stays in STATE_BUFFERING indefinitely. Same for makeAuthenticatedRequest (scans/listings) and for OneDriveRestClient.downloadFile/uploadFile/getFileInputStream (OneDriveRestClient.kt:286/336/655): a background cloud transfer coroutine hangs until process death. Sibling code proves the house standard: GoogleDriveThumbnailModelLoader sets 15 s/30-60 s on every connection.
- Fix sketch: Set connectTimeout (10-15 s) and readTimeout (30-60 s) on every HttpURLConnection in GoogleDriveHttpClient (makeAuthenticatedRequest, getFileInputStream, downloadFileAsStream) and OneDriveRestClient (downloadFile, uploadFile, getThumbnail, getFileInputStream); for the ExoPlayer stream path a read timeout surfaces as IOException which the player already handles.
- Verifier rationale: Confirmed. No setConnectTimeout/setReadTimeout anywhere in GoogleDriveHttpClient.kt (makeAuthenticatedRequest line 49, getFileInputStream line 113, downloadFileAsStream line 166) nor in OneDriveRestClient.kt (openConnection at 286/336/622/655 - grep for 'Timeout' returns zero hits in that file). HttpURLConnection defaults are 0 = infinite. CloudDataSource has no watchdog (grep for watchdog/Future/timeout finds nothing), so unlike SMB a stalled socket blocks read() forever: ExoPlayer stays in STATE_BUFFERING indefinitely and background transfer coroutines hang until process death. Sibling code (GoogleDriveThumbnailModelLoader 15/30-60 s, CloudThumbnailModelLoader, GoogleDriveRestClient:881, DropboxClient OkHttp 30/60 s) proves the house standard, making the omission clearly a defect, not a design choice.

Evidence excerpt:

```
val connection = url.openConnection() as HttpURLConnection
connection.requestMethod = "GET"
connection.setRequestProperty("Authorization", "Bearer $token")
// no setConnectTimeout / setReadTimeout anywhere in this file
```

## Finding 2: PaddleOCR model download uses URL.openStream() with no connect/read timeout - OCR flow can hang forever

- Severity: P1, effort: small.
- File: `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt:100`
- Symptom: First OCR use (noLegal flavor) downloads ~3 model artifacts inline inside the user's OCR request; if the model host stalls (TCP accepted, no bytes) the request never completes - OCR spinner forever, and the blocked Dispatchers.IO thread is never reclaimed because blocking socket reads ignore coroutine cancellation.
- Failure scenario: User triggers OCR-translate on a screenshot; the Cyrillic model set is not installed yet, so recognizeTextBlocks synchronously runs downloadModel. The CDN/host accepts the connection but stops sending mid-tar (flaky mobile network, captive portal, throttled host). read() blocks with infinite timeout: the OCR flow shows progress forever, backing out does not unblock the thread (blocking I/O is not interrupted by coroutine cancel), and each retry parks one more IO thread. Over a long session the user retries several times, wedging multiple IO threads and never getting OCR until process kill.
- Fix sketch: Replace URL.openStream() with url.openConnection() setting connectTimeout/readTimeout (e.g. 15 s / 30 s), or route through the app's OkHttp client; additionally wrap each artifact download in withTimeout(...) inside downloadModel so the suspend contract stays cancellable and the temp file is cleaned on failure.
- Verifier rationale: Confirmed. Line 100 is URL(artifact.url).openStream() with no connectTimeout/readTimeout set (URLConnection defaults are 0 = infinite). Call path verified: PaddleOcrEngine.recognizeTextBlocks -> ensureInitialized -> paddleOcrModelManager.downloadModel (PaddleOcrEngine.kt:77), i.e. inline in the user's OCR request. Grepped OfflineOcrEngineProvider.kt and ui/player/helpers for withTimeout - none on the recognition/download path, so no upstream bound refutes the hang. A host that accepts TCP but stalls mid-transfer blocks read() forever inside readFully/copyExactlyTo; blocking socket I/O ignores coroutine cancellation, so backing out does not reclaim the Dispatchers.IO thread and each retry wedges another. noLegal flavor only, and requires a stalled (not cleanly refused) connection, but the scenario is reachable and the hang is indefinite - P1 (unreleased heavy resource + permanent feature hang). Fix is localized (timeouts on the connection or OkHttp + withTimeout) - small.

Evidence excerpt:

```
private fun downloadAndExtractNb(artifact: ModelArtifact, outputFile: File) {
    ...
    URL(artifact.url).openStream().use { input ->      // line 100 - default HttpURLConnection timeouts are 0 = infinite
        GZIPInputStream(input).use { gzip ->
            extractTarEntry(gzip, artifact.entryName, tempFile)
        }
    }
}
// reached from PaddleOcrEngine.ensureInitialized -> paddleOcrModelManager.downloadModel(modelVariant) (PaddleOcrEngine.kt:77), awaited by the user's recognizeTextBlocks call
```


---

## Follow-up from the 2026-08-01/02 remote log pass

The timeouts this ticket introduced now fire in the field, and the Google Drive upload path cannot
absorb them.

- On a mobile link, `HttpTimeouts.STREAM_READ_MS` (60 s) killed two uploads of a ~2 MB file after
  exactly 60 seconds, while a ~130 KB file in the same batch succeeded in 5 seconds.
- The cause is not the budget itself: `GoogleDriveMultipartUploader.upload` sets `doOutput = true`
  without a streaming mode, so the whole body is buffered and pushed inside `getResponseCode()` -
  which is what the read timeout bounds. A slow uplink therefore reads as a read timeout.
- Parked as S1361, which also covers the missing retry and the progress callback measuring buffer
  writes rather than network bytes.

Nothing here reverses this ticket - the timeout is doing its job. It is a pointer so the two are not
investigated twice.
