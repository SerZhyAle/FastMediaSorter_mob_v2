# Phase 02 - Discovery and probes

**Strategic spec:** [`../S1840_stream-publisher-script-oversized-untested.md`](../S1840_stream-publisher-script-oversized-untested.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

## Objective

Extract source adapters, liveness probes and provider-balancing logic without changing verdict semantics or network throttles.

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Shared module loader and tests pass.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | <= 1,500 |
| `scripts/streams/modules/StreamPublisher.Discovery.ps1` | New | <= 650 |
| `scripts/streams/modules/StreamPublisher.Probes.ps1` | New | <= 900 |
| `scripts/streams.tests/StreamPublisher.Probes.Tests.ps1` | New | <= 400 |

## Steps

### Step 02.1 - Extract candidate source adapters

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Discovery.ps1`
**Depends on:** Phase 01

**Prompt for developer:**

> Move radio-browser, iptv-org, webcam and community-source adapters into the discovery module. Preserve source filters, candidate fields, memoization and the existing axis selection contract.

**Why:**

Seven distinct responsibilities currently share one file, and source discovery is independently changeable from probing and publication.

**Verification:**

- Each existing source function is declared exactly once.
- Axis selection still exposes the existing `livetv`, `genres`, `geo` and `webcam` values.
- PowerShell parse check passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Discovery module extracted with all source adapters unique; entry script parses at 2076 lines and preserves the four axis names.

### Step 02.2 - Extract liveness and deep-signal probes

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Probes.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Move header liveness, deep-signal, media-kind, provider-loss and interleaving functions into the probes module. Preserve conservative prune semantics, `geo` classification, throttles and the `-SkipCaptureFirst` ladder order.

**Why:**

The probe ladder is the safety argument for append versus prune behavior; a refactor must keep its verdict semantics intact while making the logic loadable for tests.

**Verification:**

- Probe functions are declared exactly once.
- `-DeepSignal` still auto-bumps the default throttle only when the caller did not pin it.
- PowerShell parse check passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Probe module extracted with header/deep-signal and provider-balancing functions unique; parse and line checks pass.

### Step 02.3 - Add probe seam tests

**Files:** `scripts/streams.tests/StreamPublisher.Probes.Tests.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add deterministic tests for prune-status normalization, provider-key fallback, provider interleaving and verdict classification helpers. Stub process/network boundaries rather than invoking real endpoints or media tools.

**Why:**

Probe regressions can prune live rows or append non-playing rows, while the current repository has no publisher tests to catch either class of mistake.

**Verification:**

- `Invoke-Pester` exits 0 for the probe test file.
- Tests contain no live URI, `Invoke-WebRequest`, `Invoke-RestMethod`, ffmpeg or ffprobe execution.
- Tests cover both conservative prune and deep-signal classification paths.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Probe seam Pester suite passes 4 tests without live endpoints or media tools.

### Step 02.4 - Enforce discovery and probe budgets

**Files:** `scripts/streams/collect-stream-candidates.ps1`, `scripts/streams/modules/StreamPublisher.Discovery.ps1`, `scripts/streams/modules/StreamPublisher.Probes.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Remove moved declarations and record the staged line-count boundary for the entry, discovery and probes files. Keep all parameters and function names used by the entry dispatch unchanged; the final <=1,500-line check runs after artwork and delivery extraction.

**Why:**

The extraction must solve the oversized-file defect rather than distribute an unbounded implementation into another oversized module.

**Verification:**

- Discovery and probes modules are <= 1,000 lines and the entry script is shorter than its phase-01 baseline.
- The entry script parses and exposes the original parameter names.
- Pester common and probe tests pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Entry reduced from 3296 to 2076 lines; discovery is 450 lines, probes 772 lines, and both common/probe suites pass.

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Parse check and Pester tests pass.
- [ ] Dev log entries exist for each touched file.
- [ ] Phase-boundary audit finds no unresolved P0/P1 issue.

## Handoff Notes to Next Phase

Discovery and probe responsibilities are isolated and loaded by the same CLI entry point; artwork and delivery can now move without changing source collection.

## Rollback Plan

Revert the phase changes if any source axis or probe mode changes its existing dispatch or verdict behavior.
