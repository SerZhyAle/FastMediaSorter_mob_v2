# Tactical: S0248 — SMB orchestration optimizations

**Ticket:** S0248
**Status:** BlockNeedUserTest
**Strategic:** [`PLAN/S0248_smb-orchestration-optimizations.md`](../S0248_smb-orchestration-optimizations.md)
**Tier:** 3 — Moderate

> Implements S0246 path (b) — corrected versions of items 10/11/12/13 from S0237 rollback, plus two-phase emit. Library-agnostic; all changes in `src/main/java`.

---

## Baseline (verified 2026-05-18 by /spec-all discovery)

Three deltas from strategic spec's stated baseline — record before patching:

1. **Room version is 30** (not v6 as §6 wording suggested). All migrations live in `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`. New migration: **30 → 31**.
2. **Per-file metadata cache table already exists:** `file_metadata_cache` (entity `FileMetadataCacheEntity`). The `metadataState` column lands here — NOT on `MediaFilesCacheManager` (which is an in-memory `LruCache<Long, MutableList<MediaFile>>`, unrelated to per-file persistence). Strategic spec's "MediaFilesCache" wording conflates the two; tactical resolves: `metadataState` belongs on `file_metadata_cache`.
3. **`ConnectionThrottleManager.ProtocolLimits.SMB` is currently `(2, 1)`**, not `(4, ...)` as strategic spec §4 stated. The "raise base from 4 to 8 for header-only" goal applied to actual code becomes "raise base from 2 to 8 for header-only, keep full-metadata at 3". Owner expectation §2.5 ("header-only=8, full-video-metadata=3") still holds; only the baseline number in spec narrative is stale.

---

## Affected files (discovered)

| Phase | File | Current LOC | Touch type |
|---|---|---|---|
| 1 | `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheEntity.kt` | 93 | add column |
| 1 | `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | 760 | bump version 30→31 + add `MIGRATION_30_31` |
| 1 | `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/FileMetadataCacheDao.kt` | (read in phase) | accept `metadataState` on insert/update if not auto-mapped |
| 1 | new: `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MetadataState.kt` | new (~20) | enum `COMPLETE`/`PARTIAL`/`BROKEN` |
| 2 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt` | 681 | wrap per-file EXIF/video reads with `withTimeout` + aggregated metric |
| 3 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt` | (same) | write PARTIAL/BROKEN state on timeout/error; emit retry on PARTIAL every scan |
| 3 | `app_v2/src/main/java/com/sza/fastmediasorter/core/cache/FileMetadataCacheManager.kt` (or equivalent repository) | tbd | propagate `MetadataState` field on persist + read |
| 4 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbDirectoryScanner.kt` | tbd | secondary defensive in-flight coalescer (`ConcurrentHashMap<DirPath, Deferred<ListResult>>`) |
| 4 | (coordinator that double-calls listing) | tbd | primary fix — investigation in phase 4 |
| 5 | `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt` | 449 | emit listing-only PENDING batch before enrichment; background enrich with visible-first priority |
| 5 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt` | 555 | introduce `SMB_HEADER` and `SMB_FULL_METADATA` ProtocolLimits variants; per-resource adaptive override (halve-on-timeout, never raise) |

---

## Phase 1 — Room migration v30 → v31: add `metadataState` column

Goal: persist enrichment state with each per-file cache row so the scanner can retry PARTIAL on every scan, BROKEN only on user-triggered refresh, and treat legacy rows as COMPLETE.

### Steps

1. Create `MetadataState.kt` in `domain/model/` with enum: `COMPLETE`, `PARTIAL`, `BROKEN`. Stored as `TEXT` in Room (default `COMPLETE`).
2. Add to `FileMetadataCacheEntity`: `val metadataState: String = "COMPLETE"` (default for compile-time + Room default). Place after `title` field; keep nullable-pattern minimal (NOT NULL with DEFAULT 'COMPLETE' — Room maps Kotlin default to SQL DEFAULT).
3. Bump `AppDatabase.version` from `30` → `31`.
4. Add `MIGRATION_30_31`:
   ```sql
   ALTER TABLE file_metadata_cache ADD COLUMN metadataState TEXT NOT NULL DEFAULT 'COMPLETE'
   ```
   Wrap in `if (!hasColumn(db, "file_metadata_cache", "metadataState"))` for idempotency (matches existing repo style — see MIGRATION_15_16 etc.).
5. Register the migration in `Room.databaseBuilder(...).addMigrations(...)` chain — locate it in the DI module that builds `AppDatabase` (typically `DatabaseModule` in `di/`).
6. Update `FileMetadataCacheDao` if it has hand-rolled `@Insert`/`@Update` queries (most likely it uses entity inserts and needs no changes — verify in implementation).
7. **Backup rule:** `AppDatabase.kt` is 760 LOC (> 500). Take a timestamped backup to `temp/AppDatabase_<ts>.kt` before edit.

### Verification

- `expected:` Grep `version = 31` returns **exactly 1** occurrence in `AppDatabase.kt`; `version = 30` returns **0**.
- `expected:` `MIGRATION_30_31` symbol referenced in DI module's `.addMigrations(...)`.
- `expected:` `FileMetadataCacheEntity` contains `metadataState: String` field with `"COMPLETE"` default.
- `expected:` `.\a.ps1 dq` exits 0; Room annotation processor accepts the new schema (kapt step `kaptStandardDebugKotlin` clean).

---

## Phase 2 — Per-file metadata timeout in `SmbMediaScanner`

Goal: prevent one stuck file from blocking siblings. EXIF/ID3 budget = 1500 ms; video two-tier 500 ms quick-probe → escalate to 2000 ms on slow-path signature.

### Steps

1. Locate the per-file enrichment loop in `SmbMediaScanner.kt`. From discovery, the class is 681 LOC; find the loop where `ExifInterface(...)` is called (image branch) and where `MediaMetadataRetriever` is set up (video branch).
2. Wrap EXIF/ID3 reads with `withTimeoutOrNull(1500L) { … }`. On null return → fall back to header-only result (file name + size + type only, no exif/duration/dimensions/etc.).
3. Wrap video reads with two-tier:
   - First attempt: `withTimeoutOrNull(500L) { retriever.setDataSource(...); val haveQuickHeader = ... }`.
   - If quick probe returned headers → done.
   - Else escalate: `withTimeoutOrNull(2000L) { full video probe }`.
   - On second timeout → header-only fallback.
   - "Slow-path signature" = absence of duration / videoWidth in quick probe. Don't try to parse moov-at-tail by hand — the timeout escalation IS the slow-path mitigation.
4. Aggregate metric: increment a per-scan `AtomicInteger` on each timeout fallback; at end of scan emit **one** `Timber.d("SmbMediaScanner: timeout-fallback count=$count totalFiles=$total path=$path")` if count > 0. No per-file warn (avoids S0169 warn-spam regression).
5. Return PARTIAL state with the file when fallback engaged (consumed by phase 3 to persist state).

### Verification

- `expected:` `withTimeoutOrNull(1500L` appears ≥ 1 time in `SmbMediaScanner.kt`.
- `expected:` `withTimeoutOrNull(500L` AND `withTimeoutOrNull(2000L` each appear ≥ 1 time.
- `expected:` zero new occurrences of `Timber.w(` from per-file timeout (avoids warn-spam).
- `expected:` `.\a.ps1 dq` exits 0.

---

## Phase 3 — Partial-cache persistence

Goal: PARTIAL records are stored, returned to caller with state, retried on every scan; BROKEN retried only on user-triggered refresh; no TTL.

### Steps

1. Extend the metadata cache repository (the wrapper around `FileMetadataCacheDao`) to accept a `MetadataState` on write and return it on read. Find the repository class (likely `FileMetadataCacheRepository` or similar — confirm in implementation step).
2. In `SmbMediaScanner` write path: when phase-2 timeout fired → write `state=PARTIAL`; on hard error (e.g. exception other than timeout) → write `state=BROKEN`; success → `state=COMPLETE`.
3. In scan flow: when reading existing cache row:
   - `COMPLETE` → use as-is.
   - `PARTIAL` → re-attempt enrichment (every scan).
   - `BROKEN` → use cached partial result, do NOT re-attempt unless caller passed a `forceRefresh: Boolean = false` flag set true (wired only from explicit user refresh action).
4. Surface `metadataState` up to `MediaFile` domain model so `GetMediaFilesUseCase` can decide visibility / sort behaviour. Add `val metadataState: MetadataState = MetadataState.COMPLETE` to `MediaFile`. Legacy callers default to COMPLETE — no breakage.

### Verification

- `expected:` `MediaFile.kt` contains `metadataState: MetadataState` field with default `COMPLETE`.
- `expected:` cache repository write path has three branches (COMPLETE / PARTIAL / BROKEN).
- `expected:` user-refresh callsite passes `forceRefresh = true` exactly once (locate in `BrowseViewModel` or equivalent — confirm in implementation).
- `expected:` `.\a.ps1 dq` exits 0.

---

## Phase 4 — Listing dedup (primary + secondary)

Goal: fix the double-listing root cause in the coordinator (primary) and add a cheap defensive in-flight coalescer in the scanner (secondary).

### Steps

1. **Primary investigation:** grep for `scanNonRecursive(` callsites. Trace which coordinator entry calls it twice per `dirPath` within < 1 ms (per S0246 §4.2 / S0248 §4 evidence: "two `Found N files in root` logs in the same millisecond"). Candidate hotspots: `BrowseEventHandler` (UI side), `GetMediaFilesUseCase` (use case), `MediaScanCoordinator` (if exists), `resource-load coordinator` per strategic spec language.
2. **Primary fix:** eliminate the second call. Usually one of: (a) two observers both triggering load; (b) a `flow.collect` that fires on both `Loading` and `Refresh`; (c) configuration-change re-init missing a guard. Fix at root, leave a one-line comment `// S0248: prevent double-listing — root cause was <X>`.
3. **Secondary defensive:** in `SmbDirectoryScanner` (or wherever `scanNonRecursive` lives), add an in-flight coalescer:
   ```kotlin
   private val inFlight = ConcurrentHashMap<String, Deferred<List<...>>>()

   suspend fun scanNonRecursive(dirPath: String): List<...> = coroutineScope {
       inFlight.getOrPut(dirPath) {
           async {
               try { rawScanNonRecursive(dirPath) }
               finally { inFlight.remove(dirPath) }
           }
       }.await()
   }
   ```
   Remove on success AND on failure (entry always evicted in `finally`).
4. Log dedup hits at `Timber.d` level (so they're visible during debugging but not in release noise). Single line: `Timber.d("SmbDirectoryScanner: dedup hit on $dirPath")` — fired only when `getOrPut` found an existing entry (the existing-key path).

### Verification

- `expected:` `ConcurrentHashMap<String, Deferred<` appears exactly 1 time in `SmbDirectoryScanner` (or wherever).
- `expected:` `inFlight.remove(` appears exactly 1 time in a `finally` block.
- `expected:` primary fix has explanatory comment `// S0248:`.
- `expected:` `.\a.ps1 dq` exits 0.

---

## Phase 5 — Two-phase emit + concurrency split

Goal: UI receives the listing (name/size/type) right after directory scan; metadata fills in incrementally with visible-row priority. SMB concurrency split: header-only=8, full-metadata-video=3, per-resource adaptive halve-on-timeout.

### Steps (5a — two-phase emit)

1. In `GetMediaFilesUseCase`, refactor `invoke()` so it emits a **PENDING** batch (listing-only, `metadataState=PARTIAL` on each entry that hasn't been enriched yet) BEFORE awaiting enrichment.
2. Trigger background enrichment via a separate coroutine in the same scope; on each completion, update the cache + emit an incremental delta (use `Flow<List<MediaFile>>` if not already; reuse current emit pattern if it's a `StateFlow` — confirm in implementation).
3. UI prioritisation: enrichment processes a priority queue ordered by "visible rows first" — the simplest implementation is a `Channel<EnrichRequest>` fed from the UI (visible window) AND a tail-fed worker that processes off-screen rows. Skip the priority-queue complexity if a simple "front-of-list first" pass keeps UI smooth; revisit in audit.
4. `ScanMetrics` instrumentation: add `listing_complete` event (new) alongside existing `enrichment_complete` / `scan_complete`. SLOW SCAN threshold becomes background-only.

### Steps (5b — concurrency split)

1. Add two new ProtocolLimits enum values to `ConnectionThrottleManager`:
   ```kotlin
   SMB_HEADER(8, 1),         // header-only metadata reads (EXIF, ID3 quick) — raised from 2 to 8
   SMB_FULL_METADATA(3, 1),  // MediaMetadataRetriever full video probe — RAM-bound, Jellyfin-validated 1..3
   ```
2. Keep the existing `SMB(2, 1)` entry for **non-metadata SMB operations** (open/read for playback, copy, delete) — those are throughput-bound, not metadata-bound, and the existing limit has its own history.
3. Update `SmbMediaScanner` callsites: header-only EXIF reads use `SMB_HEADER`; video full-metadata reads use `SMB_FULL_METADATA`.
4. **Per-resource adaptive override** (already partially present in ProtocolState — see `currentLimit`, `consecutiveTimeouts`): on a read-timeout for a given host, halve `currentLimit` (never below `minConcurrent`). No upward auto-tune. Document the halving step in a class-level KDoc block (`// S0248: per-resource adaptive halve-on-timeout, no upward auto-tune`).

### Verification

- `expected:` `enum class ProtocolLimits` contains `SMB_HEADER` and `SMB_FULL_METADATA` entries with `maxConcurrent` 8 and 3 respectively.
- `expected:` `GetMediaFilesUseCase` has at least **2** emit/`Flow.emit`/`send` calls in `invoke()` — one for PENDING, one for COMPLETE (per file or per batch). Number-exact predicate locked in implementation.
- `expected:` no per-file warn from enrichment on listing (still aggregated phase-2 metric only).
- `expected:` `.\a.ps1 dq` exits 0; `assembleStandardDebug` reaches BUILD SUCCESSFUL.

---

## Cross-cutting requirements

- **Flavor isolation:** every change lands in `src/main/java/**`. No `BuildConfig.IS_*` gates introduced (CLAUDE.md Rule 15).
- **Logging:** Timber only. No `Log.d`. Per-scan metric → 1 `Timber.d` line, never `Timber.w`.
- **Backup rule:** any file > 500 LOC touched → `temp/<filename>_<timestamp>.kt` backup before edit. Affected: `AppDatabase.kt` (760), `ConnectionThrottleManager.kt` (555), `SmbMediaScanner.kt` (681).
- **Localization:** no new user-facing strings expected. If implementation surfaces a need (e.g. "metadata loading" indicator) → trigger EN/RU/UK trio + `scripts/check_strings_localized.ps1`. Default: silent.
- **Accessibility:** two-phase emit must NOT change TalkBack reading order — file name appears in PENDING batch, additional fields append after enrichment.
- **No layout edits expected.** If implementation discovers a list-row layout change for PARTIAL state → check both `res/layout/*.xml` AND `res/layout-land/*.xml` (Rule 12).
- **Tests:** Room migration ships with an `androidTest` that verifies `MIGRATION_30_31` preserves data and that legacy rows map to `metadataState = "COMPLETE"`. Existing migration tests in `app_v2/src/androidTest/` show the pattern.

---

## Phase order (sequential, single PR)

1. Phase 1 (Room migration) — foundational. Build must pass before Phase 3 touches the entity.
2. Phase 2 (per-file timeout) — independent of Phase 1; can swap with Phase 1 if more convenient, but recommended after Phase 1 so PARTIAL state writes can be tested.
3. Phase 3 (partial-cache persistence) — depends on Phases 1 + 2.
4. Phase 4 (listing dedup) — independent; could be done first as a quick win, but the strategic spec orders it 4th to match risk profile (coordinator investigation has unknowns).
5. Phase 5 (two-phase emit + concurrency split) — depends on Phase 3 (needs `metadataState` on `MediaFile`) and Phase 4 (must not double-emit due to coordinator double-call).

Build sanity check after EACH phase. Final build before audit. Audit via `/spec-check`.

---

## Non-goals

- Cloud / SFTP / FTP scanner changes — out of scope; if measurement shows the same problem there, separate ticket.
- Wear OS module — not touched.
- `SmbDataSource` (playback path) — separate concern; S0247 is the spike for transfer throughput.
- New user-visible strings.
- Refactoring `ConnectionThrottleManager` history of `SMB(2,1)` — keep `SMB(2,1)` for non-metadata ops; new variants are additive.
