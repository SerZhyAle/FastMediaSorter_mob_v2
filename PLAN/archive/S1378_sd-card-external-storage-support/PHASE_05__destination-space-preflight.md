# Phase 05 - Destination space pre-flight

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Measure free space on the volume that actually receives the data and stop an operation that cannot fit before it starts, with a message naming that volume.

---

## Prerequisites

- [ ] Phase 01 and Phase 04 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDestinationFreeSpaceUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/DirectoryRefusalMessages.kt` | Modified | ≤ 70 |
| `app_v2/src/main/res/values/strings.xml` | Modified | 1 key added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | 1 key added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | 1 key added |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GetDestinationFreeSpaceUseCaseTest.kt` | New | ≤ 160 |

---

## Steps

### Step 05.1 - Add `GetDestinationFreeSpaceUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDestinationFreeSpaceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a use case answering "how many bytes are free at this destination" for a destination expressed either as a filesystem path or as a document-tree address. Resolve a document-tree address to its volume through `StorageVolumeRepository` and read `availableBytes` from the volume; resolve a filesystem path through the volume whose mount path is its prefix. Return a null result when the destination cannot be attributed to any known volume - a caller must be able to distinguish "no room" from "could not measure".

**Why:**

Strategic §5.1 pillar 5 requires the fit check to take the destination volume rather than defaulting to built-in storage, and §1 records that the current free-space reading covers primary storage only.

**Verification:**

- `Grep` - `class GetDestinationFreeSpaceUseCase` matches exactly once.
- `Grep` - `StorageVolumeRepository` appears in the constructor parameter list.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Files: domain/usecase/GetDestinationFreeSpaceUseCase.kt (+72 LOC). Dev log recorded.

---

### Step 05.2 - Refuse an operation that cannot fit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/DirectoryRefusalMessages.kt`

**Depends on:** Step 05.1

**Prompt for developer:**

> Add an `INSUFFICIENT_SPACE` reason to `DirectoryOperationRefusal` and raise it from `refuseUnsafeDirectoryOperation` when the measured tree size exceeds the destination's free bytes. Apply the same check ahead of single-file copy and move, returning a failure carrying the same reason. When the destination cannot be measured, proceed rather than refuse - an unmeasurable volume must not block a legitimate operation.
>
> Amended 2026-08-05 during execution: `refusalMessageRes` consumes the reason in an exhaustive `when`, so the new constant does not compile without a branch there. Map it to the existing `R.string.error_reason_disk_space` in this step; Step 05.3 replaces that with the dedicated key naming the medium and the shortfall. Without this the step cannot satisfy its own `.\a.ps1 fk` predicate.

**Why:**

Strategic §11 criterion 4 requires an over-capacity write to stop before it starts with a message about the target medium, and §7 lists an operation that starts and dies on a full destination as the risk this check removes.

**Verification:**

- `Grep` - `INSUFFICIENT_SPACE` matches in `UnifiedFileOperationHandler.kt`.
- `Grep` - the pre-flight is called from both the directory path and the single-file copy path.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS (`fk` exit 0). Files: data/transfer/UnifiedFileOperationHandler.kt (651 -> 700 LOC, budget 700), ui/browse/helpers/DirectoryRefusalMessages.kt (+1 branch). Prompt amended mid-step: the exhaustive `when` in `refusalMessageRes` made the enum addition uncompilable on its own. Dev log recorded.

---

### Step 05.3 - Add the user-facing message in three locales

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/DirectoryRefusalMessages.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFolderPickerHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt`

**Depends on:** Step 05.2

**Prompt for developer:**

> Add one string key for the insufficient-space refusal in EN, RU and UK with a single lockstep call to `scripts/utils/set-android-string.ps1 -Action add -Key .. -En .. -Ru .. -Uk ..`, and map the new reason to it in `DirectoryRefusalMessages`. The message names the target medium and how much room is missing; it must satisfy the message formula for its type in `docs/COMMUNICATION_POLICY.md` §2, the next-step rule in §3 and the tone checklist in §6.
>
> Amended 2026-08-05 during execution: naming the medium and the shortfall needs format arguments, and `refusalMessageRes` returns a bare `@StringRes` its two callers pass straight to `getString`. So the refusal carries the two values (`destinationLabel`, `missingBytes`, both optional), a `refusalMessage(context, refusal)` formats them, and both call sites move to it. `refusalMessageRes` keeps the Step 05.2 mapping as the fallback for a refusal that carries no measurement.

**Why:**

Strategic §3.2 makes EN/RU/UK mandatory for every new string and binds the four new message classes of this ticket to the communication policy before integration.

**Verification:**

- `Grep` - the new key is present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Key `error_destination_insufficient_space` in en/ru/uk (`check_strings_localized` exit 0), `fk` exit 0. §6 checklist: no raw exception text, a next step present ("Free up room there or pick another destination"), parity confirmed, no emoji. Files: UnifiedFileOperationHandler.kt (708 LOC, budget 720), DirectoryRefusalMessages.kt (41 LOC, budget 70), BrowseFolderPickerHandler.kt, BrowseFileTransferWorker.kt, 3x strings.xml. Dev log recorded.

---

### Step 05.4 - Cover the measurement with tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GetDestinationFreeSpaceUseCaseTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerDirectoryTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerDirectoryGuardTest.kt`

**Depends on:** Step 05.3

**Prompt for developer:**

> Add unit tests asserting: a document-tree destination on a removable volume reports that volume's free bytes rather than primary storage, a filesystem path under the primary mount reports primary free bytes, and an unattributable destination returns null.

**Why:**

Strategic §7 keeps "the fit check silently stays bound to built-in memory" as a live risk, and a test comparing a removable destination against primary storage is what detects that regression.

**Verification:**

- `.\a.ps1 fu` - `GetDestinationFreeSpaceUseCaseTest` passes; record `expected: PASS | actual: <result>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - `expected: PASS | actual: PASS` - `GetDestinationFreeSpaceUseCaseTest` tests=7 failures=0 errors=0 (XML 15:42:35). Verified per class via `check-standard-fast.ps1 -Mode Unit -Tests`: `.\a.ps1 fu` cannot give a verdict here, it reports `ratio 0` whenever the test sources fail to compile.
- 2026-08-05 - Collateral from Step 05.2: the new constructor parameter broke three existing handler suites, which `fk` cannot see because it compiles `main` only. Repaired with an unmeasurable-destination mock (the "proceed" branch, so their routing assertions are untouched): UnifiedFileOperationHandlerTest 12/12, UnifiedFileOperationHandlerDirectoryTest 8/8, UnifiedFileOperationHandlerDirectoryGuardTest 6/6, all failures=0. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits in source (the only match is this criterion's own text).
- [x] Dev log entry added for the phase (five entries, one per closure).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` OK on every closure.
- [x] Phase-boundary audit run - one P1 and one P2 found and fixed in phase, no unresolved finding.

---

## Phase-boundary audit (2026-08-05)

Scope: `Files Touched` of this phase. Layer 1 always; Layer 2 because the pre-flight added suspend work to the transfer path. Layers 3 and 4 not applicable - no listener, receiver or Room surface is touched.

- **P1, fixed in phase.** The fit check measured only a local source tree, so a copy *off* a document-tree source - a card - was never checked against the destination at all. Strategic §11 criterion 4 introduces the check for every destination, and half the card scenarios fell outside it. Fixed by taking the size from `FileOperationStrategy.getDirectoryInfo().totalSize`, which local, SAF, SMB, SFTP, FTP and cloud all already implement, instead of walking the filesystem inside the handler.
- **P2, fixed in the same edit.** The first implementation re-implemented `LocalOperationStrategy`'s own recursive walk inside the handler - a second copy of a traversal that already existed, and a second place to keep correct (Rule 20).
- **P3, accepted.** The pre-flight measures a tree the transfer then traverses again; for a remote source that is one extra listing per directory level. Accepted because the criterion requires the check to happen before the operation starts, and the measurement runs on `Dispatchers.IO` inside each strategy.
- **Layer 2, clean.** `refuseUnsafeDirectoryOperation` is now `suspend` and is reached only from inside `withContext(Dispatchers.IO)`; `StorageVolumeRepositoryImpl` switches to IO itself; every `getDirectoryInfo` implementation calls `rethrowIfCancellation`, so the pre-flight cannot swallow a cancelled transfer.
- **Not a finding.** The single-file pre-flight sits in `executeCopy`, whose only caller is `CameraQuickCaptureLaunchManager` - one capture per call, not a batch loop, so the check adds no per-file volume enumeration to a bulk copy.

---

## Handoff Notes to Next Phase

Every copy and move now measures its destination volume first. Phase 06 renders the same `availableBytes` value in the picker, so the number the user sees before choosing a volume is the number the pre-flight uses.

---

## Rollback Plan

Revert the phase commit and remove the added string key via `set-android-string.ps1 -Action remove` in all three locales.
