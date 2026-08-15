# S1303 - Media3 streaming segment download continues after coroutine cancellation

**Ticket:** S1303
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-5.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: Media3 streaming segment download continues after coroutine cancellation - cancelled downloads keep pulling segments

- Severity: P2, effort: small.
- File: `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/Media3SegmentDownloader.kt:100`
- Symptom: Cancelling a streaming link download (worker stop, user cancel) does not stop the network transfer: the blocking HlsDownloader/DashDownloader keeps downloading every remaining segment (potentially hundreds of MB) on an IO thread, burning bandwidth and battery, and the SimpleCache is only released when the full download finishes.
- Failure scenario: User shares an HLS link, the streaming pipeline starts downloadVariant for a 2 GB stream, then cancels the download notification. WorkManager cancels the coroutine, but coroutine cancellation neither interrupts the thread nor calls downloader.cancel() (no runInterruptible, no invokeOnCancellation anywhere in streamingEnabled). The download runs to completion in the background - possibly tens of minutes of cellular traffic - before the finally { cache.release() } and the CancellationException finally take effect.
- Fix sketch: Run the blocking call under runInterruptible(Dispatchers.IO) (Media3 downloaders abort on thread interrupt) or use suspendCancellableCoroutine with invokeOnCancellation { downloader.cancel() } so cancellation stops segment fetching immediately.
- Verifier rationale: Confirmed. downloader.download { } (line 100) is a blocking Media3 call executed under withContext(Dispatchers.IO); coroutine cancellation neither interrupts the thread nor calls downloader.cancel() - grep across streamingEnabled finds no runInterruptible, no invokeOnCancellation, no ensureActive, and StreamingDownloadStrategy (the only caller, line 68) adds no cancellation hook; its CancellationException handler only runs after download() returns. The non-suspend progress lambda cannot propagate cancellation either. So a cancelled 2 GB HLS/DASH download keeps fetching every remaining segment on the IO thread, wasting bandwidth/battery, with cache.release() deferred to the finally after full completion. Bounded waste (finishes eventually, cache is released), no leak or crash - P2.

Evidence excerpt:

```
// Media3 Downloader.download() blocks on a worker thread; we already moved to IO.
downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
    val total = if (contentLength > 0) contentLength else null
    onProgress(bytesDownloaded, total)
}
```

