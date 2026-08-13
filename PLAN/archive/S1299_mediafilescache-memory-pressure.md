# S1299 - MediaFilesCacheManager: 128MB cap not scaled to device heap, never trimmed on memory pressure, size accounting drifts on in-place mutation

**Ticket:** S1299
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): caches-2.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: MediaFilesCacheManager: fixed 128MB singleton cache never released on memory pressure, size accounting drifts on in-place mutation

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/MediaFilesCacheManager.kt:19`
- Symptom: Process-lifetime object singleton with a hard-coded CACHE_SIZE_BYTES = 128MB not scaled to Runtime.maxMemory() - on low-RAM devices (heap 96-192MB) the cap alone exceeds or dominates the whole Java heap. clearAllCaches() is documented 'e.g. on app logout or memory pressure' but its only caller is the manual settings button (GeneralSettingsCacheHelper.kt:131); FastMediaSorterApp.onTrimMemory (lines 493-534) clears only Glide and temp files, so even TRIM_MEMORY_RUNNING_CRITICAL leaves the full cache resident, including while backgrounded. Additionally addFile() (line 140-144), updateFile() and removeFile() mutate the stored MutableList in place without re-put(), so LruCache's internal size accounting (sizeOf = value.size * 500, computed at put time) goes stale over long sessions - files added via move-in are never charged against the 128MB budget. The 500-byte/file estimate also undercounts cloud entries (path + contentUri + thumbnailUrl + webViewUrl + cloudDisplayPath + cloudItemId strings), letting real retention exceed the nominal cap severalfold.
- Failure scenario: User on a 2-3GB device (heap ~192MB) opens several large SMB/cloud resources (30-50k files each) during a multi-hour sorting session: the singleton accumulates 15-25MB+ of MediaFile lists per resource (more for cloud entries, uncharged extras from addFile), holds them for the entire process lifetime including in background, and never releases them under TRIM_MEMORY_RUNNING_CRITICAL. Combined with player/Glide bitmap load the app hits OutOfMemoryError in the player, or is repeatedly LMK-killed in background, losing session state every time the user switches apps.
- Fix sketch: Scale the cap to the device heap (e.g. min(128MB, maxMemory/8)); register clearing (full clearAllCaches or trimToSize to a fraction) in FastMediaSorterApp.onTrimMemory for RUNNING_CRITICAL/COMPLETE; after every in-place list mutation re-put the list so LruCache re-runs sizeOf and its byte accounting stays honest.
- Verifier rationale: All sub-claims verified. Line 19: CACHE_SIZE_BYTES = 128MB hard-coded, no Runtime.maxMemory() scaling, so on low-RAM devices (heap 96-192MB, supported via legacy flavor minSdk 23) the nominal cap rivals or exceeds the entire Java heap. clearAllCaches() has exactly one caller (GeneralSettingsCacheHelper.kt:131, manual settings button); FastMediaSorterApp.onTrimMemory (lines 493-534) clears only Glide memory cache and temp files even at TRIM_MEMORY_RUNNING_CRITICAL/COMPLETE, so the documented 'memory pressure' clearing is unimplemented and the full cache stays resident including in background. addFile (lines 140-144, caller TextViewerManager.kt:888) mutates the stored MutableList in place with no re-put, so LruCache's sizeOf accounting (value.size * 500, charged at put time) undercounts over time. MediaFile (Models.kt:305-335) carries ~15 nullable string/metadata fields; cloud entries plausibly exceed the 500-byte estimate severalfold, letting real retention exceed the nominal cap. The cache is nominally bounded (LRU evicts at 128MB accounted), so the OOM scenario needs large datasets, but multi-10k-file network/cloud resources are within this app's design scope, making the OOM/LMK contribution on low-RAM devices genuinely reachable - P1 (unreleased heavy in-memory resource under trim). Effort small, not medium: heap-scaled cap, an onTrimMemory hook, and re-put after in-place mutation are three localized changes.

Evidence excerpt:

```
private const val CACHE_SIZE_BYTES = 128 * 1024 * 1024
...
override fun sizeOf(key: Long, value: MutableList<MediaFile>): Int { return value.size * 500 }
...
fun addFile(resourceId: Long, file: MediaFile) = synchronized(lock) {
    val list = cache.get(resourceId) ?: mutableListOf<MediaFile>().also { cache.put(resourceId, it) }
    list.add(file)   // in-place: LruCache size accounting never updated
}
```

