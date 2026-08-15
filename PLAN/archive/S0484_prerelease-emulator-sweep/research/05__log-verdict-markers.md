# Research §6.5 - Log verdict markers (FAIL vs expected fallback)

**Strategic item:** §6.5
**Status:** Resolved
**Date:** 2026-06-17

## Question

How the verdict aggregator classifies logcat as PASS/FAIL, separating real failures from expected fallbacks.

## Findings

- `scripts/utils/search-log.ps1` provides all needed primitives: `-Errors`, `-Warnings`, `-Exceptions`, `-AppOnly`, `-Unique`, `-Stats`, `-Count`, `-Pattern`, `-Exclude`, `-Level`, `-From/-To`, `-OutFile`. `/spec-test-device` step 8 is prior art for the invocation sequence.
- `-Exceptions` detects `FATAL EXCEPTION | AndroidRuntime | ANR in | begin of crash` blocks; prints a human count, no machine exit code (capture stdout and test for `"No exception/crash blocks found."`).
- The codebase labels non-actionable warnings with `(non-critical)` / `(ignored)` in the message text - a reliable suppression handle.

## Decision

Verdict aggregator (`prerelease-verdict.ps1`, Phase 04) reuses `search-log.ps1` and applies this rule set:

- **FAIL if any of:**
  - `-Exceptions` reports ≥1 crash/ANR block;
  - net app-level `-Errors -AppOnly -Unique` count > 0 after subtracting the expected-fallback patterns;
  - `PREVIOUS SESSION ENDED WITH A CRASH` present (means clean-install/reset failed → treat as environment FAIL).
- **Expected fallbacks (suppress)** - externalise to `scripts/devtest/expected-fallbacks.txt` (one regex per line) rather than hardcoding, so it stays maintainable. Initial list:
  - `Native set .* unavailable on this install`, `OCR engines not installed`, `UnsatisfiedLinkError loading` (from `DeliveredNativeLibraryLoader`)
  - `GmsAvailabilityChecker.*unavailable`, `XR device detected`, `CctAvailabilityChecker`, `CastMediaManager.*not supported`, `LocalCastProxyServer.*unavailable`
  - `\(non-critical\)`, `\(ignored\)`
  - `NetworkReachabilityGate: no-network`/`no-wifi`, `scanFolderSAFFast.*No permission`
  - `MediaCodec error`, `Audio renderer failed`, `Media3OomSafeLogger`, `Upgrade reconciliation`, `ShareTarget package not installed`
- **WARN residual:** counted into the breakdown, does not force FAIL.
- **Exit codes:** 0 = PASS, 1 = content FAIL, 2 = infrastructure abort (missing log/metrics).
- **Verdict JSON:** `{ pass, breakdown: { log:{actionableErrors,crashBlocks,priorCrash,warnings}, perf:{...}, screenshot:{...} } }`.
- Scope queries to the run window with `-From <start_ts>` (pass start timestamp into the verdict script).

## Impact on plan

- Phase 04 implements exactly this rule set; adds `expected-fallbacks.txt` as a config input.
- Phase 03/04 reuse `search-log.ps1`; no new log parser.

## Out-of-scope findings (parked)

- `DeliveredNativeLibraryLoader.kt:93` logs an expected condition at `Timber.e` (should be `Timber.i`/`w`).
- `search-log.ps1 -Exceptions` lacks `-Count`/`-Json` machine-readable output.
