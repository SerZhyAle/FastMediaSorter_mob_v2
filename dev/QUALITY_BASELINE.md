# Quality Baseline — FastMediaSorter v2

**Baseline Date**: 2025-01
**Version**: See `DOWNLOADS/builds_versions.lst`
**Status**: Initial baseline (runtime KPIs require Crashlytics access to populate)

---

## KPI Definitions & Thresholds

### Runtime KPIs (from Crashlytics / Firebase)

| KPI | Definition | Target | Alert Threshold | Baseline Value |
|-----|-----------|--------|-----------------|----------------|
| Crash-free rate | % sessions without crash | ≥ 99.0% | < 98.5% | TBD (Crashlytics) |
| ANR rate | % sessions without ANR | ≥ 99.5% | < 99.0% | TBD (Crashlytics) |
| App startup time | Cold start to first frame (ms) | ≤ 2000ms | > 3000ms | TBD (profiler) |
| Median scan time | p50 time to complete folder scan (ms) | ≤ 3000ms | > 6000ms | TBD (instrumentation) |
| Auth success rate | % successful OAuth/credential auths | ≥ 98.0% | < 96.0% | TBD (Firebase Analytics) |
| Resource save success | % successful resource create/save ops | ≥ 99.5% | < 99.0% | TBD (Firebase Analytics) |

### CI / Build KPIs (measurable now)

| KPI | Definition | Target | Alert Threshold | Baseline Value |
|-----|-----------|--------|-----------------|----------------|
| Unit test pass rate | % passing unit tests | 100% | < 100% | 100% (105+ tests) |
| Lint warnings (new) | New lint warnings vs baseline | 0 | > 0 | 0 (baseline enforced) |
| Build time (debug) | `assembleStandardDebug` duration | ≤ 3 min | > 5 min | ~18s (incremental) |
| APK size (debug) | Size of standardDebug APK | ≤ 250 MB | > 280 MB | 239.8 MB |

---

## Measurement Sources

| KPI Category | Source | Collection |
|--------------|--------|------------|
| Crash-free / ANR | Firebase Crashlytics | Automatic (SDK) |
| Startup time | Firebase Performance | Automatic (SDK) |
| Scan time | Custom event: `scan_complete` | Requires C2-T1 instrumentation |
| Auth success | Custom event: `auth_result` | Requires C2-T2 instrumentation |
| Resource save | Custom event: `resource_save_result` | Requires C2-T2 instrumentation |
| Unit tests | CI test reports (JUnit XML) | Automatic (CI) |
| Lint | `app_v2/build/reports/lint-results*.xml` | Automatic (CI) |
| Build time | CI job duration | Automatic (CI) |
| APK size | `app_v2/build/outputs/apk/` | `generate-quality-report.ps1` |

---

## Alerting Rules

```
IF crash_free_rate < 98.5% → CRITICAL alert
IF anr_rate < 99.0%        → HIGH alert
IF startup_ms > 3000       → HIGH alert
IF scan_p50_ms > 6000      → MEDIUM alert
IF auth_success < 96.0%    → HIGH alert
IF unit_test_pass < 100%   → BLOCKING (CI gate)
IF new_lint_warnings > 0   → BLOCKING (CI gate)
```

---

## Baseline History

| Date | Version | Crash-free | ANR | Build (s) | Unit Tests | APK Size |
|------|---------|-----------|-----|-----------|------------|---------|
| 2025-01 | Initial | - | - | ~18s | 143 / 100% | 239.8 MB |

*Runtime metrics to be added after first production release with Crashlytics enabled.*

---

## Notes

- Runtime KPIs require production traffic — populate after first Play Store release.
- `generate-quality-report.ps1` covers CI KPIs (buildable offline).
- Instrumentation tasks C2-T1/T2 are prerequisites for scan/auth/resource KPIs.
