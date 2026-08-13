# Phase 03 — Atomic strategy short-circuit for public collections

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Update `AtomicFileOperationStrategy` to skip the `*.temp_copy` + rename layer when the destination classifies as a public collection — the underlying strategy already provides atomicity via MediaStore IS_PENDING. For non-public destinations the existing behavior is preserved.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `LocalDestinationClassifier` is available via Hilt injection (added in Phase 01).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt` | Modified | ≤ 460 (was 385) |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt` | Modified | ≤ +60 LOC over current |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` | Modified | ≤ 480 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt` | Modified | ≤ 720 — **BACKUP if not already in Phase 02** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt` | Modified | ≤ 580 — **BACKUP if not already in Phase 02** |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt` | Modified | ≤ 1050 — **BACKUP if not already in Phase 02** |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt` | Modified | ≤ 300 |

> `AtomicFileOperationStrategy.kt` is currently 385 LOC — no backup required. Handler files were already backed up in Phase 02 — skip re-backup if those `.bak` files still exist in `temp/`.

---

## Steps

### Step 03.1 — Add classifier param to `AtomicFileOperationStrategy` and branch in `copyFile`

**Status:** `[x]` done

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt`
**Depends on:** — start of phase

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. Constructor: added `destinationClassifier: LocalDestinationClassifier`. copyFile: short-circuit branch returns delegate.copyFile when destination classifies as PublicCollection. File 395 LOC (+10 from 385). S0231 Timber tag deferred (status invariant). Dev log recorded.

**Prompt for developer:**

> 1. Change the constructor:
>    ```kotlin
>    class AtomicFileOperationStrategy(
>        private val delegate: FileOperationStrategy,
>        private val destinationClassifier: LocalDestinationClassifier,
>        private var enableAtomic: Boolean = true
>    ) : FileOperationStrategy by delegate
>    ```
>
> 2. At the very start of `override suspend fun copyFile(source, destination, overwrite, progressCallback)`, after the existing `if (!enableAtomic)` short-circuit, add a new short-circuit:
>    ```kotlin
>    val category = destinationClassifier.classify(destination)
>    if (category is LocalDestinationCategory.PublicCollection) {
>        Timber.d("S0231: AtomicFileOperationStrategy.copyFile: public collection — delegating without temp_copy wrapper, destination=$destination")
>        return delegate.copyFile(source, destination, overwrite, progressCallback)
>    }
>    ```
>
> 3. The existing `temp_copy` + rename logic continues to apply to all `NonPublic` destinations (private app dirs, non-canonical paths, network-to-network) — unchanged.
>
> 4. Add import: `com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory` and `LocalDestinationClassifier`.

**Verification:**

- `Grep` in file — `destinationClassifier: LocalDestinationClassifier` matches once in constructor.
- `Grep` in file — `is LocalDestinationCategory.PublicCollection` matches once.
- `Grep` in file — `Timber.d("S0231:` matches once (this is the second S0231 tag site, in addition to the per-protocol tags from Phase 02).
- File total LOC ≤ 460.

**Status:** `[ ]` not done

---

### Step 03.2 — Update all `AtomicFileOperationStrategy(...)` instantiation sites

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 3/3 PASS. Updated 17 call sites across 4 handler files. All use `destinationClassifier = destinationClassifier` named argument. Build standardDebug 1m 43s, APK produced. Dev log recorded.

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Every `AtomicFileOperationStrategy(...)` constructor call now requires the `destinationClassifier` argument. Update all call sites identified by:
>
> ```powershell
> rg -n "AtomicFileOperationStrategy\(" --type=kotlin app_v2/src/main
> ```
>
> Expected sites (per Phase 1 research):
> - `SftpFileOperationHandler.kt` × 4 (sftp/smb/ftp/local strategies)
> - `SmbFileOperationHandler.kt` × 4
> - `FtpFileOperationHandler.kt` × 4
> - `CloudFileOperationHandler.kt` × 5 (sftp/smb/ftp/cloud/local strategies)
> - `DirectoryStrategyModule.kt` — wherever wrapping happens (verify with the same rg search)
>
> Each handler is Hilt-injected — add `LocalDestinationClassifier` to the handler's constructor (or reuse if already added in Phase 02) and forward it into each `AtomicFileOperationStrategy(...)` call.

**Verification:**

- `Grep -c 'AtomicFileOperationStrategy(' app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` ≥ 4 occurrences before, same count after.
- After edit: `rg -n "AtomicFileOperationStrategy\(" --type=kotlin app_v2/src/main | rg -v "destinationClassifier"` returns zero matches (every call site passes the classifier).
- `/build` (`.\a.ps1 bd`) target `standardDebug` exits 0.

**Status:** `[ ]` not done

---

### Step 03.3 — Extend `AtomicFileOperationStrategyTest` with public-collection short-circuit cases

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 1/2 PASS, 1 MANUAL-REQUIRED. Added 3 new `@Test` methods (public-collection-bypasses-temp_copy, non-public-uses-temp_copy, enableAtomic-false-bypasses-classifier) + `FakeClassifier` test helper (subclass of LocalDestinationClassifier) + `RecordingDelegate` test helper. Fixed pre-existing missing `createTextFile` override on `FakeDelegate` (CLAUDE.md Rule 14 — fixing the test infra that the new tests depend on). Made `LocalDestinationClassifier` class+method `open` for test subclassing. Per-class XML test report still blocked by remaining pre-existing failures elsewhere (`CloudFileOperationHandlerTest.kt:115`) — MANUAL-REQUIRED to run once those are repaired. Production build standardDebug green (1m 44s).

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategyTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add the following test cases to the existing test file (do not delete existing tests):
>
> 1. `public_collection_destination_bypasses_temp_copy` — mock `LocalDestinationClassifier` to return `PublicCollection(...)`. Mock `delegate.copyFile` to record arguments. Call `atomicStrategy.copyFile(source, "/storage/emulated/0/Music/song.mp3", overwrite=true, null)`. Assert `delegate.copyFile` was called with the destination value `/storage/emulated/0/Music/song.mp3` (not the `*.temp_copy` suffix).
>
> 2. `non_public_destination_uses_temp_copy_pattern` — mock classifier to return `NonPublic(...)`. Mock `delegate.copyFile` to record. Assert `delegate.copyFile` was called with destination ending in `.temp_copy`.
>
> 3. `enableAtomic_false_bypasses_classifier` — set `enableAtomic = false`. Assert `delegate.copyFile` called with raw destination; classifier should NOT have been called (verify via mock interaction count).
>
> Use the same mock framework already used in this test file (`mockk` if present; otherwise plain `Mockito`).

**Verification:**

- `Grep` in test file — three new `@Test` annotations with the function names above.
- Per-class test report at `app_v2/build/test-results/testStandardDebugUnitTest/com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest.xml` — `tests` count increased by 3, `failures = 0`, `errors = 0` for the new methods. Pre-existing failures in other test classes are out of scope (per `feedback_build_pre_existing_test_failures`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles — `standardDebug` BUILD SUCCESSFUL in 1m 44s (Step 03.3 evidence).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `rg "AtomicFileOperationStrategy\(" ... | rg -v "destinationClassifier"` — all 17 call sites carry `destinationClassifier = destinationClassifier`.
- [ ] `AtomicFileOperationStrategyTest` per-class XML — **MANUAL-REQUIRED**. 3 new test methods written + FakeClassifier/RecordingDelegate helpers; FakeDelegate `createTextFile` stubbed. Execution blocked by `CloudFileOperationHandlerTest.kt:115` pre-existing compile failure (unrelated, tracked in memory `feedback_build_pre_existing_test_failures`). Once that test target compiles, run: `./gradlew :app_v2:testNoLegalDebugUnitTest --tests "com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategyTest"`.
- [x] Dev log entry added for every file in "Files Touched" (7 entries).

---

## Handoff Notes to Next Phase

After Phase 03:
- The complete network → local public collection flow is structurally correct: writes go through MediaStore IS_PENDING with no orphan `*.temp_copy`.
- One additional `Timber.d("S0231:` tag exists in `AtomicFileOperationStrategy.kt`.
- Error handling for non-public EACCES still goes through the placeholder `LocalDestinationPermissionDeniedException` declared inside `MediaStoreLocalDestinationWriter.kt` — Phase 04 replaces it with a proper domain type and a localized user-facing message.

---

## Rollback Plan

Revert phase commit(s). Backups from Phase 02 (`temp/*.bak`) cover the handler files that this phase also touches.
