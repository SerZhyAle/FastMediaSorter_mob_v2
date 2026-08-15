# Phase 01 - Launch transport extension

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Extend the shared VR launch transport with a resource-browse target (`VrLaunchMode.RESOURCE_BROWSE` + `resourceId`) without breaking `FILE_URI` / `DIAGNOSTIC_PLAYLIST`, and consolidate the duplicated `MediaFile.toLaunchUriString()` extension into one shared location. Compiles on every flavor (contract lives in `src/main`).

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `core/xr/VrLaunchContract.kt` reads as captured in `research/01`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchUriMapper.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseVrCinemaLaunchManager.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerVrLaunchManager.kt` | Modified | ≤ 440 |

---

## Steps

### Step 01.1 - Add RESOURCE_BROWSE mode and resourceId field to the contract

**Files:** `core/xr/VrLaunchContract.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `RESOURCE_BROWSE` to `enum class VrLaunchMode` (after `FILE_URI`). Add `val resourceId: Long? = null` to `data class StartVrPlaybackRequest` (trailing, after `deliveryMode`) and to `data class VrLaunchInput` (trailing). Update `VrLaunchInput.fromRequest` to copy `resourceId = request.resourceId`. Keep all existing fields, defaults, and factory `diagnosticPlaylist(..)` unchanged so `FILE_URI` / `DIAGNOSTIC_PLAYLIST` call sites are source-compatible.

**Verification:**

- `Grep` - `RESOURCE_BROWSE` matches in `VrLaunchContract.kt`.
- `Grep` - `val resourceId: Long? = null` matches at least twice (request + input).
- `Grep` - `resourceId = request.resourceId` present in `fromRequest`.

**Status:** `[x]` done

---

### Step 01.2 - Add resourceBrowse factory and requireResourceId helper

**Files:** `core/xr/VrLaunchContract.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `StartVrPlaybackRequest.companion`, add `fun resourceBrowse(resourceId: Long, source: VrLaunchPoint, deliveryMode: VrLaunchDeliveryMode = ACTIVITY_RESULT): StartVrPlaybackRequest` returning a request with `launchMode = RESOURCE_BROWSE`, `mediaType = VrMediaType.VIDEO` (placeholder; the immersive browser lists mixed media), `resourceId = resourceId`. In `VrLaunchInput`, add `fun requireResourceId(): Long = requireNotNull(resourceId) { "VrLaunchInput requires a resourceId when launchMode=RESOURCE_BROWSE" }`.

**Verification:**

- `Grep` - `fun resourceBrowse(` present in `VrLaunchContract.kt`.
- `Grep` - `fun requireResourceId()` present.

**Status:** `[x]` done

---

### Step 01.3 - Extract shared toLaunchUriString mapper

**Files:** `core/xr/VrLaunchUriMapper.kt` (New)
**Depends on:** - independent

**Prompt for developer:**

> Create `VrLaunchUriMapper.kt` in `core/xr/` holding one `internal fun MediaFile.toLaunchUriString(): String` extension - copy the exact body currently duplicated in `BrowseVrCinemaLaunchManager` and `PlayerVrLaunchManager` (content-URI vs file-path resolution). No class; a top-level extension in package `com.sza.fastmediasorter.core.xr`. WHY-comment only if the URI branch logic is non-obvious.

**Verification:**

- `Glob` - `core/xr/VrLaunchUriMapper.kt` exists.
- `Grep` - `fun MediaFile.toLaunchUriString()` matches exactly once in that file.

**Status:** `[x]` done

---

### Step 01.4 - Point both existing call sites at the shared mapper

**Files:** `ui/browse/helpers/BrowseVrCinemaLaunchManager.kt`, `ui/player/helpers/PlayerVrLaunchManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Delete the private `toLaunchUriString()` extension from both files; add `import com.sza.fastmediasorter.core.xr.toLaunchUriString`. Behaviour unchanged - the call sites keep calling `file.toLaunchUriString()`, now resolving to the shared extension.

**Verification:**

- `Grep -n "private fun MediaFile.toLaunchUriString"` returns zero hits in both files.
- `Grep` - `import com.sza.fastmediasorter.core.xr.toLaunchUriString` present in both files.

**Status:** `[x]` done

---

## Step Log

- 2026-07-11 - Steps 01.1-01.4 Verification all PASS. Files: `VrLaunchContract.kt` (RESOURCE_BROWSE + resourceId + resourceBrowse/requireResourceId), `VrLaunchUriMapper.kt` (New, shared `toLaunchUriString`), `BrowseVrCinemaLaunchManager.kt` + `PlayerVrLaunchManager.kt` (deleted private mapper, added shared import, pruned orphaned `File`/`Uri` imports). Build gate deferred - BUILD.LOCK held by concurrent session; consolidated build after Phase 05.

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug` (No-Op path) and `.\a.ps1 fkn` (noLegal).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`VrLaunchMode.RESOURCE_BROWSE` + `resourceId` now exist on the request/input; `StartVrPlaybackRequest.resourceBrowse(..)` is the caller factory (used by Phase 05). `MediaFile.toLaunchUriString()` lives in `core/xr/VrLaunchUriMapper.kt` (used by Phase 03).

---

## Rollback Plan

Revert the phase commit - additive contract fields with defaults, no data migration or user-facing surface changed.
