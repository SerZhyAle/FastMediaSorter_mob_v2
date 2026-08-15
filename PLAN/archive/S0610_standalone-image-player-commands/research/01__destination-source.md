# Research 01 - Destination source in a resource-less standalone context

**Strategic §6 item:** 1
**Status:** Resolved

## Question

Where do «Copy to» / «Move to» destinations come from when the standalone player has no current resource?

## Finding

`GetDestinationsUseCase.invoke()` returns a global `Flow<List<MediaResource>>` derived from all resources:
filter `isDestination && destinationOrder >= 0 && !isReadOnly && !isVirtualPath`, sorted by `destinationOrder`,
capped at `settings.maxRecipients`. It takes no resource argument.

`DestinationButtonsManager.populateDestinationButtons()` calls this global use-case, then excludes the current
resource via `allDestinations.filter { it.id != resourceId }`. The exclusion is the only place a resource id is used.

## Decision

The standalone image host uses the same global destination list. With no resource context it supplies a sentinel
resource id (e.g. `-1L`) so the exclusion filter removes nothing - all configured destinations are offered.

Empty list: the panel still renders the «..» custom-path button (existing behavior), so the user can pick an arbitrary
folder even with zero configured destinations. No special-casing required.

## Implication for the plan

No new use-case. Reuse `GetDestinationsUseCase` as-is. Wiring passes `getCurrentResourceId = { -1L }`.
