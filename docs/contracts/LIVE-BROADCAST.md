<sub class="doc-stamp">26.10.07 05:04</sub>

# Pointer - `LIVE-BROADCAST`

| | |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 0.17, draft - amendment 13 is [PROPOSED]: PC producer, HTTP MPEG-TS video, `RELAY`/`TUNNEL` endpoints through `DEVICE-EXCHANGE`, receivers and cast. Owner: this product |
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
- Video: deliver the first picture within the keyframe interval plus 1 s of a listener connecting, and
  request an IDR when a listener attaches (0.17 item I, `[CONTRACT]` for the RTSP kinds).
- A producer with several sources names each `sourceId` as `<deviceSourceId>:<source>` (0.15 item G).

## Where it lives here

- Descriptor: `app_v2/.../data/broadcast/` (`BroadcastDescriptorDto.kt`, `BroadcastDescriptorParser.kt`,
  `BroadcastDescriptorSerializer.kt`, `BroadcastEndpointDto.kt`) and
  `app_v2/.../domain/usecase/broadcast/EncodeBroadcastDescriptorUseCase.kt`.
- Watch: `wear/.../data/broadcast/` and `wear/.../service/VoiceRecordingService.kt`.
