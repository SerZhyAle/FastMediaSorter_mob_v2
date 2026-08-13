# Phase 03 - docs-catalog-cleanup

**Strategic spec:** [`../S0449_nolegal-screen-gesture-accessibility-shortcut.md`](../S0449_nolegal-screen-gesture-accessibility-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Record the noLegal user-facing entry and synchronize catalog/dev-log.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | +1 |
| `docs/FEATURES_noLegal_RU.md` | Modified | +1 |
| `docs/FEATURES_noLegal_UK.md` | Modified | +1 |

> `docs/FEATURES_noLegal*.md` are gitignored noLegal docs - the published `docs/FEATURES*.md` trilogy is NOT touched (feature is noLegal-only).

---

## Steps

### Step 03.1 - Add the noLegal FEATURES entry (EN/RU/UK)

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** Phase 02

**Prompt for developer:**

> Add one trilingual line to the noLegal FEATURES docs: the Left-edge screen gestures settings now include a quick button to open accessibility settings so the screenshot mode can be re-enabled when Android turns the service off. Keep wording aligned with the user-visible strings from Phase 01. If a `FEATURES_noLegal*.md` locale file does not exist yet, create it from its sibling's structure.

**Verification:**

- `Grep` - the new entry mentions accessibility / screenshot re-enable in each of the three `FEATURES_noLegal*.md` files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS. Added §10 bullet + changelog entry on accessibility-settings shortcut to EN/RU/UK FEATURES_noLegal docs.

---

### Step 03.2 - Sync catalog and dev log

**Files:** (generated indexes / changelog)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the class catalog (the touched fragment is an existing class - regenerate to keep the index current). Ensure `dev/CHANGELOG.md` has an entry for every file modified across phases 01-03 via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `catalog_sync.ps1` exits 0.
- `Grep` - `dev/CHANGELOG.md` contains entries referencing `fragment_settings_destinations.xml` and `OperationsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` exit 0; CHANGELOG carries entries for both touched source files.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file across all phases.
- [ ] `scripts/catalog_sync.ps1 -Module app_v2` exits 0.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert doc edits - no code or user-facing surface affected by this phase.
