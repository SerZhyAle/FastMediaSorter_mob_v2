# Phase 06 - Docs catalog cleanup

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01-05
**Blocks:** Final verification only
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Close the ticket cleanly: update the public feature inventory, sync the developer catalog and changelog, and prepare `BlockNeedUserTest` with real-device evidence requirements.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +1 bullet |
| `docs/FEATURES_RU.md` | Modified | +1 bullet |
| `docs/FEATURES_UK.md` | Modified | +1 bullet |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CHANGELOG.md` | Modified | +1 ticket batch |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |
| `PLAN/S0545_camera-capabilities-expansion.md` | Modified | audit/status updates only |

---

## Steps

### Step 6.1 - Update public feature docs and developer inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the shipped capability to the public feature showcase in EN/RU/UK and write the developer-facing inventory record to `docs/ALL_FEATURES.jsonl`. The wording must mention the unified in-app camera, flash/zoom/lens/focus controls, and in-app video without promising universal macro support.

**Verification:**

- `Grep` - `in-app camera` or equivalent shipped phrase is present in `docs/FEATURES.md`.
- `Grep` - the corresponding RU and UK feature bullets are present in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.
- `Grep` - `S0545` and `camera` are present in the new `docs/ALL_FEATURES.jsonl` record.

**Status:** `[ ]` not done

---

### Step 6.2 - Sync changelog and catalog

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 6.1

**Prompt for developer:**

> Add the ticket batch to `dev/CHANGELOG.md`, then regenerate the app catalog once after the final Kotlin shape settles. Do not hand-edit the JSONL catalog - use the project sync script.

**Verification:**

- `Grep` - `S0545` is present in `dev/CHANGELOG.md`.
- `Script` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `CameraCaptureFlowManager` is present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 6.3 - Prepare BlockNeedUserTest and verification handoff

**Files:** `PLAN/S0545_camera-capabilities-expansion.md`
**Depends on:** Step 6.2

**Prompt for developer:**

> Run the cheapest valid build proof for the full camera change set, add `S0545` debug tags at the changed flow entry points that must survive until user testing, and move the ticket to `BlockNeedUserTest` with a status note that explicitly asks for real-device in-app video verification. Do not remove the tags until `/spec-check` advances the ticket out of that status.

**Verification:**

- `Script` - `.\a.ps1 fc` exits 0.
- `Grep` - `Timber.d("S0545:` matches the changed capture-flow entry points.
- `Grep` - `**Status note:**` in `PLAN/S0545_camera-capabilities-expansion.md` mentions real-device in-app video validation after the status transition.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `/spec-test-device S0545` recorded the required real-device evidence before `/spec-check`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation/catalog/status updates only; no data migration or user-facing runtime behavior changed in this phase.
