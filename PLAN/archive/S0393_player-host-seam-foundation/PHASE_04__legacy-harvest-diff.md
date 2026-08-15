# Phase 04 - Legacy harvest diff (read-only)

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 01
**Blocks:** 05, 06

## Objective

Before deprecating the legacy `StandalonePlayerActivity`, produce the exhaustive list of what it does that the specialized hosts do NOT - so nothing is lost in the port. Read-only.

## Approach

- Diff `StandalonePlayerActivity` (941 LOC, unified layout, full ViewManager) against PhotoVideo/Audio/Document/Text.
- Known uniques (S0392 MATRIX §9): PiP (`PictureInPictureManager`), playback-control dialog (`showPlaybackControlDialog`/`PlaybackControlDialogFragment`), full keyboard layer (`PlayerKeyboardHandler` incl. text-scroll/home/end keys), WebView selection ActionMode (Translate/Search-in-Google), full `StandaloneViewManager` lanes.
- Confirm/expand; produce `HARVEST.md` mapping each unique → target host + seam member needed.

## Verification

- `HARVEST.md` exists: every legacy-only capability → destination host + how it binds via the seam.

## Phase Done Criteria

- [ ] `HARVEST.md` complete; no legacy-unique capability unaccounted for.
