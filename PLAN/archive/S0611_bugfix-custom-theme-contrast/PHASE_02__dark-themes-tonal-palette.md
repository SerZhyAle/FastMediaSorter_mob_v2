# Phase 02 - Complete the M3 tonal palette for the 3 DARK_* themes

**Strategic spec:** [`../S0611_bugfix-custom-theme-contrast.md`](../S0611_bugfix-custom-theme-contrast.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** none
**Steps done:** 4 / 4

> Note: research said no `layout-land` counterpart for the player - WRONG. `res/layout-land/activity_player_unified.xml`
> exists and carried the same hardcoded white title; it was fixed identically (Rule 11 parity). Also added
> `app:navigationIconTint="?attr/colorOnPrimary"` so the back arrow stays visible on the now-light DARK_* toolbar.

---

## Objective

For DarkGreen / DarkBlue / DarkRed: add the full M3 tonal container/variant/outline/container-accent token set (fixed
brightness), lighten `colorPrimary` to a light tone with dark `colorOnPrimary`, wire every new role into the 3 overlays,
and fix the one hardcoded white toolbar title that the primary change would break.

Exact values: research artifact 01, sections "DARK_GREEN / DARK_BLUE / DARK_RED" + "Player toolbar fix".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | n/a |
| `app_v2/src/main/res/values/themes.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | n/a |

---

## Steps

### Step 02.1 - Add DARK_* tonal tokens; update primary/onPrimary values

**Files:** `values/colors.xml`

**Prompt for developer:**

> For each of the three dark themes add the new fixed-brightness tokens following the existing `theme_dark_<c>_*` naming:
> `theme_dark_<c>_surface_container_lowest/_low/_container/_high/_highest`, `theme_dark_<c>_surface_variant`,
> `theme_dark_<c>_on_surface_variant`, `theme_dark_<c>_outline`, `theme_dark_<c>_outline_variant`,
> `theme_dark_<c>_primary_container`, `theme_dark_<c>_on_primary_container`, `theme_dark_<c>_secondary_container`,
> `theme_dark_<c>_on_secondary_container`. Take hex from research artifact 01. ALSO update the value of the existing
> `theme_dark_<c>_primary` -> lightened tone and `theme_dark_<c>_on_primary` -> dark tone (DarkGreen `#FF81C784`/`#FF0A2E0A`,
> DarkBlue `#FF64B5F6`/`#FF06243B`, DarkRed `#FFEF9A9A`/`#FF3B0A0A`). Leave `theme_dark_<c>_primary_variant`, `_background`,
> `_surface`, `_on_surface` as-is.

**Verification:**

- `Grep` - `theme_dark_green_surface_container_high`, `theme_dark_blue_surface_container_high`, `theme_dark_red_surface_container_high` all present.
- `Grep` - `theme_dark_green_primary">#FF81C784`, `theme_dark_blue_primary">#FF64B5F6`, `theme_dark_red_primary">#FFEF9A9A`.
- `Grep` - `theme_dark_green_on_primary">#FF0A2E0A` (and blue/red counterparts).

**Status:** `[x]` done

---

### Step 02.2 - Wire the new roles into the 3 DARK_* overlays

**Files:** `values/themes.xml`

**Prompt for developer:**

> In each of `ThemeOverlay.FastMediaSorter.DarkGreen/DarkBlue/DarkRed` add the M3 attr items mapping to the new tokens:
> `colorSurfaceContainerLowest/Low/colorSurfaceContainer/High/Highest`, `colorSurfaceVariant`, `colorOnSurfaceVariant`,
> `colorOutline`, `colorOutlineVariant`, `colorPrimaryContainer`, `colorOnPrimaryContainer`, `colorSecondaryContainer`,
> `colorOnSecondaryContainer`. Keep the existing 7 items. The primary/onPrimary items already reference
> `theme_dark_<c>_primary`/`_on_primary` (values changed in 02.1) - no item edit needed for those.

**Verification:**

- `Grep` - each DARK_* overlay block contains `colorSurfaceContainerHigh`, `colorOnSurfaceVariant`, `colorOutline`, `colorPrimaryContainer`, `colorSecondaryContainer`.
- `Grep` - all referenced `@color/theme_dark_*` tokens exist in `colors.xml` (no dangling reference).

**Status:** `[x]` done

---

### Step 02.3 - Fix the hardcoded white player-toolbar title

**Files:** `layout/activity_player_unified.xml`

**Prompt for developer:**

> Change the MaterialToolbar `app:titleTextColor` from `@color/white` to `?attr/colorOnPrimary` so the title flips to dark
> on the now-light DARK_* primary. No layout-land counterpart exists for this file (verify). Only this one attribute.

**Verification:**

- `Grep` - `activity_player_unified.xml` toolbar uses `app:titleTextColor="?attr/colorOnPrimary"`.
- `Glob` - confirm no `res/layout-land/activity_player_unified.xml` exists (no mirror needed).

**Status:** `[x]` done

---

### Step 02.4 - Confirm no other hardcoded on-primary text on toolbars

**Files:** `app_v2/src/main/res/layout*/`

**Prompt for developer:**

> Grep toolbars/app-bars for `titleTextColor="@color/white"` or icon tints hardcoded to white where the background is
> `?attr/colorPrimary`. Research found only the player; confirm. Any additional hit on a `?attr/colorPrimary` bar -> migrate
> to `?attr/colorOnPrimary` in the same phase (and its layout-land mirror).

**Verification:**

- `Grep` - `titleTextColor="@color/white"` across `res/layout*/` returns zero hits on `?attr/colorPrimary` toolbars (player fixed).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All four steps `[x] done`.
- [ ] No dangling `@color/theme_dark_*` references (every overlay item resolves).
- [ ] `temp/wcag_s0611.ps1` still `ALL CHECKS PASS` (values match the artifact).
- [ ] Neuroslop gate passes (tokens/attrs only, no new hex in layout).

---

## Rollback Plan

Revert `colors.xml` additions + the value changes to `theme_dark_*_primary/_on_primary`, the overlay item additions, and
the one toolbar attribute. Themes fall back to the prior (grey-dialog) behavior.

---

## Handoff Notes to Next Phase

Phase 03 repeats the token+overlay work for the LIGHT_* themes (primary unchanged there). Phase 04 runs the numeric gate.
