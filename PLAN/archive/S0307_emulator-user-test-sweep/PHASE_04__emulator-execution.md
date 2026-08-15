# Phase 04 - Emulator Execution

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Install the selected build, copy prepared fixtures to the online emulator, execute runnable verification routes and harvest screenshots/logs.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] `adb devices -l` reports at least one emulator in `device` state.
- [x] `temp/s0307/03_build_install_plan.md` and `temp/s0307/03_push_manifest.txt` exist.
- [x] No real credentials or private account data are required by selected routes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/s0307/04_device_ready.txt` | New | ≤ 200 |
| `temp/s0307/04_build_install_log.txt` | New | ≤ 1200 |
| `temp/s0307/04_fixture_push_log.txt` | New | ≤ 400 |
| `temp/s0307/04_execution_log.md` | New | ≤ 1200 |
| `temp/s0307/04_logcat_summary.txt` | New | ≤ 800 |

---

## Steps

### Step 04.1 - Verify Device Ready

**Files:** `temp/s0307/04_device_ready.txt`
**Depends on:** start of phase

**Prompt for developer:**

> Require an emulator in `device` state. Capture API, ABI, screen size, storage and package state. If only `offline` devices exist, write the evidence file, mark Phase 04 blocked and set S0307 to `BlockExternal` without mutating target tickets.

**Verification:**

- `Glob` - `temp/s0307/04_device_ready.txt` exists.
- `Grep` - `device_state=device` appears exactly once to proceed beyond this step.
- `Grep` - `emulator_serial=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/3 PASS, 1/3 FAIL. Artifact: `temp/s0307/04_device_ready.txt`. `device_state=offline`, so Phase 04 is blocked before build/install/push. Target ticket mutations: 0.
- 2026-05-30 - Verification 3/3 PASS. Artifact refreshed: `temp/s0307/04_device_ready.txt`. `emulator-5554` is online, API 33, ABI x86_64.

---

### Step 04.2 - Build And Install Target Flavor

**Files:** `temp/s0307/04_build_install_log.txt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the target debug build script selected in Phase 03 and install the APK on the emulator. Use project build scripts rather than raw Gradle. Capture command, exit code, APK path and package install result.

**Verification:**

- `Glob` - `temp/s0307/04_build_install_log.txt` exists.
- `Grep` - `build_exit_code=0` appears exactly once.
- `Grep` - `install_exit_code=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/04_build_install_log.txt`. Standard debug build/install/launch passed.

---

### Step 04.3 - Push Fixtures

**Files:** `temp/s0307/04_fixture_push_log.txt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Push only files from the Phase 03 push manifest to emulator storage. Capture source path, destination path, size and adb exit code for every file. Do not push secrets.

**Verification:**

- `Glob` - `temp/s0307/04_fixture_push_log.txt` exists.
- `Grep` - `push_failures=0` appears exactly once.
- `Grep` - `secret_files=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/04_fixture_push_log.txt`. Six files pushed under `/sdcard/Download/FMS_S0307`; secret files: 0.

---

### Step 04.4 - Execute Runnable Routes

**Files:** `temp/s0307/04_execution_log.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Execute every `direct-emulator` route and every feasible `local-service` route from the matrix. Capture per-ticket expected/actual, screenshot path, log reference and provisional verdict. Do not mark a ticket verified without an observable pass.

**Verification:**

- `Glob` - `temp/s0307/04_execution_log.md` exists.
- `Grep` - `execution_matrix_version=1` appears exactly once.
- `Grep` - `unsafe_status_mutations=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/04_execution_log.md`. Partial standardDebug execution recorded: S0253 pass candidate, S0254 broken candidate, S0165 verified candidate, S0284 partial candidate, S0289 smoke pass, remaining routes deferred with explicit reasons. Unsafe status mutations: 0.

---

### Step 04.5 - Harvest Logcat And Screenshots

**Files:** `temp/s0307/04_logcat_summary.txt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Collect app-focused logcat, screenshot inventory and crash/ANR summary after execution. Reference raw artifacts under `temp/s0307/screenshots/` and `temp/s0307/logcat/`.

**Verification:**

- `Glob` - `temp/s0307/04_logcat_summary.txt` exists.
- `Grep` - `crash_count=` appears exactly once.
- `Grep` - `screenshot_count=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/04_logcat_summary.txt`. App-focused logcat captured at `temp/s0307/logcat/04_app_logcat.txt`; crash_count=0, screenshot_count=56, ui_dump_count=56, S0289 probe count=5.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `temp/s0307/04_execution_log.md` exists.
- [x] `temp/s0307/04_logcat_summary.txt` exists.
- [x] Every provisional `Verified` candidate has screenshot/log evidence.
- [x] Device came online; offline blocker was superseded and target ticket statuses remain unchanged.

---

## Handoff Notes to Next Phase

Phase 05 consumes the execution log and applies only explicitly accepted evidence-backed status transitions. This pass intentionally leaves target mutations at 0.

---

## Rollback Plan

Uninstall the debug package if installed and delete pushed fixtures from emulator storage. Do not revert target ticket statuses manually; use spec catalog scripts only for any correction.
