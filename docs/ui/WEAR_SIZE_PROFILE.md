<sub class="doc-stamp">26.11.14 23:03</sub>

# Watch size-profile record

The size-profile record of the `wear` module, as `UI-SIZING` 1.2 section 3 ("Wear OS") asks of every product with a
Wear OS app. `BUTTONS` 2.2 section 10 item A, `BOOLEAN-OPTION` 2.1 section 7 item A and `COLLAPSIBLE-GROUP` 1.2 section 7
item A point here. Written from the source on 2026-11-14 (S4600); the component metrics themselves are fixed by
`docs/ui/WEAR_UI_COMPONENT_PATTERNS.md`.

## Profile

- Input profile: touch, fixed at opening on every watch screen; the watch has no pointer or keyboard profile.
- Ordinary target floor: 48 by 48 dp (`GridColumnFit.DEFAULT_MIN_TARGET_DP`); list rows are at least 52 dp high.
- Compact mode: none selectable; the watch exposes no compact size range. One surface, the calculator keypad, is
  drawn below the ordinary floor and declares its own floor under "Declared compact floor - calculator keypad".
- Interface scale: one scale for every covered component, the system font scale and display size; text wraps instead of
  clipping on full-width rows (S3362).
- Display: round, 192 to 240 dp, as the test-lab Android profile names it; square displays use the same tokens.

## Permanent variant - toolkit components

Compose for Wear OS components whose pill ends, round-display edge scaling and palette roles the toolkit decides. They
are a permanent variant, not an expiring exception.

- `Chip`, `CompactChip` and their wrappers `StandardWearChip`, `StandardWearLongPressChip`, `LongPressChip`.
- `Button`, `CompactButton` of the Wear Material library.
- `ScalingLazyColumn` and its wrapper `WearListColumn`, including the edge scaling of the list items.
- `Card` through `StandardWearCard`.
- `ToggleChip` through `StandardWearToggleChip`: its chip shape belongs to the variant. Its boolean form draws the
  `BOOLEAN-OPTION` checkbox at reading start inside the label slot, so the glyph is not a variant (deviation 1).

Everything else binds these components unchanged: the 48 dp floor, one localized name per command, accessible state and
activation, reflowing text.

**Review condition.** Re-read this section when the module moves to another Wear Compose generation (Material 3 for Wear
OS), when a toolkit component not listed here is added to a screen, or when `UI-SIZING` section 3 changes.

## Product-drawn controls - rule 5 binds them

- `WearRectangularButton`: `WearCellCorner` 8 dp corners, the toolkit default button size; no pill ends.
- `WearSegmentedToggleRow` cells: at least the 48 dp floor high, chosen cell marked by colour and `selected` semantics.
- `PlayerCommandButton`: 48 dp touch target around 32 dp or 24 dp glyphs.
- The System info group header (`SectionTitle` in `SystemInfoScreen.kt`): keeps the `COLLAPSIBLE-GROUP` geometry. A 16 dp
  vector triangle marker leads the caption at the reading-start side of the list item, 8 dp before it: down when the
  group is open, toward reading end when it is closed. One activation region covers the whole row, at least the 48 dp
  floor high; a caption that wraps grows the row.
- The System info collapsed group header (`SectionChip` in `SystemInfoScreen.kt`): the same geometry in its packed form.
  The closed-state triangle leads the caption, 8 dp before it; one activation region covers the whole chip, at least
  48 dp high and 48 dp wide; a caption that wraps grows the chip instead of clipping.
- Packing of collapsed headers (`COLLAPSIBLE-GROUP` section 4 rule 3): enabled on the System info screen. The report
  has many sections, and one header per line pushes the report off a round glass of 192 to 240 dp. Consecutive
  collapsed headers form one content-sized run with 8 dp between chips on both axes, so a narrow glass holds fewer
  chips per row than a wide one and a long name takes a row of its own. Chips are never stretched into equal tiles. An
  open section takes a full-width row at its place in the order, with its body directly below, and the collapsed
  headers before and after it pack in their own runs.

## Declared compact floor - calculator keypad

The calculator keys are the one watch control drawn below the 48 dp floor, by owner ruling S2007: five keypad rows and
the value row fit the glass together. `UI-SIZING` section 2 rule 4 asks that such a floor is declared and tested.

- Key height: 25 dp (`KEY_HEIGHT` in `CalculatorScreen.kt`).
- Key width: one of four columns of the width the screen gives the keypad; a key that spans two columns takes both
  widths and the gap between them.
- Gap between neighbouring keys: 2 dp (`KEY_GAP`), so no two targets overlap.
- Clear key: twice the key height, in a row of its own below a guard gap.
- `CalculatorKeyFloorTest` pins both constants; a change to either number changes this record in the same edit.

Every other watch control keeps the 48 dp floor.

## Open deviations

Each one is carried by S4619 (migration of the shared UI contracts) and recorded in the catalog registry.

1. Closed in source by S4627: a boolean `StandardWearToggleChip` row draws the toolkit checkbox glyph at reading start,
   top-aligned with the caption's first line and 8 dp before it, leaves the trailing toggle slot empty and is exposed as
   one checkbox (`Role.Checkbox`). The dated registry exception (until 2026-12-31) stays until the `BOOLEAN-OPTION`
   section 5 captures from a real build exist; S4619 moves the registry row. The radio form is a single choice, not a
   boolean option, and is outside `BOOLEAN-OPTION`.
2. Closed in source by S4628 and S4632: the System info group header, open (`SectionTitle`) and collapsed
   (`SectionChip`), draws the vector triangle marker at the reading-start side, toggles from the whole header and is at
   least 48 dp high. The dated registry exception stays until the `COLLAPSIBLE-GROUP` conformance captures from a real
   build exist, narrow and wide examples of the packed form among them; S4619 moves the registry row.
3. Closed in source by S4628: the calculator keys are 25 dp high by owner ruling S2007, and that height is the declared
   compact floor of the keypad, pinned by `CalculatorKeyFloorTest`. The dated registry exception stays until the
   measured captures of the keypad exist; S4619 moves the registry row.
