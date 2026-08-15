# S0954 - Paged media sort applies per-page; but the whole Paging3 browse path is dead

**Status:** Archived
**Priority:** 60
**Date:** 2026-07-05
**Tier:** 3 - Moderate (ad-hoc)

<!-- parked by S0905 audit sweep (Layer 6) - 2026-07-05; investigated by /spec-all - 2026-07-05 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 6 static perf sweep.

Symptom: for every sort mode except `NAME_ASC`/`NAME_DESC`, `MediaFilesPagingSource.load()` sorts only the current 50-item page (`PAGE_SIZE = 50`) instead of the full dataset, so rows are globally out of order across page boundaries. The code self-documents this as a known limitation.

Evidence:
- `MediaFilesPagingSource.kt:48-61` - per-page `sortFiles()`; line 58 `Timber.w("Pagination with sort mode $sortMode may produce incorrect order..")`.
- Related O(n log n) selector recompute: `sortedWith(compareBy { it.artist?.lowercase() ?: "" }.thenBy { it.name.lowercase() })` re-invokes `.lowercase()` on every comparison (`MediaFilesPagingSource.kt:86-101`, duplicated `GetMediaFilesUseCase.kt:430-445`).

Severity (as captured): P1 (data-ordering correctness).

## 1. Investigation (2026-07-05)

The captured P1 is **latent - the Paging3 browse path is dead code and is never invoked** at runtime. The active browse flow always uses the full-scan `loadFilesStandard`, which sorts the complete dataset (`BrowseSortFilterManager.sortFiles`, `forceSort = true` for large/subfolder), so global order is correct.

Reachability trace (all in `ui/browse`):
- `MediaFilesPagingSource` is constructed only by `BrowseLoadingManager.setupPagination()`.
- `setupPagination()` is called only by `BrowseResourceLoadManager.loadMediaFilesWithPagination()`.
- `loadMediaFilesWithPagination()` has **zero callers** (grep whole tree). The scan entry point `loadMediaFiles()` calls only `loadMediaFilesStandard()` (lines 362/367).
- The produced `pagingDataFlow` is exposed on `BrowseViewModel` but **never collected**; there is no `submitData(..)` anywhere in the tree.
- `BrowseState.usePagination` is set `true` only inside the dead `loadMediaFilesWithPagination()`; every live `updateState(..)` passes `usePagination = false`. So `BrowseFileOpenManager`'s `if (usePagination)` branch is also dead.

Consequence: the mis-ordering cannot manifest for a user today. The `Timber.w` "known limitation" line is misleading - it describes a code path that never runs.

Note: `scanFolderPaged` itself is NOT fully dead - `SftpMediaScanner.scanFolder` calls its own `scanFolderPaged` internally (limit 1000) as a chunking helper, and there are scanner unit tests. So the scanner interface method stays; only the browse-level Paging3 wiring is dead.

## 2. The fork (RESOLVED 2026-07-05 via /spec-quiz -> Option A: remove)

**Owner decision: Option A - remove the dead Paging3 browse subsystem.** The live full-scan path already sorts correctly and serves large folders; reviving would be a fresh multi-scanner feature project, not a resurrection of half-wired code. If lazy-loading for huge folders is ever wanted, it is a new, properly-scoped ticket. This resolution also **subsumes S0955** (its target adapters are deleted here).

Whether to remove or revive was **not codebase-determined**: the Paging3 path was built deliberately (Paging3 `Pager`, `MediaFilesPagingSource` + unit test, prefetch config) and later disabled, not deleted. The code showed it was off, not *why* - hence the owner call.

### Quiz decisions (2026-07-05)

- Remove the dead Paging3 subsystem or revive+fix? -> **Remove (Option A)** (provably dead; live full-scan path already correct for large folders; revival would be a fresh feature ticket, not a cleanup; subsumes S0955).

Not chosen: Option B (revive + push `ORDER BY`/sort-mode into all 4 scanners) - a real lazy-loading feature project, deferred to a fresh ticket if huge-folder paging is ever wanted.

Separable follow-up (P3, not correctness): the live `sortFiles` lowercase comparator recompute (`GetMediaFilesUseCase.kt:430-445`) can get a Schwartzian/precomputed-key transform - tiny, independent of this removal.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0905 (audit-tail sweep, source), S0955 (subsumed - dead paging adapters deleted here), S0958 (deadweight player-activity - sibling dead-weight cleanup pattern).
- **Flavor scope:** all edits live in `src/main` (`ui/browse` + `data/paging`); applies to all flavors, no `BuildConfig` specifics. Removal only - no user-visible behavior change (the code never ran).
- **Scope of removal:** `MediaFilesPagingSource` (+ `MediaFilesPagingSourceTest`), `PagingMediaFileAdapter`, `PagingLoadStateAdapter`, `BrowseLoadingManager.setupPagination`, `BrowseResourceLoadManager.loadMediaFilesWithPagination`, `BrowseViewModel._pagingDataFlow`/`pagingDataFlow`/`setPagingDataFlow`, `BrowseState.usePagination` + its dead branches (`BrowseFileOpenManager`, `BrowseStateSyncManager`). Keep `scanFolderPaged` on the scanner interface (SFTP uses it internally). Verify on the minified target variant + a browse smoke pass.

## Related

- S0905 (audit-tail sweep, source); docs/CODE_AUDIT_PROTOCOL.md Layer 6.
- S0958 - deadweight player-activity class (adjacent dead-weight removal).

## Last Audit

Device test by `/spec-test-device` on emulator-5554 (Pixel 4 AVD, Android 17 / API 37), standard-debug
2.60.7041.926-DEBUG. Scenario + full log findings: temp/S0954/mobile_test_scenario_20260706_2327.md.

Verdict: **PASS** - behavior-preserving removal confirmed on-device. The live full-scan browse path
renders correct global order across sort modes on the full 40-file recursive dataset; file open resolves
the right index and fires the S0954 probe; multi-select and favorites work; zero runtime references to the
removed Paging3 classes; no app crash.

### Manual / on-device

- [x] Large folder renders in correct global order per sort mode - verified on-device 2026-07-06 (Name/Size/Type; `BrowseSortFilterManager.setSortMode ... re-sorting cache (40 files)`, size 10.97>7.92>..>2.60 MB across subfolders)
- [x] Open a file -> opens at right index; probe "S0954: browse openFile index resolve" fires - verified on-device 2026-07-06 (probe DEBUG count=1; player opened tapped index-0 file)
- [x] Multi-select still works - verified on-device 2026-07-06 (header "2 selected" + batch ops toolbar; Deselect All clears)
- [x] Favorites still work - verified on-device 2026-07-06 (`FavoritesUseCase.toggleFavorite: ADDED successfully`, adele_skyfall.mp3)
- [x] No dead Paging3 code invoked / no crash - verified on-device 2026-07-06 (0 refs to MediaFilesPagingSource/PagingMediaFileAdapter/usePagination/loadMediaFilesWithPagination; no app FATAL)

Coverage note: expected `>500-file` folder not literally reproducible on this AVD seed (max ~40 recursive
files). The threshold was only meaningful for the removed page-boundary path (`PAGE_SIZE=50`), which no
longer exists; global ordering across 40 interleaved files is decisive for the behavior-preserving claim.
Recommended R8/minified release-variant proof (Rule 20) not covered by this debug run.

## Revision History

- **2026-07-06** - by `/spec-test-device` (claude-opus-4-8[1m], device: emulator-5554 Android 17/API 37)
  - Scenario: temp/S0954/mobile_test_scenario_20260706_2327.md - PASS/FAIL/SKIPPED 5/0/0 - Errors in log: 0
