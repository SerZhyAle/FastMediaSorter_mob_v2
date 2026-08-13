# Phase 06 - Port keyboard layer + text-scroll keys + WebView ActionMode

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 04
**Blocks:** 07

## Objective

Give the specialized Audio/Document/Text hosts the input layer they lack (S0392 MATRIX §8, §9.2) and port the WebView selection ActionMode, using the already-generic `PlayerKeyboardHandler` parser.

## Approach

- **Keyboard/D-pad:** the parser `PlayerKeyboardHandler` is GEN; supply a per-host callback (transport for Audio, page/chapter nav for Document, scroll/home/end for Text) + `onKeyDown`/`dispatchGenericMotionEvent` overrides. Mirror the PV/legacy pattern.
- **Text-scroll keys:** ensure Text host honours PAGE_UP/DOWN/HOME/END (works in legacy, not in specialized Text).
- **WebView ActionMode:** port the `startActionMode` augmentation (Translate / Search-in-Google on selected document text) into `DocumentStandaloneActivity`.

## Verification

- D-pad/keyboard navigates Audio/Document/Text external opens (device-test on emulator with hardware keyboard / D-pad).
- Text host: page/home/end keys scroll.
- Document text selection shows Translate/Search augmentation.
- `standard` build green.

## Phase Done Criteria

- [ ] Audio/Document/Text have working keyboard/D-pad; Document has WebView ActionMode; no regression.
