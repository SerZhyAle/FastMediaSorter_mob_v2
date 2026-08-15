# Phase 01 - Scrollbar affordance

**Strategic spec:** [`../S1209_launcher-scrollable-desktop.md`](../S1209_launcher-scrollable-desktop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Give the desktop's scroll container a permanent, styled vertical scrollbar in both orientations.

> **Not in this phase: making the bar draggable.** The platform draws `View` scrollbars but does not route touches to them, and no attribute changes that; the only built-in draggable thumb belongs to `RecyclerView`, which ADR-9 of S0404 ruled out for this surface. A draggable thumb therefore means a new custom element whose width, appearance and visibility rule the owner has not decided - strategic §6.4 holds that question. This phase ships the half that needs no decision.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved - none block it; §6.2 records the styling choice this phase implements.
- [ ] `temp/CODE.LOCK` free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | Modified | ≤ 8 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | Modified | ≤ 8 |

> Both orientations are listed because the landscape variant exists and is structurally identical (CLAUDE.md Rule 11). A portrait-only edit would ship a scrollbar that disappears on rotation.

---

## Steps

### Step 01.1 - Style the desktop scroll container in portrait

**Files:** `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the house permanent-scrollbar attributes to the `NestedScrollView` that wraps the desktop (`launcherGridScroll`): `android:scrollbars="vertical"`, `android:scrollbarThumbVertical="@color/scrollbar_thumb"`, `android:fadeScrollbars="false"`. Copy the attribute set from `app_v2/src/main/res/layout/activity_main.xml`, which is the pattern already used on roughly fifteen screens. Do not introduce a new colour, a new drawable or a custom scrollbar view.

**Why:**

Strategic §1 states that the scrollbar today is the platform's transient overlay - thin and fading a second after the gesture - so a user whose content runs past the bottom edge gets no lasting sign that anything is there; strategic §2.1 requires a permanently visible bar, and §3.1 records the owner's wish that it read as a bar rather than a momentary hint.

**Verification:**

- `Grep` - `scrollbarThumbVertical` matches at least once in the portrait layout.
- `Grep` - `fadeScrollbars="false"` matches at least once in the portrait layout.
- `Grep` - `scrollbar_thumb` resolves in `app_v2/src/main/res/values/colors.xml` (the referenced colour exists rather than being invented here).
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. The three attributes added to `launcherGridScroll`; `scrollbarThumbVertical` 1 hit, `fadeScrollbars="false"` 1 hit, `scrollbars="vertical"` 1 hit. `name="scrollbar_thumb"` resolves once in `app_v2/src/main/res/values/colors.xml`, so no colour was invented. `.\a.ps1 fr` exit 0 with `processStandardDebugResources` executed - which also confirms the `launcherEnabled` source set is mounted by the `standard` flavor, since the edited file lives there.

---

### Step 01.2 - Mirror the styling in landscape

**Files:** `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Apply the identical three attributes to the same `NestedScrollView` in the landscape variant. Keep the attribute values byte-identical to the portrait file so a future search finds both.

**Why:**

Strategic §11.5 requires the result to hold in both orientations, and CLAUDE.md Rule 11 refuses a portrait-only layout edit when the landscape counterpart exists - the desktop is reachable in both, so a one-sided change would make the affordance vanish on rotation.

**Verification:**

- `Grep` - `scrollbarThumbVertical` matches at least once in the landscape layout.
- `Grep` - `fadeScrollbars="false"` matches at least once in the landscape layout.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Same three attributes, byte-identical values, one hit each in `layout-land`. The two files differ only in the horizontal padding they already had (`margin_small` portrait, `margin_medium` landscape) - untouched here.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`. `.\a.ps1 dq` exit 0, APK `v2.60.8041.533-DEBUG` packaged.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. `post-change: PASS (Xml)` over both layouts with `-ScopeToFile`.
- [x] Phase-boundary audit run - resource layer only; Layer 1 readability and the layout-parity check are the whole scope.

## Phase-boundary audit - 2026-08-05

- **Layout parity.** Both orientations carry the same three attributes with identical values; the only remaining difference between the two `NestedScrollView` blocks is the horizontal padding each already had. The `rtl-layout-attrs` gate is not applicable - no start/end attribute was added.
- **No invented resource.** `@color/scrollbar_thumb` is pre-existing in both `values/colors.xml` and `values-night/colors.xml`, so the bar is theme-aware without this phase adding a colour. The `layout-hardcoded-colors` neuroslop dimension reports 0 new occurrences.
- **Flavor isolation.** Both files live under `src/launcherEnabled/res/`, the source set only `standard` and `noLegal` mount, so no flavor guard reaches `src/main` and no other flavor gains a resource it cannot use.
- **Scope honesty.** This phase delivers a bar that is visible and permanent, not one that can be dragged. That limit is stated in the Objective and held open by strategic §6.4 rather than quietly shipped as if the goal were met.
- No P0/P1 findings. Nothing to defer.

---

## Handoff Notes to Next Phase

The desktop's scroll container now carries a visible, non-fading thumb. Phase 03 attaches auto-scroll to the same container, so the scroll position it drives is the one the user can now see. Dragging the bar itself remains out of scope until strategic §6.4 is answered.

---

## Rollback Plan

Revert the two layout edits - no code, no data, no user-facing string changed; the scrollbar returns to the platform default.
