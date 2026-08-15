# Phase 04 — Multilingual Search

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Extend `SettingsSearchIndex` to carry EN/RU/UK alias lists, update `SettingsSearchRegistry` with localized aliases for all entries, and update the `search()` function to match against all locale sets regardless of the active UI locale.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `docs/ia-model.md` § Multilingual Search Contract is present.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` | Modified | ≤ 800 |

> File is currently 411 lines. After adding RU/UK alias lists for 32+ registry entries it is projected to exceed 500 lines — **create a timestamped backup in `temp/` before editing** (see Step 4.1).

> `SettingsSearchAdapter.kt` is display-only and renders `item.keywords` as the description string; it does not need changes because the adapter is not the search engine — the `search()` function in `SettingsSearchRegistry` is the matching layer.

> No layout XML changes in this phase → no landscape parity check needed.

---

## Steps

### Step 4.1 — Backup SettingsSearchIndex.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `SettingsSearchIndex.kt` in `temp/` before any edits:
> ```
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt" `
>   "temp/SettingsSearchIndex_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt"
> ```

**Verification:**

- `Glob` — `temp/SettingsSearchIndex_backup_*.kt` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Backup: temp/SettingsSearchIndex_backup_20260508_165654.kt. Dev log recorded.

---

### Step 4.2 — Add `localizedKeywords` field to `SettingsSearchIndex` data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** Step 4.1

**Prompt for developer:**

> In `SettingsSearchIndex.kt`, extend the `SettingsSearchIndex` data class with a new field:
> ```kotlin
> val localizedKeywords: Map<String, List<String>> = emptyMap()
> ```
> The map key is a BCP-47 language tag (`"en"`, `"ru"`, `"uk"`). The existing `keywords` field remains as the English fallback — it is not removed. The new field defaults to `emptyMap()` so all existing entry construction sites compile without changes. Add `Timber.d("S0119: multilingual search alias matching active")` in the `search()` function body (top of the function, before the early-return check).

**Verification:**

- `Grep` — `val localizedKeywords: Map<String, List<String>>` matches exactly once in `SettingsSearchIndex.kt`.
- `Grep` — `Timber.d("S0119:` matches in `SettingsSearchIndex.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `SettingsSearchIndex.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Added localizedKeywords field + Timber.d tag. Dev log recorded.

---

### Step 4.3 — Update `SettingsSearchRegistry.search()` to match against locale aliases

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** Step 4.2

**Prompt for developer:**

> Update `SettingsSearchRegistry.search(query: String)` to also search through all values in `localizedKeywords` for each entry. The updated filter predicate must match if: `title` contains the query, OR any item in `keywords` contains the query, OR any keyword in any `localizedKeywords` value list contains the query. The active UI locale does not gate which alias set is searched — all three locale sets are always searched. Keep the existing `normalizedQuery` lowercasing logic.

**Verification:**

- `Grep` — `localizedKeywords` mentioned at least twice in `SettingsSearchIndex.kt` (declaration + search usage).
- `Grep` — `localizedKeywords.values.flatten()` or equivalent flatten / iteration pattern matches in the `search()` function body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. search() updated to match all localizedKeywords locale sets via flatten(). Dev log recorded.

---

### Step 4.4 — Populate RU and UK aliases for all registry entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** Step 4.3

**Prompt for developer:**

> For every entry in `SettingsSearchRegistry.entries`, add a `localizedKeywords` argument with Russian (`"ru"`) and Ukrainian (`"uk"`) alias lists. Each alias list must contain at minimum 2–4 keywords covering: the setting's display label transliterated or translated into the target language, common synonyms a native speaker would type. Use the existing string resource names (e.g., `R.string.settings_tab_general` → "Основные") as a guide for standard section names. Below are the minimum required alias entries for validation; all other entries must also be populated:
>
> - `general.language` → ru: `["язык", "язык интерфейса", "локаль"]`, uk: `["мова", "мова інтерфейсу", "локаль"]`
> - `general.clear_cache` → ru: `["очистить кэш", "кэш", "очистка"]`, uk: `["очистити кеш", "кеш", "очищення"]`
> - `media.images_support` → ru: `["изображения", "фото", "картинки"]`, uk: `["зображення", "фото", "картинки"]`
> - `playback.sort_mode` → ru: `["сортировка", "порядок", "имя", "дата"]`, uk: `["сортування", "порядок", "ім'я", "дата"]`
> - `operations.safe_mode` → ru: `["безопасный режим", "подтверждение"]`, uk: `["безпечний режим", "підтвердження"]`

**Verification:**

- `Grep` — `"ru"` appears in `SettingsSearchIndex.kt` with a count ≥ 30 (one per registry entry minimum).
- `Grep` — `"uk"` appears in `SettingsSearchIndex.kt` with a count ≥ 30.
- `Grep` — `язык` mentioned in `SettingsSearchIndex.kt` (spot-check for `general.language` RU alias).
- `Grep` — `мова` mentioned in `SettingsSearchIndex.kt` (spot-check for `general.language` UK alias).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. RU/UK aliases populated for all 45 entries (46 occurrences each of "ru"/"uk" keys, ≥30 required). Spot-checks: язык/мова present. Dev log recorded.

---

### Step 4.5 — Verify no `Log.d` calls and build passes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** Step 4.4

**Prompt for developer:**

> Run `/build` to confirm the project compiles. Verify no `Log.d(` calls were introduced. Verify the `Timber.d("S0119:` debug tag is present in `search()`.

**Verification:**

- `Grep` — `Log\.d\(` returns zero hits in `SettingsSearchIndex.kt`.
- `Grep` — `Timber.d("S0119:` matches exactly once in `SettingsSearchIndex.kt`.
- Build succeeds (run `/build` — do not invoke Gradle directly).

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Log.d=0, Timber.d tag present, BUILD SUCCESSFUL (1m24s). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [x] Project compiles — `/build` returns success.
- [x] `SettingsSearchIndex` data class has `localizedKeywords: Map<String, List<String>>` field.
- [x] `SettingsSearchRegistry.search()` matches against all locale alias sets.
- [x] All 32+ registry entries have non-empty `"ru"` and `"uk"` alias lists.
- [x] §6 blockers §6.6, §6.11 marked `[x]` in INDEX.md.
- [x] `Grep` for `TODO(phase-04)` returns zero hits in all files touched.
- [x] Dev log entry added for `SettingsSearchIndex.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- The `Timber.d("S0119:` tag in `search()` is the verification signal for the spec. Remove it when transitioning to `Verified`.
- The `localizedKeywords` field is the extension point for future search corpus entries.
- Phase 05 (docs-catalog-cleanup) completes the spec — no further code changes after this phase.

---

## Rollback Plan

Revert phase commit(s). The `localizedKeywords` field defaults to `emptyMap()`, so reverting only removes the alias data — no storage migration needed. Search behavior degrades gracefully to English-only matching, which was the prior state.
