# Phase 02 - idempotent-migration

**Strategic spec:** [`../S1692_persistence-mechanisms-consolidation.md`](../S1692_persistence-mechanisms-consolidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Implement safe SharedPreferences -> DataStore single-pass idempotent copying migration with a `migrated_v1` flag without deleting original SharedPreferences files.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/SettingsMigrationManager.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/di/StorageModule.kt` | Modified | ≤ 250 |

---

## Steps

### Step 02.1 - Create SettingsMigrationManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/SettingsMigrationManager.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Implement `SettingsMigrationManager.kt` to perform a single-pass idempotent copy of legacy SharedPreferences values into DataStore upon first launch after update, preserving original files and recording `migrated_v1` completion flag.

**Why:**

> Prevents user data loss during transition from SharedPreferences to DataStore while ensuring migration runs exactly once.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/SettingsMigrationManager.kt` exists.
- `Grep` - `class SettingsMigrationManager` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - SettingsMigrationManager created and validated

---

### Step 02.2 - Wire migration into StorageModule

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/di/StorageModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Wire `SettingsMigrationManager` into Hilt `StorageModule` so DataStore instance receives `SharedPreferencesMigration` or custom idempotent initializer.

**Why:**

> Ensures migration is registered and invoked when DataStore is initialized.

**Verification:**

- `Grep` - `SettingsMigrationManager` present in `StorageModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - SettingsMigrationManager wired into Hilt AppModule

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

DataStore migration pipeline initialized and wired.

---

## Rollback Plan

Revert phase commit - no data deleted on migration.
