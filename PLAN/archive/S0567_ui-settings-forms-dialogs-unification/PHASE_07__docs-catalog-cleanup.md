# Phase 07 - docs-catalog-cleanup

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Finalize: regenerate the class catalog for the new public widgets, confirm settings docs are unaffected, and journal the whole change set.

---

## Prerequisites

- [ ] Phases 01-06 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+`.md`) | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

> No `docs/FEATURES*.md` edit - strategic spec carries no §8 FEATURES sentence (visual-debt refactor). No `docs/ALL_FEATURES.jsonl` record - no new user-facing capability delivered.

---

## Steps

### Step 07.1 - Set catalog role/status for new widgets, regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> For each new class (`SettingsSelectionRow`, `SettingsDropdownRow`, `SettingsInputRow`, `FormFieldPairLayout`, `FormCheckboxRow`, `ActionHelpRow`, `ListSelectionDialog`, `ListSelectionAdapter`) set `role` + `status` via `dev/CATALOG/scripts/set.ps1`, then run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `Grep` - `SettingsSelectionRow` and `ListSelectionDialog` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 07.2 - Settings-doc-sync gate

**Files:** (gate only - regen iff flagged)
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. Migration kept every setting's presence, behavior, position, and naming identical, so the gate is expected to pass with no manifest change. If it flags a drift, regenerate `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` per CLAUDE.md Rule 22 before continuing.

**Verification:**

- `assert-settings-doc-sync.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 07.3 - Journal the change set

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 07.2

**Prompt for developer:**

> Add one dev-log entry summarizing the S0567 UI-unification change set via `pwsh -NoProfile -File scripts/add_to_dev_log.ps1` (or `close-and-log.ps1 -DevLogs` batched). Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - an `S0567` entry exists in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` contains all new widget classes.
- [ ] `assert-settings-doc-sync.ps1` exits 0.
- [ ] `dev/CHANGELOG.md` has an `S0567` entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0567` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Catalog + changelog are regenerable; no source rollback needed for this phase.
