# Phase 03 - Self-Test Fixtures

**Strategic spec:** [`../S0313_flavor-isolation-diff-guard.md`](../S0313_flavor-isolation-diff-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Prove the guard's exit-code contract on seeded inputs without depending on the live working tree: a clean set, a new flavor-gate line, a legacy-only file, and a `-LegacyAudit` run. Mirrors the `scripts/doc-drift.tests` fixture-runner precedent.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (driver entrypoint gates correctly).
- [ ] `scripts/guard.tests/` is writable (created by this phase).
- [ ] Fixtures live under `scripts/guard.tests/fixtures/`; the runner exercises the guard via explicit `-Path`, so no real `git` history is required.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/guard.tests/fixtures/clean_sample.kt.txt` | New | ≤ 40 |
| `scripts/guard.tests/fixtures/new_violation.kt.txt` | New | ≤ 40 |
| `scripts/guard.tests/fixtures/legacy_only.kt.txt` | New | ≤ 40 |
| `scripts/guard.tests/Run-Tests.ps1` | New | ≤ 220 |

> Fixtures use a `.kt.txt` suffix so they are never compiled by Gradle and never match a real `src/main/java` scan, yet carry representative Kotlin lines. The runner feeds them to the guard through `-Path` (explicit mode) and asserts exit codes. This tool reads Kotlin only; it writes no `.kt`.

---

## Steps

### Step 03.1 - Seed positive and negative fixtures

**Files:** `scripts/guard.tests/fixtures/clean_sample.kt.txt`, `scripts/guard.tests/fixtures/new_violation.kt.txt`, `scripts/guard.tests/fixtures/legacy_only.kt.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create three fixtures. `clean_sample.kt.txt`: representative main-source Kotlin with zero `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` tokens (uses an injected delegate instead, per `dev/FLAVOR_DEVELOPMENT_RULES.md`). `new_violation.kt.txt`: one line containing `if (BuildConfig.IS_NO_LEGAL_FLAVOR)`. `legacy_only.kt.txt`: one line containing `BuildConfig.SUPPORT_VR_PLAYER`. Each fixture stays small and self-explanatory.

**Verification:**

- `Glob` - all three fixture files exist under `scripts/guard.tests/fixtures/`.
- `Grep` - `clean_sample.kt.txt` contains zero `BuildConfig\.(IS_|SUPPORT_|ENABLE_)` matches.
- `Grep` - `new_violation.kt.txt` contains `BuildConfig.IS_NO_LEGAL_FLAVOR`.
- `Grep` - `legacy_only.kt.txt` contains `BuildConfig.SUPPORT_VR_PLAYER`.

> Verification results: 3 fixtures present | clean = 0 matches | new_violation = 1 `IS_NO_LEGAL_FLAVOR` | legacy_only = 1 `SUPPORT_VR_PLAYER`.

**Status:** `[x]` done

---

### Step 03.2 - Write the fixture test runner

**Files:** `scripts/guard.tests/Run-Tests.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `scripts/guard.tests/Run-Tests.ps1`. Because the guard's `Get-ChangedMainKotlin` filters to `app_v2/src/main/java`, the runner must exercise the guard's detection through a thin seam: import the Phase 01/02 library modules directly and assert on records, OR add a documented `-ScanFile` test hook to the guard that scans an arbitrary file path in explicit mode and classifies all matches as `new-or-touched`. Choose the seam that keeps production behaviour unchanged; document it in the runner header. The runner runs each fixture, asserts the resulting `blocking` flag and exit code, prints `PASS`/`FAIL` per case, and exits 0 only if all cases pass.

**Verification:**

- `Glob` - `scripts/guard.tests/Run-Tests.ps1` exists.
- `Grep` - all three fixture filenames are referenced in the runner.
- `Grep` - `PASS` and `FAIL` literals are present.
- `Grep` - the runner header documents the test seam it uses (`-ScanFile` hook or direct module import).

> Seam chosen: the guard's `-ScanFile` hook (production behaviour unchanged; documented in both the guard and the runner header). Verification results: 3 fixture filenames referenced | `PASS`+`FAIL` literals present | header documents the `-ScanFile` seam.

**Status:** `[x]` done

---

### Step 03.3 - Assert the exit-code contract

**Files:** `scripts/guard.tests/Run-Tests.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Encode three blocking assertions in the runner: clean fixture => `blocking=false`; new-violation fixture => `blocking=true`; legacy-only fixture fed in explicit mode => `blocking=true` (explicit `-Path` asserts intent so an explicit legacy token IS treated as touched), but legacy-only fed via the full-scan `-LegacyAudit` seam => `blocking=false` with `legacyCount>0`. State each expected exit code as a literal in the runner so the contract is self-documenting.

**Verification:**

- `Grep` - `blocking=false` (or equivalent assertion) appears for the clean case.
- `Grep` - `blocking=true` appears for the new-violation case.
- `Grep` - `legacyCount` appears for the `-LegacyAudit` assertion.
- `pwsh -NoProfile -File scripts/guard.tests/Run-Tests.ps1` - expected exit: 0 (all cases pass) | actual: 0.

> Contract proven (4/4 PASS): clean => blocking=False exit 0 | new_violation => blocking=True exit 1 | legacy_only explicit => blocking=True exit 1 | legacy_only -LegacyAudit => blocking=False exit 0 legacyCount=1. Expected exit literals 0/1/1/0 are encoded per case in the runner.

**Status:** `[x]` done

---

### Step 03.4 - Confirm runner is hermetic

**Files:** `scripts/guard.tests/Run-Tests.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Confirm the runner does not depend on the live working-tree git state, does not stage or modify any tracked file, and writes any transient artifacts only under `temp/`. Run it twice in a row and confirm identical exit codes. Add a header note that the runner is safe to run on a dirty tree.

**Verification:**

- `Grep` - `temp/` is the only write target in the runner (no writes under `scripts/`, `app_v2/`, or repo root).
- `Grep` - the runner header states it is dirty-tree-safe.
- `pwsh -NoProfile -File scripts/guard.tests/Run-Tests.ps1` first run - expected exit: 0 | actual: 0.
- `pwsh -NoProfile -File scripts/guard.tests/Run-Tests.ps1` second run - expected exit: 0 (identical) | actual: 0.

> Hermeticity results: zero write commands (`Set-Content`/`Out-File`/`Add-Content`/`New-Item`) in the runner - it asserts on JSON stdout and the guard runs with `-DryRun` so no artifact is written; header carries the dirty-tree-safe note; both runs returned identical exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Build gate: `pwsh -NoProfile -File scripts/guard.tests/Run-Tests.ps1` exits 0 - expected: 0 | actual: 0 (4/4 PASS, both runs).
- [x] `Glob` - all three fixtures and the runner exist under `scripts/guard.tests/`.
- [x] The runner is hermetic (no tracked-file writes, dirty-tree-safe, idempotent exit code across two runs).
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1` (ChangeType `Script`).

---

## Handoff Notes to Next Phase

Phase 04 documents the exit codes and token list next to the owning script, adds a `scripts/guard/README.md`, and records the dev-log placeholder. No behavioural change remains.

---

## Rollback Plan

Delete `scripts/guard.tests/`. The guard and its libraries remain valid. No source, catalog, or user-facing surface changed.
