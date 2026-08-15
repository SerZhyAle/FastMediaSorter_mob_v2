# Phase 01 - Environment Prepare

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add a helper that brings a chosen emulator to a clean known state: uninstall prior package, clean-install standard debug, seed test media if absent, verify first launch from logs. No scenario, no verdict yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/devtest/device-ready.ps1`, `scripts/utils/setup_test_media.ps1`, `scripts/builders/build-standard-device.ps1` exist (verified in step 2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/prerelease-prepare.ps1` | New | ≤ 220 |
| `scripts/devtest/prerelease.config.psd1` | New | ≤ 40 |

---

## Steps

### Step 01.1 - Scaffold prepare script with device gate

**Files:** `scripts/devtest/prerelease-prepare.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `prerelease-prepare.ps1` with params `-DeviceId`, `-Json`. Resolve and gate the device by delegating to `scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -DeviceId <id> -Json` and propagating its non-zero exit codes (1..6) as abort reasons. Emit a structured per-step result object (step name, status, detail) collected for `-Json` output.

**Verification:**

- `Glob` - `scripts/devtest/prerelease-prepare.ps1` exists.
- `Grep` - `device-ready.ps1` referenced once.
- `Grep` - `param(` block contains `DeviceId` and `Json`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (file exists, device-ready.ps1 referenced once, param has DeviceId+Json, PS parse OK). Files: scripts/devtest/prerelease-prepare.ps1 (New, ~95 LOC). Dev log recorded.

---

### Step 01.2 - Clean uninstall + install standard debug

**Files:** `scripts/devtest/prerelease-prepare.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a stage that uninstalls `com.sza.fastmediasorter.debug` if present (ignore "not installed"), then clean-installs via `scripts/builders/build-standard-device.ps1`. Never call `gradlew.bat` directly. On install failure, record the stage as failed and stop the run with a non-zero exit.

**Verification:**

- `Grep` - `build-standard-device.ps1` referenced once.
- `Grep` - `uninstall` present.
- `Grep` - `com.sza.fastmediasorter.debug` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (build-standard-device.ps1 ×1, uninstall stage, debug package). ANDROID_SERIAL targets the chosen device for the param-less builder. Files: scripts/devtest/prerelease-prepare.ps1 (+22 LOC). Dev log recorded.

---

### Step 01.3 - Seed test media when absent

**Files:** `scripts/devtest/prerelease-prepare.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a stage that probes the device for the seeded media root (`/sdcard/Download/FastMediaSorter_Test`); if absent, invoke `scripts/utils/setup_test_media.ps1` for the selected device. Skip seeding (record `skipped - present`) when the root already exists, so re-runs are idempotent.

**Verification:**

- `Grep` - `setup_test_media.ps1` referenced once.
- `Grep` - `FastMediaSorter_Test` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (setup_test_media.ps1 ×1, FastMediaSorter_Test present; parse OK). Idempotent probe of /sdcard/Download/FastMediaSorter_Test; seeds only when absent. Files: scripts/devtest/prerelease-prepare.ps1 (+18 LOC). Dev log recorded.

---

### Step 01.4 - Verify first launch from logs

**Files:** `scripts/devtest/prerelease-prepare.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Launch the explicit MainActivity via `scripts/devtest/adb.ps1 launch` (dodges the debug LeakCanary launcher) and confirm a clean start by checking logcat for app start without a crash/FATAL in the launch window. Record the final readiness verdict in the result object and exit 0 only when all stages passed.

**Verification:**

- `Grep` - `adb.ps1 launch` (or `adb.ps1` with `launch`) referenced.
- `Grep` - `MainActivity` present.
- `Script` - `pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 -Json` emits valid JSON on stdout and a documented exit code ({0,1,2,3} gate / 10 stage-fail) against a connected emulator.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (adb.ps1 launch ×1, MainActivity present, parse OK). Live run against emulator-5554: full prepare succeeded - stages device-gate/uninstall/install/seed-media/launch-verify all OK, ready:true, exit 0, valid JSON. No crash/FATAL/ANR in launch window. Files: scripts/devtest/prerelease-prepare.ps1 (+24 LOC). Dev log recorded.

---

### Step 01.5 - Create run-config skeleton

**Files:** `scripts/devtest/prerelease.config.psd1`
**Depends on:** - independent of 01.1-01.4

**Prompt for developer:**

> Create the shared run-config data file as a foundation artifact with three empty documented blocks: `Resources = @{}`, `Settings = @{}`, `Thresholds = @{}`. Content is populated by Phase 02 (Resources/Settings) and Phase 03 (Thresholds) once their research resolves; this step only fixes the structure so both phases modify rather than create it.

**Verification:**

- `Glob` - `scripts/devtest/prerelease.config.psd1` exists.
- `Grep` - keys `Resources`, `Settings`, `Thresholds` all present.
- `Script` - `pwsh -NoProfile -Command "Import-PowerShellDataFile scripts/devtest/prerelease.config.psd1"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (file exists, Resources/Settings/Thresholds keys present, Import-PowerShellDataFile exit 0). Files: scripts/devtest/prerelease.config.psd1 (New, 15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 -Json` runs and emits valid JSON (live run on emulator-5554, exit 0).
- [x] `prerelease.config.psd1` imports cleanly with all three blocks present.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both new files.

---

## Handoff Notes to Next Phase

Provides a clean, media-seeded, launched standard-debug emulator and a structured readiness result that Phases 02-05 build on.

---

## Rollback Plan

Delete `scripts/devtest/prerelease-prepare.ps1` - no data migration or user-facing surface changed.
