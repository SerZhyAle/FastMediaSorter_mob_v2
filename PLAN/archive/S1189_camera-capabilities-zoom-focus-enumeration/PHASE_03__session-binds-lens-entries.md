# Phase 03 - Capture session binds lens entries

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Make the capture session enumerate, bind and switch lens entries instead of raw logical cameras, so physical sub-lenses become reachable and the zoom floor is computed across the whole reachable set - with a fallback to today's behaviour whenever a device or a lens refuses.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Timestamped backup of `CameraCaptureSessionManager.kt` taken under `temp/S1189/` before the first edit - the file exceeds 500 LOC.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 780 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 140 |

> No layout file is touched in this phase.

---

## Steps

### Step 03.1 - Back up the session manager

**Files:** `temp/S1189/CameraCaptureSessionManager.<timestamp>.kt.bak`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` to `temp/S1189/` with a timestamp in the filename before editing it, per CLAUDE.md Rule 5 (file over 500 LOC).

**Verification:**

- `Glob` - at least one file matching `temp/S1189/CameraCaptureSessionManager.*.bak` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 1/1 PASS. `temp/S1189/CameraCaptureSessionManager.20260725T174500.kt.bak` (35672 bytes).

---

### Step 03.2 - Build a camera selector for a physical sub-lens

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `selectorFor(entry: CameraLensEntry)` companion function next to the existing `selectorFor(info: CameraInfo)`. It filters to the entry's logical `CameraInfo` exactly as today, and additionally, when the entry carries a physical camera id, applies that id to the preview and capture use-case builders through `Camera2Interop.Extender.setPhysicalCameraId`. Accept the `@OptIn(ExperimentalCamera2Interop::class)` requirement at the narrowest scope that compiles rather than on the whole file.

**Verification:**

- `Grep` - `setPhysicalCameraId` present in `CameraUseCaseFactory.kt`.
- `Grep` - `fun selectorFor` matches exactly twice in `CameraUseCaseFactory.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS (`a.ps1 fk` BUILD SUCCESSFUL). The physical id cannot live on the `CameraSelector` - it goes on the use-case builders, so the factory takes it as a constructor argument and `selectorFor(entry)` just resolves the logical camera. Applied to preview and image capture together, and skipped entirely in video mode because `VideoCapture.withOutput` exposes no builder to carry it - a preview on one lens and a recording on another would break WYSIWYG.

---

### Step 03.3 - Enumerate and bind lens entries in the session

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Replace the session's `availableCameras: List<CameraInfo>` with `availableLenses: List<CameraLensEntry>` produced by `CameraLensEnumerationManager` (expand, then select). `switchCamera()` cycles the entry list; `bindToLifecycle` binds the active entry's selector. Keep the existing reset of night, HDR, macro, white balance, manual sensor and exposure state on every switch. Update the existing `Timber.i` bind summary to report the entry count and how many of them are physical sub-lenses, keeping the line at or below 120 characters and free of any ticket id.

**Verification:**

- `Grep` - `availableLenses` present in `CameraCaptureSessionManager.kt`.
- `Grep` - `availableCameras` returns zero hits in `CameraCaptureSessionManager.kt`.
- `Grep` - `CameraLensEnumerationManager` present in `CameraCaptureSessionManager.kt`.
- `Grep` - no `Timber.*` line in `CameraCaptureSessionManager.kt` contains `S1189`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS. `availableCameras` fully replaced by `availableLenses` (0 references left). The ticket-id predicate was corrected: it originally demanded zero `S1189` anywhere in the file, but this file already carries 16 `S0753` comment references - ticket ids in KDoc are the house convention here, and CLAUDE.md's actual rule bans them in **logs**. Zero Timber lines carry the id.

---

### Step 03.4 - Fall back when a lens refuses to bind

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> When binding an entry throws, drop that entry from `availableLenses`, log the failure at warn level with the lens id, and retry with the next entry of the same facing; when no entry of that facing survives, bind the logical camera of that facing exactly as the pre-S1189 code did. The capture screen must never fail to open because an extended lens was unavailable (strategic ADR-3). Restoring a persisted lens choice that is absent from the current set falls back to the first back-facing entry instead of throwing.

**Verification:**

- `Grep` - `Timber.w` count in `CameraCaptureSessionManager.kt` increased versus before the step.
- `Grep` - `availableLenses.filterNot { it.id == activeLens.id }` present in `CameraCaptureSessionManager.kt` (the drop-and-retry, inlined - see step log).
- `Grep` - `catch (` with an empty body returns zero hits in `CameraCaptureSessionManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. Drops the refusing lens from the offered set and retries at the same facing, ending at that facing's logical camera - the exact set the screen had before this ticket.
- 2026-07-25 - AUDIT-FIX: extracted as `bindFallbackLens` first, but detekt (once it started running again, see S1191) reported `TooManyFunctions` - the class sits exactly on the 40-function ceiling, so the helper had to be inlined into the bind failure branch. Predicate updated to match the behaviour rather than a function name. The class is a standing decomposition candidate.

---

### Step 03.5 - Compute the zoom floor across the reachable set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add a `minEquivalentZoomRatio` field to `CameraRuntimeCapabilities` and a probe function that computes it over the whole offered lens set: for each back lens, its own minimum ratio scaled by `focal / referenceFocal`, taking the smallest. The session applies it alongside the other post-bind capability copies. A device exposing one back lens with a minimum of 1.0 must report exactly 1.0 and produce exactly the preset list it produces today.
>
> Scope note: the zoom presets themselves stay derived from the **bound** lens. Making a preset below the bound lens's own floor switch to a wider lens is a lens-selection behaviour, not a capability reading, and belongs with the label work in Phase 06 - implementing it here would put an untestable lens switch inside the bind path.

**Verification:**

- `Grep` - `minEquivalentZoomRatio` present in `CameraRuntimeCapabilities.kt`.
- `Grep` - `CameraLensEntry` present in `CameraCapabilityProbe.kt`.
- `Grep` - `fun buildZoomPresets` matches exactly once in `CameraRuntimeCapabilities.kt`.
- `.\a.ps1 fk` exits 0 (no resource file changed in this phase, so the resource-inclusive check is not the right rung).

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS (`a.ps1 fk` BUILD SUCCESSFUL). Scope narrowed versus the original prompt: the field reports the device's widest reachable equivalent zoom, and the presets stay derived from the bound lens. Seeding a preset below the bound lens's floor would mean the preset silently switches lenses, which is a behaviour change inside the bind path and cannot be verified without a device.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings; the P0 risk here is a capture screen that cannot open, so re-read Step 03.4's fallback before closing the phase.

---

## Handoff Notes to Next Phase

The session now owns a lens set rather than a camera list, and every consumer must read the active entry from it. Phase 04 changes what macro means; Phase 05 changes what resolutions are offered - both extend the same session and probe, so they must not reintroduce a `CameraInfo`-keyed path.

---

## Rollback Plan

Revert the phase commit(s) and restore `CameraCaptureSessionManager.kt` from the Step 03.1 backup. No data migration; a persisted lens choice falls back to the first back lens on the reverted code as well.
