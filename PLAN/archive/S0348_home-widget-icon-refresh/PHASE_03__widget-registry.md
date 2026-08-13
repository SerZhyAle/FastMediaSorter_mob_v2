# Phase 03 - Home widget catalog/registry

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Introduce a single source of truth for the in-app widget picker: a `HomeWidgetEntry` catalog plus a `HomeWidgetCatalog` that reports which widgets are available in the current build. Availability is derived from `AppWidgetManager.getInstalledProvidersForPackage()` (flavor gating via manifest) plus runtime `AppSettings` (game / favorites) - **no `BuildConfig.SUPPORT_*` / `ENABLE_*` reads in `src/main`** (Rule 15). Also harden flavor manifests so capability-gated receivers are physically absent where unsupported.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (icon layouts exist for previews).
- [ ] Read `dev/FLAVOR_DEVELOPMENT_RULES.md` §3-§4 before touching any flavor manifest.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetEntry.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` | New | ≤ 180 |
| `app_v2/src/lite/AndroidManifest.xml` | Modified | ≤ 10 |
| `app_v2/src/photos/AndroidManifest.xml` | Modified | ≤ 10 |

> No new Hilt module required: `HomeWidgetCatalog` takes `@ApplicationContext Context` + `SettingsRepository` via constructor `@Inject`; Hilt resolves both from existing bindings.

---

## Steps

### Step 03.1 - Define `HomeWidgetEntry`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetEntry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a data class describing one pinnable home widget: `providerClass: Class<out android.appwidget.AppWidgetProvider>`, `labelRes: Int`, `iconRes: Int`, `descriptionRes: Int`, and an optional runtime gate `settingGate: ((AppSettings) -> Boolean)? = null` (null = no runtime gate; flavor gating is handled separately by installed-provider lookup). Add a helper `fun component(context: Context): ComponentName = ComponentName(context, providerClass)`. No `BuildConfig` references.

**Verification:**

- `Glob` - `HomeWidgetEntry.kt` exists.
- `Grep -n "data class HomeWidgetEntry"` matches once.
- `Grep -n "settingGate"` and `Grep -n "fun component"` each match once.
- `Grep -n "BuildConfig"` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS: HomeWidgetEntry.kt created; `data class HomeWidgetEntry`, `settingGate`, `fun component` present; `BuildConfig` 0 hits. File: widget/registry/HomeWidgetEntry.kt.

---

### Step 03.2 - Define `HomeWidgetCatalog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a `@Singleton class HomeWidgetCatalog @Inject constructor(@ApplicationContext private val context: Context, private val settingsRepository: SettingsRepository)`. Hold a private `val allEntries: List<HomeWidgetEntry>` enumerating the pinnable widgets: Calculator, CameraOcrTranslate, CameraPhotos, ContinueReading, GameLaunch (gate `settingGate = { it.embeddedGameEnabled }`), RandomMusic, Favorites (gate `settingGate = { it.enableFavorites }`). Each entry uses its existing `R.string.widget_*_label`, the existing `R.drawable.ic_widget_*` icon, and its `R.string.widget_*_description`. Exclude `ResourceLaunchWidgetProvider` (configurable shortcut - keeps its existing per-resource pin entry in `ResourceEditorFragment`; not offered in the generic picker for the first wave).
>
> Expose `suspend fun availableEntries(): List<HomeWidgetEntry>` that:
> 1. reads installed providers via `AppWidgetManager.getInstance(context).installedProviders` filtered to `it.provider.packageName == context.packageName`, and keeps only entries whose provider class name is in that set (this is the flavor gate - receivers removed in a flavor are absent here). Use `installedProviders` (API 3+), NOT `getInstalledProvidersForPackage` which is API 26+ and would crash `legacy` (minSdk 23).
> 2. reads current `AppSettings` from `settingsRepository.getSettings().first()` and drops entries whose `settingGate` returns false.
>
> Do not read any `BuildConfig` field. Do not perform disk/network work beyond the settings read.

**Verification:**

- `Glob` - `HomeWidgetCatalog.kt` exists.
- `Grep -n "class HomeWidgetCatalog"` matches once.
- `Grep -n "installedProviders"` matches once (API-safe provider discovery).
- `Grep -n "suspend fun availableEntries"` matches once.
- `Grep -n "BuildConfig"` returns zero hits in this file.
- `Grep -n "Log\.d\("` returns zero hits in this file (Timber only).
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS: HomeWidgetCatalog.kt created; `class HomeWidgetCatalog`, `installedProviders` (API-safe), `suspend fun availableEntries`; `BuildConfig` 0 across registry pkg; `Log.d` 0. 7 entries (Resource-Launch excluded). Build at phase gate. File: widget/registry/HomeWidgetCatalog.kt.

---

### Step 03.3 - Remove Camera-OCR receiver in `lite` and `photos`

**Files:** `app_v2/src/lite/AndroidManifest.xml`, `app_v2/src/photos/AndroidManifest.xml`
**Depends on:** - independent of 03.1/03.2

**Prompt for developer:**

> `ENABLE_TRANSLATION` is `false` in `lite` and `photos` (see `app_v2/build.gradle.kts`), so the Camera-OCR widget must not be offerable there. Add a manifest-merger removal to both flavor manifests, mirroring the existing `RandomMusicWidgetProvider` removal in `src/photos/AndroidManifest.xml`:
> ```xml
> <receiver android:name=".widget.CameraOcrTranslateWidgetProvider" tools:node="remove" />
> ```
> Ensure the `tools` namespace is declared on the manifest root in each file (it already is where `tools:node` is used). Do not touch `src/legacy` or `src/vr` (both have `ENABLE_TRANSLATION=true`). Do not touch `RandomMusicWidgetProvider` (audio is `true` in `lite`; already removed only in `photos`).

**Verification:**

- `Grep -n "CameraOcrTranslateWidgetProvider"` + `Grep -n "node=\"remove\""` in `src/lite/AndroidManifest.xml` - expected: removal present | actual: <fill in>.
- Same check in `src/photos/AndroidManifest.xml` - expected: removal present | actual: <fill in>.
- `Grep -n "CameraOcrTranslateWidgetProvider"` in `src/legacy/AndroidManifest.xml` and `src/vr/AndroidManifest.xml` - expected: zero hits (untouched) | actual: zero hits (untouched).
- Build: `.\a.ps1 db` for `liteDebug` and `photosDebug` (target variants) compiles - manifest merge succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): Camera-OCR receiver removal present in src/lite + src/photos manifests; legacy/vr/noLegal untouched (only main declaration + 2 flavor removals). Merge validated at phase gate builds. Files: lite/AndroidManifest.xml, photos/AndroidManifest.xml.

---

### Step 03.4 - Verify availability matrix against flavor manifests

**Files:** none (verification step)
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> Cross-check that `availableEntries()` yields the intended set per flavor, given installed-provider gating:
> - `standard` / `legacy` / `vr` / `noLegal`: all entries (subject to settings).
> - `lite`: no Camera-OCR (removed 03.3); RandomMusic present (audio true).
> - `photos`: no Camera-OCR (removed 03.3); no RandomMusic (already removed); Camera Photos present.
> Confirm by inspecting the merged manifest receivers, not by running the device.

**Verification:**

- For each target flavor, `Grep` the merged manifest under `app_v2/build/intermediates/merged_manifests/<variant>/AndroidManifest.xml` for `CameraOcrTranslateWidgetProvider` / `RandomMusicWidgetProvider` - record expected vs actual per flavor.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (merged manifests inspected): photos -> Camera-OCR absent, RandomMusic absent, Calculator/CameraPhotos present | lite -> Camera-OCR absent, RandomMusic present. standardDebug + liteDebug + photosDebug all BUILD SUCCESSFUL. Matrix matches intent.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - standardDebug (2m4s) + liteDebug + photosDebug (52s) all BUILD SUCCESSFUL.
- [x] `Grep -n "BuildConfig"` across `widget/registry/*.kt` returns zero hits.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1616 records).

---

## Handoff Notes to Next Phase

`HomeWidgetCatalog.availableEntries()` is the single list the Phase 04 settings picker renders. Flavor gating is fully manifest-driven (no `BuildConfig` in `src/main`); runtime gating is settings-driven. Phase 04 injects `HomeWidgetCatalog` into the settings helper.

---

## Rollback Plan

Revert the two manifest edits (Camera-OCR reappears in `lite`/`photos`) and delete the two new registry classes. No data migration; no already-placed widget is affected.
