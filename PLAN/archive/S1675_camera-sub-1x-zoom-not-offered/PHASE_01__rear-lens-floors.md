# Phase 01 - Rear-lens floors on the capability snapshot

**Strategic spec:** [`../S1675_camera-sub-1x-zoom-not-offered.md`](../S1675_camera-sub-1x-zoom-not-offered.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Carry the equivalent floor of every rear lens into `CameraRuntimeCapabilities`, computed by a pure function and covered by the existing unit test class. No UI change yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 1080 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt` | Modified | ≤ 230 |

> `CameraCaptureSessionManager.kt` is 1055 LOC - over the 500-LOC threshold, so take the Rule 5 timestamped backup before editing it. No `res/layout*` file is touched, so CLAUDE.md Rule 11 landscape parity does not apply.

---

## Steps

### Step 01.1 - Add the pure floor builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the companion object of `CameraRuntimeCapabilities`, next to `buildZoomPresets`, add `fun buildRearLensFloors(nativeFloors: List<Float>): List<Float>`. The caller passes one raw equivalent floor per rear lens - `minZoomRatio * equivalentMultiplier` for that lens.
>
> The function rounds each value with the same `roundEquivalentForDisplay` rule the cross-lens pill already uses, drops non-positive values, removes duplicates that survive rounding, and returns the result sorted ascending. Keep it free of Android types so the existing unit-test class can reach it.

**Why:**

Strategic §3.2.1 records that the work is to carry the list of rear-lens equivalent floors into the capability snapshot, and the per-lens data currently stops at `CameraLensEntry` inside the session manager; a pure builder is what lets the rounding and dedup rule be proven without a device.

**Verification:**

- `Grep` - `fun buildRearLensFloors(` matches exactly once in that file.
- `Grep` - `roundEquivalentForDisplay` referenced inside the new function.
- `Grep` - no `androidx.camera` or `android.` type appears in the new function's body.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 01 in one pass: pure buildRearLensFloors (round/drop/dedup/sort, emptiness left to the caller), rearLensEquivalentFloors on the capability snapshot, rearLensFloors() at the single construction site dropping the single-rear-lens device, plus ownEquivalentFloorDisplay so the active pill can be matched by the same printed value. Greps: decl 1, prop in both files 1+1, call 1, test mentions 5. Scoped unit run exit 0, TEST-CameraRuntimeCapabilitiesTest.xml tests=20 failures=0 errors=0 (14 pre-existing + 6 new). LOC 215 / 1068 / 197, all within budget.

---

### Step 01.2 - Expose the list on the snapshot and fill it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `val rearLensEquivalentFloors: List<Float> = emptyList()` to the `CameraRuntimeCapabilities` constructor, documented as the display-rounded equivalent floor of each rear lens, ascending, and empty when the device has fewer than two rear lenses.
>
> Fill it at the single construction site in `CameraCaptureSessionManager` (the same block that already computes `minEquivalentZoomRatio` from `availableLenses`): map the entries whose `lensFacing` is back to `minZoomRatio * equivalentMultiplier`, pass them through `CameraRuntimeCapabilities.buildRearLensFloors`, and hand the result to the constructor. Leave the list empty when fewer than two rear lenses survive the mapping - a single-lens device must not gain a one-pill row.
>
> Take the Rule 5 timestamped backup of `CameraCaptureSessionManager.kt` before editing it.

**Why:**

Strategic §3.4 test 5 requires a device with one rear lens to behave exactly as today, and the emptiness rule is what encodes that; §3.2.1 names this construction site as the place the per-lens data already exists.

**Verification:**

- `Grep` - `rearLensEquivalentFloors` present in both files.
- `Grep` - `buildRearLensFloors(` called exactly once in `CameraCaptureSessionManager.kt`.
- `.\a.ps1 fk` exits 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 01 in one pass: pure buildRearLensFloors (round/drop/dedup/sort, emptiness left to the caller), rearLensEquivalentFloors on the capability snapshot, rearLensFloors() at the single construction site dropping the single-rear-lens device, plus ownEquivalentFloorDisplay so the active pill can be matched by the same printed value. Greps: decl 1, prop in both files 1+1, call 1, test mentions 5. Scoped unit run exit 0, TEST-CameraRuntimeCapabilitiesTest.xml tests=20 failures=0 errors=0 (14 pre-existing + 6 new). LOC 215 / 1068 / 197, all within budget.

---

### Step 01.3 - Unit-test the builder

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilitiesTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Extend the existing test class with cases for `buildRearLensFloors`: a three-lens device returns its floors ascending and display-rounded (0.5 / 1 / 3 from raw values that are not already round); duplicate floors collapse to one entry; a non-positive floor is dropped; an empty input returns an empty list; a single-floor input returns that one entry, so the emptiness decision stays with the caller rather than being hidden in the builder.
>
> Follow the file's existing naming and plain-JUnit style.

**Why:**

The strategic §7 risk entry states that a fix landed without measurement repeats the mistake S1261 warns about, and the rounding and dedup rule is the part of this change that can be proven without the device.

**Verification:**

- `Grep` - `buildRearLensFloors` appears in the test file at least 5 times.
- Scoped run `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*CameraRuntimeCapabilitiesTest*"` exits 0; read the class's `TEST-*.xml` and record `tests=N failures=0 errors=0`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Phase 01 in one pass: pure buildRearLensFloors (round/drop/dedup/sort, emptiness left to the caller), rearLensEquivalentFloors on the capability snapshot, rearLensFloors() at the single construction site dropping the single-rear-lens device, plus ownEquivalentFloorDisplay so the active pill can be matched by the same printed value. Greps: decl 1, prop in both files 1+1, call 1, test mentions 5. Scoped unit run exit 0, TEST-CameraRuntimeCapabilitiesTest.xml tests=20 failures=0 errors=0 (14 pre-existing + 6 new). LOC 215 / 1068 / 197, all within budget.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`capabilities.rearLensEquivalentFloors` is the single source the UI reads; it is already display-rounded, so Phase 02 prints its values unchanged and passes them straight to `switchCamera(targetEquivalentFloor)`.

---

## Rollback Plan

Revert the phase commit - one new constructor field with a default, so no call site breaks; restore `CameraCaptureSessionManager.kt` from its timestamped backup if the edit went wrong.
