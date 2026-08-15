# Phase 02 - Screenshot save path (destination resolver + saver)

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Provide a flavor-agnostic, testable path that turns a captured `Bitmap` into a saved image at the configured destination - selected resource, else the device Screenshots folder, else Downloads. No overlay/projection code.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`screenshotDestinationResourceId` persisted).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicy.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveScreenshotUseCase.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicyTest.kt` | New | ≤ 120 |

> These live in `src/main` deliberately: they carry no permissions and no flavor specifics, are reusable by the future Play rollout, and are unused on flavors that never call them.

---

## Steps

### Step 02.1 - Add ScreenshotDestinationPolicy (pure resolver)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicy.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ScreenshotDestinationPolicy` mirroring `CaptureDestinationPolicy`. Resolve in order: (1) a user-selected resource id (from `AppSettings.screenshotDestinationResourceId`) → that `MediaResource`; (2) else the device Screenshots public folder via `MediaStore` relative path - prefer `Pictures/Screenshots`, fall back to `DCIM/Screenshots`; (3) else `Downloads`. Keep it a pure resolver with no Android `Context` I/O beyond `MediaStore` path constants so it stays unit-testable. Return a sealed result describing target kind (selected resource vs public relative path).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenshotDestinationPolicy` matches once.
- `Grep` - `Screenshots` literal present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicy.kt (+51 LOC). Dev log recorded.

---

### Step 02.2 - Add SaveScreenshotUseCase (Bitmap → destination)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveScreenshotUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `SaveScreenshotUseCase` (Hilt `@Inject constructor`) that takes a `Bitmap` + the resolved `ScreenshotDestinationPolicy` result and writes a PNG. Reuse the proven save mechanics of `SaveVideoFrameManager`: write a temp file, then either copy into the selected resource via `FileOperationUseCase` (`FileOperation.Copy`) or write to the public collection via `MediaStoreLocalDestinationWriter` (`RELATIVE_PATH = Pictures/Screenshots`, `IS_PENDING` toggle on API 29+). All I/O on `Dispatchers.IO`. Recycle the `Bitmap` and delete the temp file in a `finally`. Return a sealed result that on success carries the saved **file name** and a human-readable **destination label** (the resource display name, or the public-folder name) - the capture service (Phase 03) needs both for the confirmation toast. No silent empty catch (CLAUDE.md Rule 19).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class SaveScreenshotUseCase` matches once.
- `Grep` - `MediaStoreLocalDestinationWriter` OR `FileOperationUseCase` referenced.
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveScreenshotUseCase.kt (+146 LOC). Dev log recorded.

---

### Step 02.3 - Unit-test the destination resolver

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicyTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add JVM unit tests for `ScreenshotDestinationPolicy`: selected-resource path wins when id present; Screenshots public path chosen when id null; Downloads fallback chosen when Screenshots unavailable. Pure logic only - no Android instrumentation.

**Verification:**

- `Glob` - test file exists.
- `Grep` - `class ScreenshotDestinationPolicyTest` matches once.
- Run `./gradlew.bat testStandardDebugUnitTest --tests "*ScreenshotDestinationPolicyTest"`; the per-class XML report under `app_v2/build/test-results/` shows 0 failures (ignore unrelated pre-existing failures).

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/test/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicyTest.kt (+60 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `ScreenshotDestinationPolicyTest` passes (own-class XML report green).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`SaveScreenshotUseCase` is injectable and turns a `Bitmap` into a saved image at the configured destination. Phase 03's capture service injects and calls it after producing a frame.

---

## Rollback Plan

Revert phase commit(s) - new, unreferenced classes; no migration, no user-facing surface.
