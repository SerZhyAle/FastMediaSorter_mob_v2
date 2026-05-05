# Phase 05 — First-Launch Banner

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Show a one-time informational banner the first time the app runs on Chrome OS, listing key environment differences (no haptic feedback, Cast may be limited, keyboard shortcuts applied). The banner is non-blocking and dismissible; it never reappears.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] Blocker §6.3 acknowledged (window size / VR flavor — banner placement does not depend on resolution, but note the finding).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +4 keys |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainChromeOsBannerManager.kt` | **New** | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1010 |

> `MainActivity.kt` is currently ~983 lines — **backup required before edit**. See Step 5.2.

---

## Steps

### Step 5.1 — Add trilingual strings for Chrome OS banner

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following four string keys to all three `strings.xml` files (EN / RU / UK). Use `..` (two dots) for ellipsis in UI strings, never `...`. Use `ё`/`Ё` correctly in Russian.
>
> | Key | EN | RU | UK |
> |---|---|---|---|
> | `chromeos_banner_title` | `Running on Chrome OS` | `Запуск на Chrome OS` | `Запуск на Chrome OS` |
> | `chromeos_banner_body` | `Folder access uses the system picker. Cast and haptics may be limited.` | `Доступ к папкам — через системный диалог. Cast и вибрация могут быть недоступны.` | `Доступ до тек — через системний діалог. Cast і вібрація можуть бути недоступні.` |
> | `chromeos_banner_action` | `Got it` | `Понятно` | `Зрозуміло` |
> | `chromeos_banner_shown_pref_key` | `chromeos_banner_shown` | *(same key, not a string resource — skip)* | *(same key, not a string resource — skip)* |
>
> Note: `chromeos_banner_shown_pref_key` is a SharedPreferences key constant — add it as a string resource only in EN `strings.xml` with value `"chromeos_banner_shown"`. RU and UK do not need it.

**Verification:**

- `Grep` — `chromeos_banner_title` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `chromeos_banner_title` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `chromeos_banner_title` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Grep` — `chromeos_banner_action` present in all three files.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "chromeos_banner"` — exit code 0.

**Status:** `[ ]` not done

---

### Step 5.2 — Create MainChromeOsBannerManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainChromeOsBannerManager.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Create `MainChromeOsBannerManager.kt` in `ui/main/helpers/`. It is a non-Hilt helper class constructed with `(activity: AppCompatActivity)`. Expose one public function: `showIfNeeded()`. Implementation:
>
> 1. Return immediately if `!ChromeOsCompat.isChromeOs(activity)`.
> 2. Read `SharedPreferences` with name `"fms_prefs"` and key `activity.getString(R.string.chromeos_banner_shown_pref_key)`. If the key is `true`, return.
> 3. Show a `Snackbar` on the root view of the Activity with:
>    - Text: `"${getString(R.string.chromeos_banner_title)}: ${getString(R.string.chromeos_banner_body)}"`
>    - Action: `getString(R.string.chromeos_banner_action)` — dismiss the Snackbar.
>    - Duration: `Snackbar.LENGTH_INDEFINITE`.
>    - `addCallback` on the Snackbar: when dismissed for ANY reason (action or swipe), write `true` to the SharedPreferences key.
> 4. Log: `Timber.i("MainChromeOsBannerManager: showing Chrome OS first-launch banner")`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainChromeOsBannerManager.kt` exists.
- `Grep` — `class MainChromeOsBannerManager` matches exactly once.
- `Grep` — `fun showIfNeeded` present in that file.
- `Grep` — `ChromeOsCompat.isChromeOs` present in that file.
- `Grep` — `LENGTH_INDEFINITE` present in that file.
- `Grep` for `Log.d(` in `MainChromeOsBannerManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 5.3 — Wire banner manager into MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 5.2

**Prompt for developer:**

> First, create a timestamped backup of `MainActivity.kt` in `temp/` (file is ~983 lines, backup required). Verify with Glob before proceeding.
>
> In `MainActivity`, instantiate `MainChromeOsBannerManager(this)` as a private field. Call `bannerManager.showIfNeeded()` from `onResume()` (after `super.onResume()`). The banner manager internally guards against repeated calls via SharedPreferences — calling it from `onResume()` is safe and ensures it fires after the window is fully attached. Do not add `@Inject` — the manager is constructed directly.

**Verification:**

- `Glob` — `temp/MainActivity_*_backup.kt` returns at least one match.
- `Grep` — `MainChromeOsBannerManager` present in `MainActivity.kt`.
- `Grep` — `showIfNeeded` present in `MainActivity.kt`.
- `Grep` for `Log.d(` in `MainActivity.kt` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "chromeos_banner"` exits 0.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entries added for every modified/created file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

On first Chrome OS launch, users see a `LENGTH_INDEFINITE` Snackbar explaining the environment. After dismissal (action button or swipe), the banner never reappears. No new layout files were created — landscape parity is not applicable.

---

## Rollback Plan

Revert phase commit(s). String keys are removed (locale audit will flag if missed — run `check_strings_localized.ps1` to confirm). `MainChromeOsBannerManager.kt` is deleted. Restore `MainActivity.kt` from backup if needed. No DB changes.
