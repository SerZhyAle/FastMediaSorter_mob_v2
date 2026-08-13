# Phase 03 - Default OFF & Settings Migration

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-09
**Completed:** 2026-06-09

> **Revised 2026-06-09 (model correction):** the persistence layer writes every text-recognition key on each save (`TextRecognitionSettingsStore.write`) and reads with a hardcoded `?: true` fallback, so an existing user already has `enable_translation`/`enable_ocr` persisted - a default-true value is indistinguishable from a user choice. Therefore ADR-2 ("preserve previously user-set values on update") is satisfied simply by flipping the read default to `false`: a persisted key (existing users) is preserved; only a fresh store (new install, key absent) gets `false`. No migration write and no "previouslyEnabled" flag are needed (the original 03.2/03.4 assumed an absent-key-means-default model that does not hold here); the "first enable after update" message is handled by Phase 06's enable-intercept, not a settings flag.

---

## Objective

Make OCR/translation OFF by default for fresh installs and every device-profile first-run preset, without overwriting values existing users already set (strategic Pillar B, ADR-2).

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/TextRecognitionSettingsStore.kt` | Modified | ≤ 120 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | - |

---

## Steps

### Step 03.1 - Flip the default values

**Files:** `domain/model/AppSettings.kt`, `data/repository/settings/TextRecognitionSettingsStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the fresh-install defaults of `enableTranslation` and `enableOcr` from `true` to `false` in both the `AppSettings` data class and - the actual read fallback - `TextRecognitionSettingsStore.read` (`preferences[KEY_...] ?: false`). A persisted key keeps its stored value, so existing users are not perturbed (ADR-2). Leave all other text-recognition fields unchanged.

**Verification:**

- `Grep` - `val enableTranslation: Boolean = false` and `val enableOcr: Boolean = false` both present in `AppSettings.kt`.
- `Grep` - `KEY_ENABLE_TRANSLATION] ?: false` and `KEY_ENABLE_OCR] ?: false` both present in `TextRecognitionSettingsStore.kt`.
- `Grep` - no remaining `KEY_ENABLE_TRANSLATION] ?: true` / `KEY_ENABLE_OCR] ?: true` in that file.

**Status:** `[x]` done

---

### Step 03.2 - First-run device-profile presets set OFF

**Files:** `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `device_profile_presets.csv`, set every column of the `enableTranslation` and `enableOcr` rows to `FALSE`, so no device-profile first-run preset re-enables OCR/translation for any profile (the applier copies these into settings on first run). Do not touch other rows.

**Verification:**

- `Grep` - the `enableTranslation` and `enableOcr` rows contain zero `TRUE` values.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Fresh installs and all profiles start OFF; existing users' persisted choices are preserved by key presence. Phase 06's enable-intercept shows the download offer (and the "this is now on-demand" explanation) when the user turns a capability on.

---

## Rollback Plan

Revert phase commit(s). Default-value + CSV change only; reverting restores prior defaults without data loss (no schema version bump).

---

## Step Log

- 2026-06-09 - Steps 03.1-03.2 PASS. Modified: `AppSettings.kt` (`enableTranslation`/`enableOcr` defaults → false), `TextRecognitionSettingsStore.kt` (read fallback `?: false` - the actual default source), `device_profile_presets.csv` (both toggle rows all FALSE). Model correction: existing users' persisted keys preserve their value (ADR-2); no migration write / no "previouslyEnabled" flag (original 03.2/03.4 dropped). Build: `assembleStandardDebug` BUILD SUCCESSFUL.
