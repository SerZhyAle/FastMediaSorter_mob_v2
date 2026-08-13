# Phase 02 - APK classification

**Strategic spec:** [../S0298_vr-companion-apk-badge.md](../S0298_vr-companion-apk-badge.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-27
**Completed:** 2026-05-27

---

## Objective

Implement bounded, cached VR APK classification and archive materialization inside the noLegal source set.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassification.kt` | New | ≤ 160 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassifier.kt` | New | ≤ 260 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt` | New | ≤ 280 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt` | New | ≤ 320 |

---

## Steps

### Step 02.1 - Add VR APK signal and manifest classifier

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassification.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassifier.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Add a small noLegal-only model for VR APK classification and implement the manifest reader that checks `com.oculus.supportedDevices`, `android.software.vr.mode`, and `android.hardware.vr.headtracking`. Return a graceful non-VR result on parse or file errors and keep all logging free of persistent `Sxxxx` ids.

**Verification:**

- `Glob` - `VrApkClassification.kt` exists.
- `Glob` - `VrApkClassifier.kt` exists.
- `Grep` - `enum class VrApkSignal` present in `VrApkClassification.kt`.
- `Grep` - `class VrApkClassifier` present in `VrApkClassifier.kt`.
- `Grep` - `com.oculus.supportedDevices` present in `VrApkClassifier.kt`.
- `Grep` - `android.hardware.vr.headtracking` present in `VrApkClassifier.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 6/6 PASS. Files: `VrApkClassification.kt`, `VrApkClassifier.kt`. Existing dev log entries present from 2026-05-26; no new code edit needed in this pass.

---

### Step 02.2 - Add archive materialization and bounded cache coordinator

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkClassificationCache.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Materialize local/network/cloud APKs into reusable local files only when needed, then wrap classification in an in-memory `(path,size,mtime)` cache with in-flight deduplication and bounded concurrency. Expose a synchronous cache peek plus an asynchronous request API suitable for RecyclerView bind callbacks.

**Verification:**

- `Glob` - `VrApkArchiveResolver.kt` exists.
- `Glob` - `VrApkClassificationCache.kt` exists.
- `Grep` - `class VrApkArchiveResolver` present in `VrApkArchiveResolver.kt`.
- `Grep` - `class VrApkClassificationCache` present in `VrApkClassificationCache.kt`.
- `Grep` - `Semaphore` present in `VrApkClassificationCache.kt`.
- `Grep` - `downloadFromCloudToPublic` or `NetworkFileDownloader` present in `VrApkArchiveResolver.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 6/6 PASS. Files: `VrApkArchiveResolver.kt`, `VrApkClassificationCache.kt`. Existing dev log entries present from 2026-05-26; no new code edit needed in this pass.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The noLegal source set now owns archive resolution, bounded classification, and cache semantics.

---

## Rollback Plan

Revert phase commit(s) - cache is in-memory only and safe to drop.
