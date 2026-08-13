# Phase 07 - Docs, Catalog, And Cleanup

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 06
**Blocks:** Final phase - see INDEX.md Completion Gate
**Steps done:** 2 / 3
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Sync the public docs, catalog, and audit trail around the revised settings rollout, then close S0125 only if the final audit returns green.

---

## Prerequisites

- [x] Phase 06 is ✅ Done.
- [x] If the revised host became public, the owner-approved feature wording is ready for EN, RU, and UK mirrors.
- [ ] Manual device checks for touch, mouse, keyboard, and D-pad are recorded before `/spec-check S0125` runs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 260 |
| `docs/FEATURES_RU.md` | Modified | ≤ 260 |
| `docs/FEATURES_UK.md` | Modified | ≤ 260 |
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |
| `PLAN/S0125_settings-activity-revision.md` | Modified | ≤ 900 |
| `PLAN/S0125_settings-activity-revision/INDEX.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 07.1 - Update the public feature inventory for revised settings

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet to the public feature inventory that describes the revised settings window as a four-tab surface with global search and dedicated management entry points. Keep the EN, RU, and UK mirrors semantically aligned and apply `docs/COMMUNICATION_POLICY.md` §2 and §6 to the wording.

**Verification:**

- `Grep` - `4 tabs with global search` present in `docs/FEATURES.md`.
- `Grep` - `4 вкладки с глобальным поиском` present in `docs/FEATURES_RU.md`.
- `Grep` - `4 вкладки з глобальним пошуком` present in `docs/FEATURES_UK.md`.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`. Evidence: `4 tabs with global search` present in EN, `4 вкладки с глобальным поиском` present in RU, `4 вкладки з глобальним пошуком` present in UK, wording kept semantically aligned around global search and dedicated management entry points, dev log recorded for all three mirrors.

---

### Step 07.2 - Refresh catalog and changelog evidence

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run the app catalog sync after the revised settings changes, then make sure the changelog and functionality log capture the public rollout. Do not hand-edit generated catalog files beyond the approved catalog workflow.

**Verification:**

- `Glob` - `dev/CATALOG/app_v2.jsonl` exists.
- `Grep` - `ui/settings/revised/RevisedSettingsActivity.kt` present in `dev/CATALOG/app_v2.md`.
- `Grep` - `S0125` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`. Evidence: catalog sync already regenerated both app_v2 catalog artefacts after the revised settings rollout, `ui/settings/revised/RevisedSettingsActivity.kt` present in `dev/CATALOG/app_v2.md`, `S0125` present in `dev/CHANGELOG.md`, dev log recorded for both catalog files, `./scripts/add_to_functionality_log.ps1 -Id S0125 -Op CHANGE` recorded the public revised-settings rollout.

---

### Step 07.3 - Run the final spec audit and close S0125 only on green

**Files:** `PLAN/S0125_settings-activity-revision.md`, `PLAN/S0125_settings-activity-revision/INDEX.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `/spec-check S0125`, apply any mechanical follow-up through the normal workflow, and only close the tactical plan when the strategic spec is updated to `Verified`. Keep the tactical index in sync with the completed phase count and leave the ticket open if the audit reports Partial or Broken.

**Verification:**

- `Grep` - `**Outcome:** Verified` present in `PLAN/S0125_settings-activity-revision.md`.
- `Grep` - `**Status:** Verified` present in `PLAN/S0125_settings-activity-revision.md`.
- `Grep` - `**Status:** Done` present in `PLAN/S0125_settings-activity-revision/INDEX.md`.

**Status:** `[ ]` not done

**Blocked by:** Manual device checks for touch, mouse, keyboard, and D-pad are not yet recorded, so `/spec-check S0125` has not been run.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] No remaining Phase 07 TODO markers are present outside this tactical checklist.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert final phase commit(s), rerun catalog sync after restore, and keep S0125 in Tactical or Partial until `/spec-check` returns green again.