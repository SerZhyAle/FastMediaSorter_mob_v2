# Phase 08 - Docs, catalog, device-test

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 BlockNeedUserTest (code finalized; on-device sweep pending)
**Depends on:** all

## Objective

Finalize: catalog the new seam + adapters, dev-log, and device-verify in-app non-regression + the ported standalone capabilities. Update the S0392 MATRIX rows that moved to present.

## Steps

1. `scripts/catalog_sync.ps1 -Module app_v2`; fill role/status for the seam interface + new adapters/managers.
2. `add_to_dev_log.ps1` per modified file.
3. Update S0392 `MATRIX.md` rows now satisfied (PiP, playback-dialog, keyboard, text-keys, WebView ActionMode, doc/text capabilities) → present.
4. Device-test sweep: in-app player unchanged (crop/draw/edit/nav); standalone PV PiP + playback-dialog; Audio/Doc/Text keyboard; Document ActionMode.
5. Status → `BlockNeedUserTest`; insert one `Timber.d("S0393: ..")` per changed flow before the final build.

## Verification

- Catalog has the seam interface.
- MATRIX updated.
- Device-test pass recorded.

## Phase Done Criteria

- [x] Catalog synced (`scripts/catalog_sync.ps1 -Module app_v2`).
- [x] Dev-log entry added.
- [x] S0392 MATRIX audio-keyboard row refreshed (PiP/playback-dialog/keyboard/ActionMode already present + consolidated "Update after S0393" section).
- [x] 5 `Timber.d("S0393: ..")` tags inserted at changed-flow entry points (seam crop, standalone PiP, playback-control dialog, shared keyboard layer, WebView ActionMode).
- [x] `standard` + `noLegal` debug green.
- [x] Status `BlockNeedUserTest`.
- [ ] On-device sweep green (owner) → then strip tags + status `Verified`.
