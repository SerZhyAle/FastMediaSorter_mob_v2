# Phase 01 — Adaptive SLOW SCAN threshold and extended log

**Strategic spec:** [`../S0056_smb-scan-slowness-investigation.md`](../S0056_smb-scan-slowness-investigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Make `ScanMetricsRecorder` compute the SLOW SCAN warning threshold as a function of the previously-known file count of the scanned resource, and extend the SLOW SCAN warning line with `expected_file_count` and `effective_threshold_ms`. Single caller in `GetMediaFilesUseCase` is updated to pass `resource.fileCount`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `ScanMetricsRecorder.kt`, `GetMediaFilesUseCase.kt` exist and compile against current `main`.
- [ ] `MediaResource.fileCount` field is non-null and updated by scan callers (verified during /spec-tech analysis).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt` | Modified | unchanged file size |

---

## Steps

### Step 01.1 — Extend `ScanToken` with `expectedFileCount`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add an `expectedFileCount: Int` field to the `ScanToken` data class so the recorder can compute a per-scan adaptive threshold without keeping resource-level state. Default value is irrelevant — the field is always set by `beginScan`.

**Verification:**

- `Grep -n "expectedFileCount: Int"` in `ScanMetricsRecorder.kt` returns ≥ 1 hit inside the `data class ScanToken(` block.
- `Grep -n "data class ScanToken"` in `ScanMetricsRecorder.kt` returns exactly 1 hit.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: core/metrics/ScanMetricsRecorder.kt (+1 LOC). Dev log recorded.

---

### Step 01.2 — Extend `beginScan` signature with `expectedFileCount`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a third parameter `expectedFileCount: Int = 0` to `beginScan(resourceId, resourceType)` (default keeps non-updated callers compiling). Pass it through into the returned `ScanToken`. Update the existing `Timber.d("ScanMetrics: begin scan ..")` line to also log `expected_file_count=$expectedFileCount`.

**Verification:**

- `Grep -n "fun beginScan\(" -A 5` in `ScanMetricsRecorder.kt` shows the parameter `expectedFileCount: Int = 0` in the signature.
- `Grep -n "expected_file_count=" ` in `ScanMetricsRecorder.kt` returns ≥ 1 hit (begin-scan log).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: core/metrics/ScanMetricsRecorder.kt (+8 LOC). Dev log recorded.

---

### Step 01.3 — Add adaptive `effectiveThreshold` and rename baseline constant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Rename the constant `SLOW_SCAN_THRESHOLD_MS` to `SLOW_SCAN_BASE_THRESHOLD_MS` (still `6_000L`). Add a private function `effectiveThreshold(expectedFileCount: Int): Long` that returns `maxOf(SLOW_SCAN_BASE_THRESHOLD_MS, (expectedFileCount * 0.4).toLong())`. Add a private constant `SLOW_SCAN_PER_FILE_MS = 0.4` and use it instead of the literal `0.4` so the heuristic is greppable. Brief one-line `// WHY` comment: empirical 2-3x parallelism baseline gives ~0.4 ms / file (see strategic §6.2).

**Verification:**

- `Grep -n "SLOW_SCAN_BASE_THRESHOLD_MS"` in `ScanMetricsRecorder.kt` returns ≥ 2 hits (declaration + usage in `effectiveThreshold`).
- `Grep -n "SLOW_SCAN_PER_FILE_MS"` in `ScanMetricsRecorder.kt` returns ≥ 2 hits.
- `Grep -n "private fun effectiveThreshold\("` in `ScanMetricsRecorder.kt` returns exactly 1 hit.
- `Grep -n "SLOW_SCAN_THRESHOLD_MS"` in `ScanMetricsRecorder.kt` returns 0 hits (old constant fully removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: core/metrics/ScanMetricsRecorder.kt (+5 LOC, renamed constant in W-log block — block itself rewritten in Step 01.4). Dev log recorded.

---

### Step 01.4 — Use adaptive threshold in `endScan` and extend SLOW SCAN W-log

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/metrics/ScanMetricsRecorder.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside `endScan(token, fileCount)`, compute `val thresholdMs = effectiveThreshold(token.expectedFileCount)` and use it for the slow-scan check (`if (durationMs > thresholdMs)`). Replace the existing SLOW SCAN `Timber.w` block so its message contains, in this order: `${durationMs}ms`, `threshold=${thresholdMs}ms`, `expected_file_count=${token.expectedFileCount}`, `actual_file_count=$fileCount`, `resourceId=${token.resourceId}`, `type=${token.resourceType.name}`. Keep the existing `Timber.d("ScanMetrics: scan_complete .." )` line unchanged.

**Verification:**

- `Grep -n "if (durationMs > thresholdMs)"` in `ScanMetricsRecorder.kt` returns exactly 1 hit.
- `Grep -n "SLOW SCAN detected"` in `ScanMetricsRecorder.kt` returns exactly 1 hit.
- `Grep -n "expected_file_count=" -A 0` in `ScanMetricsRecorder.kt` returns ≥ 2 hits (one in begin-log from 01.2, one in slow-scan W-log).
- `Grep -n "actual_file_count="` in `ScanMetricsRecorder.kt` returns exactly 1 hit.
- `Grep -n "Log\.d\("` in `ScanMetricsRecorder.kt` returns 0 hits (Timber-only invariant).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: core/metrics/ScanMetricsRecorder.kt (+3 LOC). Dev log recorded.

---

### Step 01.5 — Pass `resource.fileCount` from the SMB caller

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> At the single call site `ScanMetricsRecorder.beginScan(resource.id, resource.type)` (currently around line 308), add the third argument `expectedFileCount = resource.fileCount`. No other call sites exist — verified during /spec-tech.

**Verification:**

- `Grep -n "ScanMetricsRecorder.beginScan\("` in `app_v2/` returns exactly 1 hit, and that hit includes `expectedFileCount = resource.fileCount`.
- `Grep -rn "ScanMetricsRecorder\.beginScan\(" app_v2/` returns exactly 1 hit total (no stale call sites with the old 2-arg form).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS (intent). Literal grep returns 2 hits: (a) call site at GetMediaFilesUseCase.kt:308 with `expectedFileCount = resource.fileCount`, (b) KDoc example at ScanMetricsRecorder.kt:19 — also updated to 3-arg form so no stale 2-arg form remains anywhere. Intent of "no stale call sites" satisfied. Files: domain/usecase/GetMediaFilesUseCase.kt (1 line changed), core/metrics/ScanMetricsRecorder.kt (1 KDoc line changed). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` v2.60.5031.138 BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `core/metrics/ScanMetricsRecorder.kt` and `domain/usecase/GetMediaFilesUseCase.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `scan.ps1 -Module app_v2` (883 files scanned).

---

## Handoff Notes to Next Phase

After this phase, the SLOW SCAN warning is silenced for resourceId=18 (large SMB tree) under the heuristic `threshold = max(6000, fileCount * 0.4ms)`. The new W-log fields (`threshold`, `expected_file_count`, `actual_file_count`) are required by Phase 03 to confirm the warning no longer fires for the field-tested resource.

---

## Rollback Plan

Revert the two-file phase commit. No data migration, no DB schema change, no user-facing surface — pure logic change inside `ScanMetricsRecorder`.
