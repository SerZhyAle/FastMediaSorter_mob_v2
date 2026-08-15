# Phase 01 — Landscape Settings Dimens

**Strategic spec:** [../S0044_settings-layout-compactness.md](../S0044_settings-layout-compactness.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Establish landscape-only spacing and sizing overrides for the settings area so every later layout edit can consume shared resource values instead of hardcoded numbers. The invariant is that settings-specific land compactness is expressed through `values-land/dimens.xml`, not ad-hoc `dp` or `sp` literals inside layout files.

## Files Touched

| File | Action | Note |
|------|--------|------|
| `app_v2/src/main/res/values-land/dimens.xml` | Modified | Add settings-specific compact landscape overrides used by settings layouts. |

---

## Steps

### Step 1.1 — Add settings-specific land overrides

**Status:** `[x] done`
**Depends on:** none
**Blocks:** Step 1.2

**Prompt for developer:**

Add landscape overrides for the settings dimension keys already used by settings layouts. Keep scope limited to settings resources such as `settings_fragment_padding`, `settings_item_min_height`, `settings_item_margin_bottom`, `settings_margin_standard`, `settings_padding`, `settings_switch_margin_end`, `settings_help_icon_margin`, and `settings_text_input_width`. Choose compact but readable land values; do not change unrelated app-wide dimensions.

**Files Touched:** `app_v2/src/main/res/values-land/dimens.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/values-land/dimens.xml -> 1 result
Grep: "name=\"settings_fragment_padding\"" in app_v2/src/main/res/values-land/dimens.xml -> 1 hit
Grep: "name=\"settings_text_input_width\"" in app_v2/src/main/res/values-land/dimens.xml -> 1 hit
```

### Step 1.2 — Keep overrides settings-scoped only

**Status:** `[x] done`
**Depends on:** Step 1.1
**Blocks:** Phase 02

**Prompt for developer:**

Review the updated `values-land/dimens.xml` block and ensure the compactness work is encoded only through settings-specific resource names. Do not redefine generic margins like `margin_small` or unrelated dialog/player dimensions as part of S0044.

**Files Touched:** `app_v2/src/main/res/values-land/dimens.xml`

**Verification:**

```text
Grep: "name=\"margin_small\"" in app_v2/src/main/res/values-land/dimens.xml -> 0 hits
Grep: "name=\"settings_item_min_height\"" in app_v2/src/main/res/values-land/dimens.xml -> 1 hit
Grep: "name=\"settings_help_icon_margin\"" in app_v2/src/main/res/values-land/dimens.xml -> 1 hit
```

---

## Phase Done Criteria

- [x] Project compiles (BUILD-REQUIRED — run `/build standard-debug`).
- [x] `app_v2/src/main/res/values-land/dimens.xml` contains landscape overrides for settings-only keys.
- [x] No generic `margin_small` override was added to `values-land/dimens.xml`.

---

## Step Log

- 2026-05-01 — Step 1.1 done. Added landscape-only settings dimension overrides in `app_v2/src/main/res/values-land/dimens.xml`. Verification PASS: `settings_fragment_padding`, `settings_text_input_width`.
- 2026-05-01 — Step 1.2 done. Confirmed the compactness block stays settings-scoped only. Verification PASS: no `margin_small` override added.
- 2026-05-01 — Phase done. `assembleStandardDebug` PASS after Phase 01 resource changes.
