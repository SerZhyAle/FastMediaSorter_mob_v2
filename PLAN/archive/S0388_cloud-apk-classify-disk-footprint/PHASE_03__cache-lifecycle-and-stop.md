# Phase 03 - Transient lifecycle + out-of-space session stop

**Strategic spec:** [`../S0388_cloud-apk-classify-disk-footprint.md`](../S0388_cloud-apk-classify-disk-footprint.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 (`VrArchiveResolution`)
**Blocks:** Phase 04 (build)
**Steps:** 3 / 3

---

## Objective

Consume `VrArchiveResolution` in `VrApkClassificationCache`: read the archive then delete the transient copy, and stop all further classification for the session once the cache volume is out of space (one logged failure, no cascade). Update the noLegal unit test to the new resolver contract. All changes live in the noLegal source set (production + test).

Implements strategic §6.2 conclusion (stop-for-session) and the transient-delete half of §6.1.

---

## Prerequisites

- [ ] Phase 02 is done - `resolve` returns `VrArchiveResolution`.
- [ ] `VrApkClassificationCache.kt` is < 500 LOC - no backup required.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt` | Modified | ≤ 190 |
| `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCacheTest.kt` | Modified | ≤ 120 |

---

## Steps

### Step 03.1 - Add the session disk-full flag and short-circuit in `requestClassification`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Volatile private var diskFullThisSession = false`. At the top of `requestClassification`, after the blank-key guard and the `peek` fast path, if `diskFullThisSession` is true call `onResult(VrApkClassification.NOT_VR)` and return - no coroutine launch, no download. This is the session-wide stop: once the cache volume is full, every later item degrades to `NOT_VR` immediately, ending the repeated-error cascade. The flag is an in-memory field, so it resets on process restart and classification resumes after space is freed (strategic §6.2 "стоп-на-сессию"). Add a one-line WHY comment.

**Verification:**

- `Grep` - `@Volatile private var diskFullThisSession` matches.
- `Grep` - `if (diskFullThisSession)` (or equivalent) short-circuit in `requestClassification`.

**Status:** `[x] done`

---

### Step 03.2 - Switch `classifyForCache` on `VrArchiveResolution` and delete the transient copy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Rewrite the body of `classifyForCache` to switch on `archiveResolver.resolve(mediaFile)`:
>
> - `is VrArchiveResolution.Available` → `try { ClassificationTaskResult(classification = classifier.classify(res.file), cacheable = true) } finally { res.cleanup() }`. The `finally` deletes the transient copy immediately after the classifier reads the manifest - the copy never persists past one read. `cleanup()` is a no-op for local-source files, so the user's originals are never deleted.
> - `VrArchiveResolution.OutOfSpace` → set `diskFullThisSession = true`, log once via `Timber.w(..)` ("cache volume full, stopping VR APK classification for this session"), return `ClassificationTaskResult(VrApkClassification.NOT_VR, cacheable = false)`.
> - `VrArchiveResolution.Unavailable` → return `ClassificationTaskResult(VrApkClassification.NOT_VR, cacheable = false)`.
>
> Keep the surrounding `try { .. } catch (e: Exception) { Timber.w(e, ..); ClassificationTaskResult(NOT_VR, cacheable = false) }` guard from S0355. No ticket id in log text. The `NOT_VR` results stay `cacheable = false` so they are not memoized as a real verdict; the session flag (not the LRU) is what suppresses repeat downloads after a disk-full event.

**Verification:**

- `Grep` - `is VrArchiveResolution.Available` matches.
- `Grep` - `res.cleanup()` (or the bound name) inside a `finally`.
- `Grep` - `diskFullThisSession = true` matches.
- `Grep` - `VrApkClassification.NOT_VR` still appears (Unavailable + OutOfSpace + catch paths).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x] done`

---

### Step 03.3 - Update the noLegal unit test to the new resolver contract

**Files:** `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCacheTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> The test `requestClassification stores cacheable classifier result` mocks `archiveResolver.resolve(mediaFile) returns archive` (a `File`). Update it to `returns VrArchiveResolution.Available(archive) {}` so it compiles against the new return type and still asserts the cacheable result is stored. Add one new test `requestClassification stops for session on OutOfSpace`: mock `resolve(..) returns VrArchiveResolution.OutOfSpace`, request classification for one file → result is `NOT_VR`; then request a second different file and `coVerify(exactly = 1) { archiveResolver.resolve(any()) }` to confirm the second request short-circuits without re-resolving (session stop). Keep `@Config(sdk = [34])` and the existing dispatcher wiring.

**Verification:**

- `Grep` - `VrArchiveResolution.Available(archive)` matches in the test.
- `Grep` - `VrArchiveResolution.OutOfSpace` matches in the test.
- Build/test run deferred to Phase 04 (`testNoLegalDebugUnitTest` for these two classes, or compile via `assembleNoLegalDebug`).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Build deferred to Phase 04 (`noLegalDebug`).

---

## Handoff Notes to Next Phase

Classification now: reads a transient copy, deletes it, and stops for the session on a full volume. Phase 04 builds `noLegalDebug`, runs docs/catalog/functionality bookkeeping, and inserts the single `Timber.d("S0388: ..")` device-test probe at the changed-flow entry (`resolveCloudArchive`).

---

## Rollback Plan

Revert the phase commit alongside Phase 02 - the two are coupled by `VrArchiveResolution`.
