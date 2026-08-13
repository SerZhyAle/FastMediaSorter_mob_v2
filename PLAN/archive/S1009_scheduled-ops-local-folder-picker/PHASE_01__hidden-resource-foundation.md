# Phase 01 - Hidden-resource foundation

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 7 / 7
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Steps 01.1-01.7 verification PASS. `is_hidden` on entity+domain, mapped both directions, `MIGRATION_43_44` registered, `AppDatabase` version 44. Build: BUILD SUCCESSFUL (2m21s), `44.json` exported with `is_hidden` defaultValue "0". Migration test (androidTest) added; androidTest schema-assets srcDir wired in `build.gradle.kts`. Phase-boundary audit (Room/Layer1): additive migration, no table recreation, mapping mirrors `needsSignIn` - no P0/P1.

---

## Objective

Add an `is_hidden` boolean to the resource entity and domain model with a Room `43 -> 44` migration and entity<->domain mapping; export schema `44.json`. No visibility filtering, picker, or cleanup yet - this phase only establishes the flag that later phases read.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Live `AppDatabase` is `@Database(version = 43)` (verified: migrations wired through `MIGRATION_42_43`, schema exports through `43.json`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration43To44.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 200 |
| `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/44.json` | New (generated) | - |

> `ResourceRepositoryImpl.kt` and `AppDatabase.kt` are large; both edits here are single-line additions. Backup >500 LOC files under `temp/S1009/` before editing if the mapping edit is non-trivial.

---

## Steps

### Step 01.1 - Add `is_hidden` column to ResourceEntity

**Files:** `data/local/db/ResourceEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> As the last field of the `ResourceEntity` data class (immediately after `accessNote`, add a trailing comma to it), add the hidden flag mirroring the existing `needs_sign_in` precedent:
> `@ColumnInfo(name = "is_hidden", defaultValue = "0")` on its own line, then `val isHidden: Boolean = false` with a one-line comment stating: S1009 - ad-hoc local-folder resource hidden from visible surfaces; FK still resolves. `defaultValue = "0"` is MANDATORY so the exported schema hash matches (else `IllegalStateException` at DB open).

**Verification:**

- `Grep` - `name = "is_hidden"` matches exactly once in `ResourceEntity.kt`.
- `Grep` - `val isHidden: Boolean = false` present.
- `Grep` - `defaultValue = "0"` appears on the line above `val isHidden`.

**Status:** `[x]` done

---

### Step 01.2 - Add `isHidden` to MediaResource domain model

**Files:** `domain/model/Models.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `MediaResource` data class, after the last field `accessNote` (add a trailing comma to it), add `val isHidden: Boolean = false` with a one-line comment: S1009 - resource hidden from visible surfaces (FK-only). Keep it a plain domain field (no Room annotations - this is the domain model, not the entity).

**Verification:**

- `Grep` - `val isHidden: Boolean = false` matches once in `Models.kt`.
- `Grep` - the `isHidden` line sits inside the `data class MediaResource(` block (after `accessNote`).

**Status:** `[x]` done

---

### Step 01.3 - Map `isHidden` in the entity<->domain mappers

**Files:** `data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `ResourceEntity.toDomain()` (the `MediaResource(` constructor call), add `isHidden = isHidden,` next to the existing `needsSignIn = needsSignIn,` line. In `MediaResource.toEntity()` (the `ResourceEntity(` constructor call), add `isHidden = isHidden,` next to its `needsSignIn = needsSignIn,` line. Mirror the `needsSignIn` pattern exactly - no transform, straight copy in both directions.

**Verification:**

- `Grep -n` - `isHidden = isHidden` matches exactly twice in `ResourceRepositoryImpl.kt` (once per mapper).
- `Grep` - both occurrences are adjacent to a `needsSignIn = needsSignIn` line.

**Status:** `[x]` done

---

### Step 01.4 - Create the 43 -> 44 migration

**Files:** `data/local/db/Migration43To44.kt` (New)
**Depends on:** - independent of 01.1-01.3

**Prompt for developer:**

> Create `Migration43To44.kt` in `data/local/db/`, modelled on `Migration42To43.kt`: a top-level `val MIGRATION_43_44 = object : Migration(43, 44) { override fun migrate(db: SupportSQLiteDatabase) { ... } }`. Body: a single `db.execSQL("ALTER TABLE resources ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")`. KDoc: S1009 - additive boolean column marking a resource hidden from visible surfaces; existing rows default to 0 (visible). Additive + NOT NULL DEFAULT, so no table recreation. Boolean-NOT-NULL precedent: `MIGRATION_28_29` (`needs_sign_in`).

**Verification:**

- `Glob` - `data/local/db/Migration43To44.kt` exists.
- `Grep` - `val MIGRATION_43_44 = object : Migration(43, 44)` matches once.
- `Grep` - `ALTER TABLE resources ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0` present.

**Status:** `[x]` done

---

### Step 01.5 - Bump DB version and register the migration

**Files:** `data/local/db/AppDatabase.kt`, `core/di/DatabaseModule.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `AppDatabase.kt` change `version = 43` to `version = 44` in the `@Database(...)` annotation (leave `exportSchema = true`). In `DatabaseModule.kt` add `import com.sza.fastmediasorter.data.local.db.MIGRATION_43_44` next to the other migration imports, and append `MIGRATION_43_44` to the `.addMigrations(...)` list after `MIGRATION_42_43` (add a comma after `MIGRATION_42_43`). Do not add `fallbackToDestructiveMigration`.

**Verification:**

- `Grep` - `version = 44` matches once in `AppDatabase.kt`.
- `Grep` - `import com.sza.fastmediasorter.data.local.db.MIGRATION_43_44` present in `DatabaseModule.kt`.
- `Grep` - `MIGRATION_43_44` appears in the `.addMigrations(` block after `MIGRATION_42_43`.

**Status:** `[x]` done

---

### Step 01.6 - Export and commit schema 44.json

**Files:** `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/44.json` (generated)
**Depends on:** Step 01.5

**Prompt for developer:**

> Build standard debug (via `/build`) so Room's `exportSchema = true` writes `44.json` under `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/`. Commit the generated file. The schema-export guard `AppDatabaseSchemaExportTest` (S1050) now parses `@Database(version)` straight from source, so no hardcoded constant needs bumping - the guard passes once `44.json` matches version 44.

**Verification:**

- `Glob` - `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/44.json` exists.
- `Grep` - `"version": 44` present in `44.json`.
- `Grep` - the `44.json` `resources` table entity contains an `is_hidden` field definition.

**Status:** `[x]` done

---

### Step 01.7 - Instrumented migration test 43 -> 44

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration43To44Test.kt` (New)
**Depends on:** Step 01.6

**Prompt for developer:**

> Add an instrumented `MigrationTestHelper` test that creates the DB at version 43, runs `MIGRATION_43_44`, and asserts the `resources` table gained an `is_hidden` column defaulting to 0 for a pre-existing row. Follow the going-forward convention noted in `AppDatabaseSchemaExportTest` KDoc (each version bump adds an (N-1)->N test). This is an `androidTest` (device-gated) - it validates migration correctness, not merely schema export. Structural verification below is build-independent; actual execution runs on device (prerelease sweep).

**Verification:**

- `Glob` - `AppDatabaseMigration43To44Test.kt` exists under `src/androidTest/`.
- `Grep` - `MIGRATION_43_44` referenced in the test.
- `Grep` - an assertion on `is_hidden` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `44.json` committed and `AppDatabaseSchemaExportTest` passes (JVM unit test).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every modified source file via `.\scripts\add_to_dev_log.ps1` (batch through `close-and-log.ps1 -DevLogs`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `Migration43To44.kt` class) via `scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 (focus: migration additive + default-0, schema hash matches).

---

## Handoff Notes to Next Phase

`isHidden` now exists on both `ResourceEntity` and `MediaResource` and round-trips through the mappers, defaulting to `false`/`0`. Nothing filters on it yet - every resource still renders everywhere. Phase 02 adds the visibility filter; Phase 03 sets `isHidden = true` when persisting an ad-hoc local folder.

---

## Rollback Plan

Revert the phase commit(s) and delete `44.json`. No data loss: the column is additive with a default; a rolled-back build simply never reads it. Do not hand-edit prior migrations.
