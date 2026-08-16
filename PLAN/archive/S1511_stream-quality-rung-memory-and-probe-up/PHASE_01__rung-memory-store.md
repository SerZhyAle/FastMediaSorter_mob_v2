# Phase 01 - Rung memory store

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Persist what a channel learned about its quality rung, in its own table, keyed so it survives a catalog re-import.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamQualityMemoryEntity.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamQualityMemoryDao.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration49To50.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 4 |
| `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/50.json` | Generated | n/a |
| `dev/TECH_REQUIREMENTS.md` | Modified | ≤ 4 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration49To50Test.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/util/StreamUrlNormalizer.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/util/StreamUrlNormalizerTest.kt` | New | ≤ 90 |

> Place `Migration49To50.kt` beside the existing per-version migration files; confirm their exact package and folder from `Migration48To49.kt` before creating it.

---

## Steps

### Step 01.1 - Add the channel-address normalizer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/util/StreamUrlNormalizer.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/util/StreamUrlNormalizerTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a pure function turning a channel address into a stable key: lowercase the scheme and host, drop a default port, drop a trailing slash, and keep path, query and case elsewhere untouched because a stream path is frequently case-sensitive. Cover each rule with a unit test, including the case that two addresses differing only in scheme case normalize equal, and the case that two differing in path case do not.

**Why:**

Strategic ADR-5 keys the memory by channel address rather than by the catalog row id so a re-imported channel keeps what it learned, and the spec records that no normalizer exists in the project yet.

**Verification:**

- `Glob` - `StreamUrlNormalizer.kt` exists.
- `Grep` - `class StreamUrlNormalizer` or `object StreamUrlNormalizer` matches exactly once.
- `.\a.ps1 fu` - `StreamUrlNormalizerTest` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - StreamUrlNormalizer added with 9 unit tests; XML report tests=9 failures=0 errors=0. Unblocked first: StandalonePlayerViewModelTest did not compile (missing searchLyricsUseCase arg after the S1329 Lazy ctor change), which had been blocking the whole unit-test source set.

---

### Step 01.2 - Add the entity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamQualityMemoryEntity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `@Entity(tableName = "stream_quality_memory")` with a composite primary key of normalized address and rung bitrate, plus the learned ceiling height and width, the count of recorded probe failures for that rung, and the epoch-millis timestamp of the last write. Model the KDoc on `StreamPlayOutcomeEntity`, stating why this is a separate table.

**Why:**

Strategic ADR-2 puts the memory in its own table because Room invalidates per table, and the composite key follows ADR-5, where the rung bitrate is part of the identity since a source may re-encode.

**Verification:**

- `Grep` - `tableName = "stream_quality_memory"` matches exactly once in the module.
- `Grep` - `primaryKeys` present in that entity.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - StreamQualityMemoryEntity added: table stream_quality_memory, composite key (normalizedUrl, rungBitrateBps). Grep: tableName match 1, primaryKeys present.

---

### Step 01.3 - Add the DAO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamQualityMemoryDao.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add read-one, upsert, delete-by-address, prune-older-than and count queries. Write the upsert with the `INSERT OR REPLACE .. WHERE NOT EXISTS` idiom that `StreamPlayOutcomeDao` uses, never the native `ON CONFLICT DO UPDATE`. Add a bounded-size prune that keeps the newest N addresses.

**Why:**

Strategic section 3.2 records that native upsert needs SQLite 3.24, which is API 30, while the `legacy` flavor runs from API 23 - the reason the neighbouring DAO is already written that way; strategic section 5.1 item 5 requires the store to age out and stay bounded.

**Verification:**

- `Grep` - `ON CONFLICT` returns zero hits in `StreamQualityMemoryDao.kt`.
- `Grep` - `INSERT OR REPLACE` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - StreamQualityMemoryDao added: learnedRungFor, rememberRung (INSERT OR REPLACE .. WHERE NOT EXISTS), deleteByUrl, pruneOlderThan, pruneToNewestChannels, countChannels. Grep: ON CONFLICT 0 hits, INSERT OR REPLACE present. KDoc reworded off the literal ON CONFLICT so the zero-hit predicate is not defeated by the comment explaining why the clause is avoided.

---

### Step 01.4 - Register the table and migrate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration49To50.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add the entity to the `entities` list, add the DAO accessor, bump `version` from 49 to 50, and add `Migration49To50` creating the table. Write the migration SQL to match Room's generated schema character for character, the way `Migration48To49` does, and register it wherever the migration list is assembled. Build once so the exported `50.json` schema is produced and committed.

**Why:**

Strategic section 3.2 records that `runMigrationsAndValidate` diffs the migration against the exported schema and that there is no destructive fallback, so a mismatched or unregistered migration throws at runtime rather than degrading quietly.

**Verification:**

- `Grep` - `version = 50` in `AppDatabase.kt`.
- `Glob` - `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/50.json` exists.
- `Grep` - `MIGRATION_49_50` referenced in `DatabaseModule.kt`'s migration list, not only declared in `Migration49To50.kt`.
- `/build` `standard debug` succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - AppDatabase version 49 -> 50, entity + streamQualityMemoryDao accessor registered, MIGRATION_49_50 written and added to DatabaseModule's list. a.ps1 dq exit 0; exported 50.json carries stream_quality_memory and its createSql matches the migration character for character. Plan corrected: migration lives in data/local/db (not a migrations/ subfolder), the schema exports under schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/, and DatabaseModule.kt was missing from Files Touched.
- 2026-08-13 - Phase-boundary audit (Layer 1 + Layer 4). P1: Layer 4 requires every schema change to ship a migration AND a migration test; none existed. Fixed in phase - AppDatabaseMigration49To50Test added, mirroring AppDatabaseMigration48To49Test. Layer 1 clean. Note: no a.ps1 or check-standard-fast mode compiles androidTest, so the instrumented test is shipped uncompiled exactly as its two predecessors are; the schema-match risk it guards is separately proven by diffing the generated createSql against MIGRATION_49_50 character for character.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings; the migration is checked against the exported schema.

---

## Handoff Notes to Next Phase

The store exists and is reachable, but nothing writes to it and no policy reads it.

---

## Rollback Plan

Reverting after the version bump ships is not a revert - a database that already migrated to 50 cannot be handed back to 49. Revert only before the change leaves the working tree; afterwards, fix forward with a new migration.
