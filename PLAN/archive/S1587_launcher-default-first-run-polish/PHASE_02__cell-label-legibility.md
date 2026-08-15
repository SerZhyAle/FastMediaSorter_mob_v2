# Phase 02 - Cell label legibility over the branded wallpaper

**Strategic spec:** [`../S1587_launcher-default-first-run-polish.md`](../S1587_launcher-default-first-run-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Give every shortcut label a translucent backing plate and a second text line, so captions read over any wallpaper and the standard starter-set names stop ending in an ellipsis.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Owner ruling in strategic §3.3 item 3 read - the chosen remedy is a plate under the label, not a muted wallpaper.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/drawable/bg_launcher_cell_label.xml` | New | ≤ 20 |
| `app_v2/src/launcherEnabled/res/color/launcher_cell_label_plate.xml` | New | ≤ 15 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified | ≤ 100 |

> `item_launcher_cell_shortcut.xml` has no `layout-land` counterpart by design - its own header states the cell is square in both orientations - so Rule 11 needs no landscape edit here.

---

## Steps

### Step 02.1 - Add the label plate drawable

**Files:** `app_v2/src/launcherEnabled/res/drawable/bg_launcher_cell_label.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a rounded-rectangle shape drawable for the caption plate, filled with a translucent surface colour taken from a theme attribute or an existing `@color/` entry, with a corner radius matching the small-radius value already used by launcher cells. No hardcoded `#hex` fill - Rule 19 rejects it in `res/layout*` and the same discipline applies to the drawable that layout references.

**Why:**

The owner ruled for a plate under the caption because it works on a user-supplied wallpaper image too, where lowering the branded pattern's contrast would change nothing - strategic §3.3 item 3 and §1 defect 10.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/drawable/bg_launcher_cell_label.xml` exists.
- `Grep` - the file contains `<corners` and `<solid`.
- `Grep` - the file contains no `="#` literal.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Caption plate added (bg_launcher_cell_label + launcher_cell_label_plate colour state list carrying the alpha, three new dimens), applied to cellLabel with maxLines 2, and the layout header comment rewritten to match. Deviation from plan: the fill needed a res/color state list because alpha cannot be applied to a theme attribute inside <solid>.

---

### Step 02.2 - Apply the plate and allow a second line

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml`, `app_v2/src/launcherEnabled/res/values/dimens.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Set `android:background="@drawable/bg_launcher_cell_label"` on `cellLabel`, give it small horizontal and vertical padding through new dimens, and raise `android:maxLines` from 1 to 2 so the standard names fit. Keep `ellipsize="end"` as the last resort for a genuinely long name, keep the label's outline attribute, and keep the cell itself transparent - only the caption gains a background.

**Why:**

Strategic §1 defects 7 and 10 are a truncated caption and an unreadable one, and §11.5 plus §11.6 require both to be observable as fixed; the cell must stay transparent because the desktop wallpaper showing through the cell is the existing design the layout header declares.

**Verification:**

- `Grep` - `android:background="@drawable/bg_launcher_cell_label"` appears exactly once, on `cellLabel`.
- `Grep` - `android:maxLines="2"` present on `cellLabel`.
- `Grep` - `app:cardBackgroundColor="@android:color/transparent"` still present on `cardLauncherCell`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Caption plate added (bg_launcher_cell_label + launcher_cell_label_plate colour state list carrying the alpha, three new dimens), applied to cellLabel with maxLines 2, and the layout header comment rewritten to match. Deviation from plan: the fill needed a res/color state list because alpha cannot be applied to a theme attribute inside <solid>.
- 2026-08-12 - Phase-boundary audit (Layer 1) on the device screenshots found a P1 regression from this phase: maxLines=2 made the second caption line clip against the cell bottom (Launcher settings, Exit launcher mode, Photo OCR translate, the app's own cell). Fixed in the same phase - cell vertical padding 10dp -> 4dp and label margin 6dp -> 3dp, both as named dimens; rebuilt, re-capture round running. Before: temp/S1587/after/02_desktop_scroll1.png.
- 2026-08-12 - Re-capture after the padding fix: temp/S1587/after2/03_desktop_appfunctions.png shows 'Exit launcher mode', 'Photo OCR translate' and 'Fast Media Sorter & Organizer' wrapped to two fully visible lines, no clipping and no ellipsis; captions sit on the plate over the branded wallpaper. Placement decision: owner ruling, strategic 3.3.

---

### Step 02.3 - Correct the layout's own contract comment

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite the sentence in the file's header comment stating that contrast lives in the icon's and caption's own contour and not in a card behind them, so it describes what the file now does: the icon keeps its contour, the caption additionally sits on a translucent plate. Name S1587 and the reason - the branded wallpaper runs a high-contrast pattern directly under the caption.

**Why:**

That comment is a recorded requirement under Rule 8, so leaving it contradicting the layout would make the next reader restore the old behaviour; strategic §3.3 item 3 is the ruling that supersedes it.

**Verification:**

- `Grep` - the phrase `not in a card behind them` no longer appears in the file.
- `Grep` - `S1587` appears in the file's header comment.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Caption plate added (bg_launcher_cell_label + launcher_cell_label_plate colour state list carrying the alpha, three new dimens), applied to cellLabel with maxLines 2, and the layout header comment rewritten to match. Deviation from plan: the fill needed a res/color state list because alpha cannot be applied to a theme attribute inside <solid>.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The caption plate is the launcher's shared "text over wallpaper" treatment; a gadget needing the same protection reuses `bg_launcher_cell_label` rather than defining its own.

---

## Rollback Plan

Revert the phase commit - the change is a background attribute plus a maxLines value, with no data or navigation impact.
