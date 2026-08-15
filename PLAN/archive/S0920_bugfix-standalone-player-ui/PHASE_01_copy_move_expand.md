# Phase 01 - Copy/Move panel expand (#1)

**Goal:** tapping the "Copy to.."/"Move to.." section header expands/collapses its buttons grid in every standalone host.

Root cause: standalone hosts never register `setOnExpandedChangeListener` on the Copy/Move headers, so the chevron flips but the grid never toggles. `DestinationButtonsManager` already owns the headers and the `setCopyPanelExpanded()`/`setMovePanelExpanded()` persist+visibility methods.

## Steps

1. Add a public binder method to `DestinationButtonsManager` (`ui/player/helpers/DestinationButtonsManager.kt`):
   - `fun bindHeaderToggles()` that registers `safeViews.copyToPanelHeader.setOnExpandedChangeListener { setCopyPanelExpanded(it) }` and the Move twin.
   - KDoc: WHY (standalone has no `CommandPanelController`), one line.
   - Verification: `.\a.ps1 fk` compiles; grep shows the method present.

2. Call `bindHeaderToggles()` once during setup in each of the four hosts, where the host wires its `DestinationButtonsManager`:
   - `PhotoVideoStandaloneActivity`
   - `TextStandaloneActivity`
   - `DocumentStandaloneActivity`
   - `AudioStandaloneActivity`
   - Verification: grep each host for `bindHeaderToggles()`; compile.

3. Confirm `updateCopyPanelVisibility`/`updateMovePanelVisibility` already call `setExpanded(.., notify = false)` so the binder does not re-enter (it does - no change needed, note only).

## Verification predicates

- `.\a.ps1 fk` PASS.
- All four hosts call `destinationButtonsManager.bindHeaderToggles()`.
- On-device (device gate): tapping either header in text/image/video/audio standalone toggles the grid.
