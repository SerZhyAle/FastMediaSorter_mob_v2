# Phase 03 - Settings UI status

**Strategic spec:** [`../S0334_translation-model-prewarm.md`](../S0334_translation-model-prewarm.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Show the prewarm status near the target-language row in the translation settings, with a manual retry action on failure. Status is non-intrusive (a status line, not a modal dialog) and distinguishable by text, not colour alone.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] ViewModel exposes the prewarm status flow and `retryTranslationModelPrewarm()`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> Landscape counterpart `layout-land/fragment_settings_other.xml` exists - the status row MUST be added to both layouts in this phase.

---

## Steps

### Step 03.1 - Add prewarm status strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four strings in all three locales: `translation_model_prewarm_downloading`, `translation_model_prewarm_ready`, `translation_model_prewarm_failed`, `translation_model_prewarm_retry`. Wording must follow `docs/COMMUNICATION_POLICY.md` §2 (status/progress message formula) and pass the §6 tone checklist. Preserve author style in RU/UK: `..` (not `...`), correct `ё`/`Ё`. Keep them consistent with the existing `download_translation_model_*` strings.

**Verification:**

- `Grep` - each of the four keys present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (12 hits total).
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "translation_model_prewarm"` - exit 0, all keys EN/RU/UK = OK.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Expected string-key hits: 12 | actual: 12. `check_strings_localized.ps1 -KeyPrefix "translation_model_prewarm"` exit 0. COMMUNICATION_POLICY §6 checklist PASS. Dev log recorded for EN/RU/UK string files.

---

### Step 03.2 - Add status row to both layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_other.xml`, `app_v2/src/main/res/layout-land/fragment_settings_other.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a non-intrusive status row directly under the target-language picker row in both portrait and landscape layouts: a status text view plus a retry control (button/text) shown only on failure. Match the surrounding settings rows' visual style (spacing, typography). The retry control must be `focusable="true"` and `clickable="true"` and sit in the logical focus order after the language row (keyboard / D-pad / mouse reachable). Keep content inside system-bar safe bounds (the fragment already uses inset-safe scrolling).

**Verification:**

- `Grep` - the status text view id (e.g. `tvTranslationPrewarmStatus`) present in both `layout/` and `layout-land/` variants.
- `Grep` - the retry control id present in both variants.
- `Grep` - `focusable="true"` and `clickable="true"` on the retry control in both variants.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Status and retry ids present in portrait and landscape. Retry control focusable/clickable in both variants. Portrait layout line budget expected: ≤400 | actual: 394. Dev log recorded for both layout files.

---

### Step 03.3 - Bind status and retry in fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Collect the ViewModel's prewarm status flow on the fragment lifecycle. Map `Idle` → row hidden; `Downloading` → show `translation_model_prewarm_downloading`, retry hidden; `Ready` → show `translation_model_prewarm_ready`, retry hidden; `Failed` → show `translation_model_prewarm_failed`, retry visible. Wire the retry control to `viewModel.retryTranslationModelPrewarm()`. Add `Timber.d("S0334: prewarm status rendered <state>")` at the collector entry as the BlockNeedUserTest probe.

**Verification:**

- `Grep` - the prewarm status flow collected in `OtherMediaSettingsFragment.kt`.
- `Grep` - `retryTranslationModelPrewarm()` invoked from the retry control listener.
- `Grep` - all four status string keys referenced.
- `Grep` - `Timber.d("S0334:` present exactly once in the fragment.
- `Grep -n "Log\.d\("` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 5/5 PASS. `translationModelPrewarmStatus` collected, retry invokes ViewModel, all four status keys referenced, fragment `Timber.d("S0334:` count expected: 1 | actual: 1, `Log.d()` expected: 0 | actual: 0. Backup: temp/OtherMediaSettingsFragment.kt.20260603_105759.backup. Dev log recorded; catalog sync exit 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` exit 0, `assembleStandardDebug` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits. Expected: 0 | actual: 0.
- [x] Strings audit `check_strings_localized.ps1 -KeyPrefix "translation_model_prewarm"` exits 0.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

User-visible surface complete. Phase 04 regenerates the catalog, records dev log closure, and confirms no FEATURES change is needed.

---

## Rollback Plan

Revert phase commit(s) - layout, fragment, and string additions only; no persistent state or migration.
