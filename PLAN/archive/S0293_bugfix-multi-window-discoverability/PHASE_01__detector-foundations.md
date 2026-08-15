# Phase 01 - Detector Foundations

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Extend `MultiWindowCapabilityDetector` with (1) a private helper that encapsulates the install-time capability signal set, (2) a parallel install-time pair method `defaultFileOpsInOverflowMenu(context)` symmetric to `defaultAllowSeparateWindow(context)`, and (3) a runtime-aware static method `isMultiWindowActiveNow(activity)` that returns true when the Activity is currently in a desktop/freeform UX context.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `dev/CATALOG/app_v2.jsonl` is up-to-date (run `scripts/catalog_sync.ps1 -Module app_v2` if uncertain).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/MultiWindowCapabilityDetector.kt` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Extract private capability-signals helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/MultiWindowCapabilityDetector.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `MultiWindowCapabilityDetector`, extract the install-time signal check used inside `defaultAllowSeparateWindow` into a private helper `hasInstallTimeMultiWindowSignal(context: Context): Boolean` that returns the boolean expression currently inlined there: ChromeOS OR `FEATURE_ANDROID_XR_IMMERSIVE` OR `FEATURE_ANDROID_XR_OPENXR` OR `FEATURE_VR_HEADTRACKING` OR `isKnownVrManufacturer()`. Refactor `defaultAllowSeparateWindow` to delegate to the new helper. Behavior must be identical - this is pure extraction.

**Verification:**

- `Grep` - `private fun hasInstallTimeMultiWindowSignal\(context: Context\): Boolean` matches exactly once in the file.
- `Grep` - `fun defaultAllowSeparateWindow\(context: Context\): Boolean` matches exactly once.
- `Grep` - inside the file body, `ChromeOsCompat.isChromeOs` appears exactly once (no longer duplicated by the new pair method - the helper owns it).
- Compile check via `/build` (target: `assembleStandardDebug`) - deferred to Phase Done Criteria.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 Grep PASS. Files: `MultiWindowCapabilityDetector.kt` (+2 LOC). Compile deferred to Phase Done. Dev log + catalog scan via post-change.ps1.

---

### Step 01.2 - Add `defaultFileOpsInOverflowMenu` pair method

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/MultiWindowCapabilityDetector.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add public method `fun defaultFileOpsInOverflowMenu(context: Context): Boolean` directly under `defaultAllowSeparateWindow`. Body: `return hasInstallTimeMultiWindowSignal(context)`. Add a KDoc comment one line above noting that this returns the install-time default for the paired `fileOpsInOverflowMenu` preference - the per-row ⋮ overflow button is required to expose multi-window entries on capability-detected devices.

**Verification:**

- `Grep` - `fun defaultFileOpsInOverflowMenu\(context: Context\): Boolean` matches exactly once.
- `Grep` - in the method body, `hasInstallTimeMultiWindowSignal\(context\)` appears (the implementation delegates, no signal duplication).
- Compile check via `/build` (target: `assembleStandardDebug`) - deferred to Phase Done Criteria.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 Grep PASS. Files: `MultiWindowCapabilityDetector.kt` (+8 LOC incl. KDoc). Compile deferred. Dev log + catalog scan via post-change.ps1.

---

### Step 01.3 - Add runtime-aware `isMultiWindowActiveNow(activity)`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/MultiWindowCapabilityDetector.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add public method `fun isMultiWindowActiveNow(activity: android.app.Activity): Boolean`. Body returns true if EITHER (a) `hasInstallTimeMultiWindowSignal(activity)` (the device is a permanent-multi-window form factor - Quest 3 / XR / ChromeOS / Meta), OR (b) `(activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_DESK` (the Activity is currently in a desktop/DeX-mode UI container). Add a KDoc comment summarising the runtime override rule per ADR-3.

**Verification:**

- `Grep` - `fun isMultiWindowActiveNow\(activity: android.app.Activity\): Boolean` matches exactly once.
- `Grep` - `Configuration.UI_MODE_TYPE_DESK` appears in the file body.
- `Grep` - method body references `hasInstallTimeMultiWindowSignal` for the install-time override path.
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS (BUILD SUCCESSFUL 1m 44s).

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 4/4 PASS (Grep + assembleStandardDebug). Files: `MultiWindowCapabilityDetector.kt` (+22 LOC incl. imports + KDoc). Dev log + catalog scan via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS 1m 44s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `MultiWindowCapabilityDetector.kt` via `.\scripts\add_to_dev_log.ps1` (3 entries via post-change.ps1).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via post-change.ps1 catalog_sync.

---

## Handoff Notes to Next Phase

`MultiWindowCapabilityDetector` now exposes three public methods sharing the same install-time signal helper:

- `defaultAllowSeparateWindow(context)` - install-time default for "Allow new windows" preference.
- `defaultFileOpsInOverflowMenu(context)` - install-time default for "File ops in ⋮" preference.
- `isMultiWindowActiveNow(activity)` - runtime-aware effective state combining install-time signals with current Activity UI mode.

Phase 02 consumes the first two as fallback defaults in `SettingsRepositoryImpl`. Phase 05 consumes `isMultiWindowActiveNow` for live UI reactivity.

---

## Rollback Plan

Revert the phase commit. No data migration; no user-facing surface; no breaking change to existing callers - `defaultAllowSeparateWindow` keeps its signature and behavior.
