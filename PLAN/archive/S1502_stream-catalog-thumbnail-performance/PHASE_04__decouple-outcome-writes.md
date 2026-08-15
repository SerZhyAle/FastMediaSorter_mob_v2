# Phase 04 - Decouple play-outcome writes from the list flow

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 9
**Started:** -
**Completed:** -

---

## Objective

Move `lastPlayOutcome` / `lastPlayOutcomeAt` out of `stream_sources` into a dedicated `stream_play_outcome` table observed as a side channel, so recording one channel's outcome no longer re-emits the whole catalog; then capture the after-numbers against Phase 01's baseline.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6.2 is Resolved - it is, and the separate-table option it selects is what this phase implements.
- [ ] `temp/S1502/baseline/` still holds Phase 01's five before-numbers.
- [ ] Working tree is clean or on a feature branch.

---

## Schema change - declared up front

This phase changes the Room schema. Per CLAUDE.md Hard-Stop Conditions the version and migration class are named here rather than decided during implementation:

- **Database version:** 47 -> **48**, in `AppDatabase.kt`.
- **Migration class:** `MIGRATION_47_48`, in the new file `Migration47To48.kt`, following the `MIGRATION_46_47` naming and file shape already in that package.
- **Registration:** added to the `.addMigrations(..)` list in `core/di/DatabaseModule.kt`.
- **Exported schema:** `app_v2/schemas/<db-class>/48.json` is produced by the build (`exportSchema = true`) and committed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeEntity.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeDao.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration47To48.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ClearStreamPlayOutcomesUseCase.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ObserveStreamPlayOutcomesUseCase.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamAdapterPayloads.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatter.kt` | Modified | ≤ 120 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration47To48Test.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeDaoTest.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/StreamSourceDaoOutcomeTest.kt` | Deleted | - |

> Plan gap found during Step 04.2 and repaired in it (CLAUDE.md Rule 20 - the step that orphans something deletes it): `StreamSourceDaoOutcomeTest` covered the S1169 change-guard on the DAO method this step removes. It was not a delete-only case - the guard is exactly what the new table must preserve, and its SQL form changed - so the test was repointed at `StreamPlayOutcomeDao` rather than dropped.

> `StreamsViewModel.kt` and `StreamsActivity.kt` are both over 500 LOC - take a fresh timestamped backup of each into `temp/S1502/` before the first edit (CLAUDE.md Rule 5), and leave `temp/S1502/baseline/` untouched.

---

## Steps

> **Where the first green compile is, and why it is not where this plan first put it.** Step 04.2 deletes `lastPlayOutcome` / `lastPlayOutcomeAt` from the entity, and four UI call sites keep reading them until Steps 04.5 and 04.6 repoint the last one. So the tree cannot compile between 04.2 and 04.6, and the `.\a.ps1 fk` predicate as originally written on Steps 04.3, 04.4 and 04.5 was unsatisfiable by construction - it asserted a state the plan's own ordering forbids. The predicate is **carried forward to Step 04.6**, not dropped: every intermediate step still verifies statically, and 04.6's `fk` is the one run that proves the whole chain 04.2-04.6 compiles. Patched during execution 2026-08-08 (`/spec-all` spec self-correction).

### Step 04.1 - Add the outcome entity and DAO

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamPlayOutcomeEntity` for table `stream_play_outcome` with `@PrimaryKey val streamId: String`, `val outcome: String` and `val recordedAt: Long`. Create `StreamPlayOutcomeDao` with `@Query("SELECT * FROM stream_play_outcome") fun observeAll(): Flow<List<StreamPlayOutcomeEntity>>`, an upsert that writes only on a real value change (mirror the `WHERE .. <> :outcome` guard `StreamSourceDao.markPlayOutcome` already uses, so an unchanged outcome fires no invalidation), a `@Query("DELETE FROM stream_play_outcome")` clear-all, and a delete-by-id for channel removal.
>
> Register both on `AppDatabase`: add `StreamPlayOutcomeEntity::class` to the `entities` list and an abstract `streamPlayOutcomeDao(): StreamPlayOutcomeDao` accessor. Leave the database `version` at 47 in this step - Step 04.2 bumps it together with the migration, so no intermediate commit declares a schema the migration list cannot satisfy.

**Why:**

Strategic §6.2 resolves that a separate table is the only mechanism that works, because Room tracks invalidation per table and a write to any column of `stream_sources` re-emits `observeAll()` regardless of which columns the query selects.

**Verification:**

- `Glob` - both new files exist.
- `Grep` - `@Entity(tableName = "stream_play_outcome")` matches once.
- `Grep` - `StreamPlayOutcomeEntity::class` matches in `AppDatabase.kt`.
- `Grep` - `version = 47` still matches in `AppDatabase.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 PASS. Files: data/local/db/StreamPlayOutcomeEntity.kt (new, 22 LOC), data/local/db/StreamPlayOutcomeDao.kt (new, 41 LOC), data/local/db/AppDatabase.kt (+2 LOC). The change-guarded write is `INSERT OR REPLACE .. SELECT .. WHERE NOT EXISTS`, not `ON CONFLICT DO UPDATE`: upsert syntax needs SQLite 3.24 (API 30) and strategic §3.2 pins the floor at minSdk 23. Dev log recorded.

---

### Step 04.2 - Write the migration and bump the schema version

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration47To48.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `Migration47To48.kt` exporting `val MIGRATION_47_48`, following the file shape of `Migration46To47.kt` (private `SCHEMA_VERSION_FROM` / `SCHEMA_VERSION_TO` constants, object expression, KDoc stating what changes and why). The migration must:
>
> 1. Create `stream_play_outcome`.
> 2. Copy every `stream_sources` row whose `lastPlayOutcome IS NOT NULL` into it, mapping `id -> streamId`, `lastPlayOutcome -> outcome` and `COALESCE(lastPlayOutcomeAt, 0) -> recordedAt`.
> 3. Recreate `stream_sources` without the `lastPlayOutcome` / `lastPlayOutcomeAt` columns, using the create-new / copy / drop / rename sequence, because SQLite on minSdk 23 has no `DROP COLUMN`. Take the exact target DDL from the generated `app_v2/schemas/<db-class>/48.json` after the first build rather than hand-writing it, and re-create every index the 47 schema declares on that table.
>
> Then set `version = 48` in `AppDatabase.kt`, add `MIGRATION_47_48` to the `.addMigrations(..)` list and its import in `DatabaseModule.kt`, delete the two properties from `StreamSourceEntity`, and delete `markPlayOutcome` and `clearAllPlayOutcomes` from `StreamSourceDao`. Commit the generated `48.json`.

Reference - the version 47 DDL this migration starts from, read from `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/47.json`. The version 48 target is this minus `lastPlayOutcome` and `lastPlayOutcomeAt`; confirm against the generated `48.json` rather than trusting the transcription:

```sql
CREATE TABLE IF NOT EXISTS `stream_sources` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `mediaKind` TEXT NOT NULL, `sourceOrigin` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER, `category` TEXT, `topic` TEXT, `language` TEXT, `country` TEXT, `lastPlayOutcome` TEXT, `lastPlayOutcomeAt` INTEGER, `access` TEXT, `preferredAudioLang` TEXT, `preferredSubtitleLang` TEXT, `subtitlesEnabled` INTEGER, PRIMARY KEY(`id`))
CREATE UNIQUE INDEX IF NOT EXISTS `index_stream_sources_url` ON `stream_sources` (`url`)
```

The unique index on `url` is the one index to re-create after the rename; dropping it silently would let duplicate catalog rows in on the next import.

**Why:**

Strategic §3.2 permits a schema change only when it is carried by a migration with an explicit version number, and §7 records that a migration touching installed databases is the risk this phase carries, which is what makes the atomic recreate sequence rather than an in-place edit the required form.

**Verification:**

- `Grep` - `val MIGRATION_47_48` matches once in `Migration47To48.kt`.
- `Grep` - `version = 48` matches in `AppDatabase.kt`.
- `Grep` - `MIGRATION_47_48` matches in `DatabaseModule.kt`.
- `Grep` - `lastPlayOutcome` returns zero hits in `StreamSourceEntity.kt` and `StreamSourceDao.kt`.
- `Glob` - `app_v2/schemas/*/48.json` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 5/5 PASS. Files: Migration47To48.kt (new, 60 LOC), AppDatabase.kt (version 47 -> 48), DatabaseModule.kt (+2), StreamSourceEntity.kt (-5), StreamSourceDao.kt (-17), StreamPlayOutcomeDaoTest.kt (new, replaces the orphaned StreamSourceDaoOutcomeTest.kt). Both migration DDLs were checked against the generated `48.json` and match it exactly, column order included - `stream_sources` line 1467, `stream_play_outcome` line 1583. Dev log recorded.
- 2026-08-08 - `.\a.ps1 fk` exits 1 here **by plan design, not by defect**: `kspStandardDebugKotlin` succeeded (which is what wrote `48.json`), and every one of the 15 compile errors is a call site that Steps 04.3-04.6 repoint - repository x2, formatter x1, payloads x7, grid adapter x2, list adapter x2. No error names anything outside that set. This step's own predicates are static by design for exactly this reason; the first green compile is Step 04.3's.

---

### Step 04.3 - Route the write path to the new table

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ClearStreamPlayOutcomesUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ObserveStreamPlayOutcomesUseCase.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `StreamPlayOutcomeDao` into `StreamSourceRepository` and repoint `recordPlayOutcome` and `clearPlayOutcomes` at it. Add `observePlayOutcomes(): Flow<Map<String, String>>` mapping the entity list to `streamId -> outcome`. Extend `remove(source)` to delete the channel's outcome row in the same call, so a removed channel leaves no orphan. Create `ObserveStreamPlayOutcomesUseCase` as a thin `@Inject constructor` wrapper over that repository method, matching the shape of `ObserveStreamSourcesUseCase`.
>
> `RecordStreamPlayOutcomeUseCase` and `ClearStreamPlayOutcomesUseCase` keep their public signatures - only the repository call underneath changes.

**Why:**

Strategic §11 criterion 5 requires that playing a channel and finishing a reachability probe stop causing a pass over the whole catalog, which holds only once the write itself lands in a table the list flow does not observe.

**Verification:**

- `Glob` - `ObserveStreamPlayOutcomesUseCase.kt` exists.
- `Grep` - `observePlayOutcomes` matches in `StreamSourceRepository.kt`.
- `Grep` - `streamPlayOutcomeDao` matches in `StreamSourceRepository.kt`.
- `.\a.ps1 fk` - **carried forward to Step 04.6** (see the note under Steps).

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3/3 static PASS (build predicate carried to 04.6). Files: StreamSourceRepository.kt (+outcome DAO, observePlayOutcomes, orphan-safe remove), ObserveStreamPlayOutcomesUseCase.kt (new, 14 LOC), DatabaseModule.kt (+provideStreamPlayOutcomeDao). `RecordStreamPlayOutcomeUseCase` and `ClearStreamPlayOutcomesUseCase` needed no edit at all - their public signatures already sat on the repository methods this step repointed, which is what the step predicted. `remove()` deletes both rows in one `db.withTransaction`: the outcome row is keyed by channel id, so a half-completed removal would strand a row no query can ever reach again. The Hilt provider is a copy of the sibling `provideStreamSourceDao` including its `@Singleton` - the scope was read off the sibling, not chosen. Dev log recorded.

---

### Step 04.4 - Expose the outcomes as a side channel on the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Inject `ObserveStreamPlayOutcomesUseCase` and expose `val playOutcomes: StateFlow<Map<String, String>>` built with `stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())`, alongside the existing `favoriteStreamUrls` and following its construction exactly. Do not add the map to `StreamsUiState` and do not reference it from `applyFilter` or the `combine` block.

**Why:**

Strategic §5.2 requires that service-row updates travel beside the list flow rather than through it, and folding the map into the combined state would re-couple a per-probe signal to the per-keystroke pass that Phase 02 and Phase 03 just made cheap.

**Verification:**

- `Grep` - `val playOutcomes: StateFlow<Map<String, String>>` matches once in `StreamsViewModel.kt`.
- `Grep` - `playOutcomes` returns zero hits inside the `combine {` block and inside `applyFilter`.
- `.\a.ps1 fk` - **carried forward to Step 04.6** (see the note under Steps).

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3/3 static PASS (build predicate carried to 04.6). Files: StreamsViewModel.kt (+8 LOC, 678 total, budget 760). `playOutcomes` occurs exactly once in the file, at its declaration; the `combine` block (lines 170-182) and `applyFilter` never mention it, which is the isolation the step asked for. Backup: temp/S1502/StreamsViewModel.kt.20260808_140700.bak (Rule 5). Dev log recorded.

---

### Step 04.5 - Bind the status bullet from the side channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamAdapterPayloads.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Give `StreamSourceAdapter` and `StreamGridAdapter` a settable `playOutcomes: Map<String, String>` property whose setter repaints only the affected rows with the existing `StreamAdapterPayloads.STATUS` payload, and have `bindPlayStatus` / `bindStatusOnly` read `playOutcomes[source.id]` instead of `source.lastPlayOutcome`. Delete the outcome comparison from `streamRowChangePayload`, leaving its pin branch intact, and update its KDoc. In `StreamsActivity`, collect `viewModel.playOutcomes` with `collectOnLifecycle` (`utils/LifecycleExtensions.kt`) and push the map into all four adapters.
>
> Keep the visual result identical - same bullet, same colours, same position. No layout file changes.

**Why:**

Strategic §2 non-goals state that the screen gains no new user-visible controls, so this step must move where the status value comes from without moving or restyling the affordance the user already sees.

**Verification:**

- `Grep` - `lastPlayOutcome` returns zero hits across `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/`.
- `Grep` - `collectOnLifecycle` matches on the `playOutcomes` collection in `StreamsActivity.kt`.
- `Grep` - no `lifecycleScope.launch` is introduced for this flow (CLAUDE.md Rule 19).
- `.\a.ps1 fk` - **carried forward to Step 04.6** (see the note under Steps).

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4/4 static PASS (build predicate carried to 04.6). Files: StreamAdapterPayloads.kt (-10 LOC), StreamSourceAdapter.kt (+16), StreamGridAdapter.kt (+16), StreamsActivity.kt (+8). `lastPlayOutcome` is now absent from the whole `ui/streams/` tree. The six `lifecycleScope.launch` calls still in StreamsActivity are all pre-existing (lines 592, 655, 860, 1108, 1280, 1286) - the new collection at line 735 uses `collectOnLifecycle`. Both setters compare per-id and repaint only rows whose outcome actually moved, so a probe that confirms an already-green channel costs zero rebinds. Placement decision on record, so the UI gate has something to check against: strategic §2 non-goal ("Экран не получает .. новых элементов управления") plus this step's own "same bullet, same colours, same position. No layout file changes" - the affordance is unchanged by instruction, not by guess. Backups: temp/S1502/*.20260808_140700.bak. Dev log recorded.

---

### Step 04.6 - Repoint the channel-info readout

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamPropertiesFormatter.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> `StreamPropertiesFormatter` reads `entity.lastPlayOutcome` for its last-outcome row. Pass the outcome in as an explicit parameter resolved by the caller from the outcome map, so the formatter stays a pure mapping over values it is given.

**Why:**

Step 04.2 removes the property this line reads, so leaving it untouched would not compile; the strategic spec's §2 non-goal of adding no user-visible change requires the info dialog keep showing the same row.

**Verification:**

- `Grep` - `\.lastPlayOutcome` (a property read) returns zero hits across `app_v2/src`. Amended from the original bare-word `lastPlayOutcome` grep scoped to `ui/dialog/`: the fix names the value it passes in `lastPlayOutcome`, which is the right name for it, so the bare word now matches the repair as readily as the defect and cannot tell them apart. The dotted form tests the thing the step actually asserts - that nothing reads the removed column any more.
- `.\a.ps1 fk` exits 0. **This is the phase's first green compile** - it proves the whole 04.2-04.6 chain, carrying the predicate deferred from Steps 04.3, 04.4 and 04.5.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2/2 PASS. `.\a.ps1 fk` **exit 0, BUILD SUCCESSFUL in 1m 46s** - no warning in any touched file. `\.lastPlayOutcome` has zero hits across `app_v2/src`; the only surviving mentions of the name are SQL string literals in `Migration34To35` (which created the columns) and `Migration47To48` (which moves them), where the literal spelling is mandatory.
- 2026-08-08 - **The step's file list was one file; the real read path was ten.** `StreamPropertiesFormatter` has two callers, not one: the streams screen through `StreamInfoDialogManager`, and the fullscreen player through `PlayerDialogAndUiStateManager`. Only the first has the outcome map in memory. Rather than let the player quietly render an empty outcome row - a user-visible regression against strategic §2's non-goal - the player got a real one-shot read: `StreamPlayOutcomeDao.outcomeFor` -> `StreamSourceRepository.playOutcome` -> new `GetStreamPlayOutcomeUseCase` -> `PlayerViewModel.streamPlayOutcome`. The streams screen reads its existing StateFlow instead, so it touches no database. Added: GetStreamPlayOutcomeUseCase.kt. Modified: StreamPlayOutcomeDao.kt, StreamSourceRepository.kt, StreamPropertiesFormatter.kt, StreamInfoDialog.kt, StreamInfoDialogManager.kt, StreamsActivity.kt, PlayerViewModel.kt, PlayerDialogAndUiStateManager.kt, StreamPropertiesFormatterTest.kt.
- 2026-08-08 - `lastPlayOutcome` is a **required** parameter on `catalogGroup`/`readout`, not a defaulted one. A default of null would let a future caller forget it and silently render "never tried" for a channel that has an outcome - the exact silent-wrong-value failure this step exists to prevent. Cost: three call-site updates in the existing test. Three further tests constructing `StreamSourceRepository(db, dao)` were fixed for the third constructor argument added back in 04.3 - `.\a.ps1 fk` compiles main only, so they would otherwise have surfaced at the unit suite.

---

### Step 04.7 - Prove the migration preserves recorded outcomes

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration47To48Test.kt`
**Depends on:** Step 04.6

**Prompt for developer:**

> Add a migration test following `AppDatabaseMigration46To47Test`: create the database at version 47, insert stream rows with and without a recorded outcome, run `MIGRATION_47_48`, and assert that `stream_play_outcome` holds exactly the rows that had one, with matching `outcome` and `recordedAt`, and that every `stream_sources` row survives with its remaining columns intact.

**Why:**

Strategic §7 lists an installed-database migration as this phase's leading risk with consequences disproportionate to the gain, and a test over a real version-47 database is the only evidence that separates a working migration from one that silently drops user data.

**Verification:**

- `Glob` - `AppDatabaseMigration47To48Test.kt` exists.
- `Grep` - `MIGRATION_47_48` matches in that test.
- The test is instrumented - it runs on the device sweep, not in `.\a.ps1 fu`; record its result in the device-test gate rather than claiming it from a unit-suite run.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2/2 static PASS (Glob + Grep). Files: AppDatabaseMigration47To48Test.kt (new, 87 LOC). Three seed rows at v47 cover the three shapes that can go wrong separately: a channel with an outcome and a timestamp, a channel with an outcome but a NULL timestamp (the `COALESCE(.., 0)` path, since the new column is NOT NULL), and a channel never tried (which must get no row at all rather than a null one). The index assertion is not ceremony - `DROP TABLE` takes `index_stream_sources_url` with the old table, and losing it silently would let the next catalog import insert duplicate channels, which is a data defect no build gate can see.
- 2026-08-08 - **Not run in this session, and not claimed as run.** The test is instrumented; `.\a.ps1 fu` does not execute androidTest. Its verdict belongs to the device gate this ticket ends in.

---

### Step 04.8 - Insert the debug probe tags

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.7

**Prompt for developer:**

> Insert one `Timber.d("S1502: <entry-point description>")` at each changed flow entry: the filtered-state emission in the ViewModel's `combine` collector, and the play-outcome map collection in the Activity. One tag per flow entry, not per modified line. These are the last code edits of the ticket, so the build that follows validates code and tags together.

**Why:**

CLAUDE.md "Debug Verification Tags" makes the tags an invariant of the `BlockNeedUserTest` status this ticket enters at the end of the phase, and strategic §6.1 records that the acceptance question cannot be closed without a device run, which is what that status marks.

**Verification:**

- `Grep` - `Timber.d("S1502:` matches exactly twice across `app_v2/src`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2/2 PASS. Exactly two tags: StreamsViewModel.kt:186 (the filtered-state emission, reporting the row count) and StreamsActivity.kt:737 (the outcome side channel, reporting the entry count). `.\a.ps1 fk` **exit 0, BUILD SUCCESSFUL in 54s** - one build validating implementation and tags together, as the phase requires. The two counts are what make the tags worth reading on device: if the outcome tag fires without the catalog tag following it, the decoupling holds; if they fire in pairs, it does not.
- 2026-08-08 - The tags live only while S1502 is `BlockNeedUserTest` and are removed by `/spec-check` on the transition out (CLAUDE.md "Debug Verification Tags"). `StreamsViewModel` gained its `timber.log.Timber` import for them - it had none.

---

### Step 04.9 - Capture the after-numbers against the baseline

**Files:** `temp/S1502/after/`
**Depends on:** Step 04.8

**Prompt for developer:**

> Install the debug APK built from the finished tree onto the same device Phase 01 measured, run `streams-perf-seed.ps1`, then run all five checkpoints with `-Json` into `temp/S1502/after/<checkpoint>.json`. Write a short comparison table of baseline against after for each checkpoint. Where the after-number is worse, say so plainly - a regression found here is the harness working, not a result to reword.
>
> A device other than the baseline's makes the pair meaningless: re-run Phase 01 Step 01.3 on the new device first if the original is gone.
>
> **The three frame-based checkpoints need their baseline re-taken - Phase 01's are void.** Phase 01's step log records why: a 12-swipe burst rendered 27-30 frames on the emulator and three identical repeats spread 46-60 %. Before comparing anything, satisfy all three conditions: no gradle or other build running on the host, a scroll long enough that `detail` reports at least 100 frames, and `insufficient: false` in every record. A run that reports `insufficient: true` is not a number - do not put it in the comparison table. `streams-open` and `streams-peak-memory` are not frame-sampled and keep their Phase 01 baselines (274 ms, 382,668 kB).
>
> If the frame-based checkpoints still cannot clear 100 frames on any available device, say so in the verdict and leave §11.2 and §11.3 unproven rather than quoting an insufficient sample.

**Why:**

Strategic §6.1 asks for the measurement before and after pillar A, ADR-2 states the ticket is accepted on a number rather than an impression, and §11 criteria 4 and 6 are comparisons that only a matched pair can settle.

**Verification:**

- `Glob` - `temp/S1502/after/` holds the same five checkpoint files as `temp/S1502/baseline/`.
- The comparison table names the device and states a verdict per criterion 1, 2, 3, 4 and 6.

**Status:** `[manual - deferred to human]` - one criterion measured and settled (§11.6), three unmeasurable on any hardware available here (§11.1-§11.3), one blocked behind a setting a human must turn on (§11.4). Detail below. The ticket sits in `BlockNeedUserTest`, which is precisely the state that says the rest is owed to a device run - not a step that was skipped.

**Step Log:**

- 2026-08-08 - Ran on `emulator-5554`, `sdk_gphone64_x86_64` API 35 - **the same device as Phase 01's baseline**, checked against `temp/S1502/baseline/device.json` before measuring, because a different device would make the pair meaningless. Tree state: the finished Phase 04 tree, built with `.\a.ps1 d` (BUILD SUCCESSFUL 1m 29s) and installed with `adb install -r` **over the existing v47 install**, so the migration ran against real data rather than a fresh database. Catalog re-seeded to 19,855 rows (`streams-perf-seed.ps1`, exit 0).

**Comparison - device `sdk_gphone64_x86_64` API 35 (NOT the floor tier §11 targets)**

| Checkpoint | Baseline | After | Verdict |
|---|---:|---:|---|
| `streams-peak-memory` | 382,668 kB | **272,756 kB** | §11.6 **PASS** - peak memory did not grow; it fell 28.7% |
| `streams-open` | 274 ms | not captured | §11.4 **unproven** - see below |
| `streams-search` | 21.15% janky | `insufficient` | §11.1 **unproven** |
| `streams-list-scroll` | 1.06% janky | `insufficient` | §11.2 **unproven** |
| `streams-grid-scroll` | 2.77% janky | `insufficient` | §11.3 **unproven** |

- 2026-08-08 - **The three frame checkpoints failed exactly as the Blockers Log predicted, and that is the harness working.** Every one returned `insufficient: true` - "under 100 frames - not comparable". Their Phase 01 baselines were already declared void for the same reason (three identical repeats spread 46-60%), so this is not a regression appearing, it is a measurement that this hardware cannot take in either direction. Quoting the raw percentages as a before/after pair would have produced a confident-looking table built on two numbers that are both noise. §11.1-§11.3 stay open and are owed to floor-tier hardware.
- 2026-08-08 - **`streams-open` was not captured, and the reason is worth recording rather than retrying blindly.** `StreamsActivity` is `android:exported="false"`, so the checkpoint reads the system's `Displayed` marker and the screen must be reached through the UI. It could not be: the entry is gated on the preference `enable_streams`, whose default is `false` (`StreamsSettingsStore.kt:48`), so it is absent from both the dropdown menu and the main-window panel until a human turns it on. While automating the settings trip the app went into an ANR ("isn't responding") under sustained `uiautomator` dumping. A cold-open timing taken from a machine in that state is not evidence, so none was recorded. This is a **device-run item**, not a code defect - no `SQLiteException`, no `FATAL`, no `AndroidRuntime` line appeared at any point.
- 2026-08-08 - **The migration was proven on a live upgraded database, which is stronger evidence than the measurement it sits beside.** Pulled `databases/fastmediasorter_v2.db` off the device after the upgrade and read it with `sqlite3`: `PRAGMA user_version` = **48**; `stream_sources` carries exactly the 17 expected columns with `lastPlayOutcome` / `lastPlayOutcomeAt` gone; `stream_play_outcome` exists with the generated schema; `index_stream_sources_url` is present after the drop-and-rename; and all **19,855** channels survived. The one path this does NOT cover is the outcome copy itself - the pre-upgrade database had no recorded outcomes (a seeded perf catalog nobody had played), so `stream_play_outcome` is legitimately empty. That path is what the instrumented `AppDatabaseMigration47To48Test` exists for, and it has not been run. Artifact: `temp/S1502/device_db_after_migration.db`.

---

## Phase-boundary audit (CLAUDE.md §13, `docs/CODE_AUDIT_PROTOCOL.md`)

Run 2026-08-08 over this phase's `Files Touched`. Layer 1 always; Layers 2, 3 and 4 because the phase changed a Room surface, a Flow topology and adapter ownership.

- **P2, fixed in phase - the outcome map was being built on the main thread.** `StreamSourceRepository.observePlayOutcomes()` ends in `.map { associate { .. } }`, and `stateIn(viewModelScope, ..)` collects on `Dispatchers.Main.immediate`; a Room `Flow` runs its query on the query executor but downstream operators run in the collector's context. So the `HashMap` build ran on the main thread, sized by the number of channels ever probed - which a full health sweep pushes toward catalog size. Small today, and still far cheaper than the whole-catalog filter/sort/diff it replaced, but it is the same mistake this ticket exists to remove, one layer down. Fixed by `.flowOn(defaultDispatcher)` in `StreamsViewModel`, reusing the dispatcher Phase 02 already injected. Layer 4 review question that caught it: *does this query run off the main thread end to end.*
- **P2, recorded not fixed - each adapter's `playOutcomes` setter scans its whole list.** Four adapters × `currentList.forEachIndexed` per emission, so an outcome write costs O(list) map lookups on the main thread even though a probe changes exactly one channel. Not fixed because the honest fix is an `id -> position` index maintained on every `submitList`, which is new machinery this step did not ask for, and because the guards already in place make it cheap in practice: the setter returns immediately when the map is unchanged, and the DAO refuses to write an unchanged outcome at all, so the emission itself does not happen for a repeat probe. Two hash lookups per row with no allocation, against the previous cost of a full filter, sort, partition and DiffUtil pass. Candidate for a follow-up if a profile ever shows it.
- **Layer 4, clean:** every new DAO method is `suspend` or returns `Flow`; `remove()` wraps its two deletes in `db.withTransaction` so a removed channel cannot strand an outcome row keyed by a dead id; the migration ships with a test and no destructive fallback; `stream_play_outcome` is keyed by `streamId` so the one-shot read hits the primary key.
- **Layers 1-3, clean:** no listener added without a matching removal (the new collection uses `collectOnLifecycle`); no new long-lived reference from an adapter to a lifecycle owner; naming follows the existing entity/DAO/use-case shape.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this phase adds public classes.
- [ ] `app_v2/schemas/*/48.json` committed.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`), with the Room entity/DAO/query/migration layer applied.

---

## Handoff Notes to Next Phase

An outcome write now touches only `stream_play_outcome`, so `observeAll()` on `stream_sources` no longer fires for it. The S1502 probe tags are in the source and stay there until the ticket leaves `BlockNeedUserTest`. Phase 05 is documentation and closure only - it adds no application source.

---

## Rollback Plan

Reverting this phase after it has shipped to a device requires a downgrade path the app does not have, because the migration recreates `stream_sources`. Roll back only before release: revert the phase commits and delete `app_v2/schemas/*/48.json`. After release, fix forward with a 48 -> 49 migration.
