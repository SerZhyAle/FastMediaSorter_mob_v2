# Phase 01 - Phone landscape buckets

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Create `dimens.xml` in the two existing combined buckets so the landscape values the project always
intended actually win on phones. No declaration is removed in this phase - the tree stays correct at
every step.

---

## Prerequisites

- [ ] Strategic §6 answers 1-3 are Resolved (they are - owner, 2026-07-29).
- [ ] `app_v2/src/main/res/values-sw320dp-land/` and `values-sw480dp-land/` exist (they do - both hold `integers.xml`).
- [ ] The per-key decision table in [`INDEX.md`](INDEX.md) is the single source for values - do not re-derive them.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-sw320dp-land/dimens.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values-sw480dp-land/dimens.xml` | New | ≤ 35 |
| `app_v2/src/main/res/values-sw600dp/dimens.xml` | Modified - added by the phase-boundary audit | ≤ 200 |

> No layout file changes in this phase, so the landscape-parity rule (CLAUDE.md Rule 11) does not apply.
> These are `values-*` resource buckets, not `layout*`.

---

## Steps

### Step 01.1 - Create the sw320dp-land dimension bucket

**Files:** `app_v2/src/main/res/values-sw320dp-land/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the file with exactly the seven keys the decision table marks for `sw320dp-land`:
> `empty_state_padding` 24dp, `dialog_padding_large` 20dp, `player_controls_padding` 6dp,
> `welcome_page_padding` 16dp, `welcome_icon_margin_top` 4dp, `welcome_title_text_size` 20sp,
> `welcome_description_margin_top` 4dp. Do not add the four keys the table excludes - three of them
> already resolve to the landscape value at this threshold and `padding_xxlarge` has no phone
> declaration here at all. Open the file with a comment in the same voice as the sibling
> `integers.xml` in this folder: state that smallestWidth outranks orientation, so `values-sw320dp`
> silently beat `values-land`, and this bucket restores the intended landscape value.

**Verification:**

- `Glob` - `app_v2/src/main/res/values-sw320dp-land/dimens.xml` exists.
- `Grep` - the file contains exactly 7 `<dimen name=` lines.
- `Grep` - `name="welcome_title_text_size">20sp<` present.
- `Grep` - `name="padding_xxlarge"` returns zero hits in this file.
- `Grep` - `name="item_padding_vertical"` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. Files: `app_v2/src/main/res/values-sw320dp-land/dimens.xml` (New, 7 keys). Dev log recorded.

---

### Step 01.2 - Create the sw480dp-land dimension bucket

**Files:** `app_v2/src/main/res/values-sw480dp-land/dimens.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the file with the ten keys the decision table marks for `sw480dp-land`: the seven from step
> 01.1 plus `padding_xxlarge` 16dp, `welcome_icon_size` 31dp and `welcome_title_margin_top` 6dp.
> These three are here and not in the sw320dp bucket because at the 480dp threshold the sw value
> differs from the landscape value, while at 320dp it either matches or does not exist. Use the same
> opening comment style as step 01.1.

**Verification:**

- `Glob` - `app_v2/src/main/res/values-sw480dp-land/dimens.xml` exists.
- `Grep` - the file contains exactly 10 `<dimen name=` lines.
- `Grep` - `name="padding_xxlarge">16dp<` present.
- `Grep` - `name="welcome_icon_size">31dp<` present.
- `Grep` - `name="item_padding_vertical"` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. Files: `app_v2/src/main/res/values-sw480dp-land/dimens.xml` (New, 10 keys). Dev log recorded.

---

### Step 01.3 - Prove the buckets resolve as intended

**Files:** `app_v2/src/main/res/values-sw320dp-land/dimens.xml`, `app_v2/src/main/res/values-sw480dp-land/dimens.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Build `standard debug` through `/build` so aapt2 validates both new resource files. A malformed
> dimension or a duplicate key inside one bucket fails the resource merge, which is the only static
> proof available for resource buckets.

**Verification:**

- `/build` -> `standard debug` exits 0.
- `Grep` - no `duplicate value for resource` or `resource merge` error in the build output.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `a.ps1 dq` BUILD SUCCESSFUL in 10s, exit 0; `mergeStandardDebugResources` + `processStandardDebugResources` executed. Log `temp/build_debug_20260731_104204.log`: 0 duplicate-resource, 0 merge errors.
- 2026-07-31 - AUDIT-FIX (P1): step 01.2 silently moved `padding_xxlarge` on tablets in landscape from 18dp to 16dp. It is the only restored key that neither `values-sw600dp` nor `values-sw720dp` declares, so a tablet scored it through `values-sw480dp`; the new `values-sw480dp-land` ties that smallestWidth score and wins on orientation. Declared `padding_xxlarge` 18dp in `values-sw600dp` to freeze the value tablets already resolved. Rebuild below re-proves the phase.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` BUILD SUCCESSFUL in 9s after the audit fix (`temp/build_debug_20260731_104519.log`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` - three files, all PASS.
- [x] Public API unchanged - catalog regeneration not required.
- [x] Phase-boundary audit run - one P1 found and fixed in-phase (`padding_xxlarge` tablet leak); no unresolved findings.

---

## Handoff Notes to Next Phase

Phone landscape now resolves to the intended values through the combined buckets. Every declaration
this phase restored is therefore redundant in `values-land`, which is what Phase 02 removes. Nothing
was deleted yet, so at this point the tree is correct but says the same thing twice.

---

## Rollback Plan

Delete the two new files. No other file changed, so the tree returns to its previous resolution exactly.
