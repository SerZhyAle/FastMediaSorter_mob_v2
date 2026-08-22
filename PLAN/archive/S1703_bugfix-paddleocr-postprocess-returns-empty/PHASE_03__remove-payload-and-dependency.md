# Phase 03 - Remove the payload and the dependency

**Strategic spec:** [`../S1703_bugfix-paddleocr-postprocess-returns-empty.md`](../S1703_bugfix-paddleocr-postprocess-returns-empty.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

Stop shipping and stop downloading what nothing loads any more - and leave a device that already downloaded
it in a defined state.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - nothing references the engine.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSet.kt` | Modified | ≤ 40 |
| `app_v2/build.gradle.kts` | Modified | ≤ 40 |

---

## Steps

### Step 03.1 - Take the models and the native libraries out of the set

**Files:** the three delivery files

**Depends on:** - start of phase

**Prompt for developer:**

> The OCR deliverable set lists Tesseract and Paddle artefacts together, each with a hash and a size. Remove
> the Paddle entries so the set describes only what the app can still load, and check what the set's total
> size is used for - a progress figure computed from the old total would misreport every download.

**Why:**

Strategic §3 withdraws the payload with the engine, and the set's declared size is read by the download
progress, so removing entries without checking that reader would leave a bar that never reaches its end.

**Verification:**

- `Grep` - no Paddle artefact remains in the descriptor catalog or the loader.
- `Grep` - whatever reads the set's total size reads the new one.
- `.\a.ps1 fk`, `.\a.ps1 fkn` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - PADDLE native-lib map and ocrEnginesNoLegal deleted from DeliverableDescriptorCatalog (grep: zero Paddle artefacts left there and in DeliveredNativeLibraryLoader); ocrEnginesNoLegal had no caller in any source set, so nothing was rewired. The set total is derived, not stored: DeliverableInventoryImpl.moduleSizeLabel sums descriptors[set].files.minSize, so it reads the new total automatically; the FALLBACK_SIZE constant for OCR_ENGINES (7514856) never counted Paddle and stays correct. a.ps1 fk 0, a.ps1 fkn 0, post-change PASS.

---

### Step 03.2 - Decide what happens to an already-downloaded payload

**Files:** the delivery files, per what step 03.1 finds

**Depends on:** Step 03.1

**Prompt for developer:**

> A device that downloaded the OCR set before this change has Paddle files in its delivered payload
> directory. Decide and implement one behaviour: leave them (harmless, wastes space) or delete them on the
> next verification pass (reclaims space, one-time IO). Write the decision and its reason into the file that
> implements it. Whichever is chosen, a stale Paddle file must never make the set's verification fail - a
> user must not be told their OCR payload is damaged because it contains something we retired.

**Why:**

The set is verified against its descriptor, so removing a descriptor entry silently changes what "complete"
means on a device that already has the file - the failure would surface as a corrupt-payload message about
a file the app no longer wants.

**Verification:**

- `Grep` - the chosen behaviour is implemented and its reason is written beside it.
- `Grep` - the verification path tolerates an extra retired file.
- `.\a.ps1 fu` - passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Behaviour chosen: leave a retired payload file where it is; the reason is written beside promote() in RealDeliverableSetDownloader, the function that implements the reclaim. Mechanism verified in code, not assumed: DeliverableSourceDescriptor.stamp is derived from the file list, so dropping the two Paddle entries changes the OCR_ENGINES stamp; DeliverableCapabilityRepositoryImpl.isStale compares stamps and DeliverableInventoryImpl reports UpdateAvailable and enqueues with force, whose download ends in promote() replacing the whole payload directory. Verification tolerates the extra file by construction - the download loop verifies only the descriptor's own staged files, never the directory contents. a.ps1 fu exit 0 (BUILD SUCCESSFUL, 517 reports for 517 test files). post-change PASS.

---

### Step 03.3 - Drop the build dependency

**Files:** `app_v2/build.gradle.kts`

**Depends on:** Step 03.2

**Prompt for developer:**

> Remove the PaddleOCR / Paddle Lite dependency declarations from every flavor that carried them, and any
> packaging rule, ABI filter or keep rule that exists only for it. Then build the flavors that had it.

**Why:**

Strategic §3 names the dependency among what is withdrawn, and a dependency left behind keeps paying its
size and its licence obligations for code that cannot run.

**Verification:**

- `Grep` - no Paddle dependency, packaging rule or keep rule remains.
- `.\a.ps1 nd` - the noLegal debug build succeeds.
- `Grep` - `THIRD-PARTY-NOTICES` and the licence manifest no longer list it, if they did.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - No gradle coordinate ever declared Paddle - the dependency was vendored: two .so under app_v2/src/noLegal/jniLibs/arm64-v8a (10 MB) plus the com.baidu.paddle.lite JNI wrapper package. Nothing in Kotlin referenced either, so both were deleted. Grep across build.gradle.kts, libs.versions.toml and both proguard files finds no Paddle dependency, packaging rule, ABI filter or keep rule; the only remaining mention is the S0971 de-bundle history comment, which is accurate. No THIRD-PARTY-NOTICES artifact exists in the tree. a.ps1 nd exit 0 (BUILD SUCCESSFUL in 2m 54s) after the deletion. post-change PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 nd` succeeds.
- [x] Dev log entry added for every file in Files Touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The app neither ships nor fetches the engine. The user can still see its choice in settings.

---

## Rollback Plan

Restore the descriptor entries and the dependency; the payload returns to being downloadable.
