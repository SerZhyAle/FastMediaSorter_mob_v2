# Phase 02 - Video Launch Gates

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Allow supported VIDEO `FILE_URI` launches while keeping GIF and unsafe URI inputs typed as unavailable before OpenXR startup.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Prepared URI policy blocker is checked in INDEX.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt` | Modified | current 130, change <= 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | current 1091, change <= 90, backup required |

> `DiagnosticXrActivity.kt` is over 500 lines. Let the `/spec-dev` pre-edit guard create a timestamped backup in `temp/` before the first edit in this phase.

---

## Steps

### Step 02.1 - Permit local VIDEO at use-case preflight

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Update `validateRequest(request: StartVrPlaybackRequest)` so `FILE_URI` requests allow `VrMediaType.IMAGE` and local `VrMediaType.VIDEO`. For VIDEO, accept only raw local paths or `file://` URIs; return `VrLaunchUnavailableReason.InvalidUri` for blank, `content://`, network, cloud and other schemes. Keep `VrMediaType.GIF` returning `VrLaunchUnavailableReason.NotYetSupported`.

**Verification:**

- `Grep` - `VrMediaType.VIDEO` exists in `validateRequest`.
- `Grep` - `VrMediaType.GIF -> VrLaunchUnavailableReason.NotYetSupported` exists exactly once in `StartVrPlaybackUseCaseImpl.kt`.
- `Grep` - `VrLaunchUnavailableReason.InvalidUri` is returned for non-local VIDEO URI schemes.
- `Grep` - `Log.d(` returns zero hits in `StartVrPlaybackUseCaseImpl.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Remove Activity VIDEO short-circuit

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `prepareLaunchMedia()`, replace the old `mediaType != VrMediaType.IMAGE` guard with a GIF-only guard. VIDEO must proceed into `resolveSingleLaunchFile(launchInput)`; GIF must still deliver `VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported)` before OpenXR startup.

**Verification:**

- `Grep` - `launchInput.mediaType == VrMediaType.GIF` exists in `prepareLaunchMedia`.
- `Grep` - `mediaType != VrMediaType.IMAGE` returns zero hits in `DiagnosticXrActivity.kt`.
- `Grep` - `VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NotYetSupported)` still exists in the GIF guard.
- `Grep` - `resolveSingleLaunchFile(launchInput)` still exists exactly once in `prepareLaunchMedia`.

**Status:** `[x]` done

---

### Step 02.3 - Keep VIDEO URI resolution local-only

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Update `resolveSingleLaunchFile(input: VrLaunchInput)` so VIDEO accepts only `file://` URIs and raw local file paths. Keep the existing `content://` to cache copy path for IMAGE only. For VIDEO `content://`, network or cloud schemes, return null so the caller returns `Unavailable(InvalidUri)`.

**Verification:**

- `Grep` - `input.mediaType == VrMediaType.VIDEO` exists inside `resolveSingleLaunchFile`.
- `Grep` - `resolveContentUriToCacheFile(uri)` is guarded so it runs only when `input.mediaType == VrMediaType.IMAGE`.
- `Grep` - `return file?.takeIf { it.isFile }` still exists or equivalent local-file existence check exists.
- `Grep` - `Log.d(` returns zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] noLegal debug source compiles through `/build` or `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1`.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` completed after Kotlin changes.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

Supported VIDEO requests can pass preflight, but playback behavior is still owned by Phase 03.

---

## Rollback Plan

Revert phase commit(s). Existing IMAGE and diagnostic playlist behavior is restored by the old preflight gates.
