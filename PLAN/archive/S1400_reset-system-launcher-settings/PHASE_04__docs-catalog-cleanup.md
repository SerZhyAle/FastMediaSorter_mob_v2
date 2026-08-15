# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Bring the generated indexes back in step with the change: class catalog, settings documentation, icon inventory and the capability inventory.

---

## Prerequisites

- [ ] Phases 01, 02 and 03 are ✅ Done.
- [ ] `/build` on `standard debug` passed after Phase 03.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/settings/settings-manifest.json` | Regenerated | n/a |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | n/a |
| `docs/icons/icon-inventory.json` | Regenerated if the gate demands | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CHANGELOG.md` | Appended via script | n/a |

> Every file above is a render target - regenerate it, never hand-edit it.

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set the role and status of the new `ResetLauncherToDefaultsUseCase` entry with `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1`.

**Why:**

Strategic §5.1 introduces a new domain operation, and the catalog is the lookup every later ticket uses before grepping, so a use case missing from it is a use case the next ticket re-implements.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ResetLauncherToDefaults*"` returns exactly one record.
- That record carries a non-empty `role`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2\2 PASS. `catalog_sync.ps1 -Module app_v2` had already run inside each phase's `post-change.ps1`; 2463 records rendered. `set.ps1` gave a role and `status=new` to both new classes - `ResetLauncherToDefaultsUseCase` and the `LauncherSettingsViewModel` the step 03.4 amendment introduced.

---

### Step 04.2 - Re-sync the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. If it fails, regenerate the settings manifest and reference with the generator the failure names and update the annotation entry for the launcher settings dialog, then re-run the gate until it exits 0.

**Why:**

Strategic §3.2 records that the launcher settings dialog is a registered documented-settings surface, so CLAUDE.md Rule 22 makes any change to the controls it hosts a documentation change as well.

**Verification:**

- Exit code of `assert-settings-doc-sync.ps1` is 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 1\1 PASS, exit 0, no regeneration needed. 25 layouts with settings rows all classified, 258 annotation keys in en/ru/uk parity, reference up to date. The reset control is an action button, not a settings row, so it adds no manifest entry - the gate confirms that rather than assuming it.

---

### Step 04.3 - Re-sync the icon inventory

**Files:** `docs/icons/icon-inventory.json`, `docs/ICON_LEGEND*.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -Gate`. If it reports drift caused by `ic_restore_defaults`, regenerate with `-RegenerateInventory` and re-render the legend, then re-run the gate until it exits 0.

**Why:**

Strategic §6 item 2 adds a new drawable, and the icon inventory gate is what keeps the generated icon documentation from silently diverging from the icons the app actually ships.

**Verification:**

- Exit code of `assert-icon-inventory-sync.ps1 -Gate` is 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 1\1 PASS, exit 0, no regeneration needed. 84 vector svgs present, no orphans, legend fresh, locales in parity. `ic_restore_defaults` is referenced only from a dialog layout, so it enters none of the app registries the inventory is generated from. The inventory-vs-source freshness check stays CI/opt-in as designed.

---

### Step 04.4 - Record the capability and close the change

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 04.2, Step 04.3

**Prompt for developer:**

> Add one record to `docs/ALL_FEATURES.jsonl` with `pwsh -NoProfile -File scripts/all_features/add.ps1`, in English, naming the capability from strategic §8 and citing `S1400` in its `spec` field. Then close the whole change through `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file this ticket touched>" -ScopeToFile -Target "S1400" -Description "Reset the system launcher to its as-installed state" -ChangeType Mixed -Module app_v2`.

**Why:**

Strategic §8 states this ticket delivers a capability a user would perceive as new, and `docs/ALL_FEATURES.jsonl` is the inventory the release pipeline diffs to build the public showcase, so an unrecorded capability never reaches the release notes.

**Verification:**

- `Grep` - `S1400` matches at least once in `docs/ALL_FEATURES.jsonl`.
- Exit code of `scripts/all_features/validate.ps1` is 0.
- `post-change.ps1` prints `post-change: PASS` and exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. Record `launcher.reset-launcher-settings-to-as-installed-state` added for flavors `standard,noLegal`, spec `S1400`; `all_features/validate.ps1` PASS, 645 records. First closure came back `PASS WITH ADVISORIES (1)`: the document-registry gate asked whether `docs/ALL_FEATURES.schema.json` needed the same edit. It does not - the record uses only existing fields, which the schema check confirms - so the run was repeated with `-RegistryAck "feature-inventory"` and closed `post-change: PASS`. Each phase closed through its own `post-change.ps1`, so the whole-ticket file set is covered across four runs rather than one.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - no code changed in this phase; the last build of record is Phase 03's `.\a.ps1 dq`, BUILD SUCCESSFUL in 2m 51s.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1`.
- [x] Phase-boundary audit skipped - `Files Touched` is doc and generated-index only, which the protocol exempts.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert phase commit(s) and re-run each generator against the reverted tree - every file this phase writes is regenerated, never authored.
