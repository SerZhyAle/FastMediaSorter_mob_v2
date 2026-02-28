# Cloud Scan Baseline — Procedure & Reference Values

*Created: 2026-02-28 | Author: automated plan execution (P0-3)*

---

## Purpose

Provides a repeatable benchmarking procedure for measuring cloud resource scan
speed. Results serve as the performance baseline for evaluating future
improvements: incremental scan, metadata caching (A5), and pagination
optimisations.

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
   adb shell setprop log.tag.FastMediaSorter VERBOSE
   adb logcat -s FastMediaSorter > /tmp/scan_log.txt &
   ```

---

## Measurement Steps

### Step 1 — Cold Scan (no local cache)

1. Open resource. Note timestamp `T_start` from logcat:
   ```
   tag=CloudScanner msg="startScan resource=<name>"
   ```
2. Wait until the file list is fully loaded (spinner disappears).
3. Note timestamp `T_end` from logcat:
   ```
   tag=CloudScanner msg="scanComplete files=<N>"
   ```
4. **Duration** = `T_end − T_start` (seconds)
5. Record `files_scanned` from the `scanComplete` log line.

### Step 2 — Warm Scan (cache populated)

1. Leave resource open or navigate out and back in within 5 minutes.
2. Repeat timestamps as in Step 1.
3. Expected: significantly shorter than cold scan.

---

## Reference Values (2026-02-27 log analysis)

| Provider    | Files | Cold Scan | Notes |
|-------------|-------|-----------|-------|
| Google Drive | ~120  | **24.5 s** | Observed in `log_analysis_2026_02_27.md` |
| OneDrive    | ~80   | ~18 s     | Estimated from log spans |

*Target (post-A5 optimisation): cold scan < 5 s for ≤ 500 files.*

---

## Metrics to Record

| Metric | Source |
|---|---|
| `scan_duration_s` | `T_end - T_start` |
| `file_count` | `scanComplete` log tag |
| `api_pages_fetched` | Count `pageToken` occurrences in logcat |
| `cache_hit_rate` | Future: `FileMetadataCache` hit/miss counters |
| `network_errors` | Count `ERROR` lines during scan window |

---

## Re-running the Benchmark

After any scan-related change (incremental scan, pagination, caching):

1. Clear cache.
2. Run Steps 1-2.
3. Append results to the table above with date + commit hash.
4. Confirm ≥ 20 % improvement before marking A5 as complete.

---

## Automation Opportunity (future)

Add a Maestro test flow (`maestro/scan_benchmark.yml`) that:
- Navigates to a predefined cloud resource.
- Measures scroll-to-first-item time as a proxy for scan completion.
- Outputs a structured JSON result to `temp/scan_benchmark_<date>.json`.
