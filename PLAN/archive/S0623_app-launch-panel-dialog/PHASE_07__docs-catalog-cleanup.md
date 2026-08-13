# Phase 07 - Docs, Catalog and Settings Sync

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-06-23
**Completed:** 2026-06-23

> 07.2 note: `assert-settings-doc-sync.ps1` returns OK without a manifest change - the gesture-action picker is a runtime single-choice dialog, so the new `OPEN_PANEL` value is a label string, not a catalogued declarative setting row. Rule 22 satisfied by the green mechanical gate.

---

## Objective

Final synchronization: regenerate the class catalog, regenerate the settings manifest/reference (new gesture-action value - Rule 22), record the capability in the feature inventory, and confirm dev-log coverage.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `app_v2.md` | Regenerated (gitignored) | - |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE.md` (+ locale variants) | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |

---

## Steps

### Step 07.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then fill `role` + `status` for the new public classes (entity, dao, repository, UseCases, ViewModels, activities, adapters, dialog fragments) via `dev/CATALOG/scripts/set.ps1` where the scan left them blank.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*AppLaunchPanel*"` returns the new classes.
- `query.ps1 -Module app_v2 -ClassMatches "*EditAppLaunchPanel*"` returns the Edit-screen classes.

**Status:** `[x]` done

---

### Step 07.2 - Regenerate the settings manifest + reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** Phase 06

**Prompt for developer:**

> The gesture-direction setting gained a new selectable action (`OPEN_PANEL`) - Rule 22 requires regenerating the settings docs. Run the settings-manifest generation (`assembleStandardDebug` with `-Dsettings.manifest.generate=true`, or the project's documented regen path) and update `settings-annotations.json` for the gesture-action rows so the new value is annotated. Confirm with the mechanical gate.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - `OPEN_PANEL` (or its label key) reflected in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done

---

### Step 07.3 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phase 06

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the shipped capability: a left-edge gesture can open a quick-launch panel of large app tiles (fixed 3x5 / 5x3 grid) with an Edit screen for pinning external apps. EN-only. Set the `spec` field to `S0623`. (Normally `/spec-dev` writes this on `Implemented`; do it here if it was not already captured.)

**Verification:**

- `Grep` - a record with `"spec":"S0623"` (or `S0623` in the spec field) present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 07.4 - Confirm dev-log coverage

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Phases 01-06

**Prompt for developer:**

> Confirm every logical change in this ticket has a `dev/CHANGELOG.md` entry (added via `.\scripts\add_to_dev_log.ps1`, one entry per phase/logical change, not per file). Add any missing entry. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0623` appears in `dev/CHANGELOG.md` for the panel work.

**Status:** `[x]` done

---

### Step 07.5 - Final neuroslop + quality gate sweep

**Files:** (touched Kotlin/XML from all phases)
**Depends on:** Phases 01-06

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` (or its bundled gates: `assert-neuroslop.ps1`, `assert-deprecated-pm-flags.ps1`, `assert-no-ticket-logs.ps1`) over the touched files. Resolve any new findings (the single `Timber.d("S0623:` tag is expected and allowed while the ticket is `BlockNeedUserTest`; the ticket-log gate permits `Timber.d` probes, only persistent `Timber.i/w/e` with a ticket id fail).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-deprecated-pm-flags.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Catalog, settings manifest/reference, and ALL_FEATURES are current.
- [ ] All mechanical gates exit 0.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, the ticket is `BlockNeedUserTest` (one `Timber.d("S0623:` probe present); device verification of the gesture -> panel -> launch / Edit flow closes it via `/spec-test-device` + `/spec-check`.

---

## Rollback Plan

Docs/catalog regeneration is idempotent - re-run the generators to revert. No source rollback in this phase.
