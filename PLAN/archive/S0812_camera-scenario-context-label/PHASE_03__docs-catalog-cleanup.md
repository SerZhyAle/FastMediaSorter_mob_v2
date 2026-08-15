# Phase 03 - Docs & catalog cleanup

**Strategic spec:** [`../S0812_camera-scenario-context-label.md`](../S0812_camera-scenario-context-label.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new `CameraScenario` type, record the shipped capability, and journal the change.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 03.1 - Set catalog role for CameraScenario

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the catalog and fill role/status for the new class:
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then
> `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Class CameraScenario -Role "Camera capture scenario enum (S0812)" -Status active` (adjust flag names to `set.ps1` signature).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*CameraScenario*"` returns the class with a non-empty role.

**Status:** `[ ]` not done

---

### Step 03.2 - Record shipped capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one capability record (EN-only) via `scripts/all_features/add.ps1` describing: the in-app camera shows a scenario context label (text recognition) above the zoom presets when opened for that scenario; hidden for generic capture. Set `spec` field to `S0812`.

**Verification:**

- `Grep` - `S0812` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 03.3 - Journal the change

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> One dev-log entry for the ticket (batched, one logical change): `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraScenario.kt" "spec-dev" "S0812: camera scenario context label"`. Settings-doc-sync is N/A (no setting added/changed).

**Verification:**

- `Grep` - `S0812` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Catalog regenerated; `CameraScenario` has a role.
- [ ] Capability recorded in `docs/ALL_FEATURES.jsonl`.
- [ ] Dev log entry present.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0812`.

---

## Rollback Plan

Docs/catalog only - revert generated files; no runtime impact.
