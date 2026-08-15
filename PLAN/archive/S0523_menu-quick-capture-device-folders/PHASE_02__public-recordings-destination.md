# Phase 02 - Public recordings destination + indexing

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research:** [`research/01__recordings-folder-indexing.md`](research/01__recordings-folder-indexing.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Resolve the quick-voice destination to the phone's recordings folder and make that folder MediaStore-indexable, so voice notes appear in the system Files app / sound picker. Photo and video destinations already resolve to `DCIM/Camera` and `Movies` and need no change.

---

## Prerequisites

- [ ] Strategic §6.1 research item reflected (see research/01).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/CaptureDestinationPolicy.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifier.kt` | Modified | ≤ 150 |

---

## Steps

### Step 2.1 - Add the quick-voice public-recordings resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/CaptureDestinationPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun resolveQuickVoiceDestination(): File` returning the phone's public recordings folder for the menu quick-voice capture (no resource parameter - quick capture is always public). Back it with a private `publicRecordingsDirectory()` that returns `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)` on API 31+ and `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)` below (mirror the existing `resolveCameraDirectory` create-or-fallback shape; fall back to Music if the Recordings dir cannot be created). Keep the existing `resolveMicDestination` (Downloads fallback for Browse) unchanged. Update the KDoc header to mention the quick-voice resolver.

**Verification:**

- `Grep` - `fun resolveQuickVoiceDestination()` matches once.
- `Grep` - `Environment.DIRECTORY_RECORDINGS` present in the file.
- `Grep` - `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` present (API-31 guard).
- `Grep` - `fun resolveMicDestination` still present (unchanged signature).

**Status:** `[x]` done

---

### Step 2.2 - Classify the recordings folder as a public AUDIO collection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifier.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a guarded `getRecordingsDirectoryName()` mirroring `getAudiobooksDirectoryName()` - returns `Environment.DIRECTORY_RECORDINGS` on API 31+ (`Build.VERSION_CODES.S`), literal `"Recordings"` below - and include it in the `AUDIO` arm of `matchPublicCollectionKind`. This makes a file written under `<external>/Recordings/` classify as `PublicCollection(AUDIO)` so the MediaStore writer publishes and indexes it on API 29+. Do not change other arms.

**Verification:**

- `Grep` - `fun getRecordingsDirectoryName()` matches once.
- `Grep` - `getRecordingsDirectoryName()` appears inside `matchPublicCollectionKind` (AUDIO branch).
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`CaptureDestinationPolicy.resolveQuickVoiceDestination()` returns an indexable public recordings dir; the classifier maps `Recordings` to `AUDIO`. Phase 03's voice save path writes there through the existing `LocalDestinationWriter`.

---

## Step Log

- 2026-06-19 - Step 2.1 Verification PASS. `CaptureDestinationPolicy.kt`: `resolveQuickVoiceDestination()` L63, `publicRecordingsDirectory()` (Recordings on API 31+, Music below/fallback); `resolveMicDestination` unchanged.
- 2026-06-19 - Step 2.2 Verification PASS. `LocalDestinationClassifier.kt`: `getRecordingsDirectoryName()` added to AUDIO branch (L77); no Log.d.
- 2026-06-19 - Phase compile: `a.ps1 fk` BUILD SUCCESSFUL 48s (covers Phases 01+02).

---

## Rollback Plan

Revert phase commit(s) - pure helper additions, no user-facing surface, no data migration.
