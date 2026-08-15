# Phase 03 - Docs, catalog and closure

**Strategic spec:** [`../S1422_launcher-settings-collapsible-groups.md`](../S1422_launcher-settings-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Land the settings documentation the regrouping obliges, record the capability change, and close the ticket
through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries the layout and fragment changes of Phase 02.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | ≤ 8 added |
| `docs/ALL_FEATURES.jsonl` | Appended via script | 1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 03.1 - Regenerate the settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`

**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate through `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1`, the wrapper that owns the
> `-Dsettings.manifest.generate=true` run and the reference render. `dialog_launcher_settings` is already
> registered in `SettingsDocScopeCatalog`, so nothing is added to either catalog - the rows are picked up as
> they were before. If the gate refuses an unannotated manifest key introduced by the new group headers, write
> the annotation first, then re-run.

**Why:**

CLAUDE.md Rule 22 and strategic §3.2 make the manifest, the reference and the annotations a required part of
any change to a setting's position, including one hosted in a dialog rather than a settings screen.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - `dialog_launcher_settings` still appears exactly once in `SettingsDocScopeCatalog.kt` and zero times in `SettingsSearchLayoutCatalog.kt`.

**Status:** `[x]` done - with one predicate corrected, see Step Log.

---

### Step 03.2 - Record the capability change

**Files:** `docs/ALL_FEATURES.jsonl`

**Depends on:** Step 03.1

**Prompt for developer:**

> Append one CHANGE record via `pwsh -NoProfile -File scripts/all_features/add.ps1` stating that the system
> launcher settings dialog now presents its rows as four collapsible groups whose expanded state persists, with
> `spec` set to `S1422`. English only. The dialog ships wherever the launcher does - `standard` and `noLegal`
> per `docs/FLAVOR_MATRIX.md` (`SUPPORT_LAUNCHER`); read the matrix rather than restating it from memory.

**Why:**

Strategic §3.3 records this as a user-visible change to an existing surface, and the feature inventory is the
developer record of every shipped capability.

**Verification:**

- `Grep` - `S1422` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Close through the facade

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`

**Depends on:** Step 03.2

**Prompt for developer:**

> Run the closure facade over the whole changed set, scoping the gates to it because the tree carries other
> tickets' work:
>
> ```powershell
> pwsh -NoProfile -File scripts/post-change.ps1 -Files "app_v2/src/main/res/layout/dialog_launcher_settings.xml,app_v2/src/main/res/layout-land/dialog_launcher_settings.xml,app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt" -ScopeToFile -Target "LauncherSettingsDialogFragment" -Description "S1422: group the launcher settings dialog into four collapsible sections" -ChangeType Mixed -Module app_v2
> ```
>
> Read the verdict: only a bare `post-change: PASS` is clean, and `-Files` writes the changelog row for the
> first file only, so add a dev-log line by hand for each remaining file.

**Why:**

CLAUDE.md section 12 requires mechanical closure through the facade, and the always-dirty tree needs the
scoped form so other tickets' in-flight work does not fail this ticket's gates.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory named and judged).
- `Grep` - `dev/CHANGELOG.md` carries a row for each of the three changed source files.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 03.1 done, after one corrected failure. First `reindex-settings.ps1` run exited 3: the scan lifted the four new group headers into the manifest as keys, and the annotations stage refuses an unannotated key - `MISSING annotations for 4 manifest key(s)`, exactly the case the step prompt anticipated. Wrote the four EN/RU/UK annotations, re-ran: exit 2 `DRIFT regenerated`, which is the "mirror refreshed, commit it" outcome, with `settings annotations: OK - 268 unique keys, 0 orphans` and `howto-settings-paths: OK`. Verification then PASS: `assert-settings-doc-sync.ps1` exit 0 with `catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync`, and the rendered `SETTINGS_REFERENCE.md` shows the four group rows under the launcher dialog. Predicate correction: `dialog_launcher_settings` greps **once** in `SettingsSearchLayoutCatalog.kt`, not zero - the hit is inside that file's KDoc, which names the dialog as deliberately NOT indexed. The registration count, which is what the predicate meant, is zero there and one in `SettingsDocScopeCatalog.kt`.
- 2026-08-07 - Step 03.2 done. `add.ps1` wrote `launcher.settings-dialog-collapsible-groups` with `flavors: standard,noLegal`, read off `docs/FLAVOR_MATRIX.md`'s `SUPPORT_LAUNCHER` row ([+] standard, [+] noLegal, [-]* everywhere else) rather than a sibling record. `validate.ps1` PASS, 668 records.
- 2026-08-07 - Step 03.3 done. `post-change.ps1 -ScopeToFile` over the three source files exited 0 with a bare `post-change: PASS (Mixed, 47508 ms)` - detekt `PASS [scoped]` (2 files with new findings project-wide, none among the changed files), rtl-layout-attrs, dialog-cancel-style, listener-symmetry and neuroslop all PASS, catalog re-scanned to 2574 records. Its own settings-doc-sync gate SKIPPED (its matcher looks for `fragment_settings_*`, so a dialog layout does not trip it) - that gate was run directly in Step 03.1 instead and passed. `dev/CHANGELOG.md` carries a row for each of the three files.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `Build Successful!`, APK `FastMediaSorter_standard_debug_v2.60.8071.632-DEBUG.apk`. This is the single build validating the code plus the `S1422:` probe tag inserted before it.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" - batched into the `close-and-log.ps1` call.
- [x] Phase-boundary audit skipped by rule - `Files Touched` is documentation and generated indexes only, no code surface.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit and re-run `reindex-settings.ps1` against the reverted layouts - documentation is
generated, so it follows the code back.
