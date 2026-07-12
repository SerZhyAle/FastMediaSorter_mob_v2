---
name: res-sw-qualifier-beats-land
description: values-swNNNdp silently overrides values-land for the same key - landscape ints need combined swNNNdp-land buckets
metadata:
  type: project
---

In Android resource matching, smallestWidth (`swNNNdp`) outranks orientation (`land`). Any key
defined in both `values-swNNNdp` and `values-land` resolves to the sw bucket on every device with
sw >= NNN - the `values-land` value is dead there. This bit `resource_grid_column_count`: phones
(sw>=320) always got the sw320dp value 1, so the main resource list stayed single-column in
landscape even though values-land said 4. Fixed 2026-07-11 by adding combined buckets
`values-sw320dp-land` / `values-sw480dp-land` (=2).

**Why:** the qualifier precedence table is easy to forget; the layout LOOKS orientation-driven but
never is once a sw bucket defines the same key. `grid_column_count_landscape` in values-sw480dp
(=2) vs values-land (=6) has the same shape - suspicious but intentional-looking, not touched.

**How to apply:** when an orientation-dependent integer/dimen "does not apply" in landscape, list
ALL buckets defining the key (`grep -Hn <key> res/values*/..`) and check for a swNNNdp bucket
shadowing values-land. Fix with a combined `values-swNNNdp-land` bucket, not by editing values-land.
