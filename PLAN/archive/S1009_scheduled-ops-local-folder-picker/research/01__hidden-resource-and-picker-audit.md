# S1009 research - hidden-resource foundation, Room migration, scheduled-op picker + lifecycle

**Date:** 2026-07-15
**Method:** parallel code audit (Room migration + scheduled-op UI/lifecycle readers succeeded; the query-blast-radius reader failed on a tool cap, its scope is folded into the owner-gated §6.2 visibility question).

## Room migration recipe (isHidden on `resources`, v40 -> v41)

Current DB version **40** (`AppDatabase.kt:32`, `exportSchema=true`). No existing `isHidden`/`isSystem`/`is_hidden` column (the only `hidden` match, `showHiddenFiles`, is an unrelated dot-file display toggle). Exact steps:

1. New `data/local/db/Migration40To41.kt`, top-level `val MIGRATION_40_41 = object : Migration(40, 41) { ... }` running `db.execSQL("ALTER TABLE resources ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")`. Template: `Migration39To40.kt`. Boolean-NOT-NULL precedent: `MIGRATION_28_29` (`needs_sign_in`, `AppDatabase.kt:685-691`).
2. `AppDatabase.kt:32` - bump `version = 40` -> `41`.
3. `ResourceEntity.kt` - add `@ColumnInfo(name = "is_hidden", defaultValue = "0") val isHidden: Boolean = false` (mirrors `needs_sign_in` at `:101-102`). `defaultValue="0"` is MANDATORY for the schema-hash to match, else `IllegalStateException` at open.
4. `DatabaseModule.kt` - import `MIGRATION_40_41` and append it to `.addMigrations(...)` after `MIGRATION_39_40` (`:108`). No autoMigrations / no fallbackToDestructiveMigration.
5. `Models.kt` (MediaResource, fields end ~`:233-235`) - add `val isHidden: Boolean = false`.
6. `ResourceRepositoryImpl.kt` - add `isHidden = isHidden` to BOTH `toDomain()` (~`:516`) and `toEntity()` (~`:576`).
7. S0731: run a build to regenerate + commit `schemas/.../41.json`; update `AppDatabaseSchemaExportTest.kt:62 CURRENT_VERSION 36 -> 41` and add a 40->41 MigrationTestHelper case. NB: the guard is stale (v36 vs DB v40) - parked as **S1050**.

## Query filter blast radius (visibility scope)

Hidden resources must resolve by id (FK) but never render. Known list surfaces that DO NOT filter today:

- `GetResourcesUseCase.invoke()` -> `repository.getAllResources()` UNFILTERED (`:16`) - feeds main list + scheduled-op sender dropdown.
- `GetDestinationsUseCase` - filters isDestination/order/isReadOnly/virtual but NOT hidden (`:23,35`) - feeds receiver dropdown.
- `ResourceDao.getAllResources` (`:115-116`) / `getDestinations` (`:132-133`) - no hidden predicate.
- `ResourceDao.getResourceByLocalPath` `WHERE type=LOCAL AND path=:path` (`:129`) - available for de-dup lookup.

Still to enumerate (owner-gated, §6.2): browse home, Wear watch-sync, backup/export, search, resource count/limits. Recommended: a `getVisibleResources()` / `WHERE is_hidden=0` on list queries while `getResourceById` stays unfiltered for FK resolution.

## Scheduled-op UI, FK, and orphan lifecycle

- `ScheduledOperationDialog.kt` - sender = `actvSource` AutoCompleteTextView dropdown (`setupDropdowns :73-79`), receiver = `actvTarget` (`:96-101`), both over pre-loaded `MediaResource` lists (ctor `:32-33`); name->id resolve in `trySave :377-398`. Read-only source already forces COPY via `applyReadOnlySourceConstraint :275-287`. PRIMARY insertion point for the "Local folder" top option in both dropdowns.
- `ScheduledOperationEntity.kt` - FK `source_resource_id` (Long, non-null, `:39-40`), `target_resource_id` (Long?, nullable, `:45-46`), both -> `ResourceEntity.id` `onDelete=CASCADE` (`:13-24`). Cascade is resource->scheduled_op ONLY.
- `ScheduledOperationDao.deleteById` (`:31-32`) deletes only the operation row; `ScheduledOperationsViewModel.delete` (`:81-86`) never touches ResourceRepository -> **linked hidden resource is orphaned**. Same gap on edit-to-different-sender/receiver (upsert REPLACE) and clear-all (`OperationsScheduledManager.kt:109-119`).
- `OperationsScheduledManager.kt` - hosts the dialog (`:195-216`), feeds `viewModel.resources.value`/`destinations.value`. Fragment-scoped: the place to register a folder-picker `ActivityResultLauncher` (the dialog has no ActivityResult host).

## Reusable folder browser + writability

- `AddResourceScanManager` - in-app RecyclerView browser `showFolderBrowserDialog` (`:225`), SAF via `handleSelectedFolderUri` -> `takePersistableUriPermission(READ|WRITE)` (`:48-52`); Activity-bound to `AddResourceActivity.folderPickerLauncher = OpenDocumentTree()` (`:74-84`).
- `AddResourceVirtualCoordinator.addManualFolder` (`:101-182`) - LOCAL-resource construction template: `MediaResource(type=LOCAL, path, isWritable via scanner.isWritable withTimeout(5000))`.
- Writability primitive: `LocalMediaScanner.isWritable` (`:408-413`) - virtual=false; `content://`->`DocumentFile.canWrite`; plain path->`File.exists() && canWrite()`. Alternate reference: `BrowseFolderPickerHandler.onFolderPicked` (`:99-179`, `error_folder_not_writable` toast).

## Owner-gated decisions (see spec §6)

1. Orphan-cleanup policy (auto-delete vs leave; 1:1 vs shareable/reference-counted).
2. Visibility filter scope (which surfaces exclude hidden).
3. Creation timing (pick-time vs Save-time).
4. De-dup with an existing visible resource.
5. Folder-browser reuse/hosting (extract vs Fragment-hosted launcher).
6. Non-writable receiver UX + read-only sender force-COPY confirmation.
7. Storage model (is_hidden column vs link table).
