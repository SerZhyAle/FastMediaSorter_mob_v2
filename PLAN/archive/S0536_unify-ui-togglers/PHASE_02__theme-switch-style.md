# Phase 02 - Theme switch style

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Introduce a project-level Material3 switch style and wire it to the app theme via `materialSwitchStyle`, so every `MaterialSwitch` that stays outside the canonical component renders with one consistent look.

---

## Prerequisites

- [ ] App theme parent is `Theme.Material3.*` (verified: `Theme.FastMediaSorter.App` → `Theme.Material3.DayNight.NoActionBar`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/themes.xml` | Modified | n/a |

> No `layout` edits in this phase - no landscape counterpart concern.

---

## Steps

### Step 02.1 - Define Widget.FastMediaSorter.Switch and bind materialSwitchStyle

**Files:** `app_v2/src/main/res/values/themes.xml`

**Prompt for developer:**

> Add a named switch style `Widget.FastMediaSorter.Switch` with parent `Widget.Material3.CompoundButton.MaterialSwitch`, placed alongside the existing `Widget.FastMediaSorter.*` style taxonomy. Add only theme-color items if a deviation from the Material3 default is wanted; otherwise leave the style body empty (parent-only). Then inside `style name="Theme.FastMediaSorter.App"` add `<item name="materialSwitchStyle">@style/Widget.FastMediaSorter.Switch</item>` next to the other component-default items, before the closing `</style>`. Do NOT add the legacy `switchStyle` attr - it targets `SwitchMaterial`/`SwitchCompat`, a different widget; `materialSwitchStyle` targets `MaterialSwitch` only. Use `?attr`/`@color` tokens for any color, never hardcoded hex.

**Verification:**

- `Grep` - `<style name="Widget.FastMediaSorter.Switch"` matches once in `themes.xml`.
- `Grep` - `parent="Widget.Material3.CompoundButton.MaterialSwitch"` present.
- `Grep` - `<item name="materialSwitchStyle">@style/Widget.FastMediaSorter.Switch</item>` present inside the app theme block.
- `Grep` - no hardcoded `="#` hex inside the new style.
- `.\a.ps1 fr` (resources/manifest) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 5/5 PASS. themes.xml: `materialSwitchStyle` item in app theme (line 42); `Widget.FastMediaSorter.Switch` parent-only style (line 88, no hardcoded hex); `.\a.ps1 fr` -> BUILD SUCCESSFUL in 12s, Fast check passed. Dev log batched to Phase 05.2 per plan.

---

## Phase Done Criteria

- [x] `Step 02.1` is `[x] done`.
- [x] Project resources compile - `.\a.ps1 fr` PASS (BUILD SUCCESSFUL in 12s).
- [x] `Grep` for `materialSwitchStyle` returns exactly one hit (the app theme).
- [ ] Dev log entry added for `themes.xml`. - batched to Phase 05.2 per plan.

---

## Handoff Notes to Next Phase

`MaterialSwitch` instances outside the canonical component now inherit `Widget.FastMediaSorter.Switch`. Phase 03 relies on this for any on/off switch it leaves bare (e.g. the compact list-item `switchEnabled` if wrapping breaks row compactness).

---

## Rollback Plan

Revert the phase commit - removing the style and the theme item; no behavior depends on it beyond visual styling.
