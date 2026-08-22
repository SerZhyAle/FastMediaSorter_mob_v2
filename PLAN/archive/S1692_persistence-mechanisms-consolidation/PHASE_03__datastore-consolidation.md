# Phase 03 - datastore-consolidation

**Strategic spec:** [`../S1692_persistence-mechanisms-consolidation.md`](../S1692_persistence-mechanisms-consolidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Consolidate remaining unmanaged SharedPreferences accesses in application managers into the unified `SettingsRepository` DataStore pipeline.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 800 |

---

## Steps

### Step 03.1 - Extend SettingsRepository interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add remaining preference keys and accessor methods to `SettingsRepository.kt` domain interface to replace direct SharedPreferences calls.

**Why:**

> Consolidates all settings properties into a single domain repository interface backed by DataStore.

**Verification:**

- `Grep` - `fun get` or `val` additions in `SettingsRepository.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - SettingsRepository interface extended

---

### Step 03.2 - Implement consolidated keys in SettingsRepositoryImpl

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement DataStore PreferenceKey mappings and Flow accessors in `SettingsRepositoryImpl.kt`.

**Why:**

> Connects domain repository interface to DataStore backing storage.

**Verification:**

- `Grep` - `preferencesKey` mappings in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - SettingsRepositoryImpl implementation updated

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All non-legitimate SharedPreferences accesses mapped to SettingsRepository DataStore.

---

## Rollback Plan

Revert phase commit.
