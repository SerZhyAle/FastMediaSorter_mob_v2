# Phase 04 - RTL layout hygiene

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of the registry
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Remove the last absolute `Left`/`Right` layout attributes so Arabic and Urdu mirror correctly, and keep them from coming back.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-w600dp/activity_browse.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/dialog_scheduled_operation.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 600 |
| `scripts/quality/assert-rtl-layout-attrs.ps1` | New | ≤ 120 |

> Both orientation variants of every affected layout are listed, so Rule 11 is satisfied within the phase. `layout/activity_browse.xml` and `layout/fragment_settings_general.xml` carry no absolute attributes today - if a grep finds any at implementation time, add them here rather than fixing them silently.

---

## Steps

### Step 04.1 - Mirror the five layouts

**Files:** the five layout files above
**Depends on:** - start of phase

**Prompt for developer:**

> Replace every absolute attribute with its start/end counterpart: `layout_marginLeft` -> `layout_marginStart`, `layout_alignParentRight` -> `layout_alignParentEnd`, `layout_toRightOf` -> `layout_toEndOf`, `paddingLeft` -> `paddingStart`, `gravity="left"` -> `gravity="start"`, and so on for right. Where an attribute is genuinely about physical direction rather than reading order - a rotation, a drawable edge tied to a fixed diagram - keep it and say why in a one-line comment. Do not restyle, resize, or reorder anything else.

**Verification:**

- `Grep` - `layout_marginLeft|layout_marginRight|layout_alignParentLeft|layout_alignParentRight|layout_toLeftOf|layout_toRightOf|paddingLeft|paddingRight|gravity="left"|gravity="right"` returns zero hits across the five files.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. 33 absolute attributes across the five files, now zero; `fr` exit 0. Every one was a symmetric `paddingLeft`/`paddingRight` pair or a single `gravity="left"`, so nothing needed a physical-direction exemption and no one-line comment was warranted. One trap avoided: `btnSort` in both `activity_browse` variants already carried `paddingStart="8dp"`/`paddingEnd="4dp"` *alongside* `paddingLeft`/`paddingRight="12dp"` - renaming there would have produced a duplicate attribute, so the absolute pair was dropped instead, which is also what the platform already did with it from API 17 on. Applied through `temp/S1190/rtl-attrs.ps1` with per-file backups beside it. Dev log recorded.

---

### Step 04.2 - Gate the regression

**Files:** `scripts/quality/assert-rtl-layout-attrs.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a ratchet gate that counts absolute directional attributes across `app_v2/src/*/res/layout*/**.xml` and fails when the count rises above the recorded baseline, mirroring the shape of the existing neuroslop gate (baseline file, `-ScopeToFile` support, exit-code contract in the header). Wire it into `post-change.ps1` for `ChangeType Xml` and `Mixed`. Set the baseline from the post-Step-04.1 count, not from zero, if any deliberate physical-direction attributes survived.

**Verification:**

- `Glob` - `scripts/quality/assert-rtl-layout-attrs.ps1` exists.
- `pwsh -NoProfile -File scripts/quality/assert-rtl-layout-attrs.ps1` exits 0.
- `Grep` - `assert-rtl-layout-attrs` referenced in `scripts/post-change.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Gate present at 119 lines against a 120 budget, exit 0 in default, `-Gate` and `-ChangedFiles` modes, and wired into `post-change.ps1` for `Xml` and `Mixed` whenever a layout is touched - it ran in this phase's own closure and reported `scoped to 5 changed layout(s): occurrences 0`.
- 2026-08-05 - Baseline is **0**, not a count carried over. The step allowed for surviving physical-direction attributes, but after Step 04.1 the repository has none anywhere under `app_v2/src/*/res/layout*`, so the gate reads "never again" rather than "no worse than today". `docs/SCRIPT_CHEATSHEET.md` regenerated for the new script, since it is a generated render target and a new param block makes it stale. Dev log recorded.

---

## Phase-boundary audit (2026-08-05)

Resource layer only, as the criteria say - no lifecycle, coroutine, listener or Room surface is touched.

- **Every replacement was symmetric, so none of them moves a pixel in a left-to-right locale.** All 33 occurrences were `paddingLeft`/`paddingRight` pairs with equal values, plus one `gravity="left"`. No `layout_marginLeft`, `alignParent*` or `toLeftOf` existed, so no `RelativeLayout` constraint graph was rewritten and the class of bug where a mirrored chain detaches a view never arose.
- **The one asymmetric element was the interesting one.** `btnSort` carried `paddingStart="8dp"`/`paddingEnd="4dp"` *and* `paddingLeft`/`paddingRight="12dp"`. Renaming would have produced a duplicate attribute and failed the build; dropping the absolute pair keeps exactly the values the platform was already using, since start/end wins over left/right from API 17.
- **The gate is stricter in scoped mode than a delta gate, deliberately.** Any occurrence in a changed layout fails, rather than only an increase. That is only reasonable because the project-wide count is zero - the baseline records what may exist at all, and a file being edited is not the one to raise it.
- **Left for the device.** The visual pass in Arabic or Urdu (strategic §11.6) is not something a grep can stand in for: this phase proves no absolute attribute survives, not that the mirrored layout looks right. It stays on the manual list.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1 -ChangeType Mixed`.
- [x] Phase-boundary audit run - resource-layer only, no unresolved finding.

---

## Handoff Notes to Next Phase

Layouts are direction-neutral and a gate keeps them that way. The visual RTL pass on Arabic or Urdu (strategic §11.6) still needs a device and stays on the manual list.

---

## Rollback Plan

Revert the phase commit - resource attributes only, no behaviour or data change.
