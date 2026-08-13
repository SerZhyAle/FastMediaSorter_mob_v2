# Phase 02 — Data Foundations

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Add a nullable `iconId` column (`ico-XX-NNN` string format) to the resources table with Room migration; surface the field through the entity → domain mapper and repository contract. No icon assignment, no UI, no rendering yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] No pending Room migration in flight on another branch.
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResourceRepository.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 — Extend `ResourceEntity` with `iconId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Append a nullable column to `ResourceEntity` after the `comment` field:
>
> ```kotlin
> @ColumnInfo(name = "icon_id", defaultValue = "NULL")
> val iconId: String? = null
> ```
>
> Format contract: `ico-XX-NNN` where XX = set id (`01`..`99`) and NNN = ordinal (`001`..`999`). `null` means "not yet assigned" (handled by Phase 05 backfill). Do NOT add an index — lookup is always by primary key.

**Verification:**

- `Grep` — `name = "icon_id"` matches once in `ResourceEntity.kt`.
- `Grep` — `val iconId: String\? = null` matches once.

**Status:** `[x] done`

---

### Step 02.2 — Bump Room version and add migration 25→26

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Change `version = 25` to `version = 26`. Add a new `Migration(25, 26)` to the existing migrations list:
>
> ```kotlin
> val MIGRATION_25_26 = object : Migration(25, 26) {
>     override fun migrate(db: SupportSQLiteDatabase) {
>         db.execSQL("ALTER TABLE resources ADD COLUMN icon_id TEXT DEFAULT NULL")
>     }
> }
> ```
>
> Register `MIGRATION_25_26` in the `addMigrations(..)` chain alongside the others. Never modify migrations 1..24.

**Verification:**

- `Grep` — `version = 26` matches once in `AppDatabase.kt`.
- `Grep` — `MIGRATION_25_26` matches at least twice (definition + registration).
- `Grep` — `ALTER TABLE resources ADD COLUMN icon_id` matches once.
- `Grep` — `Migration(25, 26)` matches once.

**Status:** `[x] done`

---

### Step 02.3 — Add `iconId` to domain `MediaResource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `val iconId: String? = null` to the `MediaResource` data class, placed after `comment`. Default null preserves binary compatibility for any test fixtures or call sites that construct `MediaResource` positionally — verify by searching for `MediaResource(` constructor call sites and confirm none rely on positional ordering past `comment`.

**Verification:**

- `Grep` — `val iconId: String\? = null` matches at least once in `Models.kt`.
- `Grep -n "MediaResource\("` returns zero positional construction call sites past the `comment` parameter (manual inspection acceptable; record sites visited in commit message).

**Status:** `[x] done`

---

### Step 02.4 — Wire entity ↔ domain mapper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Steps 02.1, 02.3

**Prompt for developer:**

> Locate the existing `ResourceEntity` ↔ `MediaResource` mapper functions (likely `toEntity` / `toDomain` or named after the destination type). Pass `iconId` through both directions: domain → entity copies the field as-is; entity → domain copies the field as-is. Do not rename other fields.

**Verification:**

- `Grep` — `iconId = ` matches at least twice in `ResourceRepositoryImpl.kt` (one per direction).

**Status:** `[x] done`

---

### Step 02.5 — Extend `ResourceRepository` contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResourceRepository.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a single suspending method to the `ResourceRepository` interface:
>
> ```kotlin
> suspend fun updateIcon(resourceId: Long, iconId: String?)
> ```
>
> Implement it in `ResourceRepositoryImpl` as a passthrough to a new `ResourceDao.updateIcon(id, iconId)` query (`@Query("UPDATE resources SET icon_id = :iconId WHERE id = :id")`). Add the matching DAO method.

**Verification:**

- `Grep` — `suspend fun updateIcon\(resourceId: Long, iconId: String\?\)` matches once in `ResourceRepository.kt`.
- `Grep` — `override suspend fun updateIcon` matches once in `ResourceRepositoryImpl.kt`.
- `Grep` — `UPDATE resources SET icon_id` matches once in `ResourceDao.kt`.

**Status:** `[x] done`

---

### Step 02.6 — Build + Room compile check

**Files:** —
**Depends on:** Steps 02.1..02.5

**Prompt for developer:**

> Trigger `/build` (standard debug). Confirm the Room annotation processor accepts the new column without `Schema export directory is not provided` failures and that no migration test fails (project sets `exportSchema = false` so schema dump is skipped).

**Verification:**

- `/build standard debug` exits with status PASS.
- `Grep -n "Log\.d\("` returns zero hits across the five files modified in this phase.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed (it does — repository contract): `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The `iconId` column is live and migratable but every existing row still has `null`. Phase 05 backfills via DB-update on first app launch after schema bump. Phase 03 will define the constants and registry that Phase 05 reads from.

---

## Rollback Plan

Revert phase commit(s). Migration 25→26 is additive (nullable column), so a downgrade is achievable by dropping the column manually (`ALTER TABLE resources DROP COLUMN icon_id`) — but in practice prefer revert of the merge commit before any user installs the new version.
