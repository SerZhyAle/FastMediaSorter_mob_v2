# Phase 01 - Foundation Types

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Introduce the domain types and tab-mapping data that the new auto-index pipeline consumes. No behavior change yet - existing `SettingsSearchRegistry` object stays intact and remains the active index source until Phase 04.

---

## Prerequisites

- [ ] All Pre-Implementation Blockers in INDEX are resolved (4 strategic §6 research items moved to `Resolved`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchSource.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchKeywordCollector.kt` | New | ≤ 30 |

---

## Steps

### Step 01.1 - Add `RawSettingsSearchEntry` data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a data class `RawSettingsSearchEntry` carrying unresolved attribute references discovered in a layout XML pass: `viewId: Int`, `titleResId: Int?`, `subtitleResId: Int?`, `hintResId: Int?`, `inlineTitle: String?`, `inlineSubtitle: String?`, `inlineHint: String?`, `layoutResId: Int`, `kind: EntryKind` (enum: `TOGGLE_ROW`, `SECTION_HEADER`, `BUTTON`, `TEXT_INPUT`, `SPINNER`). One of the resId/inline pairs must be non-null for the entry to be useful; do not enforce that constraint in the data class itself - the collector in Phase 03 filters empty entries. The kind enum is used later by the localization collector to decide which attributes to harvest. No methods, no logic, pure data.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt` exists.
- `Grep` - `data class RawSettingsSearchEntry` matches exactly once in that file.
- `Grep` - `enum class EntryKind` matches exactly once in that file.
- `Grep` - all five enum members (`TOGGLE_ROW`, `SECTION_HEADER`, `BUTTON`, `TEXT_INPUT`, `SPINNER`) present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/RawSettingsSearchEntry.kt (+33 LOC).

---

### Step 01.2 - Add `SettingsSearchSource` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create an interface `SettingsSearchSource` with one method: `fun collect(): List<RawSettingsSearchEntry>`. This is the seam Phase 02 implements (XML layout scanner). Keep the interface dependency-free - the implementor receives whatever it needs (Context, layout resIds list) via constructor injection in Phase 04.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchSource.kt` exists.
- `Grep` - `interface SettingsSearchSource` matches exactly once.
- `Grep` - `fun collect(): List<RawSettingsSearchEntry>` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchSource.kt (+12 LOC).

---

### Step 01.3 - Add `SettingsSearchTabMapping` data table

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create an object `SettingsSearchTabMapping` exposing a `Map<Int, TabAssignment>` where the key is a layout resource id (`R.layout.fragment_settings_general`, `R.layout.fragment_settings_playback`, `R.layout.fragment_settings_media_container`, `R.layout.fragment_settings_images`, `R.layout.fragment_settings_video`, `R.layout.fragment_settings_audio`, `R.layout.fragment_settings_documents`, `R.layout.fragment_settings_other`, `R.layout.fragment_settings_destinations`, `R.layout.fragment_settings_backup_restore`) and the value is a `TabAssignment` data class with `destination: SettingsSearchDestination` and `sectionId: String`. Mirror today's section ids exactly: `"general"`, `"playback"`, `"destinations"`, `"images"`, `"video"`, `"audio"`, `"documents"`, `"other"`. The `fragment_settings_media_container` layout maps to `("media", SettingsSearchDestination.MEDIA)` — but in practice container itself has no rows; the sub-fragment layouts (images/video/audio/documents/other) get the actual sub-section ids and `SettingsSearchDestination.MEDIA`. Both `fragment_settings_destinations` and `fragment_settings_backup_restore` map to `("destinations", SettingsSearchDestination.OPERATIONS)`. Expose a single function `assignmentFor(layoutResId: Int): TabAssignment?` returning the entry or `null` if unmapped.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt` exists.
- `Grep` - `object SettingsSearchTabMapping` matches exactly once.
- `Grep` - `data class TabAssignment` matches exactly once.
- `Grep` - `fun assignmentFor(layoutResId: Int): TabAssignment?` matches exactly once.
- `Grep` - all ten layout resId references (`R.layout.fragment_settings_general`, `R.layout.fragment_settings_playback`, `R.layout.fragment_settings_media_container`, `R.layout.fragment_settings_images`, `R.layout.fragment_settings_video`, `R.layout.fragment_settings_audio`, `R.layout.fragment_settings_documents`, `R.layout.fragment_settings_other`, `R.layout.fragment_settings_destinations`, `R.layout.fragment_settings_backup_restore`) present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt (+54 LOC).

---

### Step 01.4 - Add `SettingsSearchKeywordCollector` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchKeywordCollector.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create an interface `SettingsSearchKeywordCollector` with one method: `fun enrich(raw: RawSettingsSearchEntry): SettingsSearchIndex?`. Returns the fully-populated `SettingsSearchIndex` (existing data class in `SettingsSearchIndex.kt`) with title and multi-locale keyword pool, or `null` if the raw entry has no resolvable title in any locale. Phase 03 implements this interface against EN/RU/UK locales.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchKeywordCollector.kt` exists.
- `Grep` - `interface SettingsSearchKeywordCollector` matches exactly once.
- `Grep` - `fun enrich(raw: RawSettingsSearchEntry): SettingsSearchIndex?` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchKeywordCollector.kt (+13 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `RawSettingsSearchEntry` is the contract Phase 02 produces and Phase 03 consumes.
- `SettingsSearchTabMapping` is the single source of truth for layout-resId → tab/section mapping; Phase 02 uses it to attach metadata to each raw entry's neighborhood, but the actual sectioning happens at registry-merge time in Phase 04.
- `SettingsSearchIndex` data class in `SettingsSearchIndex.kt` is kept untouched in this phase - Phase 04 removes the static `SettingsSearchRegistry.entries` list and wires the new pipeline as its replacement.

---

## Rollback Plan

Revert phase commit - no consumer references the new files yet, no data migration, no user-facing surface changed.
