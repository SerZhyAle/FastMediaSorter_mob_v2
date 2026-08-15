# Phase 01 - Writer Harvester Script

**Strategic spec:** [`../S0703_shared-state-mutation-audit.md`](../S0703_shared-state-mutation-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-26
**Completed:** 2026-06-26

---

## Objective

Add a PowerShell reporting script that statically harvests writers of shared state across the app modules - both UI view properties and data carriers - groups them by ownership domain, flags unsafe-mutation patterns, and emits a ranked report.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none).
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/quality/measure-hotspots.ps1` exists (idiom reference for a non-gate reporting script).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/audit-shared-state-writers.ps1` | New | ≤ 320 |

---

## Steps

### Step 01.1 - UI-surface writer harvest

**Files:** `scripts/quality/audit-shared-state-writers.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a PowerShell 7 reporting script (`#requires -Version 7.0`, `Set-StrictMode -Version Latest`, `$ErrorActionPreference = 'Stop'`, `[CmdletBinding()]`) that scans Kotlin sources under `app_v2/src` and `wear/src` (all source sets). Collect every write to an observable view property: `visibility`, `isVisible`, `isEnabled`, `alpha`, `isClickable`, `isFocusable`, `text`. Match BOTH direct receivers (`binding.<id>` / `binding.<id>?`) AND indirect receivers (a local/loop/`it`/parameter variable, or a write inside `apply{}`/`with{}`) - the indirect case is mandatory because the generic loop writer that caused the seed bug is invisible to id-only matching. For each writer record the source file (leaf name + relative path) and whether the receiver was direct (`binding.<id>`) or indirect (generic). Do not hardcode any project class name or path beyond the two scan roots; drive everything off regex over the scanned files.

**Verification:**

- `Glob` - `scripts/quality/audit-shared-state-writers.ps1` exists.
- `Grep` - `#requires -Version 7.0` present in the file.
- `Grep` - `isVisible|visibility|isEnabled|alpha` regex literal present in the file.
- `Grep` - the file references both scan roots `app_v2/src` and `wear/src`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-26 - Verification 4/4 PASS. Files: scripts/quality/audit-shared-state-writers.ps1 (New). Script runs exit 0, 1609 UI writer records over 2157 files. Dev log recorded.

---

### Step 01.2 - Data-surface writer harvest

**Files:** `scripts/quality/audit-shared-state-writers.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the same script to collect writers on the data surface: reactive state holders (`MutableStateFlow` `.value =`, `.emit(`, `.tryEmit(`, `.update {`, plus LiveData `.value =` / `.postValue(` / `.setValue(`), Room write operations (`@Insert` / `@Update` / `@Delete` annotations and `@Query` bodies starting `UPDATE`/`DELETE`/`INSERT`), preference/DataStore edits (`.edit {`, `preferences[` assignments), and in-memory cache puts (`.put(`, `[key] =` on mutable maps). Record per carrier symbol the writing files and the enclosing declared coroutine/thread context when cheaply detectable (e.g. nearest `Dispatchers.*`, `withContext`, `viewModelScope`/`lifecycleScope`). Keep UI and data harvests selectable via a `-Surface ui|data|all` parameter (default `all`).

**Verification:**

- `Grep` - `MutableStateFlow|postValue|\.update \{|\.emit\(` regex literal present in the file.
- `Grep` - `@Insert|@Update|@Delete` literal present in the file.
- `Grep` - `param(` block declares a `-Surface` / `$Surface` parameter.

**Status:** `[x] done`

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. Files: scripts/quality/audit-shared-state-writers.ps1 (+~35 LOC). Data harvest runs exit 0, 663 data writer records. Dev log recorded.

---

### Step 01.3 - Ownership grouping, pattern flags, ranked output

**Files:** `scripts/quality/audit-shared-state-writers.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Aggregate the harvested writers into findings. Group UI writers by ownership domain = referenced binding type when resolvable (`Activity<X>Binding` / `Fragment<X>Binding`), else by enclosing file; group data writers by carrier symbol. Keep only findings with `>= $MinWriters` distinct writer files (default 2). For each finding compute a `PatternFlags` set: `generic-loop-writer` (a generic/indirect writer coexists with a direct point writer of the same property), `no-single-owner` (>=2 writers, none marked authoritative), `cross-scope-write` (data carrier written from >=2 distinct coroutine/thread contexts). Rank = writerCount x patternWeight (generic-loop-writer = 3, cross-scope-write = 3, no-single-owner = 1). Print a ranked, table-free report to stdout (rank, surface, carrier, domain, writerCount, flags, writer files) honouring `-Top N` (default 20), and when `-Json <path>` is supplied also write the full machine-readable result there; default the JSON path under `temp/` (never repo root). Exit 0 on success. No business logic beyond reporting; the script must never edit source.

**Verification:**

- `Grep` - `Activity` and `Binding` literals present (binding-type grouping).
- `Grep` - `generic-loop-writer` literal present in the file.
- `Grep` - `temp/` referenced as the default JSON destination.
- `Bash`/`PowerShell` - `pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Surface ui -Top 5` exits 0 and prints a ranked header line.
- `PowerShell` - the same run lists `btnToggleView` among UI findings with `>= 2` writer files (seed conflict surfaces).

**Status:** `[x] done`

**Step Log:**

- 2026-06-26 - Verification 5/5 PASS. Files: scripts/quality/audit-shared-state-writers.ps1 (+~60 LOC). Ranked report runs exit 0; 56 UI / 69 all multi-writer findings; seed btnToggleView surfaces (2 writers per screen domain, generic-loop-writer flag); JSON written to temp/. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Script runs clean - `pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Top 5` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `scripts/quality/audit-shared-state-writers.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The harvester is the mechanical candidate pre-filter. Phase 02 documents how its output feeds the agent-run prompt; the prompt doc must reference this script by its committed path and `-Json` output convention.

---

## Rollback Plan

Revert the phase commit - a single new standalone script, no source or user-facing surface touched, no data migration.
