# Phase 06 - Docs, catalog and closure

**Strategic spec:** [`../S1415_launcher-taskbar-status-area-config.md`](../S1415_launcher-taskbar-status-area-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Land the documentation, catalog and inventory records the six indicators and the new permission oblige, and
close the ticket through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries every code change of Phases 01-05.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | ≤ 40 added |
| `docs/PRIVACY_POLICY.md` + `.ru.md` + `.uk.md` | Modified | ≤ 4 added each |
| `docs/ALL_FEATURES.jsonl` | Appended via script | ≤ 2 records |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 06.1 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the settings manifest and reference, then write an annotation for each of the six new rows.
> `dialog_launcher_settings` is already registered in `SettingsDocScopeCatalog`, so the rows are picked up
> without touching `SettingsSearchLayoutCatalog`.

**Why:**

CLAUDE.md Rule 22 makes the manifest, the reference and the annotations a required part of any change to a
setting, including one hosted in a dialog rather than a settings screen.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - the six `rowLauncherTray*` ids appear in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done - regenerated through `scripts/quality/reindex-settings.ps1`, which is the wrapper that
owns the `-Dsettings.manifest.generate=true` run; the six annotations had to be written by hand first,
because the gate refuses an unannotated manifest key.

---

### Step 06.2 - Record the new permission in the privacy policy

**Files:** `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `READ_PHONE_STATE` bullet to each of the three files, next to the existing optional-permission bullets,
> stating that it is optional, is used only to show SIM signal level in the launcher tray, that the reading never
> leaves the device, and that denying it simply hides both SIM indicators.

**Why:**

The permission list and the privacy page must state the same thing, and strategic §7 mitigates the refusal risk
by making it explicit what the permission buys and what happens when it is denied.

**Verification:**

- `Grep` - `READ_PHONE_STATE` matches once in each of the three privacy files.
- `Grep` - each bullet says the value stays on the device.

**Status:** `[x]` done

---

### Step 06.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` for `LauncherTrayIndicator`,
> `LauncherTrayBluetoothMonitor` and `LauncherTraySimSignalMonitor` with `set.ps1`. All three live in
> `src/launcherEnabled`, so declare `-NoFlavors "lite,photos,legacy"` on each - only `standard` and `noLegal`
> mount that source set.

**Why:**

Strategic §3.2 limits this work to the flavors that mount `launcherEnabled`, and the catalog hint is what makes
that limit searchable rather than implicit in the folder name.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "LauncherTray*"` lists all three with a
  non-empty `role`.

**Status:** `[x]` done - the pre-existing `LauncherTrayManager` still carries an empty `role`; it is not this
ticket's record to fill and no step claimed it.

---

### Step 06.4 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add the capability through `scripts/all_features/add.ps1` in English: a configurable launcher status area with
> per-indicator switches, and the outlined battery number with its warning colours. Take the flavor list from
> what actually mounts `launcherEnabled` rather than from memory.

**Why:**

Strategic §8 states the record goes into `docs/ALL_FEATURES.jsonl` after implementation, and CLAUDE.md §11 makes
that file the only per-spec inventory surface - the showcase docs belong to `/skill-release`.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1415` matches in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done - flavors taken from what actually mounts `launcherEnabled` (`standard`, `noLegal`),
read off `app_v2/build.gradle.kts`, not from memory.

---

### Step 06.5 - Close through the facade

**Files:** every file changed in Phases 01-06
**Depends on:** Step 06.1, Step 06.2, Step 06.3, Step 06.4

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with the whole changed set in `-Files`, `-ChangeType Mixed` and
> `-ScopeToFile`, then read the verdict line rather than assuming it. Follow with the document-registry
> validate/generate pair, since a registered document changed.

**Why:**

CLAUDE.md §12 routes mechanical closure through the facade so the dev log, the gates and the catalog sync cannot
drift apart, and `-ScopeToFile` is what lets the gates judge this ticket's files on an always-dirty tree.

**Verification:**

- `post-change` prints `PASS` or `PASS WITH ADVISORIES` and exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1` (or through the facade).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Step Log

- 2026-08-06 - Step 06.1 Verification 2/2 PASS. The first gate run failed at stage `manifest-fresh` and the regeneration then failed at stage `annotations`, which is the intended order: the manifest picks the rows up automatically because `dialog_launcher_settings` is already registered in `SettingsDocScopeCatalog`, but the six annotations are hand-written. After writing them, `assert-settings-doc-sync -Gate` exits 0 and the manifest carries all six ids.
- 2026-08-06 - Step 06.2 Verification 2/2 PASS. `READ_PHONE_STATE` recorded in EN/RU/UK privacy policies, each saying the reading never leaves the device and that a refusal only hides the SIM indicators.
- 2026-08-06 - Step 06.3 Verification 1/1 PASS. Roles and `-NoFlavors "lite,photos,legacy"` set on the three new classes. The flavor list was verified against `app_v2/build.gradle.kts`, not recalled: `launcherFlavors = setOf("standard", "noLegal")`.
- 2026-08-06 - Step 06.4 Verification 2/2 PASS. `launcher.configurable-tray-status-area` added; `validate.ps1` exits 0 over 651 records.
- 2026-08-06 - Probe tags: five `Timber.d("S1415: ..")` at the changed flow entries - composition applied, battery level rendered, Bluetooth state, SIM levels, permission request. Inserted as the last code edits **after** the status flip to `BlockNeedUserTest` (the ticket-log gate only tolerates probes for a ticket already in that status) and **before** the single `.\a.ps1 fk` that validated code and tags together, exit 0.
- 2026-08-06 - Step 06.5 Verification 2/2 PASS. `post-change.ps1 -ScopeToFile` verdict `PASS WITH ADVISORIES (1)`, exit 0. The one advisory was `document-registry` (exit 1); re-run standalone straight after, `validate.ps1` reports PASS over 27 records and `generate.ps1 -Check` reports the generated views current, so the advisory did not survive verification.
- 2026-08-06 - Phase-boundary audit: skipped by the protocol - the phase is documentation and generated indexes apart from the probe tags, which carry no logic.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate. The device test still owes strategic §11 criteria 3, 5
and 6: the blink at 9%, a single-SIM device showing only slot 1, and the blink stopping when the panel is
hidden. Emulators run with animator scale 0, so the blink cannot be judged there.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only; regenerate the manifest and the catalog after
any revert.
