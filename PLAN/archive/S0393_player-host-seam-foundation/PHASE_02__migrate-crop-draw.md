# Phase 02 - Migrate crop/draw delegates onto the seam

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 01
**Blocks:** -

## Objective

Prove the seam end-to-end on the two already-generic-engine actions: crop family + draw. After this, the same delegate serves in-app AND standalone, replacing the S0390 `StandaloneImageEditController` duplication path.

## Approach

- Refactor `PlayerCropDelegate` (and the draw save helper) to consume `PlayerActionHost` instead of `PlayerActivity`.
- `PhotoVideoStandaloneActivity` provides the seam; fold `StandaloneImageEditController` (S0390) into the shared delegate so crop logic lives once.
- Draw overlay: the standalone base-bitmap provider + toolbar stub (the S0390 phase-06 deferral) is satisfied here via the seam's overlay mount + bitmap hook.

## Verification

- `Grep` - `PlayerCropDelegate` no longer references `PlayerActivity`; references the seam.
- Crop works in-app AND in PhotoVideoStandalone from the same delegate (device-test both).
- Draw overlay works in both hosts.
- `standard` build green; in-app crop/draw unchanged (regression device-test).

## Phase Done Criteria

- [ ] Crop + draw run through one seam-based delegate in both in-app and standalone; no behaviour regression in-app.
