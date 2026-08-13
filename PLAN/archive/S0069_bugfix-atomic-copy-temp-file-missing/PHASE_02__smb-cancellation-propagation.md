# Phase 02 — SMB Cancellation Propagation

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Preserve `CancellationException` through the SMB delegate stack so user cancel no longer becomes generic SMB download/upload failure. No atomic orchestrator refactor yet.

---

## Prerequisites

- [ ] Phase 01 continue-path selected.
- [ ] Backups from Phase 01 exist for `SmbOperationStrategy.kt` and `SmbFileOperations.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt` | Modified | ≤ 660 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt` | Modified | ≤ 840 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt` | Read-only audit | 956 current LOC |

---

## Steps

### Step 02.1 — Re-throw cancellation in `SmbFileOperations.downloadFile`

**Files:** `SmbFileOperations.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `SmbFileOperations.downloadFile`, add an explicit `catch (e: CancellationException) { throw e }` before the generic `catch (e: Exception)`. Do not log cancellation as `Failed to download file from SMB`. Keep the existing generic error path unchanged for non-cancellation failures.

**Verification:**

- `Grep -n "suspend fun downloadFile" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt"` matches once.
- `Grep -n "catch \(e: CancellationException\)" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt"` returns at least one hit in the `downloadFile` method region.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt (+2 LOC). Focused validation after first substantive edit: IDE diagnostics clean and `downloadFile` now re-throws `CancellationException` before generic SMB failure logging.

---

### Step 02.2 — Re-throw cancellation in `SmbFileOperations.uploadFile`

**Files:** `SmbFileOperations.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `SmbFileOperations.uploadFile`, add the same explicit `CancellationException` pass-through before the generic catch. Keep `Failed to upload file to SMB` for non-cancellation failures only.

**Verification:**

- `Grep -n "suspend fun uploadFile" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt"` matches once.
- `Grep -n "catch \(e: CancellationException\)" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt"` returns at least two hits total in the file (download + upload).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperations.kt (+2 LOC). `uploadFile` now re-throws `CancellationException` before generic SMB upload failure logging.

---

### Step 02.3 — Re-throw cancellation in `SmbOperationStrategy`

**Files:** `SmbOperationStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `downloadFromSmb`, `uploadToSmb`, and `copySmbToSmb`, add explicit `catch (e: CancellationException) { throw e }` before the generic `Exception` catch. Do not emit `SmbOperationStrategy: Download failed`, `Upload failed`, or `SMB→SMB copy failed` for cancellation. Leave generic failure logging intact for real failures.

**Verification:**

- `Grep -n "private suspend fun downloadFromSmb|private suspend fun uploadToSmb|private suspend fun copySmbToSmb" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt"` returns all three methods.
- `Grep -n "catch \(e: CancellationException\)" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt"` returns at least three hits total.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt (+7 LOC). `downloadFromSmb`, `uploadToSmb`, and `copySmbToSmb` now re-throw `CancellationException` before generic strategy failure logging.

---

### Step 02.4 — Compile gate

**Files:** none
**Depends on:** Step 02.3

**Prompt for developer:**

> Run the narrow compile gate:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> Do not continue if the SMB cancellation path no longer compiles cleanly.

**Verification:**

- `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 1/1 PASS. Compile gate satisfied by user-confirmed successful build after Step 02.3.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] SMB download/upload helpers re-throw cancellation instead of wrapping it.
- [x] `compileStandardDebugKotlin` passes.

---

## Handoff Notes to Next Phase

After this phase, user cancel can reach `AtomicFileOperationStrategy` as a real `CancellationException`. Phase 03 can then split `cancelled` vs `failed` without guessing from generic SMB error wrappers.

---

## Rollback Plan

Restore the two backed-up SMB files from `temp/` and re-run the compile gate.
