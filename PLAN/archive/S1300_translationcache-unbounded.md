# S1300 - TranslationCacheManager grows unbounded for the whole process lifetime (no cap, no eviction, no memory-pressure trim)

**Ticket:** S1300
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): caches-3, singletons-2.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Findings caches-3/singletons-2 describe the same defect from two dimensions.

## Finding 1: TranslationCacheManager grows unbounded for the whole process lifetime (no cap, no eviction, no trim on memory pressure)

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt:23`
- Symptom: Global object singleton holding two ConcurrentHashMaps keyed by file path: per-page translated text (cache) and per-page lists of TranslatedTextBlock (lensCache - originalText + translatedText + Rect per block, TranslationManager.kt:79-84). There is no size cap and no eviction of any kind; the only clears are app startup (FastMediaSorterApp.kt:275, after first frame) and the manual settings 'Clear cache' button (GeneralSettingsCacheHelper.kt:129). The class doc explicitly says it is NOT cleared when switching files. onTrimMemory never touches it.
- Failure scenario: User keeps the app running for days (the app is designed for long-lived sessions) and reads/translates scanned PDFs page-by-page with Lens-style translation: every translated page of every document ever opened stays in the singleton - a few KB of text plus dozens of TranslatedTextBlock strings+Rects per page. After hundreds/thousands of translated pages across many documents this is tens of MB of Java heap that is never evicted while the process lives, adding steady memory pressure on top of bitmap load and contributing to background kills on low-RAM devices.
- Fix sketch: Replace the outer maps with an access-ordered LinkedHashMap capped at N most-recently-used files (or a byte-budget eviction like StreamFrameCache), and clear/trim the cache from FastMediaSorterApp.onTrimMemory on RUNNING_CRITICAL/COMPLETE.
- Verifier rationale: Confirmed. TranslationCacheManager is a process-lifetime object singleton holding two ConcurrentHashMaps (per-page translated text plus lensCache of TranslatedTextBlock lists) with no size cap and no eviction of any kind. Grep for clearAll() callers shows exactly two: FastMediaSorterApp.kt:275 (app startup, after first frame) and GeneralSettingsCacheHelper.kt:129 (manual 'Clear cache' button); FastMediaSorterApp.onTrimMemory (493-534) never touches it, and the class KDoc explicitly states it is NOT cleared when switching files. So every translated page of every document opened during the process lifetime is retained. The growth is real but slow and strictly user-driven (a few KB of text plus block strings/Rects per translated page), reaching tens of MB only after hundreds to thousands of translated pages over a days-long process - a steady memory-pressure contributor rather than an independent crash path. P2 (missing memory-pressure awareness / uncapped but slow-growing cache), small fix (MRU cap on files plus an onTrimMemory clear).

Evidence excerpt:

```
private val cache = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
private val lensCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<...TranslatedTextBlock>>>()
// Cleared on: App startup, 'Clear cache' button; NOT cleared when switching between files
```

## Finding 2: TranslationCacheManager is an unbounded process-lifetime cache with no eviction and no memory-pressure trim

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/TranslationCacheManager.kt:23`
- Symptom: Static object accumulates one entry per translated page per file in two maps (full page text in `cache`, plus per-block originalText/translatedText/Rect lists in `lensCache`) and never evicts: clearAll() runs only at app startup (FastMediaSorterApp.kt:275) and from the manual settings 'Clear cache' button (GeneralSettingsCacheHelper.kt:129). FastMediaSorterApp.onTrimMemory (line 478+) clears Glide and temp files but does not touch this cache, so the OS cannot reclaim it under pressure.
- Failure scenario: User keeps the app process alive for days (Android rarely kills a media app with foreground playback) and reads/translates PDFs with page translation or Lens overlay enabled: every viewed page adds its full source text, translated text, and per-block string pairs to both maps (PdfTranslationCoordinator.kt:85,118). A few thousand translated pages across documents grows the heap by tens of MB that survive closing every screen and are immune to onTrimMemory, raising OOM-kill probability on low-RAM devices during later video playback or thumbnail-heavy browsing.
- Fix sketch: Bound both maps - e.g. an LRU over file keys (keep the N most recently read files, or a total-character budget) evicting whole per-file page maps - and clear the cache from FastMediaSorterApp.onTrimMemory at TRIM_MEMORY_RUNNING_LOW/COMPLETE alongside the existing Glide clear; translations are re-derivable so eviction only costs a re-translate.
- Verifier rationale: Confirmed. Both ConcurrentHashMaps have no eviction API whatsoever; exhaustive grep shows clearAll() is called only from FastMediaSorterApp.kt:275 (startup, after first frame) and GeneralSettingsCacheHelper.kt:129 (manual settings button). FastMediaSorterApp.onTrimMemory (lines 478-512) clears temp files but never this cache, so it is immune to memory pressure. PdfTranslationCoordinator.kt:85,118 adds full page text plus per-block string pairs per translated page, growing for the whole process lifetime. Rated P2 rather than P1 because growth is paced by explicit user translation actions (each entry costs a translation pass), payload is KB-scale text per page, data is re-derivable, and realistic accumulation reaches tens of MB only after thousands of translated pages in one long-lived process - real but slow-burn; fix (LRU over file keys plus an onTrimMemory hook) is small.

Evidence excerpt:

```
object TranslationCacheManager {
    // Translation cache: fileName -> (pageIndex -> translated text)
    private val cache = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
    // Lens style cache: fileName -> (pageIndex -> list of translated blocks)
    private val lensCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<...TranslatedTextBlock>>>()
(no remove/evict call exists; TranslatedTextBlock = originalText + translatedText + boundingBox + confidence per block)
```

