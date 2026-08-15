# Phase 05 - Docs, catalog & FEATURES cleanup

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Record the new capability in the developer inventory and public FEATURES, regenerate the class catalog, and complete dev-log journaling.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 05.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one JSONL record via `scripts/all_features/add.ps1` describing: the app-launch panel can now hold OS shortcuts and the app's own features/resources (calculator, mini-game, photo-OCR-translate, streams, a specific resource), and ships pre-populated by default. EN-only. Set the flavor list to the flavors where the panel and its routes apply.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Added `screen-capture.panel-internal-routes` (area Screen Capture, flavors standard,noLegal, spec S0663) via `all_features/add.ps1`; validate PASS (403 records).

---

### Step 05.2 - Add the FEATURES sentence (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the single user-facing sentence from strategic §8 to all three FEATURES files (translate for RU/UK). Do not duplicate an existing entry.

**Verification:**

- `Grep` - the new sentence present in each of the three files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. Added the §8 panel sentence as a `[Standard]` bullet after the Screen-capture bullet in FEATURES.md / _RU / _UK (1 occurrence each).

---

### Step 05.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then fill `role`+`status` for the new classes via `set.ps1`.

**Verification:**

- `Grep` - `InternalRouteCatalog`, `OsShortcutCatalog`, `AppLaunchPanelRouteTarget` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. `catalog_sync.ps1 -Module app_v2` regenerated (2014 records); set role + status=new for all 8 new classes via set.ps1.

---

### Step 05.4 - Final dev-log batch and status transition

**Files:** (journaling only)
**Depends on:** Step 05.3

**Prompt for developer:**

> Add the remaining dev-log entries for the docs/catalog files via `scripts/add_to_dev_log.ps1`. Then move the ticket to `BlockNeedUserTest` via `update.ps1 -Id S0663 -Status BlockNeedUserTest -StatusNote '<device steps>'`, with a note covering: open the panel, add a tile by each of the three paths, launch a feature / OS target / resource, confirm a disabled feature routes to its setting, and confirm the first-run default seed shows our features first.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0663 -Format json` shows `BlockNeedUserTest`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. `close-and-log.ps1` -> BlockNeedUserTest with device StatusNote + 3 dev-log entries (SkipFuncLog: ALL_FEATURES added in 05.1; SkipCatalogSync: synced in 05.3 to preserve roles). select.ps1 shows BlockNeedUserTest; ticket-log gate clean (probe now valid).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/ALL_FEATURES.jsonl` validates (403 records).
- [x] FEATURES trilingual sentence present.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After device verification, `/spec-check S0663` advances the strategic spec to `Verified` and removes the `S0663:` debug tag.

---

## Rollback Plan

Docs/catalog only - revert the doc edits; no code or data impact.
