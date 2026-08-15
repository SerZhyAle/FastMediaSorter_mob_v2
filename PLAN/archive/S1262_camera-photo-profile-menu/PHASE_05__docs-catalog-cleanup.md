# Phase 05 - Docs, catalog, cleanup

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Record the shipped capability, refresh the class catalog and icon docs, close the ticket into device-test state.

---

## Prerequisites

- [x] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` (via `scripts/all_features/add.ps1`) | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` (regenerated) | Modified | n/a |
| icon legend registry doc (per `scripts/document_registry/query.ps1 -ProductArea ui`) | Modified | n/a |

---

## Steps

### Step 05.1 - Capability record

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN record via `scripts/all_features/add.ps1`: photo-profile menu on the capture screen (normal, night, portrait, selfie, macro, sport), device-gated entries, replaces the macro and night buttons. Flavors: read from the gate - the capture screen compiles into all flavors. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `photo-profile` (or the chosen slug) present in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Catalog regen and roles

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the ticket; then `set.ps1` role+status for the new classes (`PhotoProfile`, `SportExposureOptionsFactory`, `CameraProfileApplyManager`).

**Verification:**

- `Grep` - `CameraProfileApplyManager` present in `dev/CATALOG/app_v2.jsonl` with a non-empty role.

**Status:** `[x]` done

---

### Step 05.3 - Icon legend / registry sync

**Files:** icon legend doc as returned by the registry query
**Depends on:** Phase 04 icons

**Prompt for developer:**

> Query `scripts/document_registry/query.ps1 -ProductArea ui -Trigger user-feature`; for the icon-legend record add the new profile-menu icon (and any new per-profile icons) with one-line meanings; run the registry `validate.ps1` and `generate.ps1 -Check`. State unchanged records with reasons.

**Verification:**

- Registry `validate.ps1` exits 0; `generate.ps1 -Check` clean.

**Status:** `[x]` done

---

### Step 05.4 - Dev logs and ticket state

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1` / `close-and-log.ps1 -DevLogs`)
**Depends on:** Step 05.1-05.3

**Prompt for developer:**

> Batch dev-log entries for the ticket (one per logical change, not per file). Confirm the S1262 probe tags exist (Phase 04 step 04.5), then advance the ticket: `update.ps1 -Id S1262 -Status BlockNeedUserTest -StatusNote '<device checks: menu entries per device, macro jump, night absorbed, sport exposure, portrait where supported>'`.

**Verification:**

- `select.ps1 -Id S1262` shows `BlockNeedUserTest` with a concrete status note.

**Status:** `[x]` done

---

## Deviation on step 05.3

The icon-legend record needs no edit, and that is a finding rather than a skip. Its inventory is
generated from `IconInventoryExportTest`, which enumerates five surfaces - `player-command`,
`program-nav`, `send-to`, `settings-header`, `settings-row`. The camera capture overlay is not one of
them: none of `ic_camera_macro_*`, `ic_camera_night_*` or `ic_tune` has ever appeared in
`docs/icons/icon-inventory.json`, so the two new profile vectors do not belong there either and the
legend is not out of date. Registry `validate.ps1` (24 records) and `generate.ps1 -Check` both pass
unchanged.

Other registry records queried and unaffected: `ui-communication` (policy text, not a string list),
`feature-inventory` (satisfied by step 05.1), `settings-reference` (this ticket adds no setting),
`site-landing` / `site-reference-pages` / `user-guides` (release-driven surfaces, populated by
`/skill-release` from the `ALL_FEATURES` diff).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `.\a.ps1 fg` (fast static gates) passes - 18 pass / 0 fail, `assert-no-ticket-logs` 0 stray
      (2026-07-31 00:44, after the status flip that legitimises the S1262 probes).
- [x] Removed-resource proof recorded - see Phase 04 done criteria: repo-wide grep plus a green
      `processStandardDebugResources`; every reference lived in `src/main`, none in a flavor source set.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Docs-only phase - revert commits.
