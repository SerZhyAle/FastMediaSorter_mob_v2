# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Close the first wave: refresh the class catalog, record the public-facing feature in `docs/FEATURES*`, append the functionality-log line, and set roles/status for the new classes.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | - |
| `docs/FEATURES.md` | Modified | +1 bullet |
| `docs/FEATURES_RU.md` | Modified | +1 bullet |
| `docs/FEATURES_UK.md` | Modified | +1 bullet |

> `dev/CATALOG/*.jsonl` and `*.md` are gitignored local indexes - regenerate, do not commit.

---

## Steps

### Step 05.1 - Regenerate catalog + set roles for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan. Then set role + status for the three new classes via `set.ps1`: `HomeWidgetEntry` (role: widget catalog model), `HomeWidgetCatalog` (role: widget availability registry), `HomeWidgetPinner` (role: in-app widget pin helper), `GeneralSettingsWidgetHelper` (role: settings widget-picker manager).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "HomeWidgetCatalog"` returns the class with a non-empty role.
- Same for `HomeWidgetPinner`, `HomeWidgetEntry`, `GeneralSettingsWidgetHelper`.

**Status:** `[x]` done

---

### Step 05.2 - Update FEATURES (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - independent

**Prompt for developer:**

> Per strategic §8, add one concise bullet to the Smart Widgets area in all three locale files (use `/doc-update` mirroring): compact `1x1` action widgets now render as launcher-style icons, Camera OCR launches from a `1x1` widget, and Settings offers "Add widget to home screen.." to pin an existing widget from inside the app. Include the manual re-add note from Phase 02 in the user-facing help/copy: an already-placed Camera-OCR keeps its old size until removed and re-added. Do NOT list the five spun-out new widgets here (they ship under their own sub-specs).

**Verification:**

- `Grep -n "icon"` near the Smart Widgets section in `docs/FEATURES.md` - expected: new bullet present | actual: <fill in>.
- The same bullet exists in `_RU.md` and `_UK.md` (parity).

**Status:** `[x]` done

---

### Step 05.3 - Functionality log

**Files:** `dev/FUNCTIONALITY.log` (via script)
**Depends on:** Phase 04 done

**Prompt for developer:**

> Append one line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0348 -Op CHANGE -Description "Home widgets: icon-style 1x1 surfaces, compact Camera-OCR, in-app 'Add widget to home screen' picker with pin + fallback"`.

**Verification:**

- `Grep -n "S0348"` in `dev/FUNCTIONALITY.log` - expected: the CHANGE line present | actual: <fill in>.

**Status:** `[x]` done

---

### Step 05.4 - Dev changelog sweep

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Phases 01-04

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every modified/created file across phases 01-04 (each phase already adds its own via `.\scripts\add_to_dev_log.ps1`). Add any missing entries.

**Verification:**

- `Grep -n "widget"` in `dev/CHANGELOG.md` - expected: entries covering layouts, registry, pinner, settings helper, manifests | actual: <fill in>.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1618 records); 4 new classes carry roles + status=new.
- [x] `docs/FEATURES.md` + `_RU` + `_UK` updated with parity (icon-style home widgets bullet in §15).
- [x] `dev/FUNCTIONALITY.log` has the S0348 CHANGE line.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, run `/spec-check S0348` against first-wave criteria 1-12. Criteria 13-17 belong to the spun-out sub-specs and must not block S0348's verification.

---

## Rollback Plan

Revert FEATURES + functionality-log edits. Catalog is a regenerated local index - no rollback needed.
