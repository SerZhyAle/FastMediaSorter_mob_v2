<sub class="doc-stamp">26.10.14 21:42</sub>

# Pointer - `LIVE-BROADCAST`

| | |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 0.21, draft - amendment 15 is [PROPOSED]: the sources of a broadcast, sound and video on the watch and the phone's hand-off to the watch (items N to P); amendment 14 is [PROPOSED]: a broadcast link with a password (item M); amendment 13 is [PROPOSED]: PC producer, HTTP MPEG-TS video, `RELAY`/`TUNNEL` endpoints through `DEVICE-EXCHANGE`, receivers and cast. Owner: this product |
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
- A `TUNNEL` endpoint's `inner` is never omitted: with no LAN address it keeps the scheme and the path on
  `192.0.2.1` (0.18 item J); the relay upload gains no listener-attached notice (item K).
- A broadcast handed outside with a `RELAY` or `TUNNEL` endpoint is protected: `auth` in the descriptor, Digest at
  the phone's and the watch's HTTP and RTSP servers, the password never in a link, file or barcode (0.20 item M,
  S4215).
- Name the source of a broadcast `mic`, `system-audio`, `screen`, `camera` or `file` where a device runs more than one
  at once, and keep one stable `sourceId` where it runs one (0.21 items G and N); end a broadcast whose per-session
  platform grant ends, the phone's screen included (item N).
- The watch broadcasts `AUDIO_ONLY` only and declares only what it plays; it plays sound and video (0.21 item O).
- Hand a live broadcast to the watch as one URL with its title, kind and open-now flag, never as a bare URL when the
  descriptor carries `auth`, and never over the phone-to-watch link as a stream (0.21 item P, S4217).

## Where it lives here

- Descriptor: `app_v2/.../data/broadcast/` (`BroadcastDescriptorDto.kt`, `BroadcastDescriptorParser.kt`,
  `BroadcastDescriptorSerializer.kt`, `BroadcastEndpointDto.kt`) and
  `app_v2/.../domain/usecase/broadcast/EncodeBroadcastDescriptorUseCase.kt`.
- Watch: `wear/.../data/broadcast/` and `wear/.../service/VoiceRecordingService.kt`.
