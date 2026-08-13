# Phase 01 - Resource alternate access paths (data foundation)

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Give a resource an additive, nullable ordered list of alternate SFTP access paths in both the Room entity and the domain model, with a forward-only migration; no resolver, import, or connection change yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Current Room `@Database(version = 38)` confirmed in `AppDatabase.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration38To39.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | backup first (>500 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | backup first (>500 LOC not expected but confirm) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | backup first (>500 LOC) |

> Backup any file >500 LOC to `temp/S1006/` before editing (CLAUDE.md Rule 5).

---

## Steps

### Step 01.1 - Add the `alt_access_paths` column to ResourceEntity

**Files:** `data/local/db/ResourceEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a nullable column `@ColumnInfo(name = "alt_access_paths", defaultValue = "NULL") val altAccessPaths: String? = null` to `ResourceEntity`. It stores the ordered alternate SFTP endpoints as a compact string (JSON array of `{host,port}` or `host:port;host:port` - pick one and keep it internal to the mappers). `null`/empty = single-path resource, current behaviour. Do not touch the primary `path` column.

**Verification:**

- `Grep` - `alt_access_paths` matches once in `ResourceEntity.kt`.
- `Grep` - `val altAccessPaths` matches once.

**Status:** `[ ]` not done

---

### Step 01.2 - Add Migration 38 -> 39

**Files:** `data/local/db/Migration38To39.kt` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `MIGRATION_38_39 = object : Migration(38, 39)` that runs `ALTER TABLE resources ADD COLUMN alt_access_paths TEXT DEFAULT NULL`. Follow the file/style of `Migration35To36.kt`. Forward-only; never edit prior migrations.

**Verification:**

- `Glob` - `Migration38To39.kt` exists.
- `Grep` - `Migration(38, 39)` matches once.
- `Grep` - `ALTER TABLE resources ADD COLUMN alt_access_paths` matches once.

**Status:** `[ ]` not done

---

### Step 01.3 - Bump database version and register the migration

**Files:** `data/local/db/AppDatabase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Change `@Database(.. version = 38 ..)` to `version = 39`. Add `MIGRATION_38_39` to the migration list passed to the database builder (same place the existing migrations are registered). Do not enable destructive fallback.

**Verification:**

- `Grep` - `version = 39` matches once in `AppDatabase.kt`.
- `Grep` - `MIGRATION_38_39` referenced in the builder/migration array.

**Status:** `[ ]` not done

---

### Step 01.4 - Add `altAccessPaths` to the MediaResource domain model

**Files:** `domain/model/Models.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a domain field to `MediaResource` holding the ordered alternate endpoints as `val altAccessPaths: List<HostPort> = emptyList()` (introduce a tiny `data class HostPort(val host: String, val port: Int)` in the domain model, reusable by the resolver). Default empty = single-path. Keep the existing `path` as the primary/display endpoint.

**Verification:**

- `Grep` - `altAccessPaths` matches in `Models.kt`.
- `Grep` - `data class HostPort` matches once.

**Status:** `[ ]` not done

---

### Step 01.5 - Map the field in both entity <-> domain mappers

**Files:** `data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Steps 01.1, 01.4

**Prompt for developer:**

> In `ResourceEntity.toDomain()` (~L451) parse `altAccessPaths` string into `List<HostPort>`; in `MediaResource.toEntity()` (~L517) serialise the list back to the stored string form (null/empty when the list is empty). Keep the serialisation format confined to these two functions. A malformed stored value degrades to an empty list, never a crash.

**Verification:**

- `Grep` - `altAccessPaths` matches at least twice in `ResourceRepositoryImpl.kt` (both mappers).
- `Grep -n "Log\.d\("` - zero hits in the edited file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new migration class) - may defer to Phase 05.

---

## Handoff Notes to Next Phase

`MediaResource.altAccessPaths: List<HostPort>` and the `HostPort` type now exist and round-trip through Room. Phase 02 populates the list on companion import; Phase 03 consumes it in the resolver.

---

## Rollback Plan

Revert the phase commit(s). The migration is additive (nullable column) - a downgrade is not supported by Room regardless, but no existing data is transformed, so a forward revert before release is safe.
