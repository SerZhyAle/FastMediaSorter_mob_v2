---
name: moving-code-resurfaces-ratchet-findings
description: Extracting a class to satisfy detekt LargeClass re-fires the ratchet gates on code you only MOVED - a BuildConfig read becomes a "new flavor flag in src/main"; pass the resolved boolean instead of carrying the flag
metadata:
  type: feedback
---

Extracting code into a new file to get under a detekt ceiling makes every count-ratchet gate treat the
moved lines as **new occurrences in a changed file**, because those gates compare a per-file delta
against HEAD and the new file has no HEAD.

Measured 2026-08-21 on S1883. Adding one settings group tipped `OperationsSettingsFragment` past
detekt's `LargeClass` threshold (no baseline entry existed - it had been sitting just under), so the
section-registration wiring moved to a new `OperationsSectionsManager`. That wiring contained one
`BuildConfig.ENABLE_SCHEDULED_OPERATIONS` read, tolerated for years in the fragment. In the new file
`assert-neuroslop`'s `flavor-flags` dimension reported `+1 new flavor flag read in src/main` and failed
the close - for a line whose text had not changed at all.

**Why:** the ratchets are delta-vs-HEAD per file, not project-wide totals, which is exactly what makes
the dirty-tree close possible. The same property means a move reads as a birth. Detekt's own baseline
behaves the other way - its ids are content-based, so a move re-keys them too (see
[[detekt-baseline-signature-resurface]]); between the two, almost nothing survives an extraction
untouched.

**How to apply:**

- Before extracting, grep the block you are about to move for the things the ratchets count:
  `BuildConfig.`, `GlobalScope`, `android.util.Log`, `TODO(`, `!!`, long dashes, raw `getPackageInfo`
  flags. Each one will be reported as new in the destination file.
- For a flavor flag the fix is not to move it: **resolve it at the old call site and pass the boolean**.
  `registerAll(wearAvailable, scheduledAvailable)` keeps `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` in
  the fragment, where its occurrence count is unchanged, and leaves the helper flavor-agnostic - which
  is also what CLAUDE.md Rule 14 wants of shared code.
- Expect the round trip: extraction fixes one gate and trips another, so plan two closure runs rather
  than treating the second failure as a mistake.
- Do not reach for a baseline edit to make a moved line green - the line is real debt that just changed
  address, and the ratchet is the only thing that ever notices.
