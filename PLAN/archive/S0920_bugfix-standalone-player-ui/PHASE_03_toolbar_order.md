# Phase 03 - Unified top-toolbar order (#5)

**Goal:** all standalone hosts share one top-toolbar order.

Canonical order (majority of 3 hosts + richest surface = Document):
`Back -> [Prev, Next, Random, Slideshow] -> Delete -> Favorite -> Share -> Info -> Rename -> [type-specific] -> Overflow`

Only the text host diverges: it places `btnRenameCmd` AFTER its type-specific cluster (Search/Translate/Copy/Edit) instead of before it.

## Steps

1. In `res/layout/activity_standalone_text.xml`, move `btnRenameCmd` from its current position (after `btnEditTextCmd`) to immediately after `btnInfoCmd`, before the Search/Translate/Copy/Edit cluster.
   - Preserve id, visibility default, focusable/clickable and any `nextFocus*` attributes; fix the D-pad focus chain if the moved view breaks it.
   - Verification: `.\a.ps1 fr` PASS; view order in xml matches canonical.

2. Apply the identical move in `res/layout-land/activity_standalone_text.xml` (Rule 11 - landscape parity).
   - Verification: portrait and landscape button sequences identical.

3. No Kotlin change - `TextStandaloneActivity` toggles `btnRenameCmd.isVisible` reactively by id.

## Verification predicates

- `.\a.ps1 fr` PASS (resources/manifest).
- Portrait and landscape text layouts have `btnRenameCmd` right after `btnInfoCmd`.
- On-device (device gate): text top-panel order matches image/video/doc.
