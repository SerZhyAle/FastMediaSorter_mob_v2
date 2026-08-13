# Phase 04 — "Powerful Extras" Adaptive Feature-Block Grid

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Turn the enhanced welcome page into a compact-header + scrollable body that shows an adaptive grid of N feature blocks (column count driven by screen-width / orientation qualifiers), wire the full block set for the "Powerful Extras" page (content + infrastructure tiles, gated by `BuildConfig`), keep the language picker on the first page, and replace the remaining bitmap onboarding illustration.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`detailDescriptionRes` + `tvDetails` convention; `welcome_description_5_details`, `welcome_description_1_details` strings exist).
- [ ] `app_v2/build.gradle.kts` `BuildConfig` feature flags reviewed (`SUPPORT_VIDEO`, `SUPPORT_AUDIO`, `SUPPORT_IMAGES`, `SUPPORT_DOCUMENTS`, `SUPPORT_CLOUD`, plus animation/anim flag).
- [ ] `docs/FEATURES.md` skimmed for the canonical capability list to draw tiles from.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values-sw320dp/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values-sw480dp/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values-sw600dp/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values-sw720dp/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values-land/integers.xml` | New | ≤ 20 |
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/drawable/resource_types.xml` | New | ≤ 60 |
| `app_v2/src/main/res/drawable/resource_types.png` | Deleted | — |

> `WelcomeActivity.kt` is near the 500-line limit — back it up to `temp/` before editing if the change would push it past 500.

---

## Steps

### Step 04.1 — Column-count integer resources for the feature grid

**Files:** `app_v2/src/main/res/values/integers.xml` (new), `values-sw320dp/integers.xml` (new), `values-sw480dp/integers.xml` (new), `values-sw600dp/integers.xml` (new), `values-sw720dp/integers.xml` (new), `values-land/integers.xml` (new)

**Depends on:** — start of phase

**Prompt for developer:**

> Create an integer resource `welcome_feature_grid_columns` in each file with values tuned per bucket: base `values/` = 2, `sw320dp` = 2, `sw480dp` = 3, `sw600dp` = 4, `sw720dp` = 4, `land` = 4. (Goal: readable tiles on narrow phones, no half-empty rows on tablets/TV/landscape.) Each file is a standard `<resources>` with a single `<integer>` element.

**Verification:**

- `Grep -n "welcome_feature_grid_columns"` in each of the six `integers.xml` files — one hit each.
- `Glob` — all six `integers.xml` files exist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (welcome_feature_grid_columns 1 hit in all six integers.xml; sw720dp newly created). Values: base 2, sw320 2, sw480 3, sw600 4, sw720 4, land 4. Dev log recorded.

---

### Step 04.2 — Rebuild `page_welcome_enhanced.xml` (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/page_welcome_enhanced.xml`, `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml`

**Depends on:** Step 04.1

**Prompt for developer:**

> Restructure both variants as: fixed header (`ivIcon` + `tvTitle` + short `tvDescription` + the existing `layoutLanguagePicker` toggle group, unchanged ids `btnLangEn`/`btnLangRu`/`btnLangUk`) pinned to the top of the page area; below it a `ScrollView` (height `0dp`, fills remaining) whose child column contains a `GridLayout` `@+id/gridFeatures` (no fixed `columnCount` in XML — set at runtime) followed by a `TextView` `@+id/tvDetails`. Remove the three hardcoded `cardFeature1/2/3` `MaterialCardView`s and the static `layoutFeatureCards` row; `gridFeatures` is populated programmatically. The page never scrolls — only the inner `ScrollView` does. In `layout-land/page_welcome_enhanced.xml` keep the two-column idea (icon left, header text + language picker + grid + details scroll on the right) and **fix the corrupted button text**: `btnLangRu` must read `Русский`, `btnLangUk` must read `Українська` (the current file has mojibake there). Save both files as UTF-8.

**Verification:**

- `Grep -n "@+id/gridFeatures"` in `layout/page_welcome_enhanced.xml` — exactly once; same in `layout-land/page_welcome_enhanced.xml`.
- `Grep -n "cardFeature1"` in `layout/page_welcome_enhanced.xml` — zero hits; same in the landscape file.
- `Grep -n "@+id/tvDetails"` in both files — exactly once each.
- `Grep -n "@+id/layoutLanguagePicker"`, `"@+id/btnLangEn"`, `"@+id/btnLangRu"`, `"@+id/btnLangUk"` — each exactly once in each file.
- `Grep -n "Русский"` in `layout-land/page_welcome_enhanced.xml` — exactly one hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (gridFeatures 1, cardFeature1 0, tvDetails 1, layoutLanguagePicker/btnLang* 1 each in both; "Русский" 1 in landscape). Hardcoded cardFeature1..3 / layoutFeatureCards removed; header + scrollable body (picker + gridFeatures + tvDetails); mojibake fixed (UTF-8). Files: layout/page_welcome_enhanced.xml, layout-land/page_welcome_enhanced.xml. Dev log recorded.

---

### Step 04.3 — Trilingual strings for the new feature tiles

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Add short label strings for the additional "Powerful Extras" tiles beyond the existing `welcome_feature_ocr` / `welcome_feature_audio` / `welcome_feature_ebook`. Suggested new keys (final set decided with the canonical `docs/FEATURES.md` list): `welcome_feature_video_library`, `welcome_feature_audio_library`, `welcome_feature_text_editor`, `welcome_feature_slideshow`, `welcome_feature_gif`, `welcome_feature_network`, `welcome_feature_cloud_sync`, `welcome_feature_widgets`, `welcome_feature_scheduled_ops`, `welcome_feature_favorites`, `welcome_feature_quick_sort`. Each ≤ ~18 chars where possible. Add all to the three locale files. Per `docs/COMMUNICATION_POLICY.md` §6; author style `..` / `ё`-`Ё`.

**Verification:**

- `Grep -n "welcome_feature_video_library"` in each of the three strings files — one hit each.
- `Grep -n "welcome_feature_quick_sort"` in each of the three strings files — one hit each.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_feature_"` — exit code 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (check_strings_localized welcome_feature_ → 16/16 EN/RU/UK, exit 0; video_library & quick_sort present in all 3). Added 10 new tile labels (text_editor, video_library, slideshow, gif, network, cloud_sync, widgets, scheduled_ops, favorites, quick_sort); dropped redundant audio_library (reuse welcome_feature_audio). Dev log recorded.

---

### Step 04.4 — Populate the grid; wire page-1 and page-5 block sets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Depends on:** Step 04.1, Step 04.2, Step 04.3

**Prompt for developer:**

> In `EnhancedViewHolder.bind()`: remove the `cardFeature1..3` / `ivFeature1..3` / `tvFeature1..3` binding; instead clear `gridFeatures`, set its `columnCount` from `binding.root.resources.getInteger(R.integer.welcome_feature_grid_columns)`, and for each `FeatureCard` in `page.featureCards` inflate/build a tile view (tinted icon + ≤2-line label, `contentDescription` = the label) and add it to `gridFeatures` with `GridLayout.LayoutParams` using `columnSpec`/`rowSpec` with weight `1f` so cells share width evenly. Bind `tvDetails` from `page.detailDescriptionRes` (Phase 02 visibility rule). Keep the language-picker wiring exactly as is. In `WelcomeActivity.setupViewPager()`: keep the first (Welcome) page's three `FeatureCard`s and add `detailDescriptionRes = R.string.welcome_description_1_details` (already wired in Phase 02 — verify); for the "Powerful Extras" page, replace the 3-card list with the full tile set — content tiles (OCR/translate, audio player, eBooks/PDF, video library, text editor, slideshow, GIF) and infrastructure tiles (network sources, cloud sync, home-screen widgets, scheduled operations, favorites, color quick-sort) — each tile added only when its `BuildConfig` feature flag is enabled (`SUPPORT_AUDIO`, `SUPPORT_VIDEO`, `SUPPORT_IMAGES`, `SUPPORT_DOCUMENTS`, `SUPPORT_CLOUD`, etc.); set `detailDescriptionRes = R.string.welcome_description_5_details`. Reuse existing `ic_*` drawables for icons; create no new icon assets. If `WelcomeActivity.kt` would exceed 500 lines, back it up to `temp/` first (and, if it actually crosses 1500 — it will not — a Manager split would be required). Add `Timber.d("S0143: extras grid page bound")` once in `EnhancedViewHolder.bind()`.

**Verification:**

- `Grep -n "gridFeatures"` in `WelcomePagerAdapter.kt` — at least two hits.
- `Grep -n "welcome_feature_grid_columns"` in `WelcomePagerAdapter.kt` — at least one hit.
- `Grep -n "cardFeature1"` in `WelcomePagerAdapter.kt` — zero hits.
- `Grep -n "welcome_description_5_details"` in `WelcomeActivity.kt` — at least one hit.
- `Grep -n "BuildConfig.SUPPORT_"` in `WelcomeActivity.kt` — at least one hit (tile gating).
- `Grep -n "Timber.d(\"S0143:"` in `WelcomePagerAdapter.kt` — at least one hit.
- `Grep -n "Log\.d\("` in `WelcomePagerAdapter.kt` and `WelcomeActivity.kt` — zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (adapter gridFeatures 2, welcome_feature_grid_columns 2, cardFeature1/ivFeature1/layoutFeatureCards 0, Timber S0143 3, Log.d 0; activity welcome_description_5_details 1, BuildConfig.SUPPORT_ 6, Log.d 0). Added `populateFeatureGrid()` + new layout `item_welcome_feature_tile.xml`; `EnhancedViewHolder` populates grid + binds tvDetails; `buildExtrasFeatureCards()` in WelcomeActivity (BuildConfig-gated, 14 tiles max). Removed unused `TAG` const → file at 500 LOC. Backup temp/WelcomeActivity_<ts>.kt.bak. Dev log recorded.

---

### Step 04.5 — Replace the `resource_types` bitmap with a vector

**Files:** `app_v2/src/main/res/drawable/resource_types.xml` (new), `app_v2/src/main/res/drawable/resource_types.png` (delete), references in welcome layouts/code

**Depends on:** — start of phase

**Prompt for developer:**

> Create a vector drawable `app_v2/src/main/res/drawable/resource_types.xml` that conveys "storage source types" (e.g. a folder/cloud/network composite glyph) and looks crisp at the welcome header icon size on all densities; delete `resource_types.png`. Update any reference (the Resource Types page sets `iconRes = R.drawable.resource_types`) so it now points at the vector — the resource name stays `resource_types`, only the file extension changes, so no code change is needed unless a fully-qualified `.png` path is referenced anywhere. If no suitable composite is feasible, reuse an existing scalable `ic_*` vector instead and delete the PNG.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/resource_types.xml` exists; `app_v2/src/main/res/drawable/resource_types.png` does not exist.
- `Grep -rn "resource_types.png"` across `app_v2/src` — zero hits.
- `Grep -rn "@drawable/resource_types" ` across `app_v2/src/main/res` and `app_v2/src/main/java` — resolves to the vector (still referenced or cleanly removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (resource_types.xml exists, resource_types.png deleted, no "resource_types.png" hits in app_v2/src). New vector illustration (220×160, stacked LOCAL/NETWORK/CLOUD rows); resource name `resource_types` unchanged so `R.drawable.resource_types` in WelcomeActivity now resolves to the vector — no code change. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (1m 9s) (covers Phases 02–04).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_feature_"` exits 0.
- [x] Dev log entry added for every file in "Files Touched" (incl. the six `integers.xml`, the new tile layout, and the `resource_types.png` deletion).
- [x] No public API change in `WelcomePagerAdapter` / `WelcomeActivity` — catalog regen deferred to Phase 05.

---

## Handoff Notes to Next Phase

- No bitmap onboarding illustrations remain (`touch_zones_scheme.png` and `resource_types.png` both gone); `welcome_hero_*` and `destinations` are already vectors.
- The feature-grid tile set is `BuildConfig`-gated — Phase 05 docs must describe the onboarding in flavor-neutral terms.

---

## Rollback Plan

Revert phase commit(s); restore `resource_types.png` from VCS history if reverting the layout. No persisted state touched.
