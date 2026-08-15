# Phase 01 - Standard surface baseline

**Strategic spec:** [`../S0553_standard-production-release-readiness.md`](../S0553_standard-production-release-readiness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-20
**Completed:** 2026-06-20

## Step Log

- 2026-06-20 - 01.1 PASS: `standard-surface-snapshot.ps1` created; `-Json` exit 0 emits `generatedFor:standard` + buildConfig + 329 capabilities. (Fixed `$json`/`$Json` case-insensitive variable collision.)
- 2026-06-20 - 01.2 PASS: `-CheckRegressions` exit 0, "standard surface clean: 329 capabilities, no flag regressions".
- 2026-06-20 - 01.3 PASS: `docs/RELEASE_READINESS_STANDARD.md` created; all six H2 headers present; snapshot tool referenced in Target surface.

---

## Objective

Produce a machine-readable canonical standard surface snapshot (from `docs/ALL_FEATURES.jsonl` + standard BuildConfig matrix) and the surface-baseline + intentional-exclusions sections of the release-readiness gate document.

---

## Prerequisites

- [ ] Strategic §9 research items Resolved (research/01 in particular).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/standard-surface-snapshot.ps1` | New | ≤ 220 |
| `docs/RELEASE_READINESS_STANDARD.md` | New | ≤ 400 |

> No `.kt` change - no catalog impact. PowerShell scripts only; keep functions small and `-NoProfile`-friendly.

---

## Steps

### Step 01.1 - Author standard surface snapshot tool

**Files:** `scripts/release/standard-surface-snapshot.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/release/standard-surface-snapshot.ps1`. It reads `docs/ALL_FEATURES.jsonl` and selects records whose `flavors` array contains `"standard"` and whose `status` is `"active"`. It also parses the `standard` flavor block in `app_v2/build.gradle.kts` for the capability flags (`SUPPORT_*`, `ENABLE_*`, `SUPPORTS_DEFAULT_PLAYER`). It emits one JSON object `{ generatedFor:"standard", buildConfig:{...flags...}, capabilities:[{id,area,name}] }`. Support `-OutFile <path>` (default `temp/standard-surface-snapshot.json`) and `-Json` (write the object to stdout). Use `$ErrorActionPreference='Stop'`, a `trap { exit 1 }` and explicit `exit 0` per the catalog-script exit-code contract.

**Verification:**

- `Glob` - `scripts/release/standard-surface-snapshot.ps1` exists.
- `Grep` - `flavors` and `ALL_FEATURES.jsonl` both present in the file.
- `Grep` - `param(` block present with `OutFile` and `Json`.
- Run: `pwsh -NoProfile -File scripts/release/standard-surface-snapshot.ps1 -Json` exits 0 and prints a JSON object containing `"generatedFor":"standard"`.

**Status:** `[x]` done

---

### Step 01.2 - Add surface-regression detection to the snapshot tool

**Files:** `scripts/release/standard-surface-snapshot.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the tool with a `-CheckRegressions` switch. When set, it cross-checks each capability whose `flavors` contains `"standard"` against the parsed BuildConfig flags and the source-set reality, and lists any capability that is documented as standard-supported but whose gating flag is `false` (candidate §5.2 flavor-surface regression). Print the candidate list and exit non-zero (1) when any candidate is found, exit 0 when clean. Keep the default (no switch) behaviour unchanged (snapshot emit, exit 0).

**Verification:**

- `Grep` - `CheckRegressions` present in the param block.
- Run: `pwsh -NoProfile -File scripts/release/standard-surface-snapshot.ps1 -CheckRegressions` exits 0 or 1 (never 2) and prints either a clean line or a candidate list.

**Status:** `[x]` done

---

### Step 01.3 - Create gate document skeleton + surface baseline sections

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `docs/RELEASE_READINESS_STANDARD.md` (EN) as the canonical standard-production release-readiness gate. Add the full section skeleton with these H2 headers in order: `Target surface`, `Intentional exclusions`, `Release-risk audit`, `Coverage matrix`, `Operator evidence pack`, `Verdict contract`. Fill the first two now: `Target surface` lists the standard baseline (cite the snapshot tool as the machine-readable source and `docs/ALL_FEATURES.jsonl` + BuildConfig as origin, per research/01); `Intentional exclusions` lists the §5.1 exclusions (noLegal-only, VR-only, debug-only, sideload-only) as not-a-regression. Leave the remaining four headers with a one-line `Filled in Phase NN` placeholder. No marketing copy - this is an operator/engineer gate.

**Verification:**

- `Glob` - `docs/RELEASE_READINESS_STANDARD.md` exists.
- `Grep` - all six H2 headers present (`## Target surface`, `## Intentional exclusions`, `## Release-risk audit`, `## Coverage matrix`, `## Operator evidence pack`, `## Verdict contract`).
- `Grep` - `standard-surface-snapshot.ps1` referenced in the Target surface section.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Both scripts run with exit 0 on the happy path - run them, record exit codes.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1` (or batched in Phase 05).

---

## Handoff Notes to Next Phase

The canonical surface snapshot tool and the gate document skeleton exist. Phase 03 fills the coverage matrix referencing the snapshot; Phase 04 folds the snapshot into the verdict aggregator.

---

## Rollback Plan

Revert phase commit(s) - new script + new doc, no runtime or user-facing surface changed.
