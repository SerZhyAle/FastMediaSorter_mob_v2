# Phase 03 - Perf Measure

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

> **Blocked:** requires research §6.1 (metrics + thresholds + tool) and §6.5 (log markers) Resolved before start.

---

## Objective

Add a helper that measures the named performance checkpoints (cold start, list scroll, player open, network/SFTP listing open) and emits a per-checkpoint timing record compared against configured thresholds.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Research §6.1 Resolved - `research/01__perf-metrics-thresholds.md` exists.
- [ ] Research §6.5 Resolved - `research/05__log-verdict-markers.md` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/prerelease.config.psd1` | Modified | ≤ 120 |
| `scripts/devtest/prerelease-measure.ps1` | New | ≤ 220 |

---

## Steps

### Step 03.1 - Add thresholds block to run config

**Files:** `scripts/devtest/prerelease.config.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the run config with a `Thresholds` block keyed by checkpoint name (cold start, list scroll, player open, network listing open) holding the PASS limits from research §6.1. Strategic §3.3 starter values apply until research refines them.

**Verification:**

- `Grep` - `Thresholds` key present in `prerelease.config.psd1`.
- `Grep` - checkpoint keys for cold start, scroll, player, listing present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (Thresholds keys ColdStart/ListScroll/PlayerOpen/NetworkListing present; import OK, limits 5000/20/4000/15000). Files: scripts/devtest/prerelease.config.psd1 (+8 LOC). Dev log recorded.

---

### Step 03.2 - Capture per-checkpoint timings

**Files:** `scripts/devtest/prerelease-measure.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `prerelease-measure.ps1` (`-DeviceId`, `-Checkpoint`, `-Json`) that captures the timing for a named checkpoint using the tool chosen in research §6.1 (log markers / adb / `dumpsys gfxinfo`) and the log markers from research §6.5. Return the measured value plus the checkpoint name.

**Verification:**

- `Glob` - `scripts/devtest/prerelease-measure.ps1` exists.
- `Grep` - `param(` includes `Checkpoint`.
- `Grep` - the measurement tool from the research artifact referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (file exists, Checkpoint param, am start -W / gfxinfo refs; parse OK). Cold-start via am start -W TotalTime; list-scroll via gfxinfo janky %; player-open/network-listing via caller -ElapsedMs. Files: scripts/devtest/prerelease-measure.ps1 (New, ~95 LOC). Dev log recorded.

---

### Step 03.3 - Compare against thresholds

**Files:** `scripts/devtest/prerelease-measure.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Load `Thresholds` from the config and emit a per-checkpoint verdict (`measured`, `limit`, `pass:bool`) as JSON. Do not aggregate the overall run verdict here - that is Phase 04.

**Verification:**

- `Grep` - `Import-PowerShellDataFile` referenced.
- `Grep` - emitted record contains `pass` and `limit`.
- `Script` - `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json` parses against a prepared emulator.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS (record has pass+limit, parse OK). Live cold-start on emulator-5554: measured 1425 ms <= 5000, pass:true, exit 0, valid JSON. Files: scripts/devtest/prerelease-measure.ps1 (+22 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json` emits valid JSON with a `pass` field (live: 1425 ms, pass:true).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

Provides per-checkpoint timing records with pass flags that Phase 04 folds into the overall verdict.

---

## Rollback Plan

Delete `prerelease-measure.ps1` and revert the `Thresholds` block - no data migration or user-facing surface changed.
