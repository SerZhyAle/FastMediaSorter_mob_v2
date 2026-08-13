# Phase 02 - Settings Button UI

**Strategic spec:** [`../S0335_settings-system-info-dialog.md`](../S0335_settings-system-info-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add the `btnSystemInfo` button to the diagnostics block of the General settings layout (portrait + landscape) and the localized button-label and dialog-title strings. No click handler yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> Landscape counterpart `res/layout-land/fragment_settings_general.xml` exists - both variants are edited in this phase (Step 02.1 portrait, Step 02.2 landscape).

---

## Steps

### Step 02.1 - Add `btnSystemInfo` to portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the diagnostics buttons block (the `LinearLayout` that holds `btnShareLogs` / `btnSaveLogs`, around the log buttons region), add a `com.google.android.material.button.MaterialButton` with `android:id="@+id/btnSystemInfo"`, `style="@style/Widget.FastMediaSorter.SettingsButton.Outlined"`, `android:text="@string/settings_system_info_button"`, the same `android:textSize` as the sibling log buttons, and a suitable existing icon (e.g. an info/device drawable already in `res/drawable`). Match the surrounding visual system (style, spacing, margins). Ensure focus reachability: `android:focusable="true"` and `android:clickable="true"` (or rely on MaterialButton defaults) and a logical `nextFocus*` chain consistent with the sibling buttons so keyboard / D-pad traversal includes it.

**Verification:**

- `Grep` - `@+id/btnSystemInfo` matches exactly once in the portrait file.
- `Grep` - `@string/settings_system_info_button` referenced in the portrait file.
- Layout build passes (covered by Phase Done Criteria `/build`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS (btnSystemInfo=1, string ref=1). Placed in APP DATA diagnostics row next to btnShareLogs/btnSaveLogs, Outlined style, ic_info. Dev log recorded.

---

### Step 02.2 - Add `btnSystemInfo` to landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the equivalent change to the landscape variant: add the same `btnSystemInfo` MaterialButton into the corresponding diagnostics buttons block, mirroring the portrait styling, focus, and ordering. Do not leave the landscape layout without the button.

**Verification:**

- `Grep` - `@+id/btnSystemInfo` matches exactly once in the landscape file.
- `Grep` - `@string/settings_system_info_button` referenced in the landscape file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS (btnSystemInfo=1, string ref=1). Landscape parity applied. Dev log recorded.

---

### Step 02.3 - Add localized strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add two string keys in all three locale files: `settings_system_info_button` (button label, e.g. EN "System info") and `settings_system_info_title` (dialog title, e.g. EN "System information"). Follow the existing `settings_*` key convention. Apply Author Style: `..` not `...`, and `ё`/`Ё` where applicable in Russian. Run the strings through `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) - both strings must pass the tone checklist before commit.

**Verification:**

- `Grep` - `settings_system_info_button` present in each of the three `strings.xml` files.
- `Grep` - `settings_system_info_title` present in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_system_info"` exits 0 (expected: 0 | actual: record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. check_strings_localized EXIT=0 (expected 0 | actual 0); both keys present EN/RU/UK. Tone checklist PASS (neutral descriptive labels). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Both portrait and landscape layouts contain `btnSystemInfo` (landscape parity satisfied).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The button `btnSystemInfo` exists in both orientations with a localized label; the dialog title string `settings_system_info_title` is available. Phase 03 binds the click handler.

---

## Rollback Plan

Revert phase commit(s) - layout/string additions only, no data migration or behaviour change (button is inert until Phase 03).
