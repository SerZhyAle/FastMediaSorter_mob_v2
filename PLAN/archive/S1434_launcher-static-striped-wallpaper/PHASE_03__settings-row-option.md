# Phase 03 - Fourth option in the desktop-wallpaper row

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Make the new mode selectable: a trilingual label plus a fourth entry in the existing wallpaper dropdown, in the same position the token occupies in `LAUNCHER_WALLPAPER_MODES`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 line |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 line |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 line |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 260 |

> No layout file changes: the row is the existing `rowLauncherWallpaper` in `dialog_launcher_settings.xml`, whose `res/layout-land` counterpart carries the same widget and needs no edit because only the entry list changes, and that list is set in code.
>
> **Flavor placement.** The dialog lives in `src/main` and is already shipped in every flavor; the launcher row it hosts is gated by the launcher capability contract, not by a flavor guard here.

---

## Steps

### Step 03.1 - Add the trilingual label

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the key `launcher_settings_wallpaper_stripes` to all three locales in one call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_settings_wallpaper_stripes -En "Still stripes" -Ru "Обои в стиле полос" -Uk "Шпалери в стилі смуг"`. The Russian wording is the owner's own name for the option and is not to be reworded. Check the result against `docs/COMMUNICATION_POLICY.md` §2 for the label formula and §6 for the tone checklist before moving on.

**Why:**

Strategic §3.2 requires the new option's label in EN, RU and UK at once, and §3.3 records that the owner named the option himself in Russian, with the other two locales translated from it.

**Verification:**

- `Grep` - `launcher_settings_wallpaper_stripes` matches exactly once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_wallpaper"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 03.2 - Offer the label in the dropdown

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Append `getText(R.string.launcher_settings_wallpaper_stripes)` as the fourth element of the `binding.rowLauncherWallpaper.setEntries(..)` list, after the image label. Leave the selection listener alone: its `when` sends every mode except `IMAGE` through `viewModel.applyLauncherWallpaperMode(mode)`, which is already correct for the new token.

**Why:**

Strategic §2 requires a fourth entry in the dropdown, and the row maps a selected index onto `LAUNCHER_WALLPAPER_MODES` positionally, so a label list shorter than that token list would make the fourth token unreachable and could select an index the dropdown does not have.

**Verification:**

- `Grep` - `launcher_settings_wallpaper_stripes` matches exactly once in `LauncherSettingsDialogFragment.kt`, inside the `setEntries` call.
- `Grep` - the `setEntries` list has four `getText(` elements in the order branded, none, image, stripes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_wallpaper"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The feature is complete for the user at the end of this phase: the dropdown offers four options and the fourth one renders. What remains is the regression test and the generated documentation.

---

## Rollback Plan

Revert phase commit(s) - the string key and one list element; a stored `STRIPES` token left behind keeps working, it simply cannot be re-selected from the dialog.
