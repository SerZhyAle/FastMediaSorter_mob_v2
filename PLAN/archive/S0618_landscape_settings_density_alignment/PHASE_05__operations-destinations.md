# Phase 05 - Operations / Destinations Propagation

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 + owner pilot sign-off
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Apply density (R1, R2) and left-alignment (R5) to the Operations/Destinations landscape layout - the largest settings fragment.

---

## Prerequisites

- [x] Phase 02 ✅ Done.
- [x] **Owner approved the General pilot landscape screenshots.** (owner sign-off 2026-06-23 via `/spec-all`)
- [x] Backup `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` to `temp/` before editing (>500 LOC). (`temp/fragment_settings_destinations.xml.20260623_005353.bak`)

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 1240 |

> Landscape-only by scope; portrait `res/layout/fragment_settings_destinations.xml` not mirrored.

---

## Steps

### Step 05.1 - Destinations landscape density + left-align (R1, R2, R5)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Left-align the centered content: remove `android:layout_gravity="center"` from `btnScheduledNotificationPermission`, and change `android:gravity="center"` to `gravity="start"` on the empty-state messages `tvNoResourcesMessage` and `tvNoScheduledOps` (owner wants placeholders left-aligned too). Pack compact toggle rows into weighted horizontal rows of up to 4 across the section groups (`layoutCameraOcrGroup`, `layoutCalculatorGameGroup`, `layoutDefaultPlayerToggles`, etc.) using the house weighted-`LinearLayout` pattern, value fields 2 per row, left-packed. Keep `center_vertical` row alignment. Preserve `nextFocus*` for D-pad order. Back up the file to `temp/` first.

**Verification:**

- `Grep` - `fragment_settings_destinations.xml` has no `android:layout_gravity="center"` on `MaterialButton`.
- `Grep` - `tvNoResourcesMessage` and `tvNoScheduledOps` no longer carry `android:gravity="center"`.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. Left-aligned the three centered elements: `layout_gravity="center"` removed from `btnScheduledNotificationPermission`; `tvNoResourcesMessage` and `tvNoScheduledOps` -> `gravity="start"`. The fragment was already densely packed by S0609/S0435/S0567 (10+ weighted 2-up rows); the remaining solo toggles are deliberate section masters that gate dependent blocks, so no further packing was forced. `.\a.ps1 fc` SUCCESSFUL.

---

## Phase Done Criteria

- [x] Step 05.1 is `[x] done`.
- [x] Project builds - run `/build`. (`.\a.ps1 fc` + final `.\a.ps1 d` SUCCESSFUL)
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for the file in "Files Touched".

---

## Handoff Notes to Next Phase

All landscape settings fragments dense + left-aligned. Phase 06 closes out catalog/docs/changelog and the final test transition.

---

## Rollback Plan

Restore `fragment_settings_destinations.xml` from the `temp/` backup - layout-only.
