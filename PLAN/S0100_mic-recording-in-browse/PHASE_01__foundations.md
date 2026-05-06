# Phase 01 — Foundations

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Add `micRecordingEnabled` / `micRecordingAskFilename` fields to `AppSettings` and add `SUPPORT_MIC_RECORDING` BuildConfig flag to each product flavor.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/build.gradle.kts` | Modified | ≤ 500 |

---

## Steps

### Step 1.1 — Add mic recording settings fields to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the `AppSettings` data class, add two fields near the existing camera-capture fields (`disableCameraCapture`, `skipCameraFilenameDialog`):
> `val micRecordingEnabled: Boolean = false` and `val micRecordingAskFilename: Boolean = true`.
> Default values mirror the behavior described in the strategic spec: feature off by default, filename dialog on by default.

**Verification:**

- `Grep` — `micRecordingEnabled` present in `domain/model/AppSettings.kt`.
- `Grep` — `micRecordingAskFilename` present in `domain/model/AppSettings.kt`.

**Status:** `[ ]` not done

---

### Step 1.2 — Add SUPPORT_MIC_RECORDING BuildConfig field per flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase (independent of 1.1)

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, inside each `productFlavors` block add a new `buildConfigField`:
> - `standard`: `buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")`
> - `lite`: `buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false")` — excluded per §6 decision
> - `photos`: `buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false")` — no audio support
> - `legacy`: `buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")`
> Place each line immediately after the corresponding `SUPPORT_AUDIO` field for readability.

**Verification:**

- `Grep` — `SUPPORT_MIC_RECORDING` appears exactly 4 times in `app_v2/build.gradle.kts`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`AppSettings` has two new fields with safe defaults; `BuildConfig.SUPPORT_MIC_RECORDING` is available in all flavors. Phases 02, 03, and 04 may proceed in parallel.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
