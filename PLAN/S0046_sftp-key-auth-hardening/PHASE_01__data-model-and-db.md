# Phase 01 — Data model and DB migration

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Add `hostKeyFingerprint` field to `MediaResource` domain model and persist it in the `resources` Room table via a new schema migration; no behavior change.

---

## Prerequisites

- [ ] Strategic §6.1–§6.4 are `Resolved`.
- [ ] Working tree is clean or on a feature branch.
- [ ] No other Room migration in flight on `main`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 600 |

> `AppDatabase.kt` is currently 728 LOC. After adding `MIGRATION_27_28` it will exceed 500 LOC — backup step required (timestamped copy in `temp/`).

---

## Steps

### Step 01.1 — Backup AppDatabase.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `AppDatabase.kt` to `temp/AppDatabase.kt.<YYYYMMDD-HHmm>.bak` before modifying it. The file is >500 LOC so the backup rule applies.

**Verification:**

- `Glob` — `temp/AppDatabase.kt.*.bak` matches at least one file with mtime within last 24h.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 1/1 PASS. Files: temp/AppDatabase.kt.20260507-1621.bak. Dev log recorded.

---

### Step 01.2 — Add `hostKeyFingerprint` to `MediaResource`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `MediaResource` data class declaration (currently starting at line 137), add a new constructor parameter `val hostKeyFingerprint: String? = null` immediately after `val iconId: String? = null`. Default-null preserves source-compat with existing `copy()` and `MediaResource(...)` call sites; SFTP-only field, but holding it on the domain model keeps SMB/FTP callers untouched.

**Verification:**

- `Grep` — `val hostKeyFingerprint: String\? = null` matches exactly once in `Models.kt`.
- `Grep` — `data class MediaResource\(` still matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 2/2 PASS. Files: Models.kt (+2 LOC). Dev log recorded.

---

### Step 01.3 — Add `host_key_fingerprint` column to `ResourceEntity` and bump DB version

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> 1. In `ResourceEntity.kt` add `@ColumnInfo(name = "host_key_fingerprint") val hostKeyFingerprint: String? = null` to the entity constructor (after the last existing field; default-null so existing inserts keep working). Update mapping helpers (`toDomain` / `fromDomain` if present) to round-trip the new field.
> 2. In `AppDatabase.kt` change `version = 27` to `version = 28`. Add `MIGRATION_27_28 = object : Migration(27, 28) { override fun migrate(db) = db.execSQL("ALTER TABLE resources ADD COLUMN host_key_fingerprint TEXT DEFAULT NULL") }` next to the other migrations. Register the new migration in the Hilt module that builds the database (search for `addMigrations` and append `, MIGRATION_27_28`).

**Verification:**

- `Grep` — `version = 28` matches in `AppDatabase.kt`.
- `Grep` — `MIGRATION_27_28` matches in `AppDatabase.kt` and at least one Hilt module file (search the project for `addMigrations`).
- `Grep` — `host_key_fingerprint` matches in `ResourceEntity.kt` and `AppDatabase.kt`.
- `Grep -n "Log\.d\("` returns zero hits in both modified files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 4/4 PASS. Files: ResourceEntity.kt (+4 LOC), AppDatabase.kt (+7 LOC), DatabaseModule.kt (+1 LOC). Dev log recorded.

---

### Step 01.4 — Round-trip new field in `ResourceRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Locate the entity↔domain mapping inside `ResourceRepositoryImpl` (search for the existing `iconId` round-trip — `host_key_fingerprint` follows the same shape). Pass through `hostKeyFingerprint` in both directions so the repository preserves the value end-to-end. No behavior changes.

**Verification:**

- `Grep` — `hostKeyFingerprint` matches at least twice in `ResourceRepositoryImpl.kt` (one read, one write).
- `Grep -n "Log\.d\("` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 2/2 PASS. Files: ResourceRepositoryImpl.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Schema change: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`MediaResource.hostKeyFingerprint` round-trips through repo and DB but is unused by callers. Phase 02 introduces normalization + verifier on top.

---

## Rollback Plan

Revert phase commit(s); migration `MIGRATION_27_28` is additive (one nullable column) — downgrade not needed.
