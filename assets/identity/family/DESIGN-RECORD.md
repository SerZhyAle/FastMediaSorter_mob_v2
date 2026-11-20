# Design record - the FastMediaSorter family mark

Written for `PRODUCT-IDENTITY` section 3.3 rule 30 (the record that is the `source` and `licence` of every rendition of
the family's picture). It is written by hand, hashed in `family-mark.json`, and read with `construction.txt`, which holds
every number and is generated.

## 1. Who drew it, when, and on whose order

Drawn on 2026-11-20 by the canon session of the portfolio (an agent session, Claude) on the owner's order of the same
day, canon ticket CANONR-0160, from numbers by `../../reference/build_family_mark.py`. The owner accepted the drawing
on a review sheet at the second question sitting of the ticket. The drawing is the owner's own work, made by an agent
on the owner's order; it is the owner's to license, and the family uses it under no third-party licence.

## 2. The origin that prompted it

The four-arrow cross the family carried until now is, by the owner's own account, a picture taken years ago from an
online clip-art library. On 2026-11-20 the same drawing was found at `https://www.pngegg.com/en/png-ynwms` under the
single line "Non-commercial use, DMCA", with no author, uploader or original source and no licence that covers a
distributed program, a store listing or a site. FMS_Win's raster master and its unreferenced
`assets/icons/1375239983_105228.png` show it, and FMS_A's `assets/identity/master/mark.svg` (ticket S4694) is a vector
redraw made to match it. The old pictures stay in git history only; each product removes them from every surface when
it adopts this master (`PRODUCT-IDENTITY` section 3.3 rule 31).

This record is not legal advice and does not settle the licence status of the old picture.

## 3. What was kept from the old mark, and what was not

The owner's brief of 2026-11-20 was "minimal changes relative to the former source - blue arrows in all directions
seen at an angle". What the new drawing takes from the old mark, and nothing else:

- **The layout**: a vertical and a horizontal double arrow crossing in the middle, the horizontal one in front.
- **The slant**: one shear, `y' = y + 0.2 x`, the factor read from the matrix of FMS_A's vector master. This is the
  single number taken from a drawing that was itself made to match the old picture; it is the closest point of the new
  drawing to the old one, and the owner chose it knowing so.
- **The hue**: `#0E66CC`, FMS_A's facet colour, kept as the base tone.
- **A light-to-dark reading** as flat shapes: light from the top left.

Dropped: the bevel, the glow, the soft highlight, the outline, the extruded side wall and every gradient. The proportions
are not measured from any picture: shaft half-width 12, head half-width 36 and head length 64 on a tip distance of 120
are the old master's ratios (read from its numbers) rounded to the 4-unit grid and scaled to fill the 240-unit square.
The arms are cut a clearance short of the horizontal shaft where the old mark has an outline.

**Not done:** the old raster and the pngegg image were not traced, sampled or overlaid; the old mark is read by the
review sheet only, for comparison by eye, and by nothing that writes a file of this folder.

## 4. The construction

Every number is in `construction.txt`. In short: a 432-unit canvas (108 dp at 4 units per dp), centre (216, 216), a
4-unit grid, live area a circle of diameter 264 units, the mark inside a 240-unit square; two double arrows from straight
segments, heads of about 59 degrees, four arms of equal length, one slant, at most three flat tones (lit, base, shade),
each a separate path, no gradient, filter, mask, clip path, image or text. The small form thickens the shafts (half-width
16, head half-width 40) and merges the tones to two. The small form is hinted at 16, 20, 24 and 32 px: the same
drawing with every number in pixels and every point rounded to a whole pixel, the viewBox in pixels.

Tones: the light set (lit `#3C8CF5`, base `#0E66CC`, shade `#014592`, plate `#EFF6FF`) and the dark set (lit `#93C0FF`,
base `#589FFF`, shade `#2E7EE6`, plate `#112239`). **The dark set exists because the light set fails on dark surfaces**:
the base `#0E66CC` reads 2.94:1 on the `#202020` taskbar and the shade `#014592` 1.76:1, under the 3:1 a non-text
graphic needs; every tone of the dark set reads at least 4:1 there, and the generator refuses to write the folder when
a rule of section 3.3 rule 28 fails.

Product signifiers (a window frame, a phone, a globe) sit upright in the lower trailing box `[50, 90] x [50, 90]`; the
sharing, server and debug badges in the upper leading box `[-90, -50] x [-90, -50]`.

## 5. The candidates and the pick

Round 1 (the proposal's own construction, three drawings of one sheet): A "Prism" (one solid faceted cross turned 15
degrees), B "Two arrows" (two double arrows crossing with a lean), C "Four destinations" (four arrows leaving a centre
square). The owner declined all three and asked for variants closer to the old mark. Round 2: variant D2, "the same
layout, its own drawing", accepted as drawn. The owner passed over D3 (the old mark's depth as a side wall) and D4 (the
old mark's tone logic, each arrow split along its ridge); neither was drawn. The round-1 and round-2 sheets and
generators are kept in canon ticket CANONR-0160 (local, not shipped).

## 6. What was looked at

Read on the review sheet from `build_family_mark.py --sheet`, by eye, on 2026-11-20:

- At 16 px the hinted small form shows four arrow heads: the up and down heads of 4 px, the left head and the dark
  right head, each its own pixels; the arms do not merge. The cut at the crossing closes at 16 and 20 px, where the
  vertical arms run into the shaft in the same tone - a cross with a dark right arm.
- At 96 px the three product signifiers are told apart in grey scale by silhouette: a wide frame, a tall phone, a round
  globe. **At 48 px the signifier is about 7 px**, a mark rather than a picture; the three differ by the proportions of
  that mark (wide, tall, round) and the family map shows them at 96 px where they read. This is the reading to repeat at
  every release of the master, and a weaker reading at 48 px is a finding for the next version, not a hidden one.
- Beside the old mark the new drawing reads as the same family, not as the same drawing: flat tones against a glossy
  bevel, thin straight arms against a rounded pseudo-3D cross.
- A second reader, 2026-11-20: a model reader given only the two marks side by side at 48 px (the current mark and the
  new full form, each cropped to its content and scaled to one frame, in an order drawn at random, labelled 1 and 2),
  with no word on which was which or what they were for, answered "one family, different drawings". It named flat
  against glossy, thin shafts against chunky, and the same four-arrow cross, lean and blue as what ties them. It is a
  model's reading, not a person's.

## 7. The trademark search (a fact, not a clearance)

Run on 2026-11-20 with `masters/mark-full.svg` rendered at 512 px on white and uploaded as an image, in two free,
read-only tools. It is a basic search, not a legal clearance, and a hit would be a finding for the owner, not a verdict.

- **EUIPO TMview** (`https://www.tmdn.org/tmview`, 144,071,347 marks, the EUIPO's own search across the European Union
  offices and WIPO): image search on the whole mark, shape only (colour off). Without a filter: 999 results (the tool's
  cap); with Nice classes 9 and 42: 257 results. The first page of 30 of each set was read by eye. No mark on either page
  reproduces the drawing, a blue cross of two double arrows under one slant. The nearest are generic: black four-way arrow
  crosses (classes 9 and 42, registered; also 11, 25 and 43), black crossed diagonal arrows (classes 9 and 42, registered
  and ended), and one blue mark of an upward and two rightward arrows (classes 9 and 35, registered).
- **WIPO Global Brand Database** (`https://branddb.wipo.int`, Brand logo, shape similarity, no class filter): the search
  ran and returned 36,192,310 ranked records, but the result logos did not render in the browser used, so no similarity
  was judged there and nothing is claimed from it. A browser where they render, or a person, repeats it.

Result: no similar figurative mark found in the part read. Not covered: the pages after the first of each set, the
conceptual (meaning) search, EUIPO eSearch plus as its own tool, and the national offices' tools.

## 8. The typeface

`lockup/typeface.txt`: Manrope ExtraBold, SIL Open Font License 1.1.
