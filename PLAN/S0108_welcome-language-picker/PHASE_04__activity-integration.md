# Phase 04 — Activity Integration: Page-0 Flags + Language-Change Handler

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Wire `WelcomeActivity.setupViewPager()` to set `showLanguagePicker = true` and supply `onLanguageSelected` on page 0. Add a private handler that saves the chosen language via `LocaleHelper` and triggers Activity recreation (on API < 33) or lets LocaleManager handle it (API 33+). Suppresses the default transition animation on recreation.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 500 |

> File is 462 lines — no backup required (under 500 threshold). If any addition brings it over 500, create a timestamped backup in `temp/` first.

---

## Steps

### Step 04.1 — Enable language picker on page 0 in setupViewPager()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `WelcomeActivity.setupViewPager()`, locate the first `WelcomePage(...)` constructor call (page 0, the Welcome page with feature cards for photos, cloud, sorting). Add two arguments to that call:
>
> ```kotlin
> WelcomePage(
>     iconRes = R.drawable.welcome_hero_media,
>     titleRes = R.string.welcome_title_1,
>     descriptionRes = R.string.welcome_description_1,
>     featureCards = listOf(
>         FeatureCard(R.drawable.ic_image, R.string.welcome_feature_photos),
>         FeatureCard(R.drawable.ic_resource_cloud, R.string.welcome_feature_cloud),
>         FeatureCard(R.drawable.ic_swap_horizontal, R.string.welcome_feature_sorting)
>     ),
>     showLanguagePicker = true,
>     onLanguageSelected = ::onWelcomeLanguageSelected,
> ),
> ```
>
> No other pages receive `showLanguagePicker = true`.

**Verification:**

- `Grep` — `showLanguagePicker = true` appears exactly once in `WelcomeActivity.kt`.
- `Grep` — `onLanguageSelected = ::onWelcomeLanguageSelected` appears exactly once in `WelcomeActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 2/2 PASS. `showLanguagePicker = true` + `onLanguageSelected = ::onWelcomeLanguageSelected` on page 0 only.

---

### Step 04.2 — Add onWelcomeLanguageSelected() private handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the following private function to `WelcomeActivity` (place it alongside the other private helpers, e.g. after `applyPageBackground()`):
>
> ```kotlin
> private fun onWelcomeLanguageSelected(code: String) {
>     Timber.d("S0108: onWelcomeLanguageSelected — code=$code")
>     LocaleHelper.saveLanguage(this, code)
>     if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
>         // API < 33: LocaleManager is unavailable — recreate Activity manually.
>         // overridePendingTransition(0, 0) called after recreate() suppresses the
>         // default enter animation of the new Activity instance.
>         recreate()
>         overridePendingTransition(0, 0)
>     }
>     // API 33+: LocaleManager already called in saveLanguage() will recreate the
>     // Activity automatically with no further action needed here.
> }
> ```
>
> Add `import com.sza.fastmediasorter.core.util.LocaleHelper` to the file imports if not already present.

**Verification:**

- `Grep` — `fun onWelcomeLanguageSelected` appears exactly once in `WelcomeActivity.kt`.
- `Grep` — `LocaleHelper.saveLanguage` appears in `WelcomeActivity.kt`.
- `Grep` — `Timber.d("S0108: onWelcomeLanguageSelected` appears in `WelcomeActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `WelcomeActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 4/4 PASS. `fun onWelcomeLanguageSelected`, `LocaleHelper.saveLanguage`, `Timber.d("S0108:...")`, no `Log.d`. Build PASS.

---

## Phase Done Criteria

- [ ] Every Step above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] Manual smoke test (emulator API 26 or 30): open Welcome, tap "Русский" → Welcome reloads in Russian, picker shows "Русский" highlighted.
- [ ] Manual smoke test (API 33+ device/emulator): same flow — system handles recreation, Welcome relaunches in Russian.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Final phase is docs and catalog cleanup only.
- The feature is fully functional after this phase.

---

## Rollback Plan

Revert `WelcomeActivity.kt` changes. Locale saved by any in-progress test remains in SharedPreferences — clear app data to reset. Medium risk (locale persists across install sessions until overwritten).
