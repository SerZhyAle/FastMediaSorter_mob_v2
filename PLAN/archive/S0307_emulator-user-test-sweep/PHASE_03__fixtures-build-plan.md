# Phase 03 - Fixtures Build Plan

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Prepare a reproducible fixture manifest, target flavor plan and emulator install plan for runnable tickets.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `temp/s0307/02_route_matrix.md` exists.
- [ ] Working tree is clean or current changes are owned by this pipeline.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/s0307/03_fixture_manifest.md` | New | ≤ 500 |
| `temp/s0307/03_build_install_plan.md` | New | ≤ 300 |
| `temp/s0307/03_push_manifest.txt` | New | ≤ 300 |

---

## Steps

### Step 03.1 - Build Fixture Manifest

**Files:** `temp/s0307/03_fixture_manifest.md`
**Depends on:** start of phase

**Prompt for developer:**

> Create a fixture manifest for every runnable route. Prefer generated or repo-owned files: short media samples, text files, archives and folder structures. Mark any fixture that cannot be generated without extra tools as `deferred-fixture`.

**Verification:**

- `Glob` - `temp/s0307/03_fixture_manifest.md` exists.
- `Grep` - `fixture_manifest_version=1` appears exactly once.
- `Grep` - `copyright_policy=generated-or-repo-owned` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/03_fixture_manifest.md`. Ready fixtures: 5; deferred fixtures: 3.

---

### Step 03.2 - Write Build And Install Plan

**Files:** `temp/s0307/03_build_install_plan.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Choose target debug flavors per route. Prefer standard debug for general non-VR checks and noLegal debug only for noLegal-only tickets. Record exact build script names without executing a build in this phase.

**Verification:**

- `Glob` - `temp/s0307/03_build_install_plan.md` exists.
- `Grep` - `standard_debug_required=` appears exactly once.
- `Grep` - `nolegal_debug_required=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/03_build_install_plan.md`. standardDebug=true; noLegalDebug=true.

---

### Step 03.3 - Write Push Manifest

**Files:** `temp/s0307/03_push_manifest.txt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Map host fixture paths to emulator destination paths. Use app-accessible shared storage paths only. Do not push credentials, tokens or private account state.

**Verification:**

- `Glob` - `temp/s0307/03_push_manifest.txt` exists.
- `Grep` - `push_manifest_version=1` appears exactly once.
- `Grep` - `secret_files=0` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/03_push_manifest.txt`. Secret files: 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `temp/s0307/03_fixture_manifest.md` exists.
- [x] `temp/s0307/03_build_install_plan.md` exists.
- [x] `temp/s0307/03_push_manifest.txt` exists.
- [x] No fixtures are copied to the emulator before Phase 04.

---

## Handoff Notes to Next Phase

Phase 04 executes only routes with prepared fixtures and an online emulator.

---

## Rollback Plan

Delete `temp/s0307/03_*` artifacts and any generated fixture files under `temp/s0307/fixtures/`. No catalog status changes are made in this phase.
