# Phase 03 - Transparent shortcut cell

**Strategic spec:** [`../S1173_launcher-cells-translucent-over-wallpaper.md`](../S1173_launcher-cells-translucent-over-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Strip background, elevation and stroke from the resting shortcut cell so the wallpaper shows through, with the outlined label and outlined icon carrying all contrast, and restore the card look while editing.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] `temp/CODE.LOCK` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml` | Modified | ≤ 95 |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified or New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 320 |

> Landscape parity: `item_launcher_cell_shortcut.xml` has no `layout-land` counterpart by design - the cell is square in both orientations, as its own header comment records. No landscape file to mirror.
>
> Gadget cells are deliberately untouched: strategic §2 non-goals keep their card, because they render their own content.

---

## Steps

### Step 03.1 - Add the launcher label outline dimension

**Files:** `app_v2/src/launcherEnabled/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `launcher_cell_outline_width` set to `1dp`. The shared 2dp default suits large camera overlay labels; on a 12sp cell caption it thickens the glyph into a blob, so the launcher overrides it. If the file does not exist in the `launcherEnabled` source set, create it with the standard resources root.

**Verification:**

- `Grep` - `name="launcher_cell_outline_width"` present in `app_v2/src/launcherEnabled/res/values/dimens.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 1/1 PASS. File already existed in the source set, so the dimension was appended with its rationale comment.

---

### Step 03.2 - Rebuild the cell layout

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Keep `FocusMaterialCardView` as the root - it draws the focus ring and it is what the binder casts to - but make it transparent at rest: `cardBackgroundColor` to `@android:color/transparent`, `cardElevation` to `0dp`. Leave `clickable`, `focusable` and `rippleColor` untouched so the whole cell stays the touch target and keeps its ripple. Replace the label `TextView` with `com.sza.fastmediasorter.ui.common.widget.OutlinedTextView`, keeping its id, gravity, `maxLines`, ellipsize and text size, and set `app:otv_outlineWidth="@dimen/launcher_cell_outline_width"`. Replace the icon `ImageView` with `com.sza.fastmediasorter.ui.common.widget.OutlinedImageView`, keeping its id and 44dp size, and set `app:oiv_outlineWidth="@dimen/launcher_cell_outline_width"` plus `android:padding="@dimen/launcher_cell_outline_width"`. That padding is not decoration: `OutlinedImageView` draws the contour inside its own bounds, so without it the outermost pass is clipped at the icon's edge (recorded at the Phase 02 boundary). Leave the mode badge as a plain `ImageView`. Use no literal hex colour anywhere (Rule 19). Update the header comment: the cell is now a transparent shortcut over the wallpaper, and the mirrored panel tile it used to match no longer applies.

**Verification:**

- `Grep` - `cardBackgroundColor="@android:color/transparent"` present.
- `Grep` - `cardElevation="0dp"` present.
- `Grep` - `ui.common.widget.OutlinedTextView` and `ui.common.widget.OutlinedImageView` both present.
- `Grep` - `android:id="@+id/cellLabel"` and `android:id="@+id/cellIcon"` both still present.
- `Grep` - `android:clickable="true"` and `android:focusable="true"` both still present.
- `Grep -E "=\"#[0-9a-fA-F]{3,8}\""` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 7/7 PASS. `layout-land` counterpart confirmed absent, so nothing to mirror (Rule 11). Icon carries `android:padding` equal to the outline width so the outermost contour pass is not clipped. `strokeColor`/`strokeWidth` stay inflated - the binder is what decides whether they are visible, and edit mode needs them back.

---

### Step 03.3 - Make the binder own one resting-versus-editing switch

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> `applyRestingOutline` currently zeroes only the stroke. Widen it into one switch that applies the whole resting-versus-editing appearance, and rename it to match what it now does. At rest a shortcut cell keeps the transparent layout as inflated. While editing, a shortcut cell gets the card look back - surface background, the inflated 1dp stroke and the inflated elevation - because the card is what makes cell boundaries and drop targets visible while arranging. Gadget cells keep their card in both modes, so branch on `LauncherCellKind`. Read the editing background from `?attr/colorSurface` through a theme lookup, never a literal colour. Keep the existing KDoc's reasoning and extend it rather than replacing it - it records why the resting desktop drops the outline (S1100).

**Verification:**

- `Grep` - `LauncherCellKind` referenced inside the resting-versus-editing function.
- `Grep` - `strokeWidth` still assigned in that function.
- `Grep` - `applyRestingOutline` returns zero hits (renamed).
- `Grep -E "0x[0-9a-fA-F]{6,8}"` - zero hits in the file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 6/6 PASS. `.\a.ps1 fc` exit 0, `BUILD SUCCESSFUL in 34s`. File 307 LOC, longest line 112. Renamed `applyRestingOutline` -> `applyCellSurface`.
- 2026-07-30 - Plan amendment: the step asked for the card look to be restored "for both kinds, branching on `LauncherCellKind`". Reading `item_launcher_cell_gadget.xml` showed a gadget already carries surface, stroke and elevation in both modes, and because a mode change re-inflates every cell (`lastBound` includes `editMode`, `bind` calls `removeAllViews`), the inflated 1dp stroke is intact in edit mode and needs no restoring - only not zeroing. So the switch reduced to "at rest zero the stroke; while editing give a shortcut back its surface and lift". Restoring all three properties for both kinds would have duplicated values that already live in the gadget layout, where the two copies could drift apart.
- 2026-07-30 - Surface colour read via `MaterialColors.getColor` instead of a hand-rolled `obtainStyledAttributes`: shorter, and it cannot leak a `TypedArray`. New dimension `launcher_cell_edit_elevation` (2dp) matches the gadget card, so both kinds read as one surface while arranging.

---

### Step 03.4 - Keep the unavailable-cell dimming readable

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> `bindShortcut` dims an unavailable cell by setting alpha on the whole root. With no card behind it, alpha now fades the outline as well, so a missing app's cell can become unreadable over a busy wallpaper. Apply the dim to the icon and the label instead of the root, leaving the root fully opaque. Keep the existing constant and its meaning.

**Verification:**

- `Grep` - `UNAVAILABLE_ALPHA` still present and still used.
- `Grep` - `binding.root.alpha` returns zero hits in the file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. `.\a.ps1 fk` exit 0, `BUILD SUCCESSFUL in 42s`. `UNAVAILABLE_ALPHA` stays public: `LauncherAppShortcutAdapter` and `LauncherHomeActivity.unavailableGadgetView` both consume it, so the companion's "public because" comment is accurate. Checked both - the activity dims a `TextView` inside a gadget card and the adapter dims an app-picker row, neither a transparent root, so neither needed the same change.
- 2026-07-30 - detekt `ReturnCount` on `applyCellSurface` (3 returns, limit 2) after step 03.3's rewrite; converted the early returns to `if / else if`, leaving one exit. Gate re-run PASS [scoped], then recompiled.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `BUILD SUCCESSFUL in 42s`, re-run after the `ReturnCount` fix.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See Audit Notes below.

---

## Audit Notes (phase boundary, 2026-07-30)

Layer 1 plus Layer 3 (view ownership, per-cell cost). Layer 2 skipped: the binder gained no lifecycle, coroutine or listener surface - `bind` is still called from the same collectors. Layer 4 not applicable.

- `applyCellSurface` is now the single owner of the resting-versus-editing appearance, which is the point of the step. Verified the two callers of the old name are gone (`applyRestingOutline` greps zero) and that `bind` passes the item it already has in scope. No P0/P1.
- Correctness check on the mode switch: it only works because a mode change re-inflates every cell, so the inflated stroke and background are pristine each time. That invariant lives in `bind`'s `lastBound` triple and `removeAllViews`. If someone later makes `bind` reuse views across a mode change, this function silently stops restoring anything. Recorded in the KDoc as the reason the switch reads the inflated state rather than caching it. P2 - the invariant is documented, not enforced; a gate for it would have to inspect `bind`, which is not worth a mechanical rule for one call site.
- The unavailable dim now touches two child views instead of one root. Both are set unconditionally on every bind, so a recycled-looking cell cannot keep a stale alpha - there is no path where the icon stays dim after the shortcut becomes available again.
- `MaterialColors.getColor(card, ..)` resolves against the card's own context, so the editing surface follows a theme switch. Checked that this is the same helper `LauncherHomeActivity` already uses for `colorOnSurface`, so the launcher has one way of doing this rather than two.
- P2 recorded, process rather than code: detekt rejected this phase's Kotlin twice (`ReturnCount` here, and `ComplexCondition` plus `ArgumentListWrapping` in Phase 02), and Phase 01 once (`ImportOrdering`). Rule 19 asks for detekt-clean on the first build; the cost of missing it is a full recompile per finding, roughly 40s each. The recurring shapes are worth internalising: keep explicit returns at two or fewer, keep boolean guards at three terms or fewer by naming a predicate, and put one argument per line in a multi-line call.

---

## Handoff Notes to Next Phase

The desktop now shows wallpaper under every shortcut. Outline width, outline colour and focus-ring contrast over a bright wallpaper are judgement calls that only a device can settle - they belong to the device-test gate, not to a further code phase.

---

## Rollback Plan

Revert the phase commit. Phase 01 and Phase 02 widgets can stay: the camera keeps using the text one, and the icon one simply has no consumer again.
