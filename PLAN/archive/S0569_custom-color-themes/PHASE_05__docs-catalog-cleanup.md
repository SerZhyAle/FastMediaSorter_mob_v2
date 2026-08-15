# Phase 05 - Docs and Catalog Cleanup

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04 (all feature phases complete)
**Blocks:** none - final cleanup phase
**Steps done:** 2 / 2
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Record the delivered capability in the developer inventory and regenerate the Kotlin class catalog so `ColorThemePrefs.applyThemeOverlay` and the touched helpers are indexed.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | - |
| `dev/CATALOG/app_v2.md` | Regenerated (gitignored) | - |

> Do NOT edit `docs/FEATURES.md` / `_RU.md` / `_UK.md` here. Per CLAUDE.md §11 the public showcase is populated only by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never per-spec.

---

## Steps

### Step 05.1 - Record the capability in `ALL_FEATURES.jsonl`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one inventory record with `scripts/all_features/add.ps1` (EN-only, never hand-edit the JSONL). Use area `Settings & Navigation` and the all-flavor set, since the feature ships in every flavor:
>
> `pwsh -NoProfile -File scripts/all_features/add.ps1 -Id S0569 -Area "Settings & Navigation" -Name "Custom color themes" -Description "Six accent color themes (dark/light green, blue, red) selectable from Settings > General > Color theme, applied app-wide and persisted across restart." -Flavors "standard,lite,photos,legacy" -Spec S0569`
>
> Then validate: `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S0569` and `Custom color themes` in `docs/ALL_FEATURES.jsonl`.
- `validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS via close-and-log: record `settings-navigation.custom-color-themes` ("Custom color themes", flavors standard/lite/photos/legacy) added; validate PASS (366 records).

---

### Step 05.2 - Regenerate the Kotlin class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to re-scan and re-render the catalog so the changed public surface (`ColorThemePrefs.applyThemeOverlay`, the extended helper) is indexed. These files are gitignored local indexes - regenerate, do not commit.

**Verification:**

- `Grep` - `applyThemeOverlay` in `dev/CATALOG/app_v2.jsonl` or `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 1/1 PASS via close-and-log catalog scan+render: `applyThemeOverlay` indexed in app_v2.jsonl and app_v2.md.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `dev/CHANGELOG.md` has an entry for the S0569 change set via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -NoProfile -File scripts/post-change.ps1` quality gates pass for the touched files (neuroslop / settings-doc-sync if applicable).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action is `/spec-dev` execution, then on-device verification (theme switch + restart + persistence across all nine options), then `/spec-check S0569`.

---

## Rollback Plan

Revert the `ALL_FEATURES.jsonl` line (remove the S0569 record); the catalog regenerates from source on the next sync.
