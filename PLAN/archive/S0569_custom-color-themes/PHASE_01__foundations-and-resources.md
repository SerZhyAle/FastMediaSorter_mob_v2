# Phase 01 - Foundations and Resources

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Add the localized spinner labels, fixed color palettes, and `ThemeOverlay.FastMediaSorter.*` style overlays for the six new themes (Dark Green, Dark Blue, Dark Red, Light Green, Light Blue, Light Red). No Kotlin and no UI wiring in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ 70 |

> No `res/layout/` file is touched: the `spinnerColorTheme` view already exists in `fragment_settings_general.xml`; only the array feeding its adapter grows. No landscape counterpart edit needed.
>
> Custom-theme colors live only in `values/colors.xml` (single default bucket). Each custom theme is a fixed-brightness palette, so it needs no `values-night/` override - night mode is forced separately in Phase 02 only to keep system bars / DayNight base resources consistent. A `@color` referenced from a `values/themes.xml` style must exist in the default bucket, so a `values-night/`-only definition would fail resource linking.

---

## Steps

### Step 01.1 - Append six localized labels to `color_theme_options`

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Append six `<item>` entries to the existing `<string-array name="color_theme_options">` in each of the three locale files, in this exact order so they map to spinner positions 3..8: Dark Green, Dark Blue, Dark Red, Light Green, Light Blue, Light Red. EN labels exactly `Dark Green`, `Dark Blue`, `Dark Red`, `Light Green`, `Light Blue`, `Light Red`. RU labels `Тёмно-зелёная`, `Тёмно-синяя`, `Тёмно-красная`, `Светло-зелёная`, `Светло-синяя`, `Светло-красная` (keep the Ё). UK labels `Темно-зелена`, `Темно-синя`, `Темно-червона`, `Світло-зелена`, `Світло-синя`, `Світло-червона`. Hand-edit the array - `set-android-string.ps1` only manages `<string>` keys, not `<string-array>` items. Keep the first three items (Auto/Light/Dark) untouched. Labels must pass the `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - `<item>Dark Green</item>` and `<item>Light Red</item>` in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `<item>Тёмно-зелёная</item>` and `<item>Светло-красная</item>` in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `<item>Темно-зелена</item>` and `<item>Світло-червона</item>` in `app_v2/src/main/res/values-uk/strings.xml`.
- Each locale's `color_theme_options` array contains exactly 9 `<item>` entries (3 existing + 6 new), in the same order.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Appended 6 `<item>` to `color_theme_options` in values/values-ru/values-uk (9 items each, frozen order). Picker nouns - §6 compliant.

---

### Step 01.2 - Define fixed custom-theme color palettes

**Files:** `app_v2/src/main/res/values/colors.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the per-theme color constants to `values/colors.xml` only (do NOT touch `values-night/colors.xml`). Use `#AARRGGBB` literals matching the existing file style. For each theme define `*_primary`, `*_primary_variant`, `*_on_primary`, `*_background`, `*_surface`, `*_on_surface`. Values:
> - `theme_dark_green_*`: primary `#FF2E7D32`, variant `#FF1B5E20`, on_primary `#FFFFFFFF`, background `#FF0F1A0F`, surface `#FF152015`, on_surface `#FFE6E6E6`.
> - `theme_dark_blue_*`: primary `#FF1565C0`, variant `#FF0D47A1`, on_primary `#FFFFFFFF`, background `#FF0F1420`, surface `#FF151B2A`, on_surface `#FFE6E6E6`.
> - `theme_dark_red_*`: primary `#FFC62828`, variant `#FF8E0000`, on_primary `#FFFFFFFF`, background `#FF1A0F0F`, surface `#FF241515`, on_surface `#FFE6E6E6`.
> - `theme_light_green_*`: primary `#FF2E7D32`, variant `#FF1B5E20`, on_primary `#FFFFFFFF`, background `#FFF1F8E9`, surface `#FFFFFFFF`, on_surface `#FF1B1B1B`.
> - `theme_light_blue_*`: primary `#FF1565C0`, variant `#FF0D47A1`, on_primary `#FFFFFFFF`, background `#FFE3F2FD`, surface `#FFFFFFFF`, on_surface `#FF1B1B1B`.
> - `theme_light_red_*`: primary `#FFC62828`, variant `#FF8E0000`, on_primary `#FFFFFFFF`, background `#FFFFEBEE`, surface `#FFFFFFFF`, on_surface `#FF1B1B1B`.

**Verification:**

- `Grep` - `name="theme_dark_green_primary"` and `name="theme_dark_green_background"` in `app_v2/src/main/res/values/colors.xml`.
- `Grep` - `name="theme_light_red_primary"` and `name="theme_light_red_surface"` in `app_v2/src/main/res/values/colors.xml`.
- `Grep` - zero new color entries added to `app_v2/src/main/res/values-night/colors.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Added 36 `theme_*` colors (6 themes x 6 attrs) to values/colors.xml only; values-night/colors.xml untouched (0 theme_ entries).

---

### Step 01.3 - Declare six theme overlays in `themes.xml`

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add six `<style name="ThemeOverlay.FastMediaSorter.<Name>" parent="">` entries (DarkGreen, DarkBlue, DarkRed, LightGreen, LightBlue, LightRed), mirroring the existing `ThemeOverlay.FastMediaSorter.CompactDialogButtons` shape. Each overlay overrides the same attributes the base `Theme.FastMediaSorter.App` defines, pointing at the matching `@color/theme_<name>_*`: `colorPrimary`, `colorPrimaryVariant`, `colorOnPrimary`, `android:colorBackground`, `colorSurface`, `colorOnSurface`, `android:windowBackground`. Reference `@color/...` only - no inline hex (Rule 19).

**Verification:**

- `Grep` - `<style name="ThemeOverlay.FastMediaSorter.DarkGreen"` and `<style name="ThemeOverlay.FastMediaSorter.LightRed"` in `app_v2/src/main/res/values/themes.xml`.
- `Grep` - `?attr` / inline `#` audit: no `>#` hex literal inside the six new overlay blocks (each item is `@color/theme_*`).
- `Grep` - `colorPrimary` and `colorSurface` present inside the `DarkGreen` overlay block.

**Status:** `[x]` done

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Added 6 `ThemeOverlay.FastMediaSorter.*` overlays (parent="") referencing `@color/theme_*` only; no inline hex. Compile validated by the consolidated final build.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (resource link must resolve every `@color/theme_*` reference).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1` reports no new EN/RU/UK parity gap introduced by this phase (note: array items are not key-prefixed, so also confirm parity by the per-locale greps above).
- [ ] Dev log entry added for the resource change set via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Overlay style ids resolve as `R.style.ThemeOverlay_FastMediaSorter_DarkGreen` .. `_LightRed` - consumed by Phase 03 `applyThemeOverlay`.
- Spinner item order is frozen (positions 3..8) - Phase 04 maps to the same order.

---

## Rollback Plan

Revert the resource edits - no Kotlin, schema, or user-facing surface changed yet.
