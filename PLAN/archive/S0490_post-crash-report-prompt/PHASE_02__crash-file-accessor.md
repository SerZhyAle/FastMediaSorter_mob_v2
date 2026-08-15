# Phase 02 - Crash-file accessor

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__reuse-and-touchpoints.md`](research/01__reuse-and-touchpoints.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Expose the most recent crash-log file from the logging layer so the prompt manager can detect a new crash and read its text. No behaviour change elsewhere.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt` | Modified | ≤ 520 |

---

## Steps

### Step 02.1 - Add getLatestCrashFile()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `LoggingHelper` (the object public surface, next to `getLogFiles()` / `hasPreviousCrash()`), add `fun getLatestCrashFile(): File? = getLogFiles().firstOrNull { it.name.startsWith("fastmediasorter_crash_") && it.name.endsWith(".log") }`. `getLogFiles()` is already sorted by lastModified descending, so the first crash file is the newest. Do not change any other method.

**Verification:**

- `Grep` - `fun getLatestCrashFile` matches exactly once in `LoggingHelper.kt`.
- `Grep` - `fastmediasorter_crash_` present in `LoggingHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (getLatestCrashFile present; `.\a.ps1 fk` BUILD SUCCESSFUL). Files: LoggingHelper.kt.

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added.
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05 (noted).

---

## Handoff Notes to Next Phase

`LoggingHelper.getLatestCrashFile(): File?` returns the newest crash file or null. Phase 03 uses its name as the watermark key and its text as the email body source.

---

## Rollback Plan

Revert the single method addition - no caller until Phase 03.
