# Phase 01 - Button style family

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - 01.1 Verification 3/3 PASS (5 styles, M3 parents). 01.2 standard debug BUILD SUCCESSFUL (with Phase 02).

---

## Objective

Introduce the unified Material3 button style family `Widget.FastMediaSorter.Button.*` in `themes.xml`; no layout or behaviour change yet.

> **Placement note.** The project has no `app_v2/src/main/res/values/styles.xml`; all project button styles already live in `values/themes.xml` next to `Widget.FastMediaSorter.SettingsButton.*`. The new family is added there (tactical deviation from strategic §5.1 "values/styles.xml" - keeps all button styles in one authoritative file).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/themes.xml` | Modified | +~40 |

> No `layout-land` counterpart (style file, not a layout). minSdk 23 (legacy) safe - Material3 button styles ship in the bundled Material library; app theme is already `Theme.Material3.*`.

---

## Steps

### Step 01.1 - Add the unified M3 button family

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `values/themes.xml`, next to the existing `Widget.FastMediaSorter.SettingsButton.*` styles, add five new styles, each with a Material3 parent and the project text convention (`textAllCaps=false`, `letterSpacing=0`). Do not set any colour items - let theme `?attr/color*` drive colours.
>
> - `Widget.FastMediaSorter.Button.Filled` parent `Widget.Material3.Button` (primary / confirm).
> - `Widget.FastMediaSorter.Button.Tonal` parent `Widget.Material3.Button.TonalButton` (secondary emphasis).
> - `Widget.FastMediaSorter.Button.Outlined` parent `Widget.Material3.Button.OutlinedButton` (secondary).
> - `Widget.FastMediaSorter.Button.Text` parent `Widget.Material3.Button.TextButton` (low-emphasis / cancel / links).
> - `Widget.FastMediaSorter.Button.Icon` parent `Widget.Material3.Button.IconButton` (icon-only).

**Verification:**

- `Grep` - `name="Widget.FastMediaSorter.Button.Filled"` matches exactly once in `themes.xml`.
- `Grep` - each of `Button.Tonal`, `Button.Outlined`, `Button.Text`, `Button.Icon` declared once with a `Widget.Material3.*` parent.
- `Grep` - no `Widget.MaterialComponents.Button` parent appears in any `Widget.FastMediaSorter.Button.*` style.

**Status:** `[x]` done

---

### Step 01.2 - Compile gate

**Files:** (none - build only)
**Depends on:** Step 01.1

**Prompt for developer:**

> Build standard debug to confirm the new styles resolve (parents valid, no duplicate style name).

**Verification:**

- `/build` -> `standard debug` PASS.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for `themes.xml` via `.\scripts\add_to_dev_log.ps1` (deferred to Phase 05 batch if preferred).

---

## Handoff Notes to Next Phase

- `Widget.FastMediaSorter.Button.{Filled,Tonal,Outlined,Text,Icon}` now exist and are the only sanctioned button taxonomy. Phases 02-04 reference these; do not introduce ad-hoc `Widget.Material3.Button.*` or `Widget.MaterialComponents.*` directly in layouts.

---

## Rollback Plan

Revert the `themes.xml` style additions - no layout references them yet; no behaviour change.
