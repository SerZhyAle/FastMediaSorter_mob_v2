<sub class="doc-stamp">26.10.18 17:46</sub>

# Pointer - `LIVE-BROADCAST`

| | |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 1.3, active - 2026-10-22 (S4405: item R in `17_contract_amendment_2026-10-22.md` - the outward link's password is the broadcaster's choice: generated, set by the person or none; with none the hand-out descriptor carries no `auth`); 1.2 - 2026-10-18; 1.1 - 2026-10-17 (the consolidation amendment: a descriptor of a higher `schemaVersion` answers the canonical `format-unsupported` refusal); 1.0 - declared 2026-10-15 (S4245, the owner's decision 22 of milestone S4203: every section and amendment binds, and the contract only grows from here); amendment 16 adds item Q, the `seal` member of a `RELAY` and a `TUNNEL` endpoint; amendment 15, items N to P: the sources of a broadcast, sound and video on the watch and the phone's hand-off to the watch; amendment 14, item M: a broadcast link with a password; amendment 13: PC producer, HTTP MPEG-TS video, `RELAY`/`TUNNEL` endpoints through `DEVICE-EXCHANGE`, receivers and cast. Owner: this product |
| **Home** | `live-broadcast/README.md` in the shared contracts catalog |
| **Role here** | producer - the phone and watch audio broadcast; also reads its own descriptor on import |

## What this repository must do to stay conformant

- Never publish a loopback address: with no reachable LAN address the broadcast does not start and no
  descriptor is created (producer rule 1).
- Offer no in-stream metadata (producer rule 2).
- One descriptor across link, import file and barcode (producer rule 3).
- A kind's conformance is shown by the handshake of section 6 of the contract, a consumer verifying against a live
  broadcast; since 1.0 it is evidence and no longer the gate between `[PROPOSED]` and `[CONTRACT]` (producer rule 4).
- A `RELAY` and a `TUNNEL` endpoint of a sealed broadcast carry `seal` - `{v, key}` and `{v, pin}` in a descriptor
  handed outside, `{v}` alone in a directory record - and the keys go only into a descriptor the person hands,
  never through the exchange server or a log; an endpoint whose `seal.v` is unknown is skipped and never played as
  clear bytes (1.0 item Q, S4245).
- When reading a descriptor, refuse a `schemaVersion` above the supported one as its own outcome, not
  as "malformed", with the canonical `format-unsupported` text and no partial import (1.1).
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
- Hand a LAN-only broadcast without `auth` to the watch as one URL with its title, kind and open-now flag; hand one
  with a `RELAY` or `TUNNEL` endpoint or with `auth` by its own message carrying the descriptor and, when protected,
  the pass the phone holds, kept on the watch in its secret store; never hand a stored channel whose password was
  never typed, and never pass the stream over the phone-to-watch link (0.21 item P, S4217, S4261).

## Where it lives here

- Descriptor: `app_v2/.../data/broadcast/` (`BroadcastDescriptorDto.kt`, `BroadcastDescriptorParser.kt`,
  `BroadcastDescriptorSerializer.kt`, `BroadcastEndpointDto.kt`) and
  `app_v2/.../domain/usecase/broadcast/EncodeBroadcastDescriptorUseCase.kt`.
- Watch: `wear/.../data/broadcast/` and `wear/.../service/VoiceRecordingService.kt`.
