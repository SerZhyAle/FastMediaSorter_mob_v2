# Phase 02 - Identity schema and migration 52

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Give every catalog row its derived identity column and create the table that holds user-authored state
under that identity, migrating both from the data already on the device. No reader switches yet.

---

## Prerequisites

- [x] Phase 01 is ✅ Done - `StreamChannelIdentity.of` exists and is tested.
- [x] Working tree is clean or on a feature branch.
- [x] `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/51.json` is present, so `runMigrationsAndValidate` has a baseline to compare against.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamUserStateEntity.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamUserStateDao.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration51To52.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 810 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 310 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration51To52Test.kt` | New | ≤ 200 |

> `AppDatabase.kt` is already past 500 LOC - take a timestamped backup before editing it (CLAUDE.md Rule 5).

---

## Steps

### Step 02.1 - Add the identity column to `StreamSourceEntity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a non-null `identityKey` string field to `StreamSourceEntity`, carrying an empty-string schema
> default via `@ColumnInfo`, and a second index on it beside the existing unique `url` index, named
> `index_stream_sources_identityKey`. The index must NOT be unique: research artifact 02 measured 58
> groups of bank rows that collapse onto one key, and a unique index would turn each of them into an
> insert conflict and shrink the catalog by 58 rows on the first import.
> The schema default has to be declared on the field because the ALTER TABLE in step 02.4 supplies one,
> and `runMigrationsAndValidate` compares default values as well as names and types.

**Why:**

Strategic ADR-3 files user data under the derived identity while leaving `id` an opaque row handle, so
the identity has to be a column the catalog row carries rather than the key it is stored under.

**Verification:**

- `Grep` - `val identityKey: String` matches exactly once in `StreamSourceEntity.kt`.
- `Grep` - `defaultValue` present in the same file.
- `Grep` - `index_stream_sources_identityKey` present, and that line does not contain `unique = true`.

**Status:** `[x]` done

---

### Step 02.2 - Add `StreamUserStateEntity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamUserStateEntity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create the Room entity for table `stream_user_state` with `identityKey` as the primary key, plus
> `pinned`, `sortIndex`, a nullable `playOutcome`, a nullable `outcomeAt` and a non-null `updatedAt`.
> This table is deliberately not a child of `stream_sources` and carries no foreign key: its whole
> purpose is to outlive the catalog row, so a cascade would delete exactly the rows the ticket exists to
> keep. State that in the KDoc, and name `updatedAt` as the field the bounded prune orders by.

**Why:**

Strategic §5.1 pillar 3 requires the user-authored part to be stored separately from the imported part
so that a returning address reconnects to it, and research artifact 01 found that the two data kinds
which already survive a prune are exactly the two stored outside the catalog row.

**Verification:**

- `Glob` - `StreamUserStateEntity.kt` exists.
- `Grep` - `tableName = "stream_user_state"` matches exactly once.
- `Grep` - `@PrimaryKey` present, on the line above `val identityKey: String`.
- `Grep` - `ForeignKey` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 02.3 - Add `StreamUserStateDao`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamUserStateDao.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create the DAO with an `observeAll` returning a Flow of the whole table, a single-key read, an upsert,
> a pin writer taking key plus pinned plus sortIndex plus updatedAt, an outcome writer taking key plus
> outcome plus recordedAt plus updatedAt, a `minSortIndex`, a `pinnedSnapshot` ordered by `sortIndex`, a
> `clearOutcomes`, and `pruneUnpinnedOlderThan(cutoffMillis)` deleting rows that are not pinned and whose
> `updatedAt` predates the cutoff. Model the prune on `StreamQualityMemoryDao.pruneOlderThan`, which
> already bounds a table keyed by address rather than by row.
> Anywhere the merge needs this table it reads it whole in one query - no per-row lookup.

**Why:**

Strategic §3.2 forbids turning the import of 17 628 rows into a per-row query, and §7 names unbounded
growth of retained state for absent channels as a risk whose mitigation is keeping only what the user
made and bounding it.

**Verification:**

- `Glob` - `StreamUserStateDao.kt` exists.
- `Grep` - `interface StreamUserStateDao` matches exactly once.
- `Grep` - `fun pruneUnpinnedOlderThan` present.
- `Grep` - `fun observeAll` present in that file.

**Status:** `[x]` done

---

### Step 02.4 - Write `Migration51To52`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration51To52.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Follow the file shape of `Migration50To51.kt` - private schema-version constants and a top-level
> `MIGRATION_51_52` value. The migration does four things in order: add the `identityKey` column to
> `stream_sources` with a NOT NULL empty-string default; create `index_stream_sources_identityKey`;
> create `stream_user_state`; then backfill.
> The backfill is Kotlin rather than SQL because the derivation parses a URI: read `id` and `url` from
> `stream_sources`, compute the identity with `StreamChannelIdentity.of` and write it back through one
> prepared UPDATE. Then seed `stream_user_state` from what the device already holds - every row with
> `pinned` set contributes its pinned flag and `sortIndex`, and every `stream_play_outcome` row
> contributes its outcome and `recordedAt` under the identity of the `stream_sources` row it points at.
> Merge the two sources so a channel that is both pinned and played ends up as one row carrying both.
> Where two catalog rows share an identity - the measured 58 pairs - the seed keeps the pinned one: state
> is per channel, and those pairs are one channel entered twice.
> Write the CREATE statements out by hand to match what Room generates character for character, as
> `Migration50To51`'s KDoc explains - `runMigrationsAndValidate` compares the migrated schema against the
> exported `52.json` and fails on any divergence.

> **Exact statements, read off the exported `51.json` on 2026-08-20 so they are not written from memory:**
>
> ```sql
> ALTER TABLE `stream_sources` ADD COLUMN `identityKey` TEXT NOT NULL DEFAULT ''
> CREATE INDEX IF NOT EXISTS `index_stream_sources_identityKey` ON `stream_sources` (`identityKey`)
> CREATE TABLE IF NOT EXISTS `stream_user_state` (`identityKey` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `playOutcome` TEXT, `outcomeAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`identityKey`))
> ```
>
> The entity must therefore declare `identityKey` non-null with `@ColumnInfo(defaultValue = "")`, `pinned`
> and `sortIndex` and `updatedAt` non-null, and `playOutcome` and `outcomeAt` nullable - anything else and
> `runMigrationsAndValidate` fails against the generated `52.json`.

**Why:**

Strategic §3.2 requires the schema version and migration class to be named in the tactical step rather
than chosen during implementation, and §11 criterion 5 requires the migration to be proven on a device
that already carries pins, which is only possible if the migration itself carries them across.

**Verification:**

- `Glob` - `Migration51To52.kt` exists.
- `Grep` - `MIGRATION_51_52` matches exactly once in that file.
- `Grep` - `StreamChannelIdentity.of` present in that file.
- `Grep` - `stream_user_state` present in that file.
- `Grep` - `Migration50To51.kt` and `Migration49To50.kt` are unchanged against HEAD.

**Status:** `[x]` done

---

### Step 02.5 - Register the entity, the DAO and the migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Take a timestamped backup of `AppDatabase.kt` first - it is over 500 LOC (CLAUDE.md Rule 5). Add
> `StreamUserStateEntity::class` to the `@Database` entity list, raise the version from 51 to 52, and add
> the abstract DAO accessor beside the existing stream DAOs. In `DatabaseModule.kt` import
> `MIGRATION_51_52`, append it to the `addMigrations` list after `MIGRATION_50_51`, and add the provider
> for the new DAO next to the other stream DAO providers.
> Build once so Room regenerates `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/52.json`, and commit that file - it is the baseline the
> next migration is validated against.

**Why:**

Strategic §3.2 marks the change as touching Room, and a migration that is written but not registered
leaves the stored database at 51 while the entity list claims 52, which fails when the database opens on
a user's device rather than at build time.

**Verification:**

- `Grep` - `version = 52` matches exactly once in `AppDatabase.kt`.
- `Grep` - `StreamUserStateEntity::class` present in the entity list.
- `Grep` - `MIGRATION_51_52` present in `DatabaseModule.kt` both as an import and inside `addMigrations`.
- `Glob` - `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/52.json` exists.
- `.\a.ps1 fk` - exit 0, banner names `app_v2`.

**Status:** `[x]` done

---

### Step 02.6 - Instrumented migration test

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration51To52Test.kt`
**Depends on:** Step 02.5

**Prompt for developer:**

> Follow `AppDatabaseMigration50To51Test.kt`'s `MigrationTestHelper` shape, then assert more than
> survival. Seed the version-51 database with a pinned row carrying a known `sortIndex`, a row carrying a
> `stream_play_outcome` row, a plain row, and an `http` and an `https` row for the same host and path.
> Run the migration to 52 with validation. Assert that every row's `identityKey` equals the identity
> derived from its own url; that the http and https pair share one `identityKey` while both rows still
> exist; that `stream_user_state` carries the pin with its original `sortIndex`; and that it carries the
> outcome with its original `recordedAt`.

**Why:**

Strategic §7 rates a migration that loses data on real devices as the risk that would cost the user
exactly what the ticket promises to keep, and the existing migration tests only prove that a row
survives untouched, which would still pass if every pin were dropped.

**Verification:**

- `Glob` - `AppDatabaseMigration51To52Test.kt` exists.
- `Grep` - `runMigrationsAndValidate` present.
- `Grep` - `identityKey` present at least four times, one per assertion above.
- `Grep` - `stream_user_state` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in every file touched.
- [x] `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/52.json` committed.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - two new public types.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Room main-safety and migration correctness are the layers to read in `docs/CODE_AUDIT_PROTOCOL.md`.

---

## Added during implementation

Three things the plan did not name. Each is recorded here rather than left in the diff, because the
next phase reads this file and not the commit.

- **Step 02.7 - the repository derives the identity on every write.** Without it the column would be
  correct only for rows the migration touched: anything inserted afterwards would carry the empty-string
  default, and every such row would share one identity. `StreamSourceRepository.withIdentity()` is now
  the single place that default is overwritten, so "no `stream_sources` row without a correct
  `identityKey`" is an invariant of the write path rather than something each caller must remember.
  `StreamSourceDao.updateUserFields` gained an `identityKey` parameter for the same reason - S0660 lets
  the user rewrite a manual channel's address, and the key has to move with it.

- **A fast-check mode for the instrumented source set.** Nothing in the repository compiled
  `src/androidTest/`, which is how `AppDatabaseMigration50To51Test` shipped referencing an undeclared
  `TEST_DB`. `scripts/builders/check-standard-fast.ps1 -Mode AndroidTest` now does; the missing constant
  was declared, and the whole set compiles (exit 0, 34 s). The mode exists but no gate calls it - parked
  as **S1844**.

- **Two documents pinning the Room schema version.** `dev/TECH_REQUIREMENTS.md` and `docs/DEV_OPS.md`
  both stated 51; the `doc-pin-drift` gate caught it on the first closure attempt.

## Observation for the device test

The backfill runs one indexed UPDATE per catalog row inside the migration transaction - on the current
published bank that is 17 628 of them, on the first database open after the upgrade. It cannot fail (the
derivation is total: `StreamUrlNormalizer.normalize` wraps its only throwing call in `runCatching`), but
its duration is unmeasured and it is not guaranteed to be off the main thread. This is what the device
test under §11 criterion 5 should watch for, alongside the pins surviving.

---

## Handoff Notes to Next Phase

After this phase the device holds the identity and the user state, and nothing reads either. Every pin
and every outcome still comes from where it came from before, so the app behaves exactly as it did at
version 51. Phase 03 switches the readers, and it may assume `identityKey` is populated for every
existing row.

---

## Rollback Plan

Revert the phase commit and delete `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/52.json`. A device already migrated to 52 keeps two
unused artifacts - one column and one table - and behaves as version 51 did; the migration copies user
data rather than moving it, so rolling back the code loses nothing.
