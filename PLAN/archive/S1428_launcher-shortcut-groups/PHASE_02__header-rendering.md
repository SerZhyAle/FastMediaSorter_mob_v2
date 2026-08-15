# Phase 02 - Header rendering

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Draw the section header on the desktop canvas as a full-width row whose width follows the current column count, and suppress the long-press affordance on it.

---

## Anchors

- `LauncherCellViewBinder` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` - `bind` at `:60`, kind `when` at `:81`, `bindShortcut` at `:278`, `nameLongPressForAccessibility` at `:290`, `bindGadget` at `:298`.
- `LauncherGridGeometry` - `.../grid/LauncherGridGeometry.kt:73` - the `spanW.coerceIn(1, safeColumns)` clamp.
- `LauncherDesktopLayout` - `.../grid/LauncherDesktopLayout.kt:23` - hand-written `ViewGroup`, knows nothing of commands or edit mode.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Back up `LauncherCellViewBinder.kt` under `temp/S1428/` before editing (over 500 LOC). Backed up at
      Phase 01; the file is 381 LOC, so Rule 5's threshold was never crossed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_section_header.xml` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 700 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Add the section header layout

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_section_header.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the header layout: a title text view sized for a 48dp minimum touch target, using theme attributes for every colour. Distinguish the header from ordinary cells by more than colour alone - a weight or divider must carry the same distinction.

**Why:**

Strategic §3.2 "Доступность" requires that section membership not rest on colour alone and that the tap target be at least 48dp, and CLAUDE.md Rule 19 forbids a hardcoded hex colour in a layout.

**Verification:**

- `Grep` - no `="#` occurrence in the new layout.
- `Grep` - a `48dp` minimum dimension is present on the tappable root.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Executed early, during Phase 01, which could not compile without it. Verification 2\2 PASS: zero `="#` occurrences (every colour is `?attr/colorOnSurface`, `?attr/colorOutline`, `?attr/selectableItemBackground`), and `android:minHeight="48dp"` sits on the `sectionHeaderRoot` itself. Distinction beyond colour: bold `textAppearanceTitleMedium` plus a 1dp underline rule.
- Root is a `FrameLayout`, not the `LinearLayout` first written: `LauncherCellViewBinder.decorateForEdit` returns early on any non-`FrameLayout` root, so the original would have silently denied the header the edit-mode remove badge that strategic §6.4/§11.14 depend on. The resize handle is separately gated to `GADGET`, so the header correctly gets a remove badge and no resize grip.

---

### Step 02.2 - Derive header width from the current column count

**Files:** `.../grid/LauncherGridGeometry.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Make a `SECTION` cell's rendered width equal the current column count rather than its stored `spanW`. The existing `spanW.coerceIn(1, safeColumns)` clamp already prevents a header from overflowing a narrower grid, but it will not grow one back when the grid gets wider, so the width has to be derived at layout time.

**Why:**

Strategic §5.1.2 states that a stored span is insufficient - a header saved on a narrower grid must still span the full width after the density multiplier changes, which §7 lists as a medium-probability risk whose symptom is a header that looks cut off with empty space to its right.

**Verification:**

- `Grep` - the geometry path special-cases `SECTION` width against the live column count.
- Manual: strategic §11.2 - the header spans the full row at every column count, across a density-multiplier change and a rotation.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 1\1 PASS (mechanical half); the manual half is device work, carried to the ticket's `BlockNeedUserTest` check. `LauncherGridGeometry.renderSpanW(cell, columns)` returns `columns` for a `SECTION` and the stored `spanW` otherwise. `.\a.ps1 fk` exit 0.
- **The change could not live in `footprintOf` alone, which is where the plan pointed it.** `LauncherDesktopLayout.boundsOf` lays a child out from its `CellLayoutParams.spanW` - the value the binder passes - and never calls `footprintOf`; `footprintOf` is what the *empty-slot sweep* uses. Changing only one would have widened the header in layout while the sweep still believed the stored span, and edit mode would have drawn "tap to add" squares on top of a live header - the exact failure `footprint`'s own KDoc warns about. Both now route through `renderSpanW`, so layout and occupancy cannot disagree. The full-width case also fixes the column: `footprint` clamps `col` into `0..columns - width`, which is `0..0` at full width.

---

### Step 02.3 - Bind the header in the renderer

**Files:** `.../grid/LauncherCellViewBinder.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Give the `when (item.cell.kind)` a `SECTION` branch that inflates the header layout and sets its resolved title. Do not route it through `bindShortcut`: that path attaches the long-press accessibility action, which step 02.4 has to keep off the header.

**Why:**

Strategic §6.8 rules the header explicitly not long-pressable, and the research shows `nameLongPressForAccessibility` is reached only from `bindShortcut`, so keeping the header out of that path is what makes the ruling true rather than a later correction.

**Verification:**

- `Grep` - the `SECTION` branch calls neither `bindShortcut` nor `nameLongPressForAccessibility`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Executed early, during Phase 01. Verification 2\2 PASS: `bindSection` contains zero references to `bindShortcut` or `nameLongPressForAccessibility`, and `.\a.ps1 fk` exit 0. It inflates `ItemLauncherSectionHeaderBinding` and sets the title resolved upstream - the header reaches it as `item.visual.label`, produced by `ResolveLauncherDesktopUseCase.toUi`, which decodes every non-`GADGET` target and so needed no branch of its own.

---

### Step 02.4 - Announce the header as a heading, and offer no action on it

**Files:** `.../grid/LauncherCellViewBinder.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Mark the header view as an accessibility heading carrying its title, and leave the long-press accessibility action unset on it. Long press on the header must not merely do nothing visible - it must not be advertised at all.

**Why:**

Strategic §6.8 decided that TalkBack must not promise what does not exist, and §7 lists the header answering an advertised action it does not have as a medium-probability accessibility defect.

**Verification:**

- `Grep` - `accessibilityHeading` (or the `ViewCompat` equivalent) is set on the header.
- Manual: strategic §11.4 and §11.5 - TalkBack reads it as a heading with its name and offers no action.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 1\1 PASS (mechanical half); the TalkBack half is device work, carried to the ticket's `BlockNeedUserTest` check. `ViewCompat.setAccessibilityHeading(binding.root, true)` - on the root rather than the caption, because the root is the focusable node TalkBack lands on. No long-press action is named: `nameLongPressForAccessibility` is never called on this path, and the root sets no `setOnLongClickListener`, so `ACTION_LONG_CLICK` is not exposed at all rather than exposed-and-inert.
- Also set `contentDescription` on the root. Without it `removeDescriptionFor` finds nothing to quote and the edit-mode remove badge falls back to the unnamed "remove cell" string - every badge on the desktop would then read identically, which is the defect that string pair exists to avoid.

---

### Step 02.5 - Confirm the long-press dispatcher leaves the header alone

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Verify that `showCellActions` needs no edit: its `when` over the decoded command already ends in `else -> false`, so a `Section` command falls through to "no menu" without a new branch. Record the check; do not add a branch that returns `false` explicitly.

**Why:**

Strategic ADR-2 requires a new cell kind to join by answering the existing dispatcher rather than by editing the long-press handler, and §6.8 wants no menu at all - which the existing fallthrough already delivers.

**Verification:**

- `Grep` - `showCellActions` contains no `Section` branch and still ends in `else -> false`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 1\1 PASS. `LauncherHomeActivity.showCellActions:500` branches on `App`, `Resource` and `Stream` and ends `else -> false`; no `Section` branch added, no edit made. Belt and braces in practice: the header never attaches a long-click listener at all, so the dispatcher is not reached for it even by the `else`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### UI phase screenshot (S1338)

Placement decision recorded: strategic §3.3 "UI placement contract - закрыт полностью 2026-08-08", with
the form (§6.1), the position (§3.1.1) and the tap behaviour (§6.8) each an owner ruling quoted verbatim
in §6. So the gate's first condition is met.

Screenshot deferred, and not for want of a device - `emulator-5554` is attached. There is nothing to
photograph yet: a header reaches the desktop only through seeding, which is Phase 05, and strategic §6.6
rules that an already-arranged desktop never gains one. The shot is taken after Phase 05, on a reset
desktop, and belongs to the ticket's `BlockNeedUserTest` check.

---

## Phase-boundary audit - 2026-08-08

- **Layer 1 (architecture)** - clean. `renderSpanW` is a pure function on the existing geometry object;
  the binder gained no business logic; `LauncherDesktopLayout` still knows nothing about cell kinds,
  which was the property worth protecting.
- **Layer 3 (listener ownership)** - `bindSection` attaches no listener and holds no reference past the
  returned view, so it adds nothing to unregister. The Phase 01 P2 stands unchanged: the root still
  declares `clickable` with no click listener behind it until Phase 03 attaches the collapse toggle.
- **Layer 2 / Layer 4** - not applicable; no coroutine, lifecycle or Room surface was touched.
- No P0 or P1 findings.

---

## Handoff Notes to Next Phase

The header draws full-width and is inert to long press. It does not collapse yet, and the renderer's guard is still the three-element `Triple` that Phase 03 must restructure before collapsed-state can enter it.

---

## Rollback Plan

Revert the phase commit. Only rendering changed - stored cells are untouched, so a revert leaves the section cell present but drawn by the pre-phase path.
