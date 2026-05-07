# Phase 03 — Model & Adapter: WelcomePage Extension + EnhancedViewHolder Wiring

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Extend the `WelcomePage` data class with two optional fields (`showLanguagePicker`, `onLanguageSelected`) and wire `EnhancedViewHolder.bind()` to show, initialise, and handle the language picker when those fields are set. All existing usages of `WelcomePage` remain binary-compatible (both fields have defaults).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (layout IDs exist in binding).
- [ ] Phase 02 is ✅ Done (string key resolves at compile time).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 300 |

---

## Steps

### Step 03.1 — Extend WelcomePage data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `WelcomePagerAdapter.kt`, locate the `WelcomePage` data class (currently at the bottom of the file). Add two fields with defaults so all existing call sites remain unchanged:
>
> ```kotlin
> data class WelcomePage(
>     val iconRes: Int,
>     val titleRes: Int,
>     val descriptionRes: Int,
>     val showTouchZonesScheme: Boolean = false,
>     val isPermissionsPage: Boolean = false,
>     val isDefaultPlayerPage: Boolean = false,
>     val onGrantClick: (() -> Unit)? = null,
>     val onSkipClick: (() -> Unit)? = null,
>     val onSetDefaultForTypeClick: ((mimeType: String) -> Unit)? = null,
>     val featureCards: List<FeatureCard> = emptyList(),
>     /** Show the language picker strip. Only set on the first Welcome page. */
>     val showLanguagePicker: Boolean = false,
>     /** Invoked with the ISO-639-1 language code when the user taps a picker button. */
>     val onLanguageSelected: ((code: String) -> Unit)? = null,
> )
> ```

**Verification:**

- `Grep` — `showLanguagePicker` appears in `WelcomePagerAdapter.kt` (at least once in the data class declaration).
- `Grep` — `onLanguageSelected` appears in `WelcomePagerAdapter.kt` (at least once in the data class declaration).
- `Grep` — `data class WelcomePage` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 3/3 PASS. `showLanguagePicker` + `onLanguageSelected` added to `WelcomePage`.

---

### Step 03.2 — Wire EnhancedViewHolder.bind() to show and handle the picker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `EnhancedViewHolder.bind()`, add the language picker wiring block **before** the existing animation calls. Import `com.sza.fastmediasorter.core.util.LocaleHelper` at the file top if not already present. The block must:
>
> 1. Show `binding.layoutLanguagePicker` when `page.showLanguagePicker == true`, hide it otherwise.
> 2. Clear any previously registered button-checked listeners with `binding.layoutLanguagePicker.clearOnButtonCheckedListeners()` to avoid duplicates on rebind.
> 3. Set the initially checked button to match the current language from `LocaleHelper.getLanguage(binding.root.context)`.
> 4. Register a listener that fires `page.onLanguageSelected?.invoke(code)` only when a button transitions to `isChecked == true` AND the chosen code differs from the current language (prevents no-op callbacks when the same button is tapped again).
>
> ```kotlin
> // Language picker wiring
> if (page.showLanguagePicker) {
>     binding.layoutLanguagePicker.visibility = android.view.View.VISIBLE
>     binding.layoutLanguagePicker.clearOnButtonCheckedListeners()
>     val currentLang = LocaleHelper.getLanguage(binding.root.context)
>     val initialId = when (currentLang) {
>         "ru" -> R.id.btnLangRu
>         "uk" -> R.id.btnLangUk
>         else -> R.id.btnLangEn
>     }
>     binding.layoutLanguagePicker.check(initialId)
>     binding.layoutLanguagePicker.addOnButtonCheckedListener { _, checkedId, isChecked ->
>         if (!isChecked) return@addOnButtonCheckedListener
>         val code = when (checkedId) {
>             R.id.btnLangRu -> "ru"
>             R.id.btnLangUk -> "uk"
>             else -> "en"
>         }
>         if (code != LocaleHelper.getLanguage(binding.root.context)) {
>             page.onLanguageSelected?.invoke(code)
>         }
>     }
> } else {
>     binding.layoutLanguagePicker.visibility = android.view.View.GONE
> }
> ```
>
> Add the debug tag immediately before the picker visibility block:
> ```kotlin
> Timber.d("S0108: EnhancedViewHolder language picker bind — showLanguagePicker=${page.showLanguagePicker}")
> ```
>
> Ensure `Timber` is already imported; do not add `Log.d`.

**Verification:**

- `Grep` — `showLanguagePicker` appears in `EnhancedViewHolder` body (inside `bind()` function).
- `Grep` — `clearOnButtonCheckedListeners` appears in `WelcomePagerAdapter.kt`.
- `Grep` — `addOnButtonCheckedListener` appears in `WelcomePagerAdapter.kt`.
- `Grep` — `Timber.d("S0108:` appears in `WelcomePagerAdapter.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `WelcomePagerAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 5/5 PASS. `showLanguagePicker` flag, `onLanguageSelected` callback, `clearOnButtonCheckedListeners`, `addOnButtonCheckedListener`, Timber tag — all present. Build PASS.

---

## Phase Done Criteria

- [ ] Every Step above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `WelcomePage.showLanguagePicker` and `WelcomePage.onLanguageSelected` are ready for use.
- Phase 04 sets these fields on page 0 and supplies the recreation callback.

---

## Rollback Plan

Revert `WelcomePagerAdapter.kt` changes. No schema or data changes. Medium risk (compile error if callback type mismatches, caught by build).
