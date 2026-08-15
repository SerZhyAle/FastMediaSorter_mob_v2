# S1307 - CachedFileListRepository blob patched per-file in loops with unsynchronized read-modify-write

**Ticket:** S1307
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): room-datastore-3.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: CachedFileListRepository blob patched per-file in loops with unsynchronized read-modify-write

- Severity: P2, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/CachedFileListRepository.kt:126`
- Symptom: deleteFile()/updateFile() patch the single-row GZIP+JSON snapshot by decompressing the WHOLE file list, parsing it with Gson, mutating one entry, re-serializing and re-gzipping the whole list, then insertOrReplace. BrowseFileListMutationManager calls it in per-path loops (removeFiles line 59-61 and removeFilesFromList line 122-126: pathsSet.forEach { cachedFileListRepository.deleteFile(resource.id, path) }), so a batch delete of B files from an N-file remembered list costs B full decompress/parse/serialize/compress cycles (O(B*N)). There is also no mutex or DB transaction around the read-modify-write: the browse-manager coroutine (scope.launch(ioDispatcher)) and PlayerMediaFilesLoader's auto-clean (PlayerMediaFilesLoader.kt line 531) can interleave on the same resourceId - both read the same snapshot, each removes its own path, and the last insertOrReplace resurrects the other coroutine's deleted entry.
- Failure scenario: User enables rememberFileList on a 20k-file network share and multi-selects 300 files to delete/move: the IO dispatcher spends minutes doing 300 full gunzip+Gson-parse+re-gzip cycles of a multi-MB 20k-entry list (battery drain, delayed cache consistency, GC churn from 300 transient 20k-element lists). If the player's missing-file auto-clean fires while such a batch is running on the same resource, the lost-update race re-inserts already-deleted paths into the snapshot, so deleted files reappear in the remembered list on next open.
- Fix sketch: Add batch APIs deleteFiles(resourceId, paths: Collection<String>) and route both forEach call sites through them (one decompress/recompress per batch). Guard all read-modify-write methods with a per-repository (or per-resourceId) kotlinx Mutex so concurrent patchers serialize instead of clobbering each other.
- Verifier rationale: Verified: deleteFile/updateFile each do full decompress + Gson parse + re-serialize + re-gzip + insertOrReplace with no Mutex and no Room transaction around the read-modify-write; BrowseFileListMutationManager lines 59-61 and 122-126 loop deleteFile per path (O(B*N) for a B-file batch on an N-entry snapshot), and PlayerMediaFilesLoader line 531 calls deleteFile on the same singleton repository from a separate coroutine, so the lost-update interleaving on the same resourceId is reachable when a player and browse window share a resource. P2 not P1: the race corrupts only an advisory cache snapshot (a deleted path can reappear and is auto-cleaned again on next encounter), no user-data loss; the dominant cost is IO/GC churn on large remembered lists. Batch API plus a per-repository Mutex is a small change.

Evidence excerpt:

```
suspend fun deleteFile(resourceId: Long, filePath: String) {
    ...
    val entity = cachedFileListDao.getByResourceId(resourceId) ?: return
    val files: MutableList<MediaFile> = gson.fromJson(decompress(entity.compressedData), mediaFileListType)
    val removed = files.removeIf { it.path == filePath }
    ...
    cachedFileListDao.insertOrReplace(entity.copy(compressedData = compressed, fileCount = files.size))
```

