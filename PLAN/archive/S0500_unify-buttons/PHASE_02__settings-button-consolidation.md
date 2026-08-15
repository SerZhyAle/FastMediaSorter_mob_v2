# Phase 02 - Settings button consolidation

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - 02.1 re-parented 5 SettingsButton.* to M3/family (0 MC parents in family; Calculator.* out of scope kept MC). 02.2 collapsed OutlinedM3/TextM3 (renamed refs in general+audio portrait+land; removed 2 styles; 0 repo-wide). 02.3 swapped all settings plain `<Button>` -> MaterialButton + remapped 8 raw MC styles to SettingsButton family (0 plain Button, 0 MC styles in settings). 02.4 standard debug BUILD SUCCESSFUL 49s.

---

## Objective

Collapse the 7-style `Widget.FastMediaSorter.SettingsButton.*` family to a single Material3 generation (no MaterialComponents/Material3 split, no duplicate names) and convert the remaining plain `<Button>` widgets in the settings fragments to `MaterialButton`, keeping every id and behaviour unchanged.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`Widget.FastMediaSorter.Button.*` family exists).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/themes.xml` | Modified | -~20 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout/fragment_settings_audio.xml` | Modified | ≤ 1000 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 1000 |
| `app_v2/src/main/res/layout/fragment_settings_video.xml` + `layout-land/` | Modified | ≤ 1000 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` + `layout-land/` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout/fragment_settings_images.xml` + `layout-land/` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout/fragment_settings_other.xml` + `layout-land/` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout/fragment_settings_documents.xml` | Modified | ≤ 800 |

> **Landscape parity (Rule 11):** every settings layout above edited in portrait + `layout-land/` together. `fragment_settings_documents.xml` has no `layout-land/` twin (single-orientation layout) - portrait only is correct here.
> **Backup (Rule 5):** `fragment_settings_general.xml`, `fragment_settings_audio.xml`, `fragment_settings_video.xml` (and land twins) exceed 500 LOC - timestamped copy in `temp/` before editing.
> **Authoritative file set:** before each step, re-run the grep in its Verification to catch any settings layout not pre-listed here.

---

## Steps

### Step 02.1 - Re-parent the SettingsButton family onto Material3

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Re-point the four MaterialComponents-parented settings styles to Material3, basing the non-icon ones on the Phase 01 family:
>
> - `SettingsButton.Outlined` -> parent `Widget.FastMediaSorter.Button.Outlined`.
> - `SettingsButton.Text` -> parent `Widget.FastMediaSorter.Button.Text`.
> - `SettingsButton.Tonal` -> parent `Widget.FastMediaSorter.Button.Tonal` (already M3; re-base for consistency).
> - `SettingsButton.OutlinedIcon` -> parent `Widget.Material3.Button.OutlinedButton.Icon`.
> - `SettingsButton.TextIcon` -> parent `Widget.Material3.Button.TextButton.Icon`.
>
> Keep every existing `<item>` override (text size, `textAllCaps=false`, etc.). Do not change style names in this step.

**Verification:**

- `Grep` - zero `Widget.MaterialComponents.Button` parents remain in the `SettingsButton.*` family (the `Widget.FastMediaSorter.Calculator.*` styles are out of scope and keep their MC parents - do not touch them).
- `Grep` - `SettingsButton.Outlined`, `SettingsButton.Text`, `SettingsButton.Tonal` each parent a `Widget.FastMediaSorter.Button.*` style.

**Status:** `[x]` done

---

### Step 02.2 - Collapse the OutlinedM3 / TextM3 duplicates

**Files:** `themes.xml`, `fragment_settings_general.xml` (+land), `fragment_settings_audio.xml` (+land)
**Depends on:** Step 02.1

**Prompt for developer:**

> After 02.1, `SettingsButton.OutlinedM3` is identical to `SettingsButton.Outlined` and `SettingsButton.TextM3` to `SettingsButton.Text`. Rename every layout reference `SettingsButton.OutlinedM3` -> `SettingsButton.Outlined` and `SettingsButton.TextM3` -> `SettingsButton.Text` (only `fragment_settings_general.xml` and `fragment_settings_audio.xml` plus their `layout-land/` twins reference them), then delete the now-orphaned `SettingsButton.OutlinedM3` and `SettingsButton.TextM3` style declarations from `themes.xml`.

**Verification:**

- `Grep` (whole repo, `*.xml`) - zero hits for `SettingsButton.OutlinedM3` and `SettingsButton.TextM3` (neither references nor declarations).
- `Grep` - `themes.xml` declares exactly five `SettingsButton.*` styles (`Outlined`, `OutlinedIcon`, `Text`, `TextIcon`, `Tonal`).

**Status:** `[x]` done

---

### Step 02.3 - Convert plain `<Button>` in settings fragments to MaterialButton

**Files:** all settings layouts in "Files Touched" (portrait + land)
**Depends on:** Step 02.1

**Prompt for developer:**

> In the settings fragments, replace each plain `<Button .../>` element (these already carry a `style="@style/Widget.FastMediaSorter.SettingsButton.*"`) with `<com.google.android.material.button.MaterialButton .../>`, preserving the existing `style`, `android:id`, all attributes, and the closing tag. Do not change ids - `GeneralSettingsViewSetupHelper.kt` and other setup helpers look them up by id and rely on id parity. Do not add or remove buttons.

**Verification:**

- `Grep` (`-oE "<Button\b"`) - zero plain `<Button>` elements remain in any `fragment_settings_*.xml` (portrait + land).
- `Grep` - id count unchanged: every `android:id="@+id/btn*"` present before the edit is still present (spot-check `btnResetSettings`, `btnResetGeneralSection`).

**Status:** `[x]` done

---

### Step 02.4 - Compile gate

**Files:** (none - build only)
**Depends on:** Steps 02.1-02.3

**Prompt for developer:**

> Build standard debug. A `Button` -> `MaterialButton` widget-class change shifts the generated ViewBinding field type from `Button` to `MaterialButton` (a `Button` subtype); the build proves all Kotlin usages still type-check.

**Verification:**

- `/build` -> `standard debug` PASS.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `SettingsButton.OutlinedM3` / `SettingsButton.TextM3` returns zero hits.
- [ ] No plain `<Button>` remains in `fragment_settings_*.xml`.
- [ ] Dev log entry added for the touched file batch (may defer to Phase 05 batch).

---

## Handoff Notes to Next Phase

- Settings button family is now five M3 styles; `Button`/`MaterialButton` parity holds in settings. The `Widget.FastMediaSorter.Button.*` family from Phase 01 is unused by settings (settings keep their `SettingsButton.*` sub-family that now extends it) and remains available for Phases 03-04.

---

## Rollback Plan

Revert the phase commit(s). Pure layout/style change - no data migration, no behaviour or id change.
