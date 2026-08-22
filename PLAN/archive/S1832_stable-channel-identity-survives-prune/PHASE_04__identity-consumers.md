# Phase 04 - Launcher cells follow the identity, and the old outcome table retires

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Move the last piece of user-authored data still filed under a vanishing row id - the desktop cell that
launches a channel - onto the identity, and delete the table Phase 03 stopped reading.

---

## Prerequisites

- [x] Phase 03 is ✅ Done - nothing reads `stream_play_outcome` any more.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 310 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration52To53.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 810 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 310 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeEntity.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamPlayOutcomeDao.kt` | Deleted | - |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration52To53Test.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCaseStreamTest.kt` | New | ≤ 180 |

> `AppDatabase.kt` is over 500 LOC - take a timestamped backup before editing it (CLAUDE.md Rule 5).
> No file under `ui/` is touched: the payload behind a desktop cell changes, the surface does not.

---

## Steps

### Step 04.1 - Resolve a stream cell by identity, with an id fallback

**Files:** `LauncherCellCommand.kt`, `ExecuteLauncherCommandUseCase.kt`, `ResolveLauncherCommandLabelUseCase.kt`, `StreamSourceDao.kt`, `StreamSourceRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a lookup by `identityKey` to the DAO and expose it from the repository beside the existing
> `getById`. Both launcher entry points - the one that launches the channel and the one that resolves its
> label and icon - resolve the payload of a `stream:` cell by identity first and fall back to the row id
> when that finds nothing.
> Keep the fallback permanently rather than removing it after step 04.2's rewrite: a backup taken before
> this change carries `stream:<row id>` payloads, and `ApplyBackupPayloadUseCase` writes them straight
> into `launcher_cells` long after the migration has run.
> Update the `stream:` line in `LauncherCellCommand`'s KDoc so the payload's meaning is documented where
> the prefix is defined, and rename the `Stream` command's property away from `streamId` to a name that
> says identity, since it is no longer a row id.

**Why:**

Strategic §11 criterion 3 forbids leaving any user data attached to an identifier that no longer exists,
and research artifact 01 measured the desktop cell as the third and last kind still bound to the row id,
silently degrading to a dead cell after a prune-and-return cycle.

**Verification:**

- `Grep` - `identityKey` present in both launcher use cases.
- `Grep` - `getById` still present in both, proving the fallback survived.
- `Grep` - `val streamId` returns zero hits in `LauncherCellCommand.kt`.
- `.\a.ps1 fk` - exit 0, banner names `app_v2`.

**Status:** `[x]` done

---

### Step 04.2 - Write `Migration52To53`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration52To53.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Follow `Migration51To52`'s file shape. The migration does two things. First, rewrite every
> `launcher_cells.target` of the form `stream:<row id>` into `stream:<identity key>` by looking the row
> up in `stream_sources`; a target whose row no longer exists is left exactly as it is, because there is
> nothing to derive an identity from and the id fallback from step 04.1 is its only remaining chance.
> Second, drop `stream_play_outcome` - `Migration51To52` already copied every outcome into
> `stream_user_state`, and Phase 03 removed the last reader.
> Report how many targets were rewritten at info level, so a device log can tell "nothing to rewrite"
> apart from "the rewrite did not run".

**Why:**

Strategic Rule 20 on dead-weight hygiene requires the orphaned table to go in the same change that
stopped using it, and §11 criterion 3 is only met once the stored payloads themselves stop naming row
ids rather than merely being read tolerantly.

**Verification:**

- `Glob` - `Migration52To53.kt` exists.
- `Grep` - `MIGRATION_52_53` matches exactly once in that file.
- `Grep` - `DROP TABLE` present and names `stream_play_outcome`.
- `Grep` - `launcher_cells` present in that file.
- `Grep` - `Migration51To52.kt` is unchanged against HEAD.

**Status:** `[x]` done

---

### Step 04.3 - Register migration 53 and remove the retired entity

**Files:** `AppDatabase.kt`, `DatabaseModule.kt`, `StreamPlayOutcomeEntity.kt`, `StreamPlayOutcomeDao.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Back up `AppDatabase.kt` first (CLAUDE.md Rule 5). Raise the version from 52 to 53, drop
> `StreamPlayOutcomeEntity::class` from the entity list and remove its abstract DAO accessor. Delete both
> `StreamPlayOutcomeEntity.kt` and `StreamPlayOutcomeDao.kt`. In `DatabaseModule.kt` import and append
> `MIGRATION_52_53`, and remove the provider for the deleted DAO.
> Build once so Room emits `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/53.json`, and commit it.
> If anything still imports the deleted DAO the compile will say so - fix the caller rather than keeping
> the file.

**Why:**

Strategic Rule 20 requires orphaned classes to be deleted in the same change as the feature that
retired them, and a schema whose entity list still declares a table the migration just dropped fails
validation the next time the database opens.

**Verification:**

- `Grep` - `StreamPlayOutcome` returns zero hits across `app_v2/src/main`.
- `Grep` - `version = 53` matches exactly once in `AppDatabase.kt`.
- `Grep` - `MIGRATION_52_53` present in `DatabaseModule.kt` as import and inside `addMigrations`.
- `Glob` - `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/53.json` exists.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 04.4 - Instrumented test for migration 53

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigration52To53Test.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Seed a version-52 database with three launcher cells: one targeting a stream row that exists, one
> targeting a stream row that does not, and one non-stream cell. Run the migration to 53 with validation.
> Assert that the first cell's target now carries the row's `identityKey`, that the second is byte-identical
> to what it was, that the third is untouched, and that `stream_play_outcome` no longer exists.

**Why:**

Strategic §7 rates a migration that loses data on real devices as the risk costing the user exactly what
the ticket promises to keep, and a rewrite that silently blanks an unresolvable target would delete a
desktop cell the user placed.

**Verification:**

- `Glob` - `AppDatabaseMigration52To53Test.kt` exists.
- `Grep` - `runMigrationsAndValidate` present.
- `Grep` - `launcher_cells` present.
- `Grep` - `stream_play_outcome` present, in the assertion that it is gone.

**Status:** `[x]` done

---

### Step 04.5 - Unit-test the launcher stream resolution

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCaseStreamTest.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Follow `ResolveLauncherCommandLabelUseCaseContactTest.kt`'s shape for the sibling branch that has no
> test at all today. Assert that a cell carrying an identity resolves to the channel's label; that a cell
> carrying a legacy row id still resolves through the fallback; that a cell whose channel is absent
> resolves to null rather than throwing; and that a channel pruned and re-imported under a new row id
> still resolves from the same cell payload.

**Why:**

Research artifact 01 measured that this branch has no test covering resolution against a live
repository, which is why the cell could degrade silently for as long as it did.

**Verification:**

- `Glob` - `ResolveLauncherCommandLabelUseCaseStreamTest.kt` exists.
- `Grep` - at least four `@Test` functions in that file.
- `.\a.ps1 fu` - the new class passes.

**Status:** `[x]` done

---

### Step 04.6 - Measure the merge against the live bank

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamSourceCatalogMergeTest.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Add one test that merges a synthetic bank of 17 628 rows twice - once cold, once with the catalog
> already populated - and records the elapsed time of each `mergeCatalog` call to the test log. Assert
> only that both calls complete and that the second issues no per-row query, by asserting the row count
> and that the pin projection restored in one statement; do not assert a wall-clock threshold, which
> would be flaky on CI.
> Record the two numbers in the phase's handoff notes so `/spec-check` can compare them against the
> pre-change figure.

**Why:**

Strategic §11 criterion 4 requires the merge of 17 628 rows not to become noticeably slower, and §3.2
forbids the import turning into a per-row query, neither of which any existing test would catch.

**Verification:**

- `Grep` - `17628` or `17_628` present in the test file.
- `.\a.ps1 fu` - `StreamSourceCatalogMergeTest` passes and the elapsed figures appear in the test output.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in every file touched.
- [x] `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/53.json` committed.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - two classes deleted, one command property renamed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Corrections made while implementing

- **"No file under `ui/` is touched" was wrong.** Renaming the command's property and switching the
  write side to the identity forced five files under `src/launcherEnabled/java/..//ui/launcher/`:
  `LauncherHomeActivity`, `LauncherHomeViewModel`, `LauncherAddFlowManager`,
  `LauncherStreamPickerDialogFragment` and `StreamsGadget`. The surface did not change; the payload
  behind it is produced there.
- **There are three resolution surfaces, not two.** Besides launching and labelling, the desktop's
  long-press menu resolves a cell through `LauncherHomeViewModel.streamById`. The identity-first,
  id-second fallback therefore went into `StreamSourceRepository.getByIdentityOrId` rather than being
  written out in each caller, so the three cannot drift into resolving the same cell differently.
- **The plan only re-addressed stored cells, not the ones being written.** Two producers still emitted
  row ids: the picker that creates a cell, and the streams gadget. The gadget matters more than it
  looks - `ExecuteLauncherCommandUseCase` records the encoded command in the launch journal and counts
  it in the "most used" tally, so launching by row id files one channel under a second key every time
  the catalog reissues its id. Both now emit the identity.
- **Phase 03 left one writer of `stream_play_outcome`, not zero.** `StreamSourceRepository.remove`
  still called `deleteByStreamId`, and the repository still injected the DAO. Removed here with the
  table, along with `StreamPlayOutcomeDaoTest`, which the plan's file list did not mention.
- **Added beyond the plan: `Migration52To53SqlTest`.** The planned instrumented test validates the
  resulting schema, which only Room's instrumented helper can do - but nothing in this repository runs
  the instrumented set (S1844), so the migration would have shipped with its irreversible statements
  never executed. The new Robolectric test runs exactly those statements against real SQLite and reads
  back what they did.
- **Left deliberately unrewritten:** `launcher_journal.target` and the launch-stats key still hold
  `stream:<row id>` for launches recorded before the upgrade. Those are a recents strip and a usage
  tally, not the pin/position/history §11 protects, and both still resolve through the permanent id
  fallback.

---

## Verification actually run

- `.\a.ps1 fk` - exit 0, `app_v2` standard debug (the flavor that mounts `src/launcherEnabled`).
- `check-standard-fast.ps1 -Mode AndroidTest` - exit 0, the instrumented set compiles.
- Filtered unit run, read from the JUnit XML rather than the gradle banner: `StreamSourceCatalogMergeTest`
  10/10, `ResolveLauncherCommandLabelUseCaseStreamTest` 4/4, `Migration52To53SqlTest` 2/2,
  `AddStreamSourceUseCaseTest` 4/4, `RecordStreamPlayOutcomeUseCaseTest` 6/6,
  `UpdateStreamSourceUseCaseTest` 6/6. 32 tests, 0 failures, 0 errors.
- `53.json` generated by the build and checked for the dropped table: `stream_play_outcome` appears
  once in `52.json` and zero times in `53.json`.

---

## Handoff Notes to Next Phase

Every kind of user-authored data now keys off the identity or off the address, and nothing keys off the
row id.

**Merge timings, step 04.6** (17 628 rows, Robolectric + in-memory Room, one machine, one run): cold
import **6869 ms**, re-import of the same bank with fresh row ids **1949 ms**. The second is the number
that matters for §11 criterion 4 - it covers the identity derivation, the prune, the re-insert and the
pin projection together, and it is roughly a third of the cold path rather than a multiple of it, which
is what a per-row query would have produced.

**Not measured:** the same merge on a device, and the duration of `MIGRATION_51_52`'s backfill on a real
catalog. Both belong to the device test this ticket ends in.

---

## Rollback Plan

Revert the phase commit and delete `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/53.json`. A device already at 53 has lost
`stream_play_outcome`, whose contents `Migration51To52` copied into `stream_user_state` and Phase 03
reads from there, so no user data is lost; the reverted code reads the surviving copy.
