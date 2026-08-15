# Phase 03 - UI Controls and Parity

**Strategic spec:** [`../S0287_tesseract-cyrillic-model-swap-evaluation.md`](../S0287_tesseract-cyrillic-model-swap-evaluation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Build a beautiful, premium, glassmorphic layout inside settings (both portrait and landscape layouts) to allow users to trigger download, monitor progress, and delete the Russian and Ukrainian high-quality models. Implement robust lifecycle-bound coroutines to execute downloads safely in `OtherMediaSettingsFragment.kt`, with localization files matching the tone and rules checklist.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 250 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 - Add Localization Strings

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Start of Phase

**Prompt for developer:**
> Define clear and respectful string resources (following tone-checklist §6 of `docs/COMMUNICATION_POLICY.md` using "ё" for Russian keys). Added keys should include: `ocr_best_models_title`, `ocr_best_rus_title`, `ocr_best_ukr_title`, `ocr_best_model_download`, `ocr_best_model_delete`, `ocr_best_model_installed`, `ocr_best_model_downloading`, `ocr_best_model_size_mb`, `ocr_best_download_error`.

**Verification:**
- Keys are identical in all three `strings.xml` files.
- Russian translations correctly use the letter `ё` where appropriate.

**Status:** `[x]` done

---

### Step 03.2 - Design Portrait UI Layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_other.xml`
**Depends on:** Step 03.1

**Prompt for developer:**
> Append a new section under the OCR Font Family configuration labeled "High-Quality Offline Models" (`@string/ocr_best_models_title`). For Russian and Ukrainian models, include separate rows containing: a Title, Status description, progress percentage, a horizontal ProgressBar (initially hidden/visible only during downloads), a Download button, and a Delete button. Ensure premium aesthetics (margins, colors matching surface tokens, clear padding, and modern typography).

**Verification:**
- XML complies with constraints and renders correctly.
- IDs are unique and well-named.

**Status:** `[x]` done

---

### Step 03.3 - Design Landscape UI Layout (Landscape Parity)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_other.xml`
**Depends on:** Step 03.2

**Prompt for developer:**
> Implement an identical "High-Quality Offline Models" section in `layout-land/fragment_settings_other.xml` to guarantee complete Landscape Parity. Row configurations, buttons, progress bars, text sizes, and resource IDs must match the portrait version exactly.

**Verification:**
- XML has no duplicate ID issues and mirrors portrait functionality completely.

**Status:** `[x]` done

---

### Step 03.4 - Bind UI Actions and Lifecycles in Fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** Step 03.3

**Prompt for developer:**
> In `OtherMediaSettingsFragment.kt`:
> 1. Setup view bindings for RU/UK model controls (buttons, progress indicators).
> 2. Implement a method `updateModelStates()` to query `TesseractModelManager` and refresh the UI (e.g. if installed: hide download button, show delete button and "Installed" text; if not: show download button, hide delete button).
> 3. Connect the Download buttons using safe lifecycle-bound coroutines (`viewLifecycleOwner.lifecycleScope.launch`). During download, disable buttons, show the progress bar/text, and update the progress in real-time. On complete, call `updateModelStates()` and update the overall settings states.
> 4. Ensure download error states are caught and reported via localized Toast or alert conforming to policies.

**Verification:**
- Threading is strictly IO for network and Main for UI updates.
- Buttons are safely disabled/enabled to prevent double clicks during downloads.

**Status:** `[x]` done

---

### Step 03.5 - Sync Catalog and Compile

**Files:** None (build validation)
**Depends on:** Step 03.4

**Prompt for developer:**
> Run compilation checks and sync the class catalog using: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Verify there are no syntax or type compilation errors in `app_v2`.

**Verification:**
- Compilation and catalog sync script runs and exits with status 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Portrait and landscape layout files compile and render without errors.
- [x] Dynamic download buttons show real-time download percentages and bytes correctly.
- [x] Safe lifecycle-bound coroutines handle configuration changes elegantly.
- [x] Verification has been completed for EN, RU, and UK strings.
- [x] Dev log entries logged.
