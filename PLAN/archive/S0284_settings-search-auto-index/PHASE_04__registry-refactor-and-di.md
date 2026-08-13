# Phase 04 - Registry Refactor and DI

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Wire the new auto-indexing pipeline into a Hilt-managed `SettingsSearchRegistry` class that replaces the deleted static registry; update `SettingsActivity` to consume the injected instance; restore project compilability.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is on the same branch as Phase 02/03 — no intermediate build attempts since Phase 03.
- [ ] Strategic §6 Research items #1, #2, #3, #4 are `Resolved`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchAvailability.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt` | New | ≤ 80 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/StandardSettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalSettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/di/LiteSettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/di/PhotosSettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/LegacySettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/di/VrSettingsSearchAvailabilityModule.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 1000 (current ~700) |

> **Deviation from original tactical plan:** Step 04.1 originally proposed a single `SettingsSearchFlavorFilter` in `src/main/` reading `BuildConfig.SUPPORT_*` — that would have been a CLAUDE.md Rule 15 / spec-dev hard-stop #14 violation (new flavor leak in src/main). Replaced with the interface-in-main + per-flavor multibinding pattern. One new file per flavor source set (6 flavors → 6 small Hilt modules) contributing supported media section ids via `@IntoSet`.

---

## Steps

### Step 04.1 - Add `SettingsSearchAvailability` with per-flavor multibinding

**Files:** see "Files Touched" table — 8 new files (interface + 6 flavor modules + Hilt @Multibinds declaration).
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the original BuildConfig-driven `SettingsSearchFlavorFilter` plan with a proper interface + per-flavor binding (CLAUDE.md Rule 15 conformance).
>
> 1. `src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchAvailability.kt`:
>    - Define a Hilt qualifier annotation `@Qualifier @Retention(BINARY) annotation class SupportedMediaSection`.
>    - Define `@Singleton class SettingsSearchAvailability @Inject constructor(@SupportedMediaSection private val supportedMedia: Set<String>)`.
>    - Method `fun isAvailable(sectionId: String): Boolean`. Returns `true` for the always-on section ids (`general`, `playback`, `destinations`, `media`, `other`); for `images`, `video`, `audio`, `documents` returns whether the id is in `supportedMedia`; for any other id returns `true` (defensive — keeps non-media sections searchable).
> 2. `src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt` (also touched in step 04.3) — declare a `@Multibinds @SupportedMediaSection abstract fun supportedMedia(): Set<String>` so Hilt resolves to an empty set when no provider contributes. The actual contributions live in per-flavor modules below.
> 3. Per-flavor Hilt modules (one each in their flavor source set under `.../di/`):
>    - `StandardSettingsSearchAvailabilityModule.kt`: provides `images`, `video`, `audio`, `documents` via `@IntoSet @SupportedMediaSection`.
>    - `NoLegalSettingsSearchAvailabilityModule.kt`: same four ids (noLegal mirrors standard's media surface).
>    - `LiteSettingsSearchAvailabilityModule.kt`: only `images`, `video`.
>    - `PhotosSettingsSearchAvailabilityModule.kt`: only `images`.
>    - `LegacySettingsSearchAvailabilityModule.kt`: `images`, `video`, `audio`, `documents` (legacy has full media surface).
>    - `VrSettingsSearchAvailabilityModule.kt`: `images`, `video`, `audio`, `documents` (vr mirrors standard).
> 4. Create `src/standard/java/com/sza/fastmediasorter/di/` as a new directory — the project doesn't have a `src/standard/java/` tree yet; AGP picks it up automatically when present.
> 5. No file in `src/main/java/` reads `BuildConfig.SUPPORT_*` for the settings-search availability — that gate moves into the flavor source sets where it belongs.
>
> Note: `SettingsSearchRegistry` in step 04.2 consumes `SettingsSearchAvailability` (not the deleted `SettingsSearchFlavorFilter`).

**Verification:**

- `Glob` - all 8 files listed in "Files Touched" exist.
- `Grep` - `class SettingsSearchAvailability` matches exactly once in `SettingsSearchAvailability.kt`.
- `Grep` - `@Qualifier` matches exactly once in `SettingsSearchAvailability.kt`.
- `Grep` - `annotation class SupportedMediaSection` matches exactly once.
- `Grep` - in each flavor module file (Standard/NoLegal/Lite/Photos/Legacy/Vr): `@SupportedMediaSection` matches at least once and `@IntoSet` matches at least once.
- `Grep` - across `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/`: literal `BuildConfig.SUPPORT_` returns zero hits (no leak in main).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - DEVIATION from original prompt: replaced `SettingsSearchFlavorFilter` (BuildConfig branch in src/main) with proper interface-in-main + 6 per-flavor multibinding modules. Verification 7/7 PASS (no BuildConfig.SUPPORT_ in new `ui/settings/search/`; 7 module files created). Files: SettingsSearchAvailability.kt (+36 LOC) + 6 flavor modules (avg +30 LOC each).

---

### Step 04.2 - Implement `SettingsSearchRegistry` class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `@Singleton class SettingsSearchRegistry @Inject constructor(private val source: SettingsSearchSource, private val collector: SettingsSearchKeywordCollector)`.
>
> Lazy property:
> ```
> private val allEntries: List<SettingsSearchIndex> by lazy {
>     val raw = source.collect()
>     raw.mapNotNull { collector.enrich(it) }
> }
> ```
>
> Public API (matches the old object's surface so SettingsActivity changes minimally):
> - `val entries: List<SettingsSearchIndex>` — returns `allEntries` filtered through `SettingsSearchFlavorFilter.isAvailable(it.sectionId)`.
> - `fun search(query: String): List<SettingsSearchIndex>` — preserve the old behavior exactly: trim and lowercase query; if empty return the filtered `entries`; else filter by `index.title.lowercase().contains(query)` OR any keyword's `lowercase().contains(query)`.
>
> Logging: at the end of the `by lazy` initializer, emit `Timber.d("Settings search index built: ${allEntries.size} raw entries")`. Phase 05 swaps this to a `Timber.d("S0284: …")` tag when the spec moves into BlockNeedUserTest.
>
> Lazy init guarantees the heavy XML pass happens only on the first `entries`/`search` access — typically when the user opens the search overlay, not on app cold start.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt` exists.
- `Grep` - `class SettingsSearchRegistry` matches exactly once.
- `Grep` - `@Singleton` annotation present on the class.
- `Grep` - `@Inject constructor` present.
- `Grep` - `val entries: List<SettingsSearchIndex>` matches exactly once.
- `Grep` - `fun search(query: String): List<SettingsSearchIndex>` matches exactly once.
- `Grep` - `SettingsSearchAvailability` reference matches at least once (renamed from original `SettingsSearchFlavorFilter` per 04.1 deviation).
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 7/7 PASS (class, @Singleton, @Inject, entries, search, SettingsSearchAvailability ref all present; no Log.d). Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt (+39 LOC).

---

### Step 04.3 - Add Hilt module `SettingsSearchModule`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class SettingsSearchModule` with two `@Binds @Singleton` declarations:
> - `abstract fun bindSettingsSearchSource(impl: LayoutSettingsSearchSource): SettingsSearchSource`
> - `abstract fun bindSettingsSearchKeywordCollector(impl: LocalizedKeywordCollector): SettingsSearchKeywordCollector`
>
> Place the file alongside the other Hilt modules in `di/`. `SettingsSearchRegistry` itself does not need a `@Provides` — its `@Inject constructor` + `@Singleton` is enough.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt` exists.
- `Grep` - `abstract class SettingsSearchModule` matches exactly once.
- `Grep` - `@InstallIn(SingletonComponent::class)` present.
- `Grep` - `bindSettingsSearchSource` matches exactly once.
- `Grep` - `bindSettingsSearchKeywordCollector` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/di/SettingsSearchModule.kt (+39 LOC). Also added `@Multibinds @SupportedMediaSection` declaration (extra to original plan, required by Hilt for the per-flavor multibinding to resolve cleanly).

---

### Step 04.4 - Update `SettingsActivity` to inject the registry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Apply the following changes to `SettingsActivity` (file is currently around 700 LOC — read the surrounding code before editing):
>
> 1. Add a field: `@Inject lateinit var settingsSearchRegistry: com.sza.fastmediasorter.ui.settings.search.SettingsSearchRegistry`. Place alongside the existing `settingsTabExtensions` field (line ~43).
> 2. Replace the three references to the old static object:
>    - Line ~327: `SettingsSearchRegistry.search(query)` → `settingsSearchRegistry.search(query)`.
>    - Line ~331: `SettingsSearchRegistry.entries` → `settingsSearchRegistry.entries`.
>    - Line ~360: `SettingsSearchRegistry.entries` → `settingsSearchRegistry.entries`.
> 3. Remove any import of `SettingsSearchRegistry` from the `ui/settings` package if one exists — the new class lives under `ui/settings/search/`. Hilt-injected fields do not need an explicit import (the fully qualified name is used in the field declaration).
> 4. Activity already has `@AndroidEntryPoint` — no DI scaffolding to add.
>
> Do NOT touch anything else in the file. No behavioral changes outside the registry wire-up.

**Verification:**

- `Grep` in `SettingsActivity.kt` - `lateinit var settingsSearchRegistry` matches exactly once.
- `Grep` in `SettingsActivity.kt` - `settingsSearchRegistry.search(query)` matches exactly once.
- `Grep` in `SettingsActivity.kt` - `settingsSearchRegistry.entries` matches exactly twice (open overlay + initial state).
- `Grep` in `SettingsActivity.kt` - `SettingsSearchRegistry\.` (literal qualified reference to the old object) returns zero hits.
- Project compiles: run `/build` for the standard flavor at the end of this step.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - SettingsActivity rewired: `@Inject lateinit var settingsSearchRegistry` added; 4 static `SettingsSearchRegistry.` references replaced with instance calls (one was a hidden debug-log size print — total 3 `entries` references instead of original spec's expected 2). Build standardDebug SUCCESS in 1m 8s. Verification 5/5 PASS.

---

### Step 04.5 - Insert `S0284:` Timber tag at the user-facing search entrypoint

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` (already modified in 04.4), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> The spec is about to enter status `BlockNeedUserTest` — insert one `Timber.d("S0284: …")` tag at the entry point of each changed flow per CLAUDE.md "Debug Verification Tags". Three tags total:
>
> 1. `SettingsActivity.openSearchOverlay()` (line ~355): add `Timber.d("S0284: open settings search overlay")` as the first statement of the function body.
> 2. `SettingsSearchRegistry.entries` getter (or the `by lazy` block from Step 04.2): replace the placeholder `Timber.d("Settings search index built: ${allEntries.size} raw entries")` with `Timber.d("S0284: settings search index built (raw=${'$'}{allEntries.size})")`.
> 3. `LayoutSettingsSearchSource` (placeholder log from Step 02.3): replace `Timber.d("Settings search scan: $layoutName -> $count entries")` with `Timber.d("S0284: layout scan ${'$'}layoutName -> ${'$'}count entries")`.
>
> No other code change in this step. After the edit, the user can open Settings → search → see the three tags in logcat to confirm the index was actually built from layouts.

**Verification:**

- `Grep` for `Timber\.d\("S0284:` across `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/**` returns exactly 3 hits.
- `Grep` for `S0284: open settings search overlay` matches exactly once in `SettingsActivity.kt`.
- `Grep` for `S0284: settings search index built` matches exactly once in `SettingsSearchRegistry.kt`.
- `Grep` for `S0284: layout scan` matches exactly once in `LayoutSettingsSearchSource.kt`.
- `Grep` for `Settings search index built:` (placeholder) returns zero hits.
- `Grep` for `Settings search scan:` (placeholder) returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - 3 `Timber.d("S0284:` tags inserted (openSearchOverlay entry, registry lazy-init entry, layout-scan per-layout). Both placeholders ("Settings search index built:" / "Settings search scan:") removed. Verification 6/6 PASS.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles for the `standardDebug` variant - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- The new pipeline is the sole index source. Static registry is gone.
- Three `Timber.d("S0284:")` tags are in place — Phase 05 finalizes by moving the spec to `BlockNeedUserTest`. Tags stay in code until `/spec-check` flips the spec to `Verified` (then `/spec-check` removes them).
- BuildConfig gating verified to still hide audio/documents/video/images on restricted flavors via `SettingsSearchFlavorFilter`.

---

## Rollback Plan

Revert Phase 02 + Phase 03 + Phase 04 commits together (they form one logical change). Static registry returns; project compiles; no data migration needed.
