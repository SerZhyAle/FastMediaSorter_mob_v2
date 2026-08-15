# Phase 01 - Seam interface + in-app adapter

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** 02, 03, 04

## Objective

Introduce the binding-agnostic host-seam and have the in-app `PlayerActivity` implement it as an adapter - additive, zero behaviour change. No delegate consumes it yet (that is Phase 02).

## Approach

- Define a seam interface (working name `PlayerActionHost`) exposing only what binding-coupled delegates need (per strategic §5.1): current-file flow, root view, overlay mount points (media content area + image container), reload-current-file hook, dialog host (`AppCompatActivity`), lifecycle scope, optional resource context.
- Derive the exact member set from the S0392 MATRIX coupling flags + reading the binding-coupled delegates (crop/draw/translate/OCR/save-frame/lyrics/playback-dialog) to see what they actually touch on `PlayerActivity`.
- `PlayerActivity` implements the seam by delegating to its existing binding/VM - no behaviour change.

## Steps

1. Read the binding-coupled delegates; list every `activity.*` / `activityBinding.*` member they reference → the seam's required surface.
2. Define the seam interface.
3. `PlayerActivity implements PlayerActionHost` via adapter members.

## Verification

- `Grep` - seam interface file exists; `PlayerActivity` declares it.
- No delegate changed yet (diff limited to the new interface + `PlayerActivity` adapter members).
- `standard` debug build green.
- Device smoke: in-app player opens, command panel + a couple of actions behave exactly as before.

## Phase Done Criteria

- [ ] Seam interface defined; in-app adapter compiles; in-app behaviour unchanged.
