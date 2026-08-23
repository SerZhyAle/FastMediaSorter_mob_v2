# Phase 04 - The choice the user could see

**Strategic spec:** [`../S1703_bugfix-paddleocr-postprocess-returns-empty.md`](../S1703_bugfix-paddleocr-postprocess-returns-empty.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

Remove the setting the user could choose, record the withdrawal where the inventory is kept, and regenerate
what those two changes invalidate.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - nothing ships or fetches the engine.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 60 |
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 40 |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 10 |
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |

> Rule 11 note: check `res/layout-land/fragment_settings_other.xml` before editing the portrait file - if the
> landscape variant exists it changes in the same step.

---

## Steps

### Step 04.1 - Remove the choice from the settings screen

**Files:** the fragment and its layout(s)

**Depends on:** - start of phase

**Prompt for developer:**

> Remove the engine selector and any string that exists only for it. A single remaining engine is not a
> choice, so nothing replaces the control - do not leave a disabled row explaining the absence. Check the
> landscape layout in the same edit, and check the settings search catalog for an entry pointing at the
> removed control.

**Why:**

Strategic §3 withdraws the engine, and a settings row that offers one option teaches the user that a choice
exists where none does - worse than an absent row, which they simply never look for.

**Verification:**

- `Grep` - the selector is gone from the fragment, from both layouts if two exist, and from the search
  catalog.
- `Grep` - the strings it used are gone from `values/strings.xml` and its locale siblings, unless another
  screen still uses them.
- `.\a.ps1 fr` and `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Removed both engine picker rows (rowOcrEngineType, rowPaddleOcrModel) from OtherMediaSettingsFragment and from both layout variants; dropped ocrEngineOptions/paddleOcrModelOptions/setupOcrEngineSpinners and the ocrEngineType parameter of updateOcrVisibility; removed six now-unreferenced string keys across all 13 locales via set-android-string.ps1; de-listed both row keys from SettingsSearchCapabilityGate and SettingsSearchDeviceFeatureGate and their tests. Verification: grep 0 hits for every removed symbol and string key, a.ps1 fr exit 0, a.ps1 fk exit 0.

---

### Step 04.2 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`

**Depends on:** Step 04.1

**Prompt for developer:**

> A setting disappeared, so regenerate the manifest and the reference and update the annotation file, exactly
> as CLAUDE.md Rule 22 requires. Do not hand-edit the generated files.

**Why:**

Rule 22 makes the regeneration mandatory for any change to a setting's presence, and the gate
`assert-settings-doc-sync.ps1` fails the closure otherwise.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exit 0.
- `Grep` - the manifest no longer names the engine setting.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Removed the two orphaned annotation entries (rowOcrEngineType, rowPaddleOcrModel), then ran reindex-settings.ps1: manifest regenerated from the live scan and SETTINGS_REFERENCE{,_RU,_UK,_noLegal}.md re-rendered (exit 2 = drift regenerated, its documented success-with-changes code). Verification: assert-settings-doc-sync.ps1 exit 0 - catalog complete, manifest fresh, annotations covered (282 keys, 0 orphans), reference up to date, 54 HOW_TO recipes in sync; grep 0 hits for either row key in the manifest, the reference and the annotations.

---

### Step 04.3 - Record the withdrawal in the inventory

**Files:** `docs/ALL_FEATURES.jsonl`

**Depends on:** Step 04.2

**Prompt for developer:**

> Set the record `ocr-translation.offline-ocr-engine-paddleocr` to `removed` through
> `scripts/all_features/add.ps1` rather than deleting the line, and check the second Paddle-related record
> the file carries - if it describes the same withdrawn capability it changes with it. Then run the catalog
> sync, because classes were deleted.

**Why:**

Strategic §3 states the record moves to `removed`; the inventory is a history of what shipped, so deleting
the line would erase the fact that it ever did - which is the question a future reader actually asks.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `Grep` - the record reads `"status":"removed"` and names S1703.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Both Paddle inventory records set to status 'removed' via all_features/add.ps1 with spec S1703 - the engine record and the on-demand download record that delivered it; neither line deleted, so the inventory keeps the fact that they shipped. Verification: all_features/validate.ps1 exit 0 (733 records), grep shows both records read status removed and name S1703, catalog_sync.ps1 -Module app_v2 exit 0 (2873 records re-rendered after the class deletions).
- 2026-08-17 - Phase-boundary audit (phases 02 and 04 together, protocol layers 1-4). Layer 1: the fragment lost one helper and one parameter, leaving updateOcrVisibility single-purpose; the capabilityLabel filter was reduced to a boolean expression proven equivalent to the removed when-block case by case (TRANSLATION always shown; non-TRANSLATION shown only in SOURCE mode). Layer 2: no coroutine, Flow or lifecycle path changed. Layer 3: both setOnRowClickListener registrations left with the views that carried them, so no dangling registration - the listener-symmetry gate reports new imbalance 0. Layer 4: Room untouched. DI: one @Provides @IntoSet removed from a flavor-only module together with its sole consumer, proven to still assemble by a full noLegal recompile plus the unit suite. No P0/P1. One P3 recorded and deliberately left: SearchableLanguagePickerDialog keeps @AndroidEntryPoint while no longer injecting anything - removing it is a DI-graph decision beyond this ticket and costs only a generated wrapper. Screenshot deferred (no device) - the bootstrap device probe reported no online device, and this phase's Done Criteria do not demand a shot; the placement decision is the owner's ruling quoted verbatim in strategic 3.3, and the change removes a control rather than placing one.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `.\a.ps1 fk`, `.\a.ps1 fkn`, `.\a.ps1 fr` exit 0.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Dev log entry added for every file in Files Touched.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Restore the settings row and the inventory status; the generated documents follow their sources.
