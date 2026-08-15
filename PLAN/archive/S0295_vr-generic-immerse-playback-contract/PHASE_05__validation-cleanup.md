# Phase 05 - Validation Cleanup

**Strategic spec:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-25
**Completed:** 2026-05-25

---

## Objective

Close the contract work with explicit spec metadata, catalog regeneration, and build gates before the audit pass.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.
- [ ] Every touched Kotlin/spec file already has a dev-log entry.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0295_vr-generic-immerse-playback-contract.md` | Modified | <= 320 |

---

## Steps

### Step 05.1 - Refresh strategic metadata and explicit no-FEATURES rationale

**Files:** `PLAN/S0295_vr-generic-immerse-playback-contract.md`
**Depends on:** start of phase

**Prompt for developer:**

> Refresh the strategic spec only where the implementation changed tactical assumptions. Keep `docs/FEATURES*` intentionally unchanged and state that explicitly if the contract work remains infrastructure-only.

**Verification:**

- `Grep` - `Without changes in docs/FEATURES` returns zero hits in `S0295_vr-generic-immerse-playback-contract.md`.
- `Grep` - `Без изменений в docs/FEATURES` appears in `S0295_vr-generic-immerse-playback-contract.md`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 2/2 PASS. Files: `PLAN/S0295_vr-generic-immerse-playback-contract.md`. Strategic spec contains explicit `Без изменений в docs/FEATURES` rationale.

---

### Step 05.2 - Regenerate catalog and prepare the audit handoff

**Files:** `PLAN/S0295_vr-generic-immerse-playback-contract.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the mechanical closure for the spec and Kotlin changes: dev log, `scripts/catalog_sync.ps1 -Module app_v2`, standard debug build, and noLegal debug build. Leave the spec ready for `/spec-check S0295` with no hidden closure work.

**Verification:**

- `Grep` - `Tactical plan` appears in `S0295_vr-generic-immerse-playback-contract.md`.
- `Grep` - `Status:` appears in `S0295_vr-generic-immerse-playback-contract.md`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 2/2 PASS. Catalog sync exit 0; `build-standard-debug.ps1` exit 0; `build-nolegal-debug.ps1` exit 0. Ready for `/spec-check S0295`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x]` done.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- [x] Standard debug build passes.
- [x] noLegal debug build passes.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `/spec-check S0295` runs immediately after this phase.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the closing commit(s) and restore the last passing buildable state before the audit loop.
