# Phase 04 - Collapsible section headers

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

**Step Log:**

- 2026-07-23 - Step 04.1 PASS: collapse/expand + weight redistribution (WEIGHT_FILL/MAIN/NONE consts), both-collapsed guard, pinned auto-hide resets collapse; chevron ic_expand_less/more swap. Constructor extended, Activity call site updated.
- 2026-07-23 - Step 04.2 PASS: headers focusable/clickable (Phase 02) + setOnClickListener drives D-pad center; chevron glyph + contentDescription (streams_section_expand/collapse) convey state non-color. `.\a.ps1 dq` Build Successful exit 0. post-change -ScopeToFile PASS (neuroslop + detekt) on both files.
- 2026-07-23 - Phase-boundary audit (L1/L3): no P0/P1. Header listeners GC with Activity view tree; safe LayoutParams cast.

---

## Objective

Make each section header a collapse/expand toggle: tapping a header collapses that section (its content hides, its container yields vertical space) and expands the other to fill it. Delivers strategic pillar P3, goal G5, criterion §11.5.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (`StreamsSectionsManager` owns both sections).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1060 |

> Phase 04 extends the `StreamsSectionsManager` constructor with the header/chevron/mainSection views; the Activity call site (built in Phase 03) is updated in lockstep.

---

## Steps

### Step 04.1 - Collapse/expand logic + weight redistribution

**Files:** `ui/streams/helpers/StreamsSectionsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add per-section collapse state (two booleans, both expanded by default - MVP does not persist, strategic non-goal). On a header tap, toggle that section's collapsed state: hide the section's content `FrameLayout` (the RV container) via `isVisible`, keep the header row visible, and redistribute the outer LinearLayout weights so the collapsed section shrinks to its header height (`layout_height=wrap_content`, `weight=0`) while the other section takes the remaining space (`layout_height=0dp`, `weight=1`). Guard the pinned toggle so a hidden pinned section (no pinned channels) is never collapsible. When both would be collapsed, keep the last-tapped one collapsed and leave the other expanded (never zero visible content). Reflect state on the chevron: `ic_expand_less` + `contentDescription=@string/streams_section_collapse` when expanded, `ic_expand_more` + `@string/streams_section_expand` when collapsed. Expose `bindHeaders()` (or wire clicks in the constructor `init`).

**Verification:**

- `Grep` - `ic_expand_more` and `ic_expand_less` referenced in `StreamsSectionsManager.kt`.
- `Grep` - `streams_section_expand` and `streams_section_collapse` referenced.
- `Grep` - `setOnClickListener` present on both header views (or a shared toggle helper).
- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

### Step 04.2 - Accessibility + input parity on the headers

**Files:** `ui/streams/helpers/StreamsSectionsManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Ensure each header is keyboard/D-pad/TV operable (the header View is `focusable`/`clickable` from Phase 02; confirm the toggle also fires on a D-pad center / Enter by relying on `setOnClickListener`, which View dispatches for KEYCODE_DPAD_CENTER). The collapsed/expanded meaning is conveyed by the chevron glyph AND the contentDescription (non-color, Rule 16 / strategic §3.2 accessibility), not color alone. No mouse-only or touch-only path.

**Verification:**

- `Grep` - `contentDescription` set on the chevron in both expanded and collapsed branches.
- `.\a.ps1 dq` - `BUILD SUCCESSFUL`, exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - `/build` (`.\a.ps1 dq`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for `StreamsSectionsManager.kt`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 (collapse toggles View visibility + weights only; no listener added without symmetric teardown; no retained references).

---

## Handoff Notes to Next Phase

Sections collapse/expand and never both hide their content. Phase 05 handles single-playback across sections independently of collapse state (a collapsed section keeps its adapter state).

---

## Rollback Plan

Revert the phase commit - collapse is additive to `StreamsSectionsManager`; Phase 03's always-expanded behavior returns.
