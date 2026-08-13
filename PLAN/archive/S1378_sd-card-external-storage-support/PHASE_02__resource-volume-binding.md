# Phase 02 - Resource-volume binding

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Persist which storage volume a resource lives on and derive resource availability from that volume's mounted state; no UI and no file-operation changes.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 830 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration44To45.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 255 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 385 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 660 |
| `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/45.json` | New (generated) | - |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration44To45Test.kt` | New | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImplTest.kt` | Modified | ≤ 200 |

> `AppDatabase.kt` (794 LOC) and `ResourceRepositoryImpl.kt` (612 LOC) cross the 500-LOC line - Step 02.1 backs both up before any edit.

> **Plan corrected during execution (2026-08-05).** Three file facts were wrong when this phase was written, and the codebase - not the plan - was followed:
> - Migrations are not declared in `AppDatabase.kt`. Since `MIGRATION_31_32` each one is a top-level `val` in its own `MigrationNToM.kt`, and registration lives in `core/di/DatabaseModule.kt`, not in `AppDatabase`. `AppDatabase.kt` carries only the `version =` bump.
> - Migration tests here are **instrumented**, not JVM. The precedent is `app_v2/src/androidTest/.../AppDatabaseMigration43To44Test.kt` using `MigrationTestHelper`, so the new test follows that name and location. It therefore cannot be run by `.\a.ps1 fu`, and its run is device-gated.
> - Bumping the version regenerates `45.json`, which is committed. `AppDatabaseSchemaExportTest` (a real JVM test) is what proves the bump and the export agree - that is the runnable evidence for Step 02.3 in CI.

---

## Steps

### Step 02.1 - Back up the two large files

**Files:** `temp/S1378/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `AppDatabase.kt` and `ResourceRepositoryImpl.kt` into `temp/S1378/` with a timestamped name before editing either.

**Why:**

not stated in strategic spec - CLAUDE.md Rule 5 requires a timestamped backup before editing any file above 500 LOC.

**Verification:**

- `Glob` - `temp/S1378/AppDatabase*.kt` and `temp/S1378/ResourceRepositoryImpl*.kt` both exist.

**Status:** `[x]` done - `temp/S1378/AppDatabase.kt.20260805-102800.bak` (40017 B) and `temp/S1378/ResourceRepositoryImpl.kt.20260805-102800.bak` (27804 B); expected: both exist | actual: both exist.

---

### Step 02.2 - Add the volume column to `ResourceEntity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a nullable `storage_volume_id` column with `defaultValue = "NULL"`, following the column style already used by `icon_id` and `access_note`. Null means "not bound to any volume" and is the value every existing row keeps.

**Why:**

Strategic §3.2 constrains data compatibility to pure addition - existing resources must keep opening without re-granting access, which a nullable column with a NULL default delivers.

**Verification:**

- `Grep` - `storage_volume_id` matches exactly once in `ResourceEntity.kt`.
- `Grep` - `defaultValue = "NULL"` present on that column.

**Status:** `[x]` done - expected: 1 match | actual: 1; `@ColumnInfo(name = "storage_volume_id", defaultValue = "NULL")` expected: present | actual: present.

---

### Step 02.3 - Bump the database version and add the migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration44To45.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`, `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration44To45Test.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Change the `@Database` version from 44 to 45, add `MIGRATION_44_45` in its own `Migration44To45.kt` performing a single `ALTER TABLE .. ADD COLUMN storage_volume_id TEXT DEFAULT NULL`, and register it in `DatabaseModule`'s `addMigrations` list. Do not touch any earlier migration. Commit the regenerated `45.json`. Add an instrumented migration test, per the `AppDatabaseMigration43To44Test` precedent, asserting that a v44 database carrying one resource row opens at v45 with the row intact and the new column null.

**Why:**

Strategic §3.2 states existing resources must survive the change untouched, and a migration test is the only evidence that the added column does not rewrite or drop existing rows.

**Verification:**

- `Grep` - `version = 45` matches exactly once in `AppDatabase.kt`.
- `Grep` - `MIGRATION_44_45` matches in both the declaration and the registration list.
- `app_v2/schemas/..AppDatabase/45.json` exists and its `"version"` reads 45.
- `check-standard-fast.ps1 -Mode Unit -Tests ..AppDatabaseSchemaExportTest` passes - the runnable proof that the bump and the committed export agree; record `expected: PASS | actual: <result>`.
- `AppDatabaseMigration44To45Test` is **device-gated** (instrumented): it cannot run in `fu`, so its execution is deferred to a connected device and reported as a manual item.

**Status:** `[x]` done - `version = 45` expected: 1 | actual: 1; `MIGRATION_44_45` expected: declaration + registration | actual: 3 hits across `Migration44To45.kt` and `DatabaseModule.kt`; `45.json` expected: exists with `"version": 45` | actual: exists (58005 B), regenerated by KSP on the compile; `AppDatabaseSchemaExportTest` expected: PASS | actual: PASS (exit 0). `AppDatabaseMigration44To45Test` written but **not executed** - no device attached this run; deferred to the manual list.

---

### Step 02.4 - Carry the binding through the domain model and mapping

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `storageVolumeId: String? = null` to `MediaResource` as the last constructor parameter, and map it in both directions where the repository converts between entity and domain model. Do not introduce a new `ResourceType` value - a resource on a removable volume stays `LOCAL`.

**Why:**

Strategic ADR-1 rules that a removable volume differs by access route and volume binding, not by resource type, because a separate type would duplicate the add form, validation and sort rules for zero user-visible difference.

**Verification:**

- `Grep` - `storageVolumeId` matches in `Models.kt` and at least twice in `ResourceRepositoryImpl.kt`.
- `Grep` - `REMOVABLE` returns zero hits in `Models.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `Models.kt` expected: >=1 | actual: 1; `ResourceRepositoryImpl.kt` expected: >=2 | actual: 4; `REMOVABLE` expected: 0 | actual: 0; `.\a.ps1 fk` expected: 0 | actual: 0.

---

### Step 02.5 - Derive availability from the bound volume

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> When the repository loads resources, mark a resource whose `storageVolumeId` names a volume that is absent or unmounted as unavailable through the existing `isAvailable` field. Leave resources with a null binding exactly as they are today. Keep the granted access itself untouched - an absent volume must never clear the binding.

**Why:**

Strategic §6 item 4 resolves that a missing volume moves the resource to "medium unavailable" without breaking the binding or forcing the user to re-grant access, and §2 goal 5 requires the ejected case to read as a clear state rather than an empty list.

**Verification:**

- `Grep` - `isAvailable` and `storageVolumeId` both appear inside the same loading function.
- `Grep` - `storageVolumeId = null` returns zero hits outside the mapping function, proving no code path clears the binding.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - both appear inside `applyVolumeAvailability` (the shared loading-path helper the five list loaders call); `storageVolumeId = null` expected: 0 | actual: 0; `.\a.ps1 fk` expected: 0 | actual: 0.

> Implemented as one private `applyVolumeAvailability` helper rather than inline in a single loader, and applied to **all five** list-returning loaders (`getAllResources`, `getAllResourcesSync`, `getResourcesByType`, `getDestinations`, `getFilteredResources`). Marking only the main list would have left the destination picker offering an ejected card as a copy target - the worse of the two failure modes this step exists to prevent. The helper returns the list untouched when no resource carries a binding, so the registry's `StatFs` enumeration is never paid for on an install that has no volume-bound resource - which is every install until Phase 06 ships the picker.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 db` expected: 0 | actual: 0 (`BUILD SUCCESSFUL`, APK `v2.60.8041.533-DEBUG`), re-run after the audit fix so the verdict covers the final state.
- [x] `Grep` for `TODO(phase-02)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added for the phase - written by `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2427 records (was 2426; `Migration44To45.kt` is the new class).
- [x] Phase-boundary audit run - one P1 found and fixed inside the phase, no unresolved P0/P1.

Closure: `post-change: PASS WITH ADVISORIES (1)`, exit 0. The single advisory is `detekt-preflight` reporting `MagicNumber` on `ResourceRepositoryImpl.kt:508-509`. Those two lines are the pre-existing media-type bitmask decode - byte-identical in the pre-phase backup - and surfaced only because this phase shifted the line numbers. The authoritative `assert-detekt` verdict is `PASS [scoped] - none among changed files`.

Unit evidence: `AppDatabaseSchemaExportTest` PASS, `ResourceRepositoryImplTest` PASS (12 tests including the four added here).

---

## Phase-boundary audit (2026-08-05)

Triggers fired: Room entity + migration change (Layer 4), DI change to a `@Singleton`'s constructor (Layer 1), a new suspending transform on four `Flow` loaders (Layer 2/5).

**P1 - derived availability leaked into storage. Found and fixed inside this phase.**

Step 02.5 marks a volume-bound resource unavailable by rewriting the *persisted* `isAvailable` field on the loaded domain object. Nineteen call sites follow the pattern `repository.updateResource(resource.copy(<one unrelated field>))` on an object that came from exactly those loaders - `AppStartupInitializer` (`isWritable`), `MigrateS0059UseCase` (`allFiles`), `PlayerNavigationCoordinator` (`lastViewedFile`), `AddResourceUseCase` (`displayOrder`), and others. With the card out, any one of them would have written the derived `false` straight to the row, and nothing anywhere sets the stored flag back to `true` - so a single eject would have marked the resource dead permanently, defeating the very §6-item-4 requirement this step cites.

Fix: `ResourceEntity.withStoredAvailabilityIfVolumeBound()` on the write path. A volume-bound resource keeps whatever `isAvailable` is on disk; an unbound resource still authors the field exactly as before, so no existing behaviour moves. Covered by `updateResource never persists the derived unavailability of a volume-bound resource`.

Cleared, no action needed:

- **Main safety (Layer 5).** `applyVolumeAvailability` suspends on `StorageVolumeRepository.getVolumes()`, which the loaders may reach while collected on Main. `StorageVolumeRepositoryImpl` pins every platform call to `Dispatchers.IO` itself (its KDoc says why: `StatFs` can stall on a dying card), so no disk work lands on the UI thread.
- **Room (Layer 4).** Additive nullable column, one `ALTER TABLE`, no table recreation, no destructive fallback (`DatabaseModule` deliberately has none), migration test written. No new query, no main-thread access.
- **DI (Layer 1).** No new scope or qualifier - `StorageVolumeRepository` was already bound in `RepositoryModule` by Phase 01; this is `@Inject constructor` wiring only. Proven by a full `standard debug` build, which is what actually validates the Hilt graph.

The first closure attempt failed the scoped detekt gate with five findings, all worth recording because two of them are traps rather than sloppiness:

- `LongParameterList` on `createMediaResource`. The factory already had 44 parameters and its baseline entry covered it - but adding a 45th **changes the signature the baseline entry is keyed on**, so the suppression stopped matching and the whole pre-existing violation resurfaced as new. Resolution: leave the factory alone and set the field with `.copy()` in the test instead. Any future phase that wants a new field on this factory hits the same wall.
- `ImportOrdering` on `ResourceRepositoryImpl` - the `com.sza.fastmediasorter.utils.*` block sat after `timber`. Shadow debt that only surfaced because the file entered the changed set, the same mechanism S1371 hit in five consecutive packages.
- `MagicNumber` ×2 on `Migration(44, 45)` - the identical literal in `Migration43To44` is baselined, so a new migration file cannot copy the existing style without failing. Resolved with named constants rather than a baseline entry, per Rule 19's detekt-clean-first.
- `ReturnCount` on the new write-path guard - split into a single conditional return.

---

## Handoff Notes to Next Phase

Schema is at version 45. `MediaResource.storageVolumeId` is the single signal telling any consumer that a resource lives on a removable volume - Phase 06 renders its icon from this field, never from the path shape.

Two invariants Phase 06 must not undo:

- `isAvailable` is **derived** for a bound resource and deliberately never written back. Do not "fix" the picker by persisting availability when the volume disappears - the write path drops it on purpose.
- The availability pass short-circuits when no resource carries a binding, which is why it costs nothing today. Phase 06 is what makes bindings exist, and from then on every list load enumerates volumes once. Read the volume list once per picker opening, as Phase 01's handoff already warned, and do not add a second per-row call on top of this one.

---

## Rollback Plan

Reverting requires care: the schema version is user-visible. Revert the phase commit and, on a device already migrated to v45, clear app data or restore from the pre-phase backup in `temp/S1378/` - do not hand-edit the version back without also removing the migration.
