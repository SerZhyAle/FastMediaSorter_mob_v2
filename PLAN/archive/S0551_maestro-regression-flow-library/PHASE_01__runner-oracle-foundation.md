# Phase 01 - Runner & Oracle Foundation

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Make `maestro/run-tests.ps1` a usable off-context suite runner (no hardcoded paths, single-line verdict, stable exit codes, per-flow aggregation, device pin) and document the one oracle convention every flow must follow. No flow content changes yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] Reference contract readable: `scripts/devtest/maestro-run.ps1` (single-flow off-context engine to mirror).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/run-tests.ps1` | Modified | ≤ 300 |
| `maestro/WRITING_TESTS.md` | Modified | ≤ 200 |

> No `res/layout` edits - landscape parity N/A. No app-runtime code.

---

## Steps

### Step 01.1 - Remove hardcoded paths; add binary/JDK auto-discovery

**Files:** `maestro/run-tests.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the hardcoded `c:\GD\tc\Programm\maestro\bin` PATH injection and the literal `C:\Program Files\Java\jdk-21.0.10` JAVA_HOME block. Resolve the Maestro binary by the same order as `scripts/devtest/maestro-run.ps1`: `PATH`, then `MAESTRO_HOME\bin`, then `%USERPROFILE%\.maestro\bin`; exit code 2 if none resolve. Do not pin a JDK version - rely on the environment's Java.

**Verification:**

- `Grep` - `c:\\GD\\tc` returns zero hits in `maestro/run-tests.ps1`.
- `Grep` - `jdk-21` returns zero hits in `maestro/run-tests.ps1`.
- `Grep` - `.maestro\\bin` present (auto-discovery path).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Hardcoded `c:\GD\tc` + `jdk-21` removed (0 hits); `Find-Maestro` discovers PATH -> MAESTRO_HOME\bin -> %USERPROFILE%\.maestro\bin (mirrors maestro-run.ps1). Files: maestro/run-tests.ps1.
- 2026-06-20 - Runtime fix: on-device run exposed that Maestro reports "0 devices connected" when ANDROID_HOME is unset. Added `Set-AndroidHome` (env var -> %LOCALAPPDATA%\Android\Sdk -> parent-of-adb, no hardcode). After fix, `run-tests.ps1 -Suite <flow>` returns `{"pass":true}` exit 0 on emulator-5556. Also learned: a stale `offline` emulator + an explicit `--device <id>` Maestro rejects both cause false 0-devices; single online device + no `--device` is the reliable path.

---

### Step 01.2 - Off-context trace + single-line verdict + stable exit codes

**Files:** `maestro/run-tests.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Route the full per-flow Maestro output to a log file under `temp/` (e.g. `temp/maestro_suite_<TS>.log`) instead of the console. Emit only a one-line human verdict per flow plus a final suite summary line; add a `-Json` switch emitting a single object `{ pass, total, failed, flows:[{flow,pass,log}] }`. Adopt the stable exit table: 0 suite pass, 1 bad args, 2 Maestro CLI missing, 3 one or more flows failed, 4 execution error. Aggregate: suite passes iff every selected flow passes.

**Verification:**

- `Grep` - `temp` referenced as the trace destination in `maestro/run-tests.ps1`.
- `Grep` - `-Json` / `[switch]$Json` parameter present.
- `Grep` - `-Code 3` present (flow-failed exit path centralised in `Exit-Suite`, `exit $Code`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. temp/ off-context log dest, `[switch]$Json` + `{pass,total,failed,flows[]}` shape, exit table 0/1/2/3/4 via `Exit-Suite -Code N`. Functional smoke: `-Suite all -Json` discovered 10 flows, exit 4 (no device) with valid JSON. Files: maestro/run-tests.ps1.

---

### Step 01.3 - Keep suite/category/flow selection; add device pin

**Files:** `maestro/run-tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Preserve the existing `[smoke|critical|all]` selection surface and extend it to also accept a single flow path and a category subpath (e.g. `features\files`). Add a `-DeviceId` parameter forwarded to Maestro as `--device` when set; omit when exactly one device is online.

**Verification:**

- `Grep` - `DeviceId` parameter present in `maestro/run-tests.ps1`.
- `Grep` - `--device` forwarded.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. `-Suite` accepts all|smoke|critical|features|subpath|flow.yaml (Get-FlowSet); `-DeviceId` forwarded as `--device`. Files: maestro/run-tests.ps1.
- 2026-06-20 - Regression guard: removing `-DebugMode` broke callers run-maestro-smoke.ps1:28 + run-stress.ps1:49 (param-binding error). Restored `-DebugMode` (now echoes failing-flow trace tail). Verified: wrapper prints `SUITE FAIL (4)` not a binding error; positional `run-tests.ps1 smoke` doc usage still binds. `-MaxMinutes` not restored - no external caller passes it (would be dead).

---

### Step 01.4 - Document the oracle convention

**Files:** `maestro/WRITING_TESTS.md`
**Depends on:** - independent

**Prompt for developer:**

> Add an "Oracle convention" section: a flow is green only when it (1) `assertVisible` the expected post-action element by exact id/text, (2) where a stable completion log marker exists, waits for it, and (3) carries a crash guard. Forbid `optional: true` on the assertion that proves the behavior (reserve `optional` for genuinely variable UI such as permission dialogs). Forbid regex id/text matchers (`.*recycler.*`) - they silently never match. Link `research/02` marker/id table conceptually (markers per operation, ids per screen).

**Verification:**

- `Grep` - `Oracle convention` heading present in `maestro/WRITING_TESTS.md`.
- `Grep` - `optional` discussed (forbidden-on-proof-assertion rule).

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Authoritative "Oracle convention" section added at top of WRITING_TESTS.md: element+marker+crash-guard, forbids optional-on-proof + regex matchers, explicitly overrides the legacy resilient-optional patterns below. Files: maestro/WRITING_TESTS.md.

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] `maestro/run-tests.ps1` parses and runs: `-Suite all -Json` discovered 10 flows and returned a valid JSON verdict with exit 4 (no device) - not a parse error.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `maestro/run-tests.ps1` and `maestro/WRITING_TESTS.md` (via post-change.ps1).

---

## Handoff Notes to Next Phase

Runner now emits a stable per-flow + suite verdict consumable off-context; flow phases (02-05) author YAML against the oracle convention and validate via this runner. The `-Json` `flows[]` shape is the contract Phase 06 folds into the prerelease aggregator.

---

## Rollback Plan

Revert the phase commit - `maestro/run-tests.ps1` returns to its prior (hardcoded) form. No data or user-facing surface touched.
