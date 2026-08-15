# Phase 03 - Complete the M3 tonal palette for the 3 LIGHT_* themes

**Strategic spec:** [`../S0611_bugfix-custom-theme-contrast.md`](../S0611_bugfix-custom-theme-contrast.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** none
**Steps done:** 3 / 3

---

## Objective

For LightGreen / LightBlue / LightRed: add the full M3 tonal container/variant/outline/container-accent token set so dialogs,
menus and cards pick up the theme tint instead of neutral grey. Primary/onPrimary are already AA-correct on the light
surface, so they stay unchanged.

Exact values: research artifact 01, sections "LIGHT_GREEN / LIGHT_BLUE / LIGHT_RED".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | n/a |
| `app_v2/src/main/res/values/themes.xml` | Modified | n/a |

---

## Steps

### Step 03.1 - Add LIGHT_* tonal tokens

**Files:** `values/colors.xml`

**Prompt for developer:**

> Add the `theme_light_<c>_*` tonal tokens (same role list as Phase 02.1) for the three light themes, hex from research
> artifact 01. Do NOT change the existing `theme_light_<c>_primary`/`_on_primary`/`_background`/`_surface`/`_on_surface`.

**Verification:**

- `Grep` - `theme_light_green_surface_container_high`, `theme_light_blue_surface_container_high`, `theme_light_red_surface_container_high` present.
- `Grep` - existing `theme_light_*_primary` value lines unchanged.

**Status:** `[x]` done

---

### Step 03.2 - Wire the new roles into the 3 LIGHT_* overlays

**Files:** `values/themes.xml`

**Prompt for developer:**

> In each of `ThemeOverlay.FastMediaSorter.LightGreen/LightBlue/LightRed` add the same M3 attr items as Phase 02.2
> (`colorSurfaceContainer*`, `colorSurfaceVariant`, `colorOnSurfaceVariant`, `colorOutline`, `colorOutlineVariant`,
> `colorPrimaryContainer`, `colorOnPrimaryContainer`, `colorSecondaryContainer`, `colorOnSecondaryContainer`) mapping to the
> `theme_light_<c>_*` tokens. Keep the existing 7 items; primary/onPrimary unchanged.

**Verification:**

- `Grep` - each LIGHT_* overlay block contains `colorSurfaceContainerHigh`, `colorOnSurfaceVariant`, `colorPrimaryContainer`, `colorSecondaryContainer`.
- `Grep` - all referenced `@color/theme_light_*` tokens exist (no dangling reference).

**Status:** `[x]` done

---

### Step 03.3 - Consistency pass vs DARK_* overlay structure

**Files:** `values/themes.xml`

**Prompt for developer:**

> Diff the LIGHT_* overlay item lists against the DARK_* ones - the SAME set of M3 attrs must be present in all six (only
> the referenced token prefix differs). Fix any missing/extra item so all six overlays are role-symmetric.

**Verification:**

- `Grep` count - the attr `colorSurfaceContainerHigh` appears in exactly 6 overlay blocks.
- `Grep` count - `colorOnSurfaceVariant` appears in exactly 6 overlay blocks.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All three steps `[x] done`.
- [ ] All six overlays carry the identical M3 role set (only token prefix differs).
- [ ] No dangling `@color/theme_light_*` references.
- [ ] `temp/wcag_s0611.ps1` still `ALL CHECKS PASS`.

---

## Rollback Plan

Revert the `theme_light_*` token additions and the LIGHT_* overlay item additions.
