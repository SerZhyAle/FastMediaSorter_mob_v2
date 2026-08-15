# Phase 04 - Data Repositories & Local Persistence

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Add JVM unit tests for repository implementations and the local persistence layer (Room DAO/query logic via in-memory DB, preferences, staging), per strategic §6.3.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`InMemoryRoomHelper`, fakes available).
- [ ] `COVERAGE_INVENTORY.md` Phase 04 rows present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/*Test.kt` | New | ≤ 400 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/{preferences,staging}/*Test.kt` | New | ≤ 300 each |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/{observer,paging}/*Test.kt` | New | ≤ 300 each |

> Test-only source set. Repositories tested with faked data sources unless the row is a DAO/query whose own logic is under test (then in-memory Room). Robolectric only where an Android type cannot be faked.

---

## Steps

### Step 04.1 - Cover `data/repository`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For each in-scope `data/repository` implementation, add a `*Test.kt` covering its orchestration logic: source selection, mapping of source results to domain models, caching/merge rules, and error propagation. Inject `testing/fakes` data sources; assert observable results and recorded calls. Update inventory rows.

**Verification:**

- `Grep` - each in-scope `data/repository` class has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 04.2 - Cover `data/local/db` (DAO/query logic via in-memory Room)

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/*Test.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> For in-scope `data/local/db` classes whose own query/mapping logic warrants it, add tests using `InMemoryRoomHelper` (real in-memory DAO). For thin DAOs without logic, mark out-of-scope in the inventory rather than testing the framework. Do not bump `@Database` version or add migrations - this phase adds no schema change. Update inventory rows.

**Verification:**

- `Grep` - each in-scope `data/local/db` class has a matching `*Test.kt`.
- `Grep` - `inMemoryDatabaseBuilder` referenced where a real DAO is exercised.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 04.3 - Cover `data/local/{preferences,staging}`, `data/observer`, `data/paging`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/local/{preferences,staging}/*Test.kt`, `.../data/{observer,paging}/*Test.kt`
**Depends on:** - independent

**Prompt for developer:**

> Add tests for in-scope classes in `data/local/preferences`, `data/local/staging`, `data/observer`, and `data/paging`, covering preference serialization/defaults, staging state transitions, observer dispatch, and paging boundaries. Update inventory rows.

**Verification:**

- `Grep` - each in-scope class in these packages has a matching `*Test.kt`.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 04.4 - Green-run Phase 04 tests

**Files:** - (validation only)
**Depends on:** Steps 04.1–04.3

**Prompt for developer:**

> Run Phase 04 test classes; confirm each passes via per-class XML reports. Do not fix unrelated pre-existing red tests; do not add new red.

**Verification:**

- Each new Phase 04 test class XML shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleStandardDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`. 18 new test classes (163 methods); 15 rows reclassified `out`; inventory Phase 04: 0 in-scope rows remain untested. `expected: 0 | actual: 0`.
- [x] Project compiles - `:app_v2:compileStandardDebugUnitTestKotlin` exit 0.
- [x] All new Phase 04 test classes green per per-class XML (`failures="0" errors="0"`); no new red. Room DAO logic (ResourceDao, FileMetadataCacheDao) covered via in-memory Room + Robolectric.
- [x] `Grep` for `TODO(phase-04)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new files.
- [x] No `@Database` version change introduced by this phase.
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Covered by one `android-kotlin-developer` batch. Repository impls + DataStore/preferences (pure JVM), DAO query logic via in-memory Room/Robolectric. Thin Room-generated DAOs reclassified `out` (covered through repositories). Adjacent observation: `FileMetadataCacheDao.upsert` dedupes on PK, not the `(resourceId, filePath)` index.

---

## Handoff Notes to Next Phase

Repository and local-persistence rows covered. Phase 05 covers network and remote data sources; Phase 07 will override repository/source contracts in flavor source sets.

---

## Rollback Plan

Delete the new `data/{repository,local,observer,paging}/**/*Test.kt` files. No production code changed.
