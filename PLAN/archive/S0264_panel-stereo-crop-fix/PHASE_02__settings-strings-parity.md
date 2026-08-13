# Phase 02 - Settings Strings Parity

**Strategic spec:** [`../S0264_panel-stereo-crop-fix.md`](../S0264_panel-stereo-crop-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Align the saved-settings fallback and localized settings copy with the new single-eye behavior.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `docs/COMMUNICATION_POLICY.md` has been reviewed before editing user-facing text.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 500 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Normalize single-eye fallback default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Make the single-eye setting default to ON for fresh installs across flavors while preserving any value already stored by existing users. Keep the behavior change limited to fallback/default logic only.

**Verification:**

- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` contains `val panelStereoSingleEye: Boolean = true`
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` contains `panelStereoSingleEye = preferences[KEY_PANEL_STEREO_SINGLE_EYE] ?: true`
- `Grep` - both files return zero hits for `SUPPORT_VR_PLAYER` near `panelStereoSingleEye`

**Status:** `[x]` done

---

### Step 02.2 - Update localized summary copy

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update the single-eye setting summary so it matches the new default behavior in all three locales. Strings must stay consistent with `docs/COMMUNICATION_POLICY.md` §2 and pass the tone checklist from §6.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/strings.xml` contains `pref_panel_stereo_single_eye_summary`
- `Grep` - `app_v2/src/main/res/values-ru/strings.xml` contains `pref_panel_stereo_single_eye_summary`
- `Grep` - `app_v2/src/main/res/values-uk/strings.xml` contains `pref_panel_stereo_single_eye_summary`
- `PowerShell` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "pref_panel_stereo_single_eye"` exits 0
- `Verification predicate` - Strings pass `COMMUNICATION_POLICY.md` §6 checklist

**Status:** `[x]` done

---

### Step 02.3 - Validate settings/default parity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run structural validation for the fallback and strings changes, record expected vs actual values, and confirm there is no remaining wording that claims VR-capable builds default to OFF.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/strings.xml app_v2/src/main/res/values-ru/strings.xml app_v2/src/main/res/values-uk/strings.xml` return zero hits for `OFF for VR`
- `Grep` - `app_v2/src/main/res/values/strings.xml app_v2/src/main/res/values-ru/strings.xml app_v2/src/main/res/values-uk/strings.xml` return zero hits for `non-VR`
- `PowerShell` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "pref_panel_stereo_single_eye"` exits 0

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Runtime behavior and user-facing settings copy now agree. Final phase can focus on tactical bookkeeping, catalog refresh, and audit-readiness only.

---

## Rollback Plan

Revert phase commit(s) - no schema migration or irreversible data change.
