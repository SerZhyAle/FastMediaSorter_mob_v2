# Phase 06 - Draw-overlay in standalone (FOLLOW-UP, deferred)

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started (deferred by owner sign-off 2026-06-10)
**Depends on:** 01-05

## Why deferred

The strategic premise that "crop/draw are generic-ready" is only half right. `ImageDrawOverlayManager` (engine) is generic, but the save side `PlayerDrawingSaveHelper` (~587 LOC) is `PlayerActivity`-bound and depends on infrastructure standalone lacks:

- **Base bitmap provider:** in-app reads `viewModel.currentDisplayedBitmap`; standalone has no equivalent (Glide loads straight into `photoView`). A standalone base-bitmap seam is required to merge the overlay onto the source image.
- **Draw toolbar layout:** `drawOverlayToolbarStub` is only in `activity_player_unified.xml(-land)`; standalone layouts omit it. Must add the `<ViewStub>`/`<include>` to both orientations.
- **Save helper:** a ~300-LOC standalone `PlayerDrawingSaveHelper` equivalent (merge → in-place/Downloads write → re-render), reusing `MergeDrawOverlayUseCase`.

Bundling this with Group A risked a half-working overlay. Split per owner decision.

## Scope when picked up

- Standalone base-bitmap provider (extract displayed bitmap from `photoView` drawable or re-decode the resolved local path).
- Add draw toolbar stub to both standalone layouts; add `btnEditDraw` bar button + gate.
- `StandaloneDrawSaveHelper` wiring `ImageDrawOverlayManager` + `MergeDrawOverlayUseCase`.
- Strings/docs/catalog; device-test.

## Done criteria

- [ ] Draw button appears for editable local images in standalone; draw + save in-place and save-to-Downloads both work; in-app draw unaffected.
