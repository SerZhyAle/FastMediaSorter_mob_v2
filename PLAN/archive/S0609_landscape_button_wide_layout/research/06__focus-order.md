# Research 06 - Focus order across column pairings

Resolves strategic §6.6. Source: layout grep (2026-06-22).

## Finding

No `nextFocusDown/Up/Left/Right` attributes exist in any settings fragment layout (portrait or landscape). Only 3 elements set explicit `focusable=true` (`tvGmsSettingsLink`, `buttonAddHomeWidget`, `tvVersionInfo` in general) - redundant since they are clickable.

## Implication for 2-column rows

By default, D-pad/keyboard traversal follows view declaration order. In a horizontal 2-column row the natural order is left then right, then next row - which is acceptable for paired toggles. The risk is left-right ambiguity when moving DOWN from a 2-column row into the next single-column row.

## Decision

- For each NEW landscape 2-column pairing, set explicit `nextFocusRight` (left col -> right col) and `nextFocusLeft` (right col -> left col) so horizontal D-pad navigation is deterministic (CLAUDE.md Rule 16).
- Vertical (`nextFocusDown`/`Up`) can stay on default declaration order unless a specific row breaks it during device test.
- Apply focus attributes in the same step that creates the pairing - no separate focus phase, to avoid editing each layout twice.
- The row widgets are already focusable as clickable; no `focusable=true` additions needed.
