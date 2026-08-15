# Phase 02 — Measurement

**Strategic spec:** [`../S0185_startup-splash-screen-investigation.md`](../S0185_startup-splash-screen-investigation.md)
**Tactical INDEX:** [`INDEX.md`](INDEX.md)
**Depends on:** Phase 01 (evidence) — ✅ Done
**Status:** ⛔ Blocked (awaiting device run; debug-log instrumentation ready)
**Steps:** 0 / 3

**Temp artefacts:** `temp/S0185/02_measurement_journal.md`, `temp/S0185/02_trace_inventory.md`

> **Scope:** quantitative measurement on a physical device. Goal: capture TTID / TTFD baseline numbers and identify the weight of each synchronous Application-level candidate from Phase 01 evidence. Phase 01 identified two dominant candidates: `GmsAvailabilityChecker.check` and `CastContext.getSharedInstance`. This phase produces the numbers needed for the Phase 03 decision.

---

## Context from Phase 01

Phase 01 (evidence) found:

- Two synchronous main-thread calls are the only material candidates for cold-start delay:
  - `GmsAvailabilityChecker.check(this)` — GMS availability probe; potentially 5–30 ms on first call.
  - `CastContext.getSharedInstance(this)` — Cast SDK bootstrap; potentially 50–200 ms; gated on `BuildConfig.SUPPORT_CAST`.
- All other calls are either sub-millisecond, DEBUG-only, or already deferred behind `firstFrameSignal.await`.
- No baseline profile pipeline exists — the runtime installer silently no-ops.

---

## Goals

1. Record TTID (Time To Initial Display) and TTFD (Time To Full Display) for a cold-start of the `standard` flavor in a release build on at least two devices: one API 31+ and one API 26..30.
2. Quantify the contribution of `GmsAvailabilityChecker.check` and `CastContext.getSharedInstance` to cold-start time using Perfetto or `adb shell am start -W`.
3. Record a comparative cold-start measurement with `CastContext.getSharedInstance` temporarily disabled (or timed around it) to confirm or deny its significance.

---

## Measurement preflight

1. Build and install `standardRelease` for Step 02.1.
2. Build and install `standardDebug` for log-based runs — canonical `S0185_TRACE` + `S0185_SUMMARY` lines are already wired into the startup path.
3. Clear logcat before every debug timing run: `adb logcat -c`.
4. Record every command, run number, serial, and output file path in `temp/S0185/02_measurement_journal.md`.
5. Record every saved logcat / Perfetto / screen recording asset in `temp/S0185/02_trace_inventory.md`.
6. Preferred grep for exported debug logs: `S0185_TRACE|S0185_SUMMARY`.

---

## Step 02.1 — TTID / TTFD baseline via `adb shell am start -W`

**Procedure (no macrobenchmark module required):**

1. Install release APK of `standard` flavor on target device: `adb install -r <path-to-apk>`.
2. Force-stop the app to ensure cold start: `adb shell am force-stop com.sza.fastmediasorter`.
3. Drop caches if possible (on a rooted device or emulator): `adb shell sync; adb shell echo 3 > /proc/sys/vm/drop_caches`.
4. Measure: `adb shell am start -W com.sza.fastmediasorter/.ui.main.MainActivity` → note `TotalTime` (≈ TTID) and `WaitTime`.
5. Repeat 5 times on the same device; drop max outlier; record mean.
6. Repeat on second device (different API level).

**Alternative (Logcat TTID):** `adb logcat -s ActivityTaskManager | grep "Displayed com.sza.fastmediasorter"` — Android logs TTID automatically after the first drawn frame.

**Log-only fallback:** if the operator only provides an exported debug log, parse the first cold-start `S0185_SUMMARY` line instead. Treat `firstFrame=...ms` as the TTID proxy and `fullyDrawn=...ms` as the TTFD proxy. Mark the result as `debug-proxy`, not release-baseline.

**Expected result format:**

```
Device A (API ??, manufacturer/model):
  Run 1: TotalTime = ??? ms
  Run 2: TotalTime = ??? ms
  Run 3: TotalTime = ??? ms
  Run 4: TotalTime = ??? ms
  Run 5: TotalTime = ??? ms
  Mean (4 runs, drop max): ??? ms

Device B (API ??, manufacturer/model):
  ...
```

**Verification predicate:** at least 4 valid runs per device recorded; mean computed and stated.

**Output artefact:** `### Step 02.1 — Findings` below.

---

## Step 02.2 — CastContext contribution estimate

**Procedure:**

Option A (logcat timing): removable debug instrumentation is already in place and emits canonical startup lines.

- `S0185_TRACE | marker=app_onCreate_start ..`
- `S0185_TRACE | marker=metric | details=name=gms_check elapsed=...ms`
- `S0185_TRACE | marker=metric | details=name=cast_init elapsed=...ms status=...`
- `S0185_TRACE | marker=main_first_frame ..`
- `S0185_TRACE | marker=main_fully_drawn ..`
- `S0185_SUMMARY | firstFrame=... | fullyDrawn=... | appOnCreate=... | gmsCheck=... | castInit=... | castStatus=...`

Recommended capture flow:

1. Install `standardDebug`.
2. Run `adb logcat -c`.
3. Force-stop the app: `adb shell am force-stop com.sza.fastmediasorter`.
4. Start log capture: `adb logcat | rg "S0185_TRACE|S0185_SUMMARY"`.
5. Launch the app once, then copy the single `S0185_SUMMARY` line and any supporting `S0185_TRACE` lines into `temp/S0185/02_measurement_journal.md`.

Option B (Perfetto): capture a system trace during cold start; search for the Cast SDK thread in the trace and read wall-clock time.

Option C (manual removal): build a variant with `CastContext.getSharedInstance` call commented out, measure TTID delta.

Owner selects whichever option is available on their setup.

**Expected result format:**

```
Method used: (A / B / C)
CastContext contribution estimate: ??? ms (on API ??)
GmsAvailabilityChecker contribution estimate: ??? ms (on API ??)
```

**Verification predicate:** at least one estimate recorded for `CastContext.getSharedInstance`; either confirmed negligible (< 20 ms) or confirmed material (≥ 20 ms).

**Output artefact:** `### Step 02.2 — Findings` below plus `temp/S0185/02_measurement_journal.md`.

---

## Step 02.3 — Baseline profile pipeline cost/benefit assessment

**Procedure (static research, no device required):**

1. Estimate build-time cost of adding a macrobenchmark module: review [developer.android.com/topic/performance/baselineprofiles/create-baselineprofile](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile) and the AGP documentation for the `:baselineprofile` plugin.
2. Estimate startup improvement from baseline profiles for a typical Kotlin/Hilt app (search recent Android blog posts / conference talks for data points).
3. Cross-reference with the project's delivery cadence: how often would profiles need to be regenerated? Is there a CI pipeline capable of running the emulator-based Macrobenchmark task?
4. Record a recommendation: `Worth it — proceed` / `Marginal — defer` / `Not worth it — drop the dependency`.

**Verification predicate:** recommendation recorded with rationale; either based on measured delta (Option C in step 02.2 can double as proxy) or on documented industry benchmarks.

**Output artefact:** `### Step 02.3 — Findings` below.

---

## Phase Done Criteria

1. Steps 02.1, 02.2, and 02.3 all marked `[x]` done.
2. All three `### Step 02.x — Findings` sections populated with concrete values.
3. At least one TTID baseline number is recorded (`expected: X | actual: Y` format where Y is the measured value).
4. INDEX row flipped to ✅ Done; `Phases: 2/3 done`.
5. Strategic spec §6.1 item updated to `Resolved` with the measured TTID/TTFD values.
6. Strategic spec §6.3 item updated to `Resolved` with the pipeline recommendation.

---

## Step Findings

### Step 02.1 — Findings

_(pending — requires physical device and release build)_

### Step 02.2 — Findings

_(pending — requires physical device or debug build instrumentation)_

### Step 02.3 — Findings

_(pending — static research; can be done without a device)_

---

## Change Log

- 2026-05-16 — Phase 02 authored by `/spec-all` (claude-sonnet-4-6). Steps drafted using Phase 01 evidence as context. Status: Blocked (needs device).
- 2026-05-16 — Measurement preflight prepared manually: instrumentation-ready status recorded, temp artefacts linked, and Step 02.2 updated to reference the exact S0185 logcat tags now present in `FastMediaSorterApp.kt`.
- 2026-05-16 — Debug-log path added: `S0185_TRACE` milestone lines and `S0185_SUMMARY` now let Phase 02 extract first-frame / fully-drawn / GMS / Cast timings from an exported debug log without live stopwatch work.
