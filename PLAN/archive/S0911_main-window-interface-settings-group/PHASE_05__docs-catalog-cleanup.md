# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Regenerate the settings manifest/reference docs (CLAUDE.md Rule 22 - the settings' presence/position changed), add the new section header's description to the annotations file, and regenerate the class catalog.

---

## Prerequisites

- [ ] Phases 02, 03, 04 are ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-annotations.json` | Modified (new entry) | n/a |
| `docs/settings/settings-manifest.json` | Regenerated | n/a |
| `docs/SETTINGS_REFERENCE.md` / `_RU.md` / `_UK.md` | Regenerated | n/a |
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated (gitignored local index) | n/a |

---

## Steps

### Step 05.1 - Add the annotation entry for the new section header

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Phase 04 done (all rows relocated, no further view-id churn expected)

**Prompt for developer:**

> Add a new entry keyed `headerMainWindowInterface`, matching the style of the neighboring `headerInterface` entry (one-sentence EN/RU/UK description of what the section groups): e.g. EN "Settings that control which optional panels and menus appear on the main window." RU "Настройки, определяющие, какие необязательные панели и меню отображаются на главном окне." UK "Налаштування, що визначають, які необов'язкові панелі та меню відображаються на головному вікні." Insert it alphabetically near `headerInterface` per the file's existing key ordering.

**Verification:**

- `Grep` - `"headerMainWindowInterface"` present in `docs/settings/settings-annotations.json`.
- `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Entry added; check-settings-annotations.ps1 reported ORPHAN (expected - manifest not yet regenerated). Confirmed PASS (0 orphans) after Step 05.2's manifest regen. Files: docs/settings/settings-annotations.json (+5 LOC). Dev log recorded.

---

### Step 05.2 - Regenerate manifest, reference docs, and catalog

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Regenerate the settings manifest by re-running its export test in generate mode: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*SettingsManifestExportTest" "-Dsettings.manifest.generate=true"` (documented regen mode of the test itself, per CLAUDE.md §9's listed `.\gradlew.bat testStandardDebugUnitTest` usage). Then run `pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1` to refresh the three reference docs. Finally run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once. Close by running the composite gate `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` to confirm everything is in sync.

**Verification:**

- `scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - `headerMainWindowInterface` or `settings_category_main_window_interface` present in `docs/settings/settings-manifest.json`.
- `Grep` - the new group title text present in `docs/SETTINGS_REFERENCE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. `assert-settings-doc-sync.ps1`: catalog complete, manifest fresh, annotations covered (208 keys, 0 orphans), reference up to date, HOW_TO recipes in sync. Note: `processStandardDebugResources` succeeded in this unit-test task graph despite the unrelated S0774 `view_recording_indicator.xml` issue still blocking the full app-assembling resource-link (`fr`/`fc`) - the test-resource compile path does not hit that failure. Files: docs/settings/settings-manifest.json, docs/SETTINGS_REFERENCE.md/_RU.md/_UK.md/_noLegal.md, dev/CATALOG/app_v2.jsonl/.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and current.
- [x] `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` fresh (composite gate PASS, 2026-07-03).
- [x] Dev log entries added for this phase's changes via `.\scripts\add_to_dev_log.ps1`.
- [ ] Run `/spec-check S0911` next.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step is `/spec-check S0911`. No `docs/ALL_FEATURES.jsonl` entry - strategic §8 states no user-visible capability change (pure settings reorg).

---

## Rollback Plan

Low-risk: regenerated doc/catalog artifacts only; revert this phase's commit(s) if the regenerated content looks wrong, then re-run the same commands.
