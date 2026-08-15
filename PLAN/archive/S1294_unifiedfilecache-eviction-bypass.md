# S1294 - UnifiedFileCache 500MB cap bypassed by all primary download paths (eviction only runs in putFile)

**Ticket:** S1294
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): caches-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: UnifiedFileCache 500MB cap is bypassed by all primary download paths (eviction only runs in putFile)

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/UnifiedFileCache.kt:122`
- Symptom: The 500MB LRU disk cap (ML-006) is enforced only inside putFile() (line 104 calls evictIfNeeded()). But nearly every writer downloads directly into the cache via getCacheFile(), which returns a File handle and never triggers eviction: NetworkFileDownloader.kt:69 (per-file metadata partial downloads, up to 5MB each while browsing thousands of network files), PlayerMediaLoaderManager.kt:481 and :654 (full SMB/SFTP/FTP and cloud audio downloads, plus next-track prefetch at :864), BackgroundMusicManager.kt:347/513, SearchLyricsUseCase.kt:198/242/274, NetworkFileManager.kt:258. The only putFile() callers are two legacy-migration paths (NetworkFileManager.kt:249, NetworkPdfThumbnailLoader.kt:163). clearAll() runs only at session boundaries: MainActivity.onDestroy (isFinishing, line 631), Browse init/shutdown (BrowseLifecycleSetupManager.kt:83, BrowseShutdownCoordinator.kt:80). No periodic worker prunes cacheDir/unified_network_cache; the 24h TTL in getCachedFile() deletes a file only if that exact file is requested again.
- Failure scenario: User plays a network (SMB/SFTP/FTP/cloud) audio playlist in PlayerActivity overnight, or browses/plays network media for hours without leaving Browse: every track/file is fully downloaded into cacheDir/unified_network_cache (preCacheNetworkAudio + next-track prefetch), each metadata scan adds up to 5MB per file, and evictIfNeeded() never runs because none of these paths call putFile(). After hours the cache grows to many GB, filling device storage - downloads and thumbnail writes start failing, the OS starts aggressively killing the app, and the user sees 'storage full' with no visible cause until Browse is exited or the app is closed.
- Fix sketch: Enforce the budget on the direct-download path: call evictIfNeeded() from getCacheFile() (async, before returning the handle) or add a commitFile(path) that downloaders call after a successful write, which runs evictIfNeeded(). Optionally add a periodic TTL sweep of the whole directory (the current 24h TTL only fires on per-file re-access).
- Verifier rationale: Confirmed by reading UnifiedFileCache.kt and grepping all callers. evictIfNeeded() is private and invoked only from putFile() (line 104); putFile has exactly two callers, both legacy-migration paths (NetworkFileManager.kt:249, NetworkPdfThumbnailLoader.kt:163). Every primary download path writes through getCacheFile() (line 122), which returns a bare File handle and never triggers eviction: NetworkFileDownloader.kt:69, PlayerMediaLoaderManager.kt:481 and :654 (verified preCacheNetworkAudio performs full downloadSmbFull/downloadSftpFull/downloadFtpFull into that handle), BackgroundMusicManager.kt:347/513, SearchLyricsUseCase.kt:198/242/274, NetworkFileManager.kt:258, NetworkPdfThumbnailLoader.kt:168. clearAll() runs only at session boundaries (MainActivity.kt:631 isFinishing, BrowseLifecycleSetupManager.kt:83 init, BrowseShutdownCoordinator.kt:80); no periodic sweep of unified_network_cache exists, and the 24h TTL in getCachedFile only deletes a file on re-access of that exact key. So during a long network-audio/browse session the 500MB ML-006 cap is genuinely never enforced and the directory grows unbounded until storage pressure. P1 per the unbounded-cache rung; fix is small (run eviction from the direct-download path or add a commit hook).

Evidence excerpt:

```
fun getCacheFile(path: String, size: Long): File {
    // Ensure cache directory exists before returning file reference
    if (!cacheDir.exists()) { cacheDir.mkdirs() }
    val cacheKey = generateCacheKey(path, size)
    return File(cacheDir, cacheKey)   // <-- no evictIfNeeded() on this write path
}
...
fun putFile(...): File { ... evictIfNeeded() ... }  // only enforcement point, called from 2 legacy sites
```

