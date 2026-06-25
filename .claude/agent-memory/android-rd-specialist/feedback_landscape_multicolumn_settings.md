---
name: landscape-multicolumn-settings
description: Canonical way to pack settings rows into multi-column landscape - weighted horizontal LinearLayout in layout-land only, column-count by element type, S0605 invariant held
type: feedback
metadata:
  type: feedback
---

When a settings fragment wastes right-side space in landscape, pack compatible rows into columns. Established as the project canon in S0609 (2026-06-22, follow-up to [[no-fullwidth-buttons-landscape]]).

**Canonical mechanism (ADR-1) - weighted horizontal LinearLayout, pure XML, no Kotlin:**
- Outer `LinearLayout orientation="horizontal"`; each inner wrapper `0dp` + `weight=1`; the row widget itself stays `match_parent`.
- Works with every settings row widget unchanged - ViewBinding ids are identical across orientations, so setup-helpers are NOT touched.
- `ConstraintLayout.Flow` (`flow_wrapMode=chain`, packed) reserved for BUTTON groups only.
- Rejected: GridLayout (fragile fixed child indices break when rows are hidden by flavor capability-gates); sw-qualified buckets (deferred as supplement, not first iteration).

**Column-count rule by element type:**
- Toggles/switches: 2 per row (phone). 3-up only on tablet `sw720dp` - deferred to extensibility, not first pass (3-up clips RU/UK labels at phone width).
- Buttons / radios / chips: 3-4+ per row via Flow.
- Wide inputs / dropdowns: 1 per row - unfit for a half-column (e.g. `SettingsInputRow` is internally `match_parent`, see [[settingsinputrow-greedy-width]]).
- Help/description text: keep the row's built-in `iconHelp` slot; merge short label+control into one row; never split long explanations across columns.

**Hard invariants:**
- Multi-column lives ONLY in `layout-land/`. Portrait stays single-column, untouched (ADR-2).
- S0605 single-wide invariant holds: single wide elements (dropdowns) never stretch edge-to-edge - bound them to ~480px or less. Multi-column packing is NOT a license to stretch.
- New column pairs need explicit `nextFocusLeft/Right` in the same change (project had none); vertical traversal is the default.

**Gotcha found during S0609 device sweep:** several `layout/` (portrait) media/playback fragments ALREADY carry `orientation="horizontal"` pairs that render 2-up and look cramped on narrow phones (`containerImagesGif`, Min/Max size inputs, command-panel/detailed-errors pair). So "portrait is single-column" is the intent but not literally true in the working tree - when auditing portrait, check the actual `layout/` file, don't assume.

**Why:** wide landscape/tablet screens leave large idle right margins and force long scrolls when settings rows stack single-column. Owner wants meaningful width use, not full-bleed stretch.

**How to apply:** when adding/reviewing a landscape settings layout, use the weighted-LinearLayout canon, match column count to the element type above, edit `layout-land/` only, keep dropdowns bounded, and stress-test EN/RU/UK long labels (RU is the worst case - multi-line subtitles must wrap inside their own column).
