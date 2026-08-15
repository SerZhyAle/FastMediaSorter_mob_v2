# Phase 01 - Bootstrap Discovery

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Capture the current `BlockNeedUserTest` catalog snapshot, emulator preflight state, and non-VR sweep scope.

---

## Prerequisites

- [ ] Working tree is clean or current changes are owned by this pipeline.
- [ ] `scripts/spec_catalog/search.ps1` and `adb` are callable from the repo shell.
- [ ] Evidence root `temp/s0307/` is writable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/s0307/01_blockneedusertest.tsv` | New | ≤ 300 |
| `temp/s0307/01_emulator_preflight.txt` | New | ≤ 200 |
| `temp/s0307/01_scope.md` | New | ≤ 300 |

---

## Steps

### Step 01.1 - Capture BlockNeedUserTest Snapshot

**Files:** `temp/s0307/01_blockneedusertest.tsv`
**Depends on:** start of phase

**Prompt for developer:**

> Run the spec catalog query for `BlockNeedUserTest` and write a tab-separated snapshot with ticket id, slug, status, priority and capture timestamp. Do not remove or mutate any target ticket in this step.

**Verification:**

- `Glob` - `temp/s0307/01_blockneedusertest.tsv` exists.
- `Grep` - `BlockNeedUserTest` appears at least once.
- `Grep` - `S0307` does not appear in the target-ticket rows.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/01_blockneedusertest.tsv`. Count: 28.

---

### Step 01.2 - Capture Emulator Preflight

**Files:** `temp/s0307/01_emulator_preflight.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Capture `adb devices -l`, device state, API level, ABI, screen size, package presence and storage availability when accessible. If the emulator is offline, record `device_state=offline` and keep the step successful as a preflight artifact; Phase 04 owns the hard execution block.

**Verification:**

- `Glob` - `temp/s0307/01_emulator_preflight.txt` exists.
- `Grep` - `device_state=` appears exactly once.
- `Grep` - `adb_devices=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/01_emulator_preflight.txt`. Device state: offline.

---

### Step 01.3 - Write Sweep Scope

**Files:** `temp/s0307/01_scope.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Split the catalog snapshot into excluded VR/3D tickets and eligible non-VR candidates. Record counts, exclusion reasons and the seed candidate list. Treat uncertain tickets as review candidates, not verified targets.

**Verification:**

- `Glob` - `temp/s0307/01_scope.md` exists.
- `Grep` - `excluded_vr_3d_count=2` appears exactly once unless the fresh catalog changes the count and the file explains why.
- `Grep` - `eligible_non_vr_count=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/01_scope.md`. Eligible: 26; excluded: 2.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `temp/s0307/01_blockneedusertest.tsv` exists.
- [x] `temp/s0307/01_emulator_preflight.txt` exists.
- [x] `temp/s0307/01_scope.md` exists.
- [x] No files were written to the project root.

---

## Handoff Notes to Next Phase

Phase 02 consumes `temp/s0307/01_scope.md` and resolves each eligible ticket into a route matrix.

---

## Rollback Plan

Delete `temp/s0307/01_*` artifacts and rerun Phase 01. No source or catalog status changes are made in this phase.
