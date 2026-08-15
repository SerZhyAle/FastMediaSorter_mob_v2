# Phase 01 - Language registry

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05, Phase 06
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Make `res/xml/locales_config.xml` the single declaration of supported UI languages, expose it to Kotlin through one catalog, and remove the two build-level restrictions that override it.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` free (`scripts/utils/lock-status.ps1 -Name Code`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/xml/locales_config.xml` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UiLanguageCatalog.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt` | Modified | ≤ 360 |
| `app_v2/build.gradle.kts` | Modified | ≤ 5 |

---

## Steps

### Step 01.1 - Declare the thirteen locales

**Files:** `app_v2/src/main/res/xml/locales_config.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> List all thirteen supported locales in `locales_config.xml`: `en`, `zh`, `hi`, `es`, `fr`, `ar`, `bn`, `pt`, `ru`, `ur`, `uk`, `de`, `it`. Use the BCP-47 tag Android expects, and pick `zh-Hans` for Chinese - simplified and traditional are mutually unreadable, so the variant has to be explicit (strategic §7). Keep the file the only place where the set is written down.

**Verification:**

- `Grep` - `locales_config.xml` contains exactly 13 `<locale` elements.
- `Grep` - `android:name="zh-Hans"` matches once.

**Status:** `[x] done`

---

### Step 01.2 - Read the declaration from Kotlin

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UiLanguageCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `UiLanguageCatalog` - an object that parses `R.xml.locales_config` once, caches the resulting ordered list of language tags, and exposes: the supported tags, whether a given tag is supported, and per tag a display name in that language itself plus a flag emoji. Take the display name from `java.util.Locale.getDisplayLanguage(locale)` and the flag from the existing emoji generator used by the translation feature - do not add drawables, and keep the existing exceptions for `ru`. Parsing runs off `XmlResourceParser`; do it on first access, not in a constructor that a UI thread hits mid-frame.

**Verification:**

- `Glob` - `UiLanguageCatalog.kt` exists.
- `Grep` - `object UiLanguageCatalog` matches exactly once.
- `Grep` - `R.xml.locales_config` matches at least once in that file.
- `Grep` - no hardcoded `"ru"`/`"uk"` language-set literal remains in the file (flag exceptions aside).

**Status:** `[x] done`

---

### Step 01.3 - Make LocaleHelper consult the catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace `SUPPORTED_NON_DEFAULT_LANGUAGES` with a catalog lookup so `resolveSupportedLanguageCode` accepts every declared locale and still falls back to English for anything else. `resolveSupportedLanguageCode` currently takes no `Context`; give the catalog whatever it needs to reach resources without changing the call sites' meaning, and keep the stored `selected_language` preference value untouched for users already on `ru`/`uk` (strategic §3.2 - data compatibility). The file is over 300 lines: back it up under `temp/S1190/` before editing.

**Verification:**

- `Grep` - `SUPPORTED_NON_DEFAULT_LANGUAGES` returns zero hits in the file.
- `Grep` - `UiLanguageCatalog` is referenced at least once.
- `Glob` - `temp/S1190/LocaleHelper_*.kt` matches at least once.

**Status:** `[x] done`

---

### Step 01.4 - Stop stripping locales at build time

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.3

**Prompt for developer:**

> Remove the `localeFilters += listOf("en", "ru", "uk")` line and the comment that explains the APK-size optimization it performed. Per strategic ADR-4/ADR-5 the size trade-off is now handled by Play language splits for the store channel, and non-Play channels deliberately carry every locale.

**Verification:**

- `Grep` - `localeFilters` returns zero hits in `app_v2/build.gradle.kts`.
- `.\a.ps1 fr` exits 0 (resources and manifest still assemble).

**Status:** `[x] done`

---

### Step 01.5 - Prove the catalog against the declaration

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/util/UiLanguageCatalogTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add a unit test asserting that the catalog reports exactly the tags declared in `locales_config.xml`, that an undeclared tag resolves to English, and that every declared tag yields a non-blank display name. Use the project's existing Robolectric or resource-backed test setup rather than hand-parsing the XML a second time - a test that reimplements the parser proves nothing.

**Verification:**

- `Glob` - `UiLanguageCatalogTest.kt` exists.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*UiLanguageCatalogTest*"` exits 0 (run through `/build`, not directly).

**Status:** `[x] done`

---

## Step Log

- 2026-07-27 - Steps 01.1-01.5 executed in order. Backup: `temp/S1190/LocaleHelper_20260727_161345.kt`. `.\a.ps1 fc` BUILD SUCCESSFUL; `testStandardDebugUnitTest --tests "*UiLanguageCatalogTest*"` BUILD SUCCESSFUL with 5/5 cases passing (results XML written 16:23:42).
- 2026-07-27 - 01.2 design note: the catalog is initialized from `LocaleHelper.applyLocale` **and** `getLanguage`, because `applyLocale(context, languageCode = getLanguage(context))` evaluates its default argument first - initializing only in the body would let the first resolution of the session run against an empty catalog and cache English.
- 2026-07-27 - 01.3 also fixed a latent recursion: `detectSystemLanguage` used to resolve through `resolveSupportedLanguageCode`, which routes a blank language back to `detectSystemLanguage`. It now asks the catalog directly. `Locale(tag)` became `Locale.forLanguageTag(tag)` so `zh-Hans` is not read as a language code.
- 2026-07-27 - 01.5 needed two corrections: `ApplicationProvider` is not on the unit-test classpath (androidx.test:core is androidTest-only) - used `RuntimeEnvironment.getApplication()`; and Robolectric 4.11.1 has no image for `targetSdk 36`, so the class is pinned to `@Config(sdk = [34])`.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on touched Kotlin returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`locales_config.xml` is the only place the language set is written down; Kotlin reads it through `UiLanguageCatalog`, and no build step trims locales out of the package any more. The settings screen and the Welcome screen still carry their own three-item lists - that is Phase 02.

---

## Rollback Plan

Restore `localeFilters`, revert `locales_config.xml` to three locales and revert the phase commit - no persisted user data changes shape.
