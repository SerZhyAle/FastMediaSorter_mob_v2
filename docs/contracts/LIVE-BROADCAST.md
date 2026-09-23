# Pointer - `LIVE-BROADCAST`

| | |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 0.9, draft. Owner: this product |
| **Home** | `live-broadcast/README.md` in the shared contracts catalog |
| **Role here** | producer - the phone and watch audio broadcast; also reads its own descriptor on import |

## What this repository must do to stay conformant

- Never publish a loopback address: with no reachable LAN address the broadcast does not start and no
  descriptor is created (producer rule 1).
- Offer no in-stream metadata (producer rule 2).
- One descriptor across link, import file and barcode (producer rule 3).
- A stream kind moves from `[PROPOSED]` to `[CONTRACT]` only through the handshake of section 6 of the
  contract, with a consumer verifying against a live broadcast (producer rule 4).
- When reading a descriptor, refuse a `schemaVersion` above the supported one as its own outcome, not
  as "malformed".

## Where it lives here

- Descriptor: `app_v2/.../data/broadcast/` (`BroadcastDescriptorDto.kt`, `BroadcastDescriptorParser.kt`,
  `BroadcastDescriptorSerializer.kt`, `BroadcastEndpointDto.kt`) and
  `app_v2/.../domain/usecase/broadcast/EncodeBroadcastDescriptorUseCase.kt`.
- Watch: `wear/.../data/broadcast/` and `wear/.../service/VoiceRecordingService.kt`.
