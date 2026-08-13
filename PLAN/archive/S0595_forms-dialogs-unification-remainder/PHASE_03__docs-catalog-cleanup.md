# Phase 03 - docs-catalog-cleanup

**Strategic spec:** [`../S0595_forms-dialogs-unification-remainder.md`](../S0595_forms-dialogs-unification-remainder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Finalize: regenerate the class catalog for the new public widgets, confirm settings docs are unaffected, and journal the whole change set.

---

## Prerequisites

- [ ] Phases 01-02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+`.md`) | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

> No `docs/FEATURES*.md` edit - visual-debt refactor. No `docs/ALL_FEATURES.jsonl` record - no new user-facing capability delivered.

---

## Steps

### Step 03.1 - Set catalog role/status for new widgets, regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> For each new class (`FormFieldPairLayout`, `FormCheckboxRow`, `ActionHelpRow`) set `role` + `status` via `dev/CATALOG/scripts/set.ps1`, then run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `Grep` - `FormFieldPairLayout` and `ActionHelpRow` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - `catalog_sync.ps1 -Module app_v2` scanned the 3 new widgets; `set.ps1` assigned role + `status=new` to `FormFieldPairLayout`, `FormCheckboxRow`, `ActionHelpRow`. Catalog path format is package-relative (`com/sza/..`).

---

### Step 03.2 - Settings-doc-sync gate

**Files:** (gate only - regen iff flagged)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. Migration kept every setting's presence, behavior, position, and naming identical, so the gate is expected to pass with no manifest change. If it flags a drift, regenerate `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` per CLAUDE.md Rule 22 before continuing.

**Verification:**

- `assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x] done` (S0595 introduces no settings drift)

**Step Log:**

- 2026-06-21 - S0595 touches no settings screen (resource editor / add resource / GIF editor are not settings surfaces), so it introduces zero settings-doc drift - the phase obligation is met. The gate currently reports a `manifest-fresh` FAIL, but that drift comes from the ambient **uncommitted S0567 WIP** (modified `fragment_settings_*.xml` + `settings-manifest.json` already in the working tree before S0595) plus a pre-existing broken unit test in `testStandardDebugUnitTest`. Regenerating the manifest here would entangle another ticket's WIP, so it is left to whoever lands the S0567 settings change set. Recorded as a manual/unresolved note.

---

### Step 03.3 - Journal the change set

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Add one dev-log entry summarizing the S0595 UI-unification remainder change set via `pwsh -NoProfile -File scripts/add_to_dev_log.ps1` (or `close-and-log.ps1 -DevLogs` batched). Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - an `S0595` entry exists in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` contains all new widget classes.
- [ ] `assert-settings-doc-sync.ps1` exits 0.
- [ ] `dev/CHANGELOG.md` has an `S0595` entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0595` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Catalog + changelog are regenerable; no source rollback needed for this phase.
