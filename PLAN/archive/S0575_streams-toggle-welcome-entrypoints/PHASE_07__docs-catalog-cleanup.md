# Phase 07 - Docs, settings-doc sync, catalog regen, feature inventory

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05, 06
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Steps 07.1-07.4 done. 07.1: registered `fragment_settings_streams` in SettingsSearchLayoutCatalog, regenerated settings-manifest.json (SettingsManifestExportTest -Dgenerate), annotations + SETTINGS_REFERENCE*.md re-rendered; `assert-settings-doc-sync.ps1` exit 0 (catalog/manifest/annotations/reference/HOW_TO all OK). 07.2/07.3/07.4 via `close-and-log.ps1`: ALL_FEATURES record (ADD, area Streams, flavors standard/legacy/noLegal/vr), catalog scan+render (new classes indexed), 8 batched dev-log entries. Also fixed in-scope test break: DeliverableInventoryFilterTest constructor updated for the new `importStreamCatalogUseCase` param. Final `.\a.ps1 d` -> BUILD SUCCESSFUL with 4 debug tags; neuroslop gate exit 0.

---

## Objective

Close out the change: regenerate settings docs (Rule 22), record the shipped capability in the developer inventory, regenerate the class catalog, and ensure the dev changelog is complete.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/settings/settings-manifest.json` | Modified | n/a |
| `docs/SETTINGS_REFERENCE.md` (+ `_RU.md`, `_UK.md`) | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` + `app_v2.md` | Modified (regen) | n/a |

---

## Steps

### Step 07.1 - Regenerate the settings documentation (Rule 22)

**Files:** `docs/settings/settings-annotations.json`, `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> The new `enable_streams` setting requires the settings docs to be in sync. Add its annotation entry (EN/RU/UK) to `docs/settings/settings-annotations.json`, update `docs/settings/settings-manifest.json`, and re-render the reference via `pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1`. Follow the format of the `enable_translation` / `enable_ocr` entries.

**Verification:**

- `Grep` - `enable_streams` present in `docs/settings/settings-annotations.json` and `docs/settings/settings-manifest.json`.
- Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exits 0.
- Run `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` - exits 0 (en/ru/uk all present).

**Status:** `[x]` done

---

### Step 07.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record describing the delivered capability via `pwsh -NoProfile -File scripts/all_features/add.ps1` (Streams feature master toggle with per-device-profile default, onboarding entry, downloadable catalog, and main-menu gating). Do NOT edit `docs/FEATURES*.md` - the public showcase is populated only by `/skill-release`.

**Verification:**

- `Grep` - a new record mentioning Streams toggle/onboarding exists in `docs/ALL_FEATURES.jsonl`.
- Run `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exits 0.

**Status:** `[x]` done

---

### Step 07.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the local class catalog so the new classes (`StreamsSettingsStore`, `StreamsSettingsFragment`, `ExtensionItem.Catalog`) are indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`/`status` for the new classes via `dev/CATALOG/scripts/set.ps1` if the scan left them blank.

**Verification:**

- `Grep` - `StreamsSettingsStore` and `StreamsSettingsFragment` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 07.4 - Complete the dev changelog

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Step 07.1, Step 07.2, Step 07.3

**Prompt for developer:**

> Ensure every logical change in this spec has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1` (one entry per phase/logical change, not per file). Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0575` or "Streams toggle" entries present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [ ] `pwsh -NoProfile -File scripts/post-change.ps1` quality gates pass for the touched files (neuroslop, deprecated-pm-flags, settings-doc-sync).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Ready for `/spec-check S0575`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, the spec enters device verification (the `/spec-dev` closeout advances to `BlockNeedUserTest` with the device checklist; `/spec-check` later sets `Verified`).

---

## Rollback Plan

Docs/catalog regen only - re-run the generators against the prior source state to revert.
