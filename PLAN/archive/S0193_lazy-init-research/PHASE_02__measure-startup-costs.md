# Phase 02 — measure-startup-costs

**Strategic spec:** [`../S0193_lazy-init-research.md`](../S0193_lazy-init-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Confirm the scope of eager initialization via `adb` command-line tools and Gradle dependency analysis — no Android Studio profiler or physical device attached to IDE required. The architectural principle ("don't load what isn't needed") is already established; this phase produces supporting numbers for the Phase 04 recommendation and future regression tracking.

> **Note:** If Phase 01 produces a definitive classification table showing multiple heavyweight `NETWORK_ONLY` / `PLAYER_ONLY` / `VR_NOLEGAL_ONLY` singletons, Phase 04 may proceed without waiting for Step 02.2 measurements. The principle is self-sufficient; the numbers are a "nice to have" for posterity.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — classification tables available.
- [ ] A connected Android device or emulator with `adb` working (`adb devices` returns the target).
- [ ] `standard` debug APK installed on the device.

---

## Files Touched

_Research phase — no production files modified._

---

## Steps

### Step 02.1 — Cold start time across flavors via `adb shell am start -W`

**Files:** device shell (adb command-line only)
**Depends on:** Phase 01 done

**Prompt for developer:**

> For each installed flavor, run a cold-start loop (5 iterations):
> ```
> adb shell am force-stop <package>
> adb shell am start -W -n <package>/<package>.ui.main.MainActivity
> ```
> Packages: `com.sza.fastmediasorter` (standard), `com.sza.fastmediasorter.lite` (lite), `com.sza.fastmediasorter.photos` (photos). Record `TotalTime` (process start) and `ThisTime` (Activity draw) per flavor — median of 5 runs. Record the table here.
>
> If only `standard` is installed, record standard alone — the delta between flavors is a "nice to have".

**Verification:**

- At least the `standard` flavor TotalTime median is recorded.
- Measurements come from ≥3 cold-start runs (force-stop between each).

**Status:** `[ ]` not done

---

### Step 02.2 — Memory snapshot via `adb shell dumpsys meminfo`

**Files:** device shell (adb command-line only)
**Depends on:** Step 02.1

**Prompt for developer:**

> Immediately after `standard` cold start (app at MainActivity, no user gesture), run:
> ```
> adb shell dumpsys meminfo com.sza.fastmediasorter
> ```
> Record: `TOTAL PSS`, `Java Heap`, `Native Heap`, `Code`, and the `Objects` section (Activities count, Views count). This gives process-level memory before any feature is used. Optionally run the same for `lite` and `photos` to compare baseline PSS. Record the table here.
>
> If `dumpsys meminfo` is not available (emulator limitation), skip this step and note the skip.

**Verification:**

- TOTAL PSS and Java Heap figures are recorded for `standard`.
- Strategic §6.1 is updated to `Resolved` with the PSS figure (or "skipped — emulator limitation").

**Status:** `[ ]` not done

---

### Step 02.3 — DEX contribution of heavyweight library groups

**Files:** Gradle build (command-line only)
**Depends on:** — can run in parallel with 02.1

**Prompt for developer:**

> Run `./gradlew.bat :app_v2:dependencies --configuration standardDebugRuntimeClasspath 2>&1 | grep -E "smbj|sshj|commons-net|dropbox|onedrive|msal|drive"` to list which network/cloud libraries are pulled into the `standard` build. Then check DEX method count contribution via:
> ```
> ./gradlew.bat :app_v2:assembleStandardDebug
> ```
> and inspect the build output or use `dexcount` if already configured. If dexcount is not configured, skip the method count and just list the library group names from the dependency tree.
>
> Goal: establish which third-party libraries are compiled into the standard APK and therefore loaded into memory at process start, even if never used in a given session.

**Verification:**

- Library list (SMB, SFTP, FTP, Dropbox, OneDrive, Google Drive, MSAL) is confirmed present or absent in `standardDebugRuntimeClasspath`.
- Each library group is classified: "always in classpath" vs "excluded in lite/photos".
- Strategic §6.4 is updated to `Resolved` with the cold-start delta (or "single flavor measured — delta unavailable").

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done` (or explicitly skipped with reason).
- [ ] Cold start TotalTime for `standard` is recorded.
- [ ] `adb dumpsys meminfo` PSS figure (or skip reason) is recorded.
- [ ] Library presence in standard classpath is confirmed.
- [ ] Strategic §6.1 and §6.4 are `Resolved`.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "PLAN/S0193_lazy-init-research/PHASE_02__measure-startup-costs.md" "research" "S0193 Phase 02: startup cost measurements complete"`.

---

## Handoff Notes to Next Phase

Phase 02 numbers are supporting evidence, not a gate. Phase 04 may proceed in parallel or immediately after Phase 03 if the Phase 01 classification table already establishes that multiple `NETWORK_ONLY` / `PLAYER_ONLY` singletons are being created unconditionally.

---

## Rollback Plan

Research phase — no code changed. Nothing to roll back.
