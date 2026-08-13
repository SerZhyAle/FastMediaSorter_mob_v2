# S1012 research - companion import flow, dedup feasibility, toast/strings, blast radius

**Date:** 2026-07-15
**Method:** parallel code audit (4 readers) over resource model, companion import, entry points/strings, and `addMultiple` blast radius.

## Entry points and surfaces

- Two in-app import entries converge on `ImportCompanionConfigUseCase`:
  - QR/barcode: `AddResourceActivity` buttons -> `CompanionQrScanActivity` -> `EXTRA_PAYLOAD` -> `AddResourceViewModel.importCompanionConfigFromQr` -> `AddResourceCompanionCoordinator.importFromPayload` -> `useCase.importFromPayload`.
  - File (SAF `OpenDocument`, `*/*` - `.fmscfg` has no registered MIME): `AddResourceViewModel.importCompanionConfig(uri)` -> `AddResourceCompanionCoordinator.importFromUri` -> `useCase.invoke(uri)`.
- Both surface the outcome via `AddResourceCompanionCoordinator.emitOutcome` (~:41-56) -> `AddResourceEvent.ShowMessage` -> `Toast.makeText(..., LENGTH_SHORT)` at `AddResourceActivity.kt:419`. This is the single natural place to build the S1012 summary; no new toast plumbing needed.
- Third (external attachment `.fmscfg`) entry: `CompanionConfigImportActivity.runImport` -> `showResultAndFinish` shows an `AlertDialog`, NOT a toast. Out of the literal "тост" scope unless the owner extends it.

## Resource identity and dedup feasibility

- `MediaResource` (Models.kt:186-235) has NO `host`/`virtualPath` fields. Identity is a single composite `path: String`.
- SFTP/companion path = `SftpPathUtils.buildSftpPath(host, virtualPath, port)` = `sftp://host[:port]/virtualPath` (SftpPathUtils.kt:85-96), assembled in `ImportCompanionConfigUseCase.buildResource` (:222). Port omitted when 22. So host+virtualPath is embedded in `path`; path-equality among SFTP rows faithfully implements the owner's `host + virtualPath` key. `SftpPathUtils.parseSftpPath` (:26-75) recovers host/port/remotePath if a port-agnostic key is wanted.
- No DB uniqueness: `ResourceEntity` indices (:11-18) are all non-unique, none on `path`; PK is autogenerate `id` (:21-22). `ResourceDao.insert` is `@Insert(onConflict=REPLACE)` on PK only (:14-15) - with `id=0` REPLACE never fires. => re-import CREATES duplicates today.
- No query by host+virtualPath (or network-by-path). Only `getLocalResourceByPathSync` (ResourceDao.kt:129-130), LOCAL-only.
- Add-or-update is feasible with NO schema/DAO change: `AddResourceUseCase.addMultiple` already loads the full existing list via `repository.getAllResources().first()` (:58). Match in memory (filter SFTP, compare normalized path), then `updateResource(merged)` vs `addResource`.

## Clobber hazard (must be a selective merge)

- `repository.updateResource` is a full-row `@Update` overwriting every column (ResourceRepositoryImpl.kt:256-261; ResourceDao.kt:80-84).
- `buildResource` creates the resource with `id=0` and DEFAULT user-owned fields. Passing it to `updateResource` would wipe: iconId, sortMode, displayMode, lastViewedFile/lastScrollPosition, fileCount/subfolderCount/lastBrowseDate/lastSyncDate, displayOrder, destinationColor/destinationOrder.
- Documented pattern: S1001 note on `ResourceRepository` single-column writers (:69-77) exists precisely because a full `updateResource` from a fresh/stale copy clobbers concurrently-written stats. The S1012 update path MUST build `existing.copy(...config-authoritative only...)`.
- Config-authoritative fields set by `buildResource`: path/endpoints, credentialsId, hostKeyFingerprint, altAccessPaths (S1006), accessNote (S1014), supportedMediaTypes, allFiles, scanSubdirectories, showSubfoldersAsItems, showHiddenFiles, isReadOnly (S1016), isDestination, comment/accessPin/slideshowInterval/destinationColor only when the config carries them (ImportCompanionConfigUseCase.kt:244-247).

## Blast radius (opt-in, not global)

- `addMultiple` has 10 call sites across 9 files; only ONE (`ImportCompanionConfigUseCase.kt:170`) is the owner-scoped companion path.
- Callers reading `addResult.addedCount` for a count toast (would visibly regress under global dedup): `AddResourceViewModel.kt:409-423` (`R.plurals.added_n_resources`), `AddResourceSmbCoordinator.kt:170-205`.
- Manual SMB/SFTP/FTP coordinators have NO dedup guard - global dedup would silently turn re-add into update (the exact regression to avoid).
- Cloud pickers already dedup on `cloudProvider + cloudFolderId` (not host+virtualPath) and short-circuit before `addMultiple`.
- 4 tests (`AddResourceUseCaseTest.kt:65,81,98,121`) assert current insert-only semantics; a global change forces edits, an opt-in path only adds tests.
- Recommendation: OPT-IN - new param (`matchExistingByPath: Boolean = false`, mirrors `addToTop`) or dedicated method, called only from the companion path; add additive `updatedCount: Int = 0` to `AddMultipleResult`.

## Result models and strings

- `AddMultipleResult` (AddResourceUseCase.kt:13-17): addedCount/destinationsFull/skippedDestinations - no updatedCount. `ImportCompanionConfigUseCase` DISCARDS it (:171) and rebuilds `CompanionImportResult(resourceNames, host, port)` (:28-32) from `resources.map { it.name }`.
- `resourceNames.size` gives the total; when size==1, `resourceNames.first()` gives the single name - both already available at every surfacing site.
- Reference for true added-vs-updated: `SzaResourcesImporter` (.fmsr importer) matches by path (:328), updates existing (:330-339) vs inserts new, returns `ImportResult.Success(imported, updated, skipped)` (:51); `ResourceImportActivity.kt:74-80` surfaces the 3 counts.
- Strings: reuse `added_n_resources` (strings.xml:1490); add `updated_n_resources` plural; add a joiner string for the both-nonzero case; add single-name variants. EN one/other, RU/UK one/few/many/other (values-ru/strings.xml:1291). Read via `getQuantityString(R.plurals.<key>, count, count)` (pattern at AddResourceViewModel.kt:410).

## Open decisions (owner-gated) - see spec §6

1. Update field-merge policy (config-authoritative vs preserved) - data-loss risk; default = SzaResourcesImporter precedent, needs confirmation.
2. isDestination/isReadOnly transitions + destination re-slotting/color on update of a customized resource.
3. Match-key: ignore port (host+virtualPath) vs path-equality (port-sensitive); virtualPath normalization.
4. "updated" = any match, or only when a field actually changed.
5. "single -> name": total==1 vs per-bucket.
6. External attachment path (dialog): include the same summary or keep in-app toast only.
