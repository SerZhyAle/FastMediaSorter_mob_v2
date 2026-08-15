# Phase 05 - Port PiP + playback-control dialog

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 04
**Blocks:** 07

## Objective

Port the two highest-value legacy-only capabilities into `PhotoVideoStandaloneActivity` (video lane) via the seam: Picture-in-Picture and the per-file playback-control dialog (speed / track / colour).

## Approach

- **PiP:** PV already declares `supportsPictureInPicture` in the manifest but never wires `PictureInPictureManager` (S0392 MATRIX §9.4, declared-but-dead). Wire it through the seam (manager is MIXED, shared with legacy).
- **Playback-control dialog:** real in legacy (`showPlaybackControlDialog`), stubbed in PV (`StandaloneVideoControlsManager` empty callback). Un-stub by reusing the legacy dialog path through the seam.

## Verification

- PV external video open: PiP enters on home/user-leave; playback-control dialog opens with speed/track/colour.
- `standard` build green; legacy host still works (not yet deprecated).
- Device-test both.

## Phase Done Criteria

- [ ] PiP + playback-control dialog work in PhotoVideoStandalone via the seam.
