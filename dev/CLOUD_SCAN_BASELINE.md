# Cloud Scan Baseline — Procedure & Reference Values

*Created: 2026-02-28 | Updated: 2026-02-28 | Scope: P0-3 baseline*

---

## Purpose

Provides a repeatable benchmarking procedure for measuring cloud resource scan
speed. Results serve as the performance baseline for evaluating future
improvements: incremental scan, metadata caching (A5), and pagination
optimisations.

This revision is aligned with the current codebase logging.

---

## Test Device Profile

| Attribute | Target |
|---|---|
| Android version | API 28 (Pie) minimum |
| Network | Wi-Fi, stable ≥ 50 Mbit/s |
| App build | `standardDebug` with `Timber` logging enabled |
| Cold vs. warm | Both runs — distinguish in results |

---

## Setup

1. Add a Google Drive / OneDrive resource with **≥ 500 media files** across
   at least 3 sub-folders (mix of images, videos, audio).
2. Clear app cache (`Settings → Apps → FastMediaSorter → Clear cache`) to
   ensure cold-start conditions.
3. Enable verbose logging:
   ```
   adb shell setprop log.tag.BrowseLoadingManager VERBOSE
   adb shell setprop log.tag.CloudMediaScanner VERBOSE
   adb logcat | findstr /i "BrowseLoadingManager CloudMediaScanner" > scan_log.txt
   ```

4. Keep a stable app state for each run:
   - select the same cloud resource,
   - keep the same sort mode/filter settings,
   - do not change network during one run.

---

## Measurement Steps

### Step 1 — Cold Scan (no local cache)

1. Open cloud resource in Browse screen.
2. Note timestamp `T_start` from logcat:
   ```
   BrowseLoadingManager: START loading - resource='<name>'
   ```
3. Wait until loading finishes.
4. Note timestamp `T_end` from logcat:
   ```
   BrowseLoadingManager: COMPLETE - <N> files loaded and displayed
   ```
5. **Duration** = `T_end − T_start` (seconds)
6. Record `file_count` from the `COMPLETE` line.

Optional provider-level validation (same scan window):
```text
CloudMediaScanner: Scanning folder actualFolderId=<id>
CloudMediaScanner: listFiles failed - <error>   (if failure occurs)
```

### Step 2 — Warm Scan (cache populated)

1. Leave resource open or navigate out and back in within 5 minutes.
2. Repeat timestamps as in Step 1.
3. Expected: significantly shorter than cold scan.

---

## Reference Values (historical)

| Provider    | Files | Cold Scan | Notes |
|-------------|-------|-----------|-------|
| Google Drive | ~120  | **24.5 s** | Historical value from planning artifacts |
| OneDrive    | ~80   | ~18 s     | Historical estimate |

*Target (post-A5 optimisation): cold scan < 5 s for ≤ 500 files.*

Note: historical rows are reference-only and must be replaced by reproducible
measurements from this procedure.

---

## Metrics to Record

| Metric | Source |
|---|---|
| `scan_duration_s` | `T_end - T_start` |
| `file_count` | `BrowseLoadingManager: COMPLETE - <N> files...` |
| `api_pages_fetched` | Count `pageToken` occurrences in logcat |
| `cache_hit_rate` | Future metric (not emitted as unified counter yet) |
| `network_errors` | Count `ERROR` lines during scan window |

---

## Re-running the Benchmark

After any scan-related change (incremental scan, pagination, caching):

1. Clear cache.
2. Run Steps 1-2.
3. Append results to a new dated section with commit hash.
4. Confirm ≥ 20 % improvement before marking A5 as complete.

---

## Automation

Implemented benchmark automation:

- Flow: `maestro/scan_benchmark.yml`
- Runner: `maestro/run-scan-benchmark.ps1`
- Output: `temp/scan_benchmark_<date>.json`

Example:

```powershell
.\maestro\run-scan-benchmark.ps1 -CloudResourceName "Google Drive Benchmark"
```

Optional flags:

- `-WarmDelaySeconds 10` (default)
- `-SkipWarmRun` (run cold only)
