# Phase 02 - Classification isolation (noLegal)

**Strategic spec:** [`../S0355_bugfix-cloud-apk-classify-crash.md`](../S0355_bugfix-cloud-apk-classify-crash.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Ensure a failed local-copy acquisition for one cloud APK degrades to `NOT_VR` for that item only, never propagating an exception out of the classification coroutine and never stalling the rest of the list. All changes live in the noLegal source set.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the shared cloud download path returns `false`/`null` instead of throwing.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt` | Modified | ≤ 130 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt` | Modified | ≤ 170 |

> Both files are flavor-only and correctly reside under `src/noLegal/java/` - keep them there. Do not move VR-recognition logic into `src/main`. No `BuildConfig` guard is needed: source-set placement already isolates this code to the noLegal flavor.
>
> Both files are <500 LOC - no backup step required.

---

## Steps

### Step 02.1 - Wrap `resolveCloudArchive` in a defensive catch

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `VrApkArchiveResolver.resolveCloudArchive`, wrap the body that calls `cloudFileOperationHandler.downloadFromCloudToPublic(..)` and validates `cacheFile` in `try { .. } catch (e: Exception) { Timber.w(e, ..); null }`. After Phase 01 the download path returns `false` rather than throwing, so this catch is the belt-and-braces guard for any other failure (parse error, cache-dir creation failure, security exception). The log text must describe a failed cloud APK copy for classification and must NOT contain `S0355` or any ticket id. Return `null` on failure (the existing contract for "no archive").

**Verification:**

- `Grep` - `catch (e: Exception)` matches in `VrApkArchiveResolver.kt`.
- `Grep` - `import timber.log.Timber` present in `VrApkArchiveResolver.kt`.
- `Grep -n "Log\.d\("` - zero hits in `VrApkArchiveResolver.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 3/3 PASS. resolveCloudArchive body wrapped in try/catch(Exception) -> Timber.w + null. import timber.log.Timber added. Log.d expected 0 | actual 0. Dev log recorded.

---

### Step 02.2 - Harden `classifyForCache` against any thrown failure

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `VrApkClassificationCache.classifyForCache`, the call to `archiveResolver.resolve(mediaFile)` and the subsequent `classifier.classify(localArchive)` run inside an `applicationScope.async` whose exception surfaces at `deferred.await()` and crashes the launching coroutine. Wrap the whole `classifyForCache` body in `try { .. } catch (e: Exception) { Timber.w(e, ..); ClassificationTaskResult(classification = VrApkClassification.NOT_VR, cacheable = false) }` so any failure (resolver or classifier) degrades to a non-cacheable `NOT_VR` for that single item. Up to 4 of these run concurrently (`Semaphore(MAX_CONCURRENT_CLASSIFICATIONS)`), so one failure must not affect the others. Log text describes a failed APK classification; no ticket id.

**Verification:**

- `Grep` - `catch (e: Exception)` matches in `VrApkClassificationCache.kt`.
- `Grep` - `VrApkClassification.NOT_VR` appears at least twice in `VrApkClassificationCache.kt` (the existing key-null path plus the new catch path).
- `Grep` - `import timber.log.Timber` present in `VrApkClassificationCache.kt`.
- `Grep -n "Log\.d\("` - zero hits in `VrApkClassificationCache.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. classifyForCache wrapped in try/catch(Exception) -> Timber.w + non-cacheable NOT_VR. catch(e: Exception) expected >=1 | actual 1. NOT_VR expected >=2 | actual 3. import Timber present. Log.d expected 0 | actual 0. Dev log recorded.

---

### Step 02.3 - Confirm no second debug probe is introduced

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> The single `Timber.d("S0355: ..")` entry probe lives in `CloudFileOperationHandler` (Phase 01, Step 01.5). The CLAUDE.md invariant is exactly one such tag while the ticket is `BlockNeedUserTest`. Do NOT add a `S0355:` tag in either noLegal file - the cloud download flow is the shared exercised path. Confirm neither file gained one.

**Verification:**

- `Grep` - `Timber.d("S0355:` returns zero hits in `VrApkArchiveResolver.kt`.
- `Grep` - `Timber.d("S0355:` returns zero hits in `VrApkClassificationCache.kt`.
- `Grep` - `Timber.d("S0355:` still matches exactly once across all `.kt` files (the Phase 01 probe).

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 3/3 PASS. Timber.d("S0355: in VrApkArchiveResolver expected 0 | actual 0; in VrApkClassificationCache expected 0 | actual 0; across all .kt expected 1 | actual 1 (CloudFileOperationHandler).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for the `noLegalDebug` variant. **DEFERRED** - user requested "no build". noLegalDebug build is mandatory before `/spec-check` since these files only compile in the noLegal source set.
- [x] `Grep` for `TODO(phase-02)` returns zero hits. (expected 0 | actual 0)
- [x] Dev log entry added for both files via add_to_dev_log.ps1.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

After Phase 02 a vanished/unreadable temp copy yields `NOT_VR` for the affected item with the rest of the list classifying normally, and no uncaught exception leaves the classification coroutine. The crash and the cascade are both closed by interception alone. Phase 03 decides - from research §6.1/§6.2 - whether to additionally protect the temp copy from background deletion, and is the only phase still gated.

---

## Rollback Plan

Revert the phase commit - both files are flavor-only, no data migration, no user-facing surface beyond the (now suppressed) crash.
