# Phase 03 - Document/Text adopt PlayerHostCapabilities

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 01
**Blocks:** -

## Objective

`DocumentStandaloneActivity` and `TextStandaloneActivity` currently do NOT implement `PlayerHostCapabilities` (S0392 MATRIX §9.1), so the shared capability/seam pipeline cannot bind to them. Make them join.

## Approach

- Decide full contract vs a narrow sub-contract (strategic §6.2): if the full `PlayerHostCapabilities` carries members irrelevant to doc/text (video handle, stereo), introduce a narrow super-interface they implement, with the heavy members on a sub-interface PV/AU keep.
- Implement on both hosts; wire `supportsTypeSpecificActions` + the seam.

## Verification

- `Grep` - both hosts implement the (sub-)contract.
- `standard` build green; doc/text external open unchanged (device smoke).

## Phase Done Criteria

- [ ] Document + Text hosts are in the capability/seam pipeline; no behaviour regression.
