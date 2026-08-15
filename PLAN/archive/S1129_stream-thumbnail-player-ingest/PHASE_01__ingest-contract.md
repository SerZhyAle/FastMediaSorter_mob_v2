# Phase 01 - Ingest Contract

**Strategic spec:** [`../S1129_stream-thumbnail-player-ingest.md`](../S1129_stream-thumbnail-player-ingest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-07-20
**Completed:** 2026-07-20

---

## Objective

Introduce one DI-backed owner for validating, caching, and persisting an adopted stream frame.

---

## Prerequisites

- [x] Strategic section 6 capture research is Resolved.
- [x] Build lock was free and the code lock was held.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/streams/StreamFrameIngestor.kt` | New | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/streams/RealStreamFrameIngestor.kt` | New | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | <= 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/streams/RealStreamFrameIngestorTest.kt` | New | <= 220 |

---

## Steps

### Step 01.1 - Define the ingest contract

**Files:** `domain/streams/StreamFrameIngestor.kt`

**Prompt for developer:**

> Define a suspend contract that adopts one bitmap for one stream URL and returns whether the frame was accepted. Keep cache and disk implementation details out of the player layer.

**Verification:**

- Contract contains exactly one `ingest(url, bitmap)` operation returning `Boolean`.
- File contains no dependency on player or streams UI packages.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. Defined the UI-independent ingest contract.

### Step 01.2 - Implement validation, cache, and persistence

**Files:** `data/repository/streams/RealStreamFrameIngestor.kt`

**Prompt for developer:**

> Implement the contract as a singleton over `StreamFrameCache` and `StreamFramePersistentStore`. Reject recycled, empty, or nearly-black bitmaps before any write; put accepted frames in memory and persist them off-main through the existing store.

**Verification:**

- Accepted frames call both `StreamFrameCache.put` and `StreamFramePersistentStore.save`.
- Rejected frames call neither writer.
- Thresholds are named constants; no bare numeric literals are introduced.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 3/3 PASS. Added quality rejection, cache adoption, and off-main persistence.

### Step 01.3 - Bind the implementation

**Files:** `core/di/RepositoryModule.kt`

**Prompt for developer:**

> Add one singleton Hilt binding from `RealStreamFrameIngestor` to `StreamFrameIngestor`; do not add a scope or qualifier.

**Verification:**

- `RepositoryModule` contains exactly one binding for `StreamFrameIngestor`.
- The implementation constructor is injectable.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. Bound the singleton implementation without a new scope or qualifier.

### Step 01.4 - Cover adoption policy

**Files:** `RealStreamFrameIngestorTest.kt`

**Prompt for developer:**

> Add focused tests for a normal frame, an all-black frame, and an invalid frame. Assert accepted/rejected results and writer interactions.

**Verification:**

- Targeted unit test command exits 0.
- Tests assert both memory and disk write behavior.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Verification 2/2 PASS. Three focused Robolectric tests passed and asserted both writers.

---

## Phase Done Criteria

- [x] Every Step 01.* is `[x] done`.
- [x] `./a.ps1 fk` passes.
- [x] Targeted ingest tests pass.
- [x] Phase-boundary audit reports no P0/P1 finding.

## Last Audit

- 2026-07-20 - P0: none; P1: none; P2: none; P3: none.
- Architecture: the domain contract has no UI/data dependency; Hilt owns one singleton implementation.
- Concurrency/memory: persistence remains main-safe through the existing IO boundary; cache size and eviction remain bounded.
- Evidence: targeted tests 3/3 PASS, `a.ps1 fk` PASS, minified standardRelease APK packaged and launched.
- The release smoke script's sole raw marker was an unrelated launcher warning for a missing
  `com.android.systemui` widget description; app-only errors were 0 and `WelcomeActivity` was resumed.

---

## Handoff Notes to Next Phase

Player code may depend only on `StreamFrameIngestor`, not the cache or persistent store.

---

## Rollback Plan

Revert the new contract, implementation, test, and Hilt binding; no stored-data format changes.
