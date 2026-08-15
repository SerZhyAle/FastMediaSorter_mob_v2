# Phase 01 — Dialog lifecycle guard

**Strategic spec:** [`../S0091_bugfix-file-op-progress-startup-race.md`](../S0091_bugfix-file-op-progress-startup-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Prevent `FileOperationProgressDialog` from touching summary `lateinit` views before delayed `show()` inflates the layout.

---

## Prerequisites

- [ ] Working tree is acceptable for edits.
- [ ] Re-read current `FileOperationProgressDialog` comment and delayed-show logic before touching the file.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt` | Modified | ≤ 30 |

---

## Steps

### Step 01.1 — Guard pre-init summary view access

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a tiny dialog-local readiness guard for `tvOverallPercent` and `tvEta`, and use it in the `Starting` and `Completed` branches of `updateProgress(...)` so no code writes those `lateinit` views before `onCreate()` runs. Keep the delayed show mechanism untouched. Do not widen the fix into `FileOperationDestinationDialog` unless the local guard proves insufficient.

**Verification:**

- Focused grep: all writes to `tvOverallPercent` / `tvEta` outside `applyProgressToUI(...)` are protected by a readiness check.
- No new TODO markers in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Added `hasSummaryViews()` and guarded `Starting` / `Completed` writes. No TODO markers introduced.

---

### Step 01.2 — Compile touched Kotlin slice

**Files:** none (validation only)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run a focused Kotlin compile for `app_v2` debug after the dialog change. If it fails, fix only errors introduced by this phase and rerun the same compile.

**Verification:**

- Compile command exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `./gradlew.bat :app_v2:compileStandardDebugKotlin` → `BUILD SUCCESSFUL in 11s`.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Step 01.2 is `[x] done`.
- [x] No new diagnostics remain in `FileOperationProgressDialog.kt`.

---

## Rollback Plan

Revert the single dialog file edit.