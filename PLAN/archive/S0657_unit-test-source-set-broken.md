# S0657 - Unit test source set fails to compile (stale tests)

**Ticket:** S0657
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-24

> Draft inbox item parked by `/spec-all` (S0654 run). Raw capture only - no research/approval done.

## 0. Raw capture

While running the S0654 unit-test gate (`:app_v2:testStandardDebugUnitTest`), the test source set failed to **compile** - blocking any unit test from running. The failures are pre-existing and unrelated to S0654 (S0654's own touched tests `FavoritesUseCaseTest` and `ExecuteScheduledOperationUseCaseTest` compile fine).

Symptom: `compileStandardDebugUnitTestKotlin FAILED` with `Unresolved reference` errors - test files reference production symbols that appear to have been renamed/removed (test drift).

Sample unresolved references:
- `ShareTargetApplicabilityTest.kt` -> `appliesTo`
- `core/util/MediaFormatUtilsTest.kt` -> `formatMediaDuration`
- `ui/player/helpers/PlayerMediaFilesLoaderReconcileTest.kt` -> `reconcileFavoriteFlags`, `isFavorite`

Full list of test files that failed to compile in the run (≈19):

- core/share/ShareTargetApplicabilityTest.kt
- core/util/MediaFormatUtilsTest.kt
- data/hash/LocalFileHasherTest.kt
- data/link/cookie/LinkCookieDomainResolverTest.kt
- data/network/exceptions/RetryPolicyTest.kt
- data/network/glide/NetworkResourceKeyTest.kt
- data/remote/ftp/FtpCommandUtilsTest.kt
- data/remote/ftp/FtpEncodingSupportTest.kt
- data/transfer/CloudProgressAdapterTest.kt
- data/transfer/strategy/StrategyUtilsTest.kt
- domain/model/ResourceFormDataTest.kt
- domain/transfer/ProgressTrackerTest.kt
- ui/browse/managers/BrowseCommandBarAllocationTest.kt
- ui/keybinding/helpers/KeybindingDeviceOptionsTest.kt
- ui/player/VideoPlayerManagerRouteErrorTest.kt
- ui/player/callbacks/PlayerPlaybackCallbackImplTest.kt
- ui/player/helpers/BdTsPlaybackHelperTest.kt
- ui/player/helpers/PlayerMediaFilesLoaderReconcileTest.kt
- ui/settings/SettingsAllFilesOverrideTest.kt

Impact: the whole `app_v2` unit suite cannot run on a clean invocation; per-class verification is only possible after the compile errors are resolved. This is the documented "verify via assembleStandardDebug / per-class XML" working condition - this ticket is to actually fix the drift so the suite compiles again.

Evidence log: `temp/s0654_test.log` (from the S0654 run, may be overwritten later).

## 1. Next step

`/spec` to triage scope (decide fix-vs-delete per stale test) -> `/spec-tech` -> `/spec-dev`.

## Last Audit

Verified 2026-06-24 - no longer reproduces. The captured compile drift was already resolved in the working tree by other ticket work landed after the 2026-06-24 02:01 capture (working tree is truth; the S0654-run log is stale).

Evidence:

- `:app_v2:compileStandardDebugUnitTestKotlin --rerun-tasks` (clean, no cache) -> BUILD SUCCESSFUL in 1m 36s. The whole test source set compiles as one unit, so a single unresolved reference would have failed the task and run zero tests.
- Corroborating: the S0658 run earlier today executed `:app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest` and 3 tests actually ran - impossible unless the entire test source set compiled.
- The three sample symbols flagged as unresolved now exist in production and the stale tests reference them correctly: `ShareTarget.appliesTo` (extension in `core/share/ShareTarget.kt`), `formatMediaDuration` (`core/util/MediaFormatUtils.kt`), `reconcileFavoriteFlags` (`ui/player/helpers/PlayerMediaFilesLoader.kt`). The tests were fixed, not deleted - coverage intact.

Scope was "fix the drift so the suite compiles again"; that acceptance criterion is met and proven. No code change required this run. Out of scope: any remaining *runtime* unit-test failures (the documented ~26 pre-existing failing tests) are a separate concern from this compile-drift ticket.
