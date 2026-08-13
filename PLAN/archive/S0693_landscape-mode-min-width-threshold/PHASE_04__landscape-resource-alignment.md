# Phase 04 - Landscape Resource Alignment

**Strategic spec:** [`../S0693_landscape-mode-min-width-threshold.md`](../S0693_landscape-mode-min-width-threshold.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Make Android's resource selection match the node for wide-portrait: add `-w600dp` mirrors of the `-land` layout and values for the primary content/settings screens, so the same 600dp width that flips the Kotlin node also flips the inflated XML. This closes the "content slides to top-left" cause that lives in the XML layer, not in Kotlin.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - threshold is `WIDE_LAYOUT_MIN_WIDTH_DP = 600`; the resource qualifier must use the same value (`w600dp`).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-w600dp/activity_main.xml` | New (copy of `layout-land/activity_main.xml`) | copy |
| `app_v2/src/main/res/layout-w600dp/activity_browse.xml` | New (copy of `layout-land/activity_browse.xml`) | copy |
| `app_v2/src/main/res/layout-w600dp/activity_streams.xml` | New (copy of `layout-land/activity_streams.xml`) | copy |
| `app_v2/src/main/res/layout-w600dp/activity_settings.xml` | New (copy of `layout-land/activity_settings.xml`) | copy |
| `app_v2/src/main/res/values-w600dp/integers.xml` | New (copy of `values-land/integers.xml`) | copy |
| `app_v2/src/main/res/values-w600dp/bools.xml` | New (copy of `values-land/bools.xml`) | copy |
| `app_v2/src/main/res/values-w600dp/dimens.xml` | New (copy of `values-land/dimens.xml`) | copy |

> **Bounded scope (logged, not silent):** this phase mirrors only the four primary screens whose portrait layout visibly collapses on wide-portrait, plus the shared `values-land` set (grid counts, dialog dimens) that any width-aware screen reads via `R.integer`/`R.dimen`. The other ~74 `layout-land` files are intentionally NOT mirrored here - blind 78-file duplication is a maintenance hazard (two copies to keep in sync). They keep working via the `-land` qualifier (small-phone landscape) and benefit from the Phase 02/03 runtime changes. Add a screen to this list only when device test confirms it collapses on wide-portrait.
>
> **Qualifier precedence note:** `w600dp` (available width) outranks `orientation` (`land`) in Android's qualifier table, so a landscape device >=600dp picks the `-w600dp` copy and a landscape device <600dp falls back to `-land`. This mirrors the Kotlin union rule exactly; the copies are byte-identical so there is no behavior fork. No portrait `res/layout/*.xml` is edited, so the landscape-parity rule is satisfied (additive new qualifier dirs only).

---

## Steps

### Step 04.1 - Mirror primary content/settings layouts into layout-w600dp

**Files:** `app_v2/src/main/res/layout-w600dp/activity_main.xml`, `activity_browse.xml`, `activity_streams.xml`, `activity_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy each of `res/layout-land/{activity_main,activity_browse,activity_streams,activity_settings}.xml` to the same filename under `res/layout-w600dp/`, byte-for-byte. Do not hand-edit the copies. No hardcoded `="#hex"` colors may be introduced (they are copies, so none are added). These give wide-portrait devices the same wide layout the node now selects in Kotlin.

**Verification:**

- `Glob` - all four files exist under `app_v2/src/main/res/layout-w600dp/`.
- `Bash` - each `layout-w600dp/<f>.xml` is identical to its `layout-land/<f>.xml` (e.g. `diff` reports no difference).

**Status:** `[x]` done

---

### Step 04.2 - Mirror values-land into values-w600dp

**Files:** `app_v2/src/main/res/values-w600dp/integers.xml`, `bools.xml`, `dimens.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Copy `res/values-land/{integers,bools,dimens}.xml` to the same filenames under `res/values-w600dp/`, byte-for-byte. These carry the landscape grid counts (`grid_column_count_landscape`, `resource_grid_column_count`, ..), landscape bools, and dialog dimens that width-aware screens read via `resources.getInteger`/`getDimension`. Without this copy, a wide-portrait device would read the node as "wide" yet still pull the portrait integers (qualifier mismatch).

**Verification:**

- `Glob` - all three files exist under `app_v2/src/main/res/values-w600dp/`.
- `Bash` - each `values-w600dp/<f>.xml` is identical to its `values-land/<f>.xml`.

**Status:** `[x]` done

---

### Step 04.3 - Verify resource selection on a wide-portrait configuration

**Files:** (build/verify only - no source edit)
**Depends on:** Step 04.2

**Prompt for developer:**

> Build `assembleStandardDebug` and confirm `aapt2 dump resources` (or the merged-resources output) lists the new `-w600dp` layout and values configurations. This is the build-time invariant that the new qualifier set is packaged and selectable.

**Verification:**

- `/build` (`assembleStandardDebug`) exits 0.
- `Bash` - `aapt2 dump resources <apk>` (or inspection of merged resources) shows `w600dp` configurations for `activity_main` and `integers`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles and packages - run `/build` (`assembleStandardDebug`).
- [ ] `-w600dp` layout and values configurations are present in the built resources.
- [ ] Dev log entry added for the new resource directories.

---

## Handoff Notes to Next Phase

- The resource layer now flips at the same 600dp the node uses, for the four primary screens. Device test (post-implementation) decides whether any additional screen needs the same `-land` -> `-w600dp` mirror.
- Sync hazard: a future edit to any mirrored `layout-land`/`values-land` file must be applied to its `-w600dp` twin. Record this in the dev log entry.

---

## Rollback Plan

Delete the `res/layout-w600dp/` and `res/values-w600dp/` directories. Pure resource addition - no code, data, or portrait-layout change to undo.
