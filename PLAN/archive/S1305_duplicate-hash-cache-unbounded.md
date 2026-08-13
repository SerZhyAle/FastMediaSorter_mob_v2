# S1305 - duplicate_hash_cache table grows unbounded - no TTL, no orphan sweep, excluded from Clear cache

**Ticket:** S1305
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): room-datastore-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: duplicate_hash_cache grows unbounded - no TTL, no orphan sweep, excluded from Clear cache

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/DuplicateHashCacheDao.kt:9`
- Symptom: The duplicate-scan hash cache table only ever gains rows. The unique index is (resourceId, filePath, lastModified, fileSize), so every time a file is modified (new lastModified) a NEW row is inserted while the old row stays forever; rows for files deleted outside the Duplicates screen also stay. No pruning exists anywhere: DuplicateHashCacheDao has no cachedAt-based DELETE despite the cachedAt index created for it (entity comment: 'epoch ms - for future TTL cleanup'), OrphanCleanupWorker does not touch this table, DuplicateHashRepository.deleteByResourceId has zero callers in src/main, the entity declares no ForeignKey so resource deletion does not cascade, and GeneralSettingsCacheHelper.clearCache() (lines 106-169) clears every other cache but not this one.
- Failure scenario: User runs the duplicate finder weekly over a camera/download folder tree whose files churn (edits, re-downloads, renames change lastModified). Each scan inserts fresh rows for every changed file while stale variants of the same path accumulate; after months the table holds hundreds of thousands of dead rows, the app database file grows by tens of MB that no user action (including Settings > Clear cache) can reclaim, and every scan's per-file getByKey lookups slow down as the table and its four indices bloat.
- Fix sketch: Add a TTL query to DuplicateHashCacheDao (DELETE FROM duplicate_hash_cache WHERE cachedAt < :cutoff, e.g. 90 days) and call it from OrphanCleanupWorker next to fileMetadataCacheDao.deleteExpired; on upsert in DuplicateHashRepositoryImpl also delete stale rows for the same (resourceId, filePath) with a different lastModified/fileSize. Optionally wire dao.deleteAll into the Clear cache flow.
- Verifier rationale: Verified by reading DuplicateHashCacheDao, DuplicateHashRepositoryImpl, OrphanCleanupWorker, and GeneralSettingsCacheHelper. No cachedAt-based DELETE exists despite the cachedAt index; OrphanCleanupWorker cleans cached_file_lists/file_metadata_cache/audio metadata but never duplicate_hash_cache; deleteByResourceId has zero callers in src/main; entity has no ForeignKey; Clear cache flow omits this table. Unique key (resourceId, filePath, lastModified, fileSize) means every file modification strands the prior row; the only deletion path (deleteHashEntry in DuplicatesViewModel) fires only for files deleted inside the Duplicates screen. Growth is genuinely unbounded. P2 not P1: on-disk DB rows (small, growth proportional to scan activity), no runtime memory impact, slow degradation rather than OOM/crash. Fix is a TTL DELETE wired into OrphanCleanupWorker plus optional Clear-cache hook - small.

Evidence excerpt:

```
val quickHash: String?,  // MD5 of first 4 KB; null if not yet computed
    val fullHash: String?,   // MD5 of full file; null if not yet computed
    val cachedAt: Long       // epoch ms - for future TTL cleanup
```

