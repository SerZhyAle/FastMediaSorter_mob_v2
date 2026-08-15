# Phase 05 - Docs and Catalog Cleanup

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Final closure: regenerate class catalog, append the dev log entries for every phase, append a functionality log line, and advance the spec status to `BlockNeedUserTest`.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Project compiles for `standardDebug` (confirmed at end of Phase 04).
- [ ] All Phase 04 verification predicates passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated (auto) | - |
| `dev/CATALOG/app_v2.md` | Regenerated (auto) | - |
| `dev/CHANGELOG.md` | Appended | - |
| `dev/FUNCTIONALITY.log` | Appended | - |
| `PLAN/spec-catalog.jsonl` | Updated via CLI | - |

---

## Steps

### Step 05.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the catalog sync wrapper to scan the new files under `ui/settings/search/` and `di/SettingsSearchModule.kt`, then render the human-readable catalog:
> ```
> pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
> ```
> The new classes should appear in `dev/CATALOG/app_v2.jsonl`:
> - `SettingsSearchSource` (interface)
> - `SettingsSearchKeywordCollector` (interface)
> - `LayoutSettingsSearchSource`
> - `LocalizedKeywordCollector`
> - `SettingsSearchRegistry`
> - `SettingsSearchTabMapping`
> - `SettingsSearchLayoutCatalog`
> - `SettingsSearchFlavorFilter`
> - `SupportedSearchLocales`
> - `RawSettingsSearchEntry` + `EntryKind` + `TabAssignment`
> - `XmlAttributeReader`
> - `SettingsSearchModule`

**Verification:**

- `Grep` in `dev/CATALOG/app_v2.jsonl` - `SettingsSearchRegistry` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.jsonl` - `LayoutSettingsSearchSource` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.jsonl` - `LocalizedKeywordCollector` matches at least once.
- Old reference: `SettingsSearchRegistry.*ui/settings/SettingsSearchIndex\.kt` (the static object's previous location) returns zero hits — confirms the static object is gone and only the new class registry remains under `ui/settings/search/`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - DEVIATION: catalog scanner `dev/CATALOG/scripts/scan.ps1` was missing the `src/standard`, `src/lite`, `src/photos`, `src/legacy` source roots (per known catalog-scan-source-sets gap). Fixed `$srcRoots` to include all 4 — 1158 files now scanned (was 1154). 6 new `*SettingsSearchAvailabilityModule` entries indexed correctly; original 8 new types (Registry/Source/Collector/Availability/etc.) also visible.

---

### Step 05.2 - Append dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Append one dev log line per file changed in Phases 01–04 via `./scripts/add_to_dev_log.ps1`. Use one line per file (the script handles formatting and branch capture). Required lines:
> ```
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt" "S0284" "Add raw settings search entry data class"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchSource.kt" "S0284" "Add SettingsSearchSource interface"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt" "S0284" "Add layout-to-tab mapping"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchKeywordCollector.kt" "S0284" "Add keyword collector interface"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchLayoutCatalog.kt" "S0284" "Add layout catalog list"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt" "S0284" "Add XML attribute reader helper"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt" "S0284" "Add layout XML source for settings search"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SupportedSearchLocales.kt" "S0284" "Add supported locales constant"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LocalizedKeywordCollector.kt" "S0284" "Add multi-locale keyword collector"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt" "S0284" "Strip static registry, keep data class only"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt" "S0284" "Replace static object with Hilt-managed class"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchFlavorFilter.kt" "S0284" "Add BuildConfig flavor filter"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt" "S0284" "Add Hilt module for settings search"
> ./scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt" "S0284" "Inject SettingsSearchRegistry, drop static reference"
> ```

**Verification:**

- `Grep` in `dev/CHANGELOG.md` - lines containing `S0284` count ≥ 14.
- `Grep` in `dev/CHANGELOG.md` - `SettingsSearchRegistry` matches at least twice (the new class file + the activity rewire).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Per-step post-change calls during Phases 01-04 already wrote per-file entries (S0284 = 29 hits, SettingsSearchRegistry = 8 hits). Added 6 missing flavor-module entries explicitly. Verification: S0284 ≥14 ✓, SettingsSearchRegistry ≥2 ✓.

---

### Step 05.3 - Append functionality log line

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 05.2

**Prompt for developer:**

> Append one functionality log entry summarizing the user-visible behavior change:
> ```
> ./scripts/add_to_functionality_log.ps1 -Id S0284 -Op CHANGE -Description "Settings search auto-derives entries from layouts and EN/RU/UK strings (3D, VR, stereo, panel single-eye, ~30 other rows now findable)"
> ```

**Verification:**

- `Grep` in `dev/FUNCTIONALITY.log` - `S0284` matches at least once.
- `Grep` in `dev/FUNCTIONALITY.log` - `Settings search auto-derives` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Functionality log entry added (CHANGE op). Verification 2/2 PASS.

---

### Step 05.4 - Advance spec status to `BlockNeedUserTest`

**Files:** `PLAN/spec-catalog.jsonl` (via CLI), `PLAN/S0284_settings-search-auto-index.md` (status header)
**Depends on:** Step 05.3

**Prompt for developer:**

> Flip the spec to `BlockNeedUserTest` — the Timber tags from Phase 04.5 are the operator's logcat probe for hands-on verification.
> ```
> pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0284 -Status BlockNeedUserTest
> ```
> Patch the strategic file's `**Status:**` line from `Approved` → `BlockNeedUserTest` (or `Implemented` if that line was advanced earlier by `/spec-dev`).
>
> The owner is expected to run the test plan from `/spec-test-device` or manual verification: open Settings, hit the search button, type the queries from strategic §11 (3D, стерео, VR, 180, 360, SBS, OU, single eye, одним глазом, одне око). Each query must yield at least one entry. Confirm by reading the three `S0284:` logcat lines.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0284 -Format json` returns `"status":"BlockNeedUserTest"`.
- `Grep` in `PLAN/S0284_settings-search-auto-index.md` - line `**Status:** BlockNeedUserTest` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Journal S0284: In Progress → BlockNeedUserTest. Strategic spec file status header updated. 3 Timber.d("S0284:") tags in place from Phase 04.5. Verification 2/2 PASS.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Catalog regenerated and contains the new classes.
- [ ] Dev log has all 14 file entries for S0284.
- [ ] Functionality log has the S0284 CHANGE entry.
- [ ] Spec status is `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Once the owner verifies on device, `/spec-check S0284` flips status to `Verified` and removes the three `Timber.d("S0284:")` tags.

---

## Rollback Plan

Documentation-only phase. Revert by removing dev log lines and resetting spec status via `update.ps1 -Status Approved`.
