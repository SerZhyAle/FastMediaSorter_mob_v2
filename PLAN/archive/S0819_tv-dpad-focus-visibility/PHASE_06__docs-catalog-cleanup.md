# Phase 06 - Docs & catalog cleanup

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, record the shippable capability, and confirm no settings-manifest drift. No behavior change.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done (or 05 at `BlockNeedUserTest`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |

---

## Steps

### Step 06.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role` + `status` for the new classes (`FocusFrameOverlay`, `FocusFrameController`, `FocusFrameActivityCallbacks`, `FocusFrameFragmentCallbacks`, `FocusFrameExcluded`) via `set.ps1`.

**Verification:**

- `Grep` - `FocusFrameController` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 06.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the app-wide travelling focus frame for D-pad/TV/gamepad (spec `S0819`). Do NOT edit `docs/FEATURES*.md` (owned by `/skill-release`). Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record with `"spec":"S0819"` in `docs/ALL_FEATURES.jsonl`.
- `all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 06.3 - Confirm no settings-manifest drift

**Files:** (validation only)
**Depends on:** Step 06.2

**Prompt for developer:**

> This spec adds no user-facing setting (the frame is always ready, per owner). Confirm the settings-doc gate is clean: `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. If it reports drift unrelated to S0819, leave it (not this spec's concern) and note it.

**Verification:**

- `assert-settings-doc-sync.ps1` exits 0 (or drift is demonstrably pre-existing / unrelated).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with the new focus classes.
- [ ] `docs/ALL_FEATURES.jsonl` has the S0819 record.
- [ ] Dev log entries added for all logical changes.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After device verification, `/spec-check S0819` flips the spec to `Verified` and removes the S0819 probes.

---

## Rollback Plan

Docs/catalog only - regenerate from source; no runtime impact.
