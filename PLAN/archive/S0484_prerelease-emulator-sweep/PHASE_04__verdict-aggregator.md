# Phase 04 - Verdict Aggregator

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

> **Blocked:** requires research §6.1 (thresholds) and §6.5 (log markers) Resolved before start.

---

## Objective

Add a helper that folds three signal sources - log analysis (errors/crashes/ANR), perf-checkpoint pass flags, screenshot-checkpoint results - into a single machine PASS/FAIL verdict with a per-source breakdown.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Research §6.1 and §6.5 Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/prerelease-verdict.ps1` | New | ≤ 220 |

---

## Steps

### Step 04.1 - Log signal via search-log

**Files:** `scripts/devtest/prerelease-verdict.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `prerelease-verdict.ps1` (`-LogFile`, `-MetricsFile`, `-ScreensDir`, `-Json`). Compute the log signal by delegating to `scripts/utils/search-log.ps1` (`-Errors -Unique -Stats -AppOnly`, `-Exceptions`) and applying the failure-vs-expected-fallback rules from research §6.5. Never read a large logcat into context directly.

**Verification:**

- `Glob` - `scripts/devtest/prerelease-verdict.ps1` exists.
- `Grep` - `search-log.ps1` referenced.
- `Grep` - `param(` includes `LogFile` and `MetricsFile`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (exists, search-log.ps1 referenced, param LogFile+MetricsFile; parse OK). Log signal: net app errors (minus expected-fallback regex), crash/ANR blocks via -Exceptions, prior-crash marker; reuses search-log.ps1. Files: scripts/devtest/prerelease-verdict.ps1 (New, ~90 LOC). Dev log recorded.

---

### Step 04.2 - Fold perf and screenshot signals

**Files:** `scripts/devtest/prerelease-verdict.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Read the perf records (Phase 03 output) from `-MetricsFile` and the screenshot-checkpoint results from `-ScreensDir`, and combine all three signals into one verdict object: `pass:bool` plus a breakdown listing each failed signal. Any failed required signal forces `pass:false`.

**Verification:**

- `Grep` - verdict object contains `pass` and a per-source `breakdown`.
- `Grep` - perf and screenshot inputs both consumed.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (MetricsFile + ScreensDir consumed, per-source breakdown objects present; parse OK). perf: all measure records must pass; screenshot: present ScreensDir must hold >=1 PNG; pass = log AND perf AND screenshot. Files: scripts/devtest/prerelease-verdict.ps1 (+22 LOC). Dev log recorded.

---

### Step 04.3 - Emit verdict JSON

**Files:** `scripts/devtest/prerelease-verdict.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Emit the verdict as a single JSON object on stdout (suppress human noise under `-Json`) and set a stable exit code: 0 = PASS, non-zero = FAIL, distinct from infrastructure-abort codes. The skill (Phase 05) branches on this.

**Verification:**

- `Grep` - `ConvertTo-Json` referenced.
- `Script` - `pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 -LogFile <sample> -Json` emits valid JSON and a deterministic exit code.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (ConvertTo-Json + exit-code refs; parse OK). Live both paths: clean sample -> pass:true exit 0; crash sample (FATAL EXCEPTION) -> crashBlocks:true pass:false exit 1. Fixed a real count-parsing bug found in test (Get-Count grabbed a digit from the filename; now parses the 'Match count: N' line). Files: scripts/devtest/prerelease-verdict.ps1 (+10 LOC, +fix). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `prerelease-verdict.ps1` emits a single JSON verdict with a `pass` field and a stable exit code (live: clean exit 0, crash exit 1).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the new file.

---

## Handoff Notes to Next Phase

Provides the single machine PASS/FAIL verdict and exit code that the skill branches on (PASS → release proposal; FAIL → catalog mutation).

---

## Rollback Plan

Delete `prerelease-verdict.ps1` - no data migration or user-facing surface changed.
