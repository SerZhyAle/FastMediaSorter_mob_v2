# Phase 04 - Docs, Catalog & Changelog Cleanup

**Strategic spec:** [`../S0328_color-theme-setting.md`](../S0328_color-theme-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

---

## Objective

Finalize the feature: regenerate the class catalog, record the new user-facing capability in feature docs, and add the functionality-log entry.

---

## Prerequisites

- [ ] Phases 01–03 are ✅ Done.
- [ ] Project compiles on `standardDebug`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 04.1 - Update feature docs (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet to the interface/general settings area of each file describing the new capability: choose the app color theme (Auto / Light / Dark) independently of the device setting. Use `..` not `...`; keep `ё` in Russian. Use `/doc-update` conventions for the EN/RU/UK mirror.

**Verification:**

- `Grep` - a color-theme bullet is present in all three `docs/FEATURES*.md` files (e.g. `Grep -i "color theme"` / `"цветов"` / `"колірн"`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 1/1 PASS (bullet in EN/RU/UK §16). Dev log recorded.

---

### Step 04.2 - Regenerate catalog and set metadata for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the new classes via `set.ps1`: `ColorThemePrefs` (role: startup color-theme apply + SP mirror) and `GeneralSettingsColorThemeHelper` (role: settings color-theme selector handler). These are flavor-independent (live in `src/main`) - no `-NoFlavors` hint needed.

**Verification:**

- `Grep` - `ColorThemePrefs` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `GeneralSettingsColorThemeHelper` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS (both classes in jsonl; roles set via set.ps1).

---

### Step 04.3 - Functionality log + dev log

**Files:** (log files - not source)
**Depends on:** Step 04.2

**Prompt for developer:**

> Append a functionality-log entry: `.\scripts\add_to_functionality_log.ps1 -Id S0328 -Op ADD -Description "Color theme setting (Auto/Light/Dark) independent of device night-mode"`. Ensure a `dev/CHANGELOG.md` entry exists for every modified source file (via `add_to_dev_log.ps1`, already done per phase).

**Verification:**

- `Grep` - `S0328` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-02 - Verification 1/1 PASS (S0328 ADD entry in FUNCTIONALITY.log).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU` + `_UK` updated.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Functionality log entry added.
- [ ] Ready for `/spec-check S0328`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Doc/catalog/log only - revert the doc edits; catalog regenerates from source. No runtime impact.
