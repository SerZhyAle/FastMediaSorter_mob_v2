# Phase 01 - Mechanical Fixes

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Clear every mechanically-fixable detekt finding on `CameraCaptureActivity.kt` (import order, supertype-list wrapping, 2 magic numbers, 1 `ReturnCount` excess) with zero behavior change, before any structural extraction begins.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch for this ticket's files.
- [ ] `./gradlew.bat :app_v2:detekt --rerun-tasks` run once to confirm current finding set matches strategic §0/§1 (LargeClass, TooManyFunctions, ImportOrdering, Wrapping, ReturnCount, 2x MagicNumber on `CameraCaptureActivity.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 964 (no growth expected) |

---

## Steps

### Step 01.1 - Reorder imports to ktlint layout

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Reorder the import block (lines 3-58) to the project's ktlint `ImportOrdering` layout (`*,java.**,javax.**,kotlin.**,^`, case-sensitive ASCII sort, uppercase before lowercase, no blank lines). Two groups are currently out of order: `android.net.Uri` must come after `android.hardware.camera2.CameraMetadata` (not before), and inside `com.sza.fastmediasorter.ui.cameracapture.helpers.*` the order must be `CameraCaptureFlowManager`, `CameraCaptureGestureManager`, `CameraCaptureSessionManager`, `CameraLocationProvider`, `CameraOrientationManager`, `CameraRecordingTimer`. The `kotlinx.coroutines.*` group must sort `Dispatchers`, `Job`, `delay`, `flow.first`, `launch`, `withContext` (uppercase-first ordinal, not alphabetic-insensitive). Do not change any non-import line.

**Verification:**

- `Grep` - `import android.net.Uri` appears on a line number greater than `import android.hardware.camera2.CameraMetadata` in the file.
- `Grep` - `import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager` appears before `import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider`.
- `Grep` - `import kotlinx.coroutines.Job` appears before `import kotlinx.coroutines.delay`.

**Status:** `[x]` done

---

### Step 01.2 - Fix Wrapping on the class supertype list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> The class declaration wraps its supertype list across multiple lines but keeps the first supertype on the same line as the `:` - ktlint `Wrapping` requires a newline immediately after `:` when the list wraps. Move `BaseActivity<ActivityCameraCaptureBinding>()` to its own indented line, so the declaration reads `class CameraCaptureActivity :` followed by one indented supertype per line. Do not change the supertype list itself (still exactly 4 supertypes plus the base class).

**Verification:**

- `Grep` - `class CameraCaptureActivity :$` matches exactly once (colon is the last non-whitespace character on the line).
- `Grep` - `    BaseActivity<ActivityCameraCaptureBinding>(),` matches exactly once, on the line immediately following.

**Status:** `[x]` done

---

### Step 01.3 - Extract 2 magic numbers to named companion constants

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add two `private const val` to the existing `companion object` (alongside `TAB_SELECTED_ALPHA` etc.): `COUNTDOWN_TICK_INTERVAL_MS = 1_000L` (self-timer countdown tick, currently the literal `1_000` passed to `delay()` in `startSelfTimer`) and `OVERLAY_ROTATION_ANIMATION_MS = 180L` (rotation animation duration, currently the literal `180L` passed to `.setDuration()` in `applyOverlayRotation`). Replace both call sites with the new constants. No other behavior change.

**Verification:**

- `Grep` - `private const val COUNTDOWN_TICK_INTERVAL_MS = 1_000L` matches exactly once.
- `Grep` - `private const val OVERLAY_ROTATION_ANIMATION_MS = 180L` matches exactly once.
- `Grep` - `delay(1_000)` returns zero hits; `delay(COUNTDOWN_TICK_INTERVAL_MS)` matches exactly once.
- `Grep` - `.setDuration(180L)` returns zero hits; `.setDuration(OVERLAY_ROTATION_ANIMATION_MS)` matches exactly once.

**Status:** `[x]` done

---

### Step 01.4 - Reduce resolveSaveDestinationName() to <=2 return statements

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `resolveSaveDestinationName()` has 3 return statements (detekt `ReturnCount` limit is 2). Keep the first early return (`if (!flowManager.multiCapture) return fixedOutputParent`) as-is. Collapse the second early return (`if (!configuredName.isNullOrBlank()) return configuredName`) and the final fallback return into a single trailing `return` expression using `configuredName?.takeUnless { it.isBlank() } ?: <fallback-when-block>`, where `<fallback-when-block>` is the existing `if (flowManager.isVideoMode) ... else ...` fallback unchanged. Net effect: identical return values for every input (null/blank/non-blank `configuredName`), function body ends in exactly 2 `return` statements.

**Verification:**

- `Grep -c "return"` inside the function body (from `private suspend fun resolveSaveDestinationName` to its closing brace) equals 2.
- `Grep` - `takeUnless { it.isBlank() }` matches exactly once in the file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fk` PASS.
- [x] `./gradlew.bat :app_v2:detekt --rerun-tasks` no longer reports `ImportOrdering`, `Wrapping`, `MagicNumber` (lines 771/792), or `ReturnCount` (resolveSaveDestinationName) for `CameraCaptureActivity.kt` - only `LargeClass`/`TooManyFunctions` remain, as expected at this point.
- [x] Dev log entry added for the file (batched with tactical plan / phase closures, see final report).

---

## Handoff Notes to Next Phase

`CameraCaptureActivity.kt` is now detekt-clean except `LargeClass`/`TooManyFunctions`. Companion object gained 2 new constants (`COUNTDOWN_TICK_INTERVAL_MS`, `OVERLAY_ROTATION_ANIMATION_MS`) that later phases must not remove. Import block is now correctly ordered - later phases adding new imports (for the extracted helper classes) must insert them in the correct ktlint position, not just append.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - purely mechanical formatting/constant-extraction/return-count refactor, no data migration or user-facing surface changed.
