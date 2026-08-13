# Phase 02 - Lens entry model and extended enumeration

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Introduce a lens-entry model and the manager that expands the logical camera list into physical sub-lenses and applies the selection rule, without changing what the capture screen binds yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` still exposes `availableCameras(provider)`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraLensEntry.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensEnumerationManager.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 260 |

> No layout file is touched in this phase.

---

## Steps

### Step 02.1 - Add the lens entry model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraLensEntry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraLensEntry` as a data class holding the `CameraInfo` to bind, the logical camera id, an optional physical camera id, lens facing, focal length in mm, the reported minimum zoom ratio, the minimum focus distance in diopters, and a `hasOwnMagnification` flag. Add a computed `isPhysicalSubLens` derived from the physical id being non-null. Keep the class free of CameraX control types - it describes a lens, it does not operate one (strategic ADR-1).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraLensEntry.kt` exists.
- `Grep` - `data class CameraLensEntry` matches exactly once.
- `Grep` - `val physicalCameraId: String?` present.
- `Grep` - `CameraControl` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS. Files: ui/cameracapture/model/CameraLensEntry.kt (+31 LOC). `cameraInfo` holds the LOGICAL camera to select and `physicalCameraId` narrows it, because a physical sub-lens is bound by naming it on the logical camera, not by selecting it directly.

---

### Step 02.2 - Expand logical cameras into lens entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensEnumerationManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `CameraLensEnumerationManager` with a function taking `ProcessCameraProvider` and returning `List<CameraLensEntry>`. For each `CameraInfo` in `availableCameraInfos`, emit one entry for the logical camera itself, then on API 28+ call `CameraInfo.getPhysicalCameraInfos()` and emit one further entry per physical sub-lens carrying its own focal length, zoom minimum and focus distance. A sub-lens whose characteristics cannot be read is skipped, not emitted with placeholder values. Order back-facing entries first, then front, and within a facing order by ascending focal length so the widest lens leads.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensEnumerationManager.kt` exists.
- `Grep` - `class CameraLensEnumerationManager` matches exactly once.
- `Grep` - `physicalCameraInfos` present (Kotlin sees the Java `getPhysicalCameraInfos()` getter as a property).
- `Grep` - `Build.VERSION.SDK_INT` present.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 5/5 PASS. Files: ui/cameracapture/helpers/CameraLensEnumerationManager.kt (+113 LOC). `CameraInfo.physicalCameraInfos` confirmed present in the pinned CameraX 1.5.3 by a passing compile, so no Camera2 fallback path was needed.

---

### Step 02.3 - Apply the lens selection rule

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLensEnumerationManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a selection function that reduces the expanded list to the entries the switcher offers, per strategic §6 item 2: keep an entry only when its focal length differs from every already-kept entry of the same facing by more than a small ratio tolerance, and drop entries that report no focal length at all when a same-facing sibling already covers that magnification. Keep the logical camera of each facing unconditionally so a device that exposes nothing extra ends up with exactly today's set. Expose the rule as a single named function so swapping it to "keep every physical lens" is a one-line change (strategic §5.3).

**Verification:**

- `Grep` - a function whose name contains `select` and returns `List<CameraLensEntry>` present in `CameraLensEnumerationManager.kt`.
- `Grep` - `hasOwnMagnification` present in `CameraLensEnumerationManager.kt`.
- `Grep` - a `private const val` tolerance constant present in the file's companion object.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. Implemented as "keep every logical camera, then add a physical sub-lens only when its focal length is new for that facing", which is what makes a single-lens device end up with byte-identical behaviour rather than merely similar.

---

### Step 02.4 - Probe a lens entry rather than only the bound camera

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an overload to `CameraCapabilityProbe` that computes the reference focal length from a `List<CameraLensEntry>` instead of a `List<CameraInfo>`, choosing the back entry with a flash unit and falling back to the first back entry, matching the existing behaviour. Leave the existing `CameraInfo`-based functions in place - Phase 03 removes their last caller.

**Verification:**

- `Grep` - `List<CameraLensEntry>` present in `CameraCapabilityProbe.kt`.
- `Grep` - `fun mainBackFocalLength` matches exactly twice in `CameraCapabilityProbe.kt` (existing plus overload).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS (`a.ps1 fk` BUILD SUCCESSFUL, exit 0). The overload needs `@JvmName` - both `List<CameraInfo>` and `List<CameraLensEntry>` erase to the same JVM signature, so plain overloading is a platform declaration clash.
- 2026-07-25 - AUDIT-P2: the scoped detekt gate failed this file on two pre-existing `MaximumLineLength` / `ArgumentListWrapping` findings at the ISO and shutter-range probes - untouched debt that only surfaces once the file is edited. Wrapped both calls across lines (no line now exceeds 120 chars) rather than baselining them, since Rule 7 makes lint in a touched file this ticket's problem.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The expanded lens set and the selection rule exist but nothing binds them yet, so runtime behaviour is unchanged at the end of this phase. Phase 03 is the first phase that can regress an existing device - keep its fallback path (strategic ADR-3) intact.

---

## Rollback Plan

Revert the phase commit(s) - two new files and an additive overload; no caller changed behaviour yet.
