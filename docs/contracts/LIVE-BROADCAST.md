<sub class="doc-stamp">26.10.26 14:19</sub>

# Pointer - `LIVE-BROADCAST`

| | |
| --- | --- |
| **Id** | `LIVE-BROADCAST` |
| **Version** | 1.6, active - 2026-10-26 (S4445: item U in `20_contract_amendment_2026-10-26.md` - the end through a tunnel and the directory's removal: the BYE may not cross a `TUNNEL`, a tunnel `connect` refused `ended` is a deliberate end and `unavailable` is not, the removal is read when the stream stops and never stops one that still delivers, a producer lost past the keepalive window reads as ended as a recorded limit; answers the exchange-server implementer's questions and StreamsPlayer Android's note); 1.5 - 2026-10-25 (S4442: item T in `19_contract_amendment_2026-10-25.md` - a deliberate end a consumer can tell from a lost connection: `broadcast-end` first, an RTCP BYE or the clean end of the chunked body, then close, `404` or a closed port afterwards; answers StreamsPlayer's proposal of 2026-10-25); 1.4 - 2026-10-25 (S4247: item S in `18_contract_amendment_2026-10-25.md` - the pending descriptor: the optional `pending {until}` member of a broadcast that has not started yet, `RELAY` and `TUNNEL` only, and the waiting consumer); 1.3 - 2026-10-22 (S4405: item R in `17_contract_amendment_2026-10-22.md` - the outward link's password is the broadcaster's choice: generated, set by the person or none; with none the hand-out descriptor carries no `auth`); 1.2 - 2026-10-18; 1.1 - 2026-10-17 (the consolidation amendment: a descriptor of a higher `schemaVersion` answers the canonical `format-unsupported` refusal); 1.0 - declared 2026-10-15 (S4245, the owner's decision 22 of milestone S4203: every section and amendment binds, and the contract only grows from here); amendment 16 adds item Q, the `seal` member of a `RELAY` and a `TUNNEL` endpoint; amendment 15, items N to P: the sources of a broadcast, sound and video on the watch and the phone's hand-off to the watch; amendment 14, item M: a broadcast link with a password; amendment 13: PC producer, HTTP MPEG-TS video, `RELAY`/`TUNNEL` endpoints through `DEVICE-EXCHANGE`, receivers and cast. Owner: this product |
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
- A link made before the start is the pending descriptor: `pending {until}` at most 30 days out, top-level `url` on
  the `RELAY` address, `RELAY` and `TUNNEL` only, `inner` on `192.0.2.1`, `auth` by item R and `seal` by item Q
  (1.4 item S, S4247).
- On a pending descriptor, show a waiting state instead of an error; probe the `RELAY` status code with a `GET` no
  more often than every 30 s and only while that state is visible, closing after the status line so no listener slot
  is spent; stop at `until`; play only on the person's action unless the person's setting plays at once (1.4 item S).
- On the person's stop, sign-out or exit, send `broadcast-end` before closing any stream, end each RTSP session with
  an RTCP BYE and each HTTP shape with the clean end of the chunked body, then close; after the end answer `404` or
  leave the port closed, never a stream that never starts; the watch's 2.2 shape is unchanged (1.5 item T, S4442).
- As a receiver, read the directory's removal, a BYE or clean end followed by a refused, `404` or `410` reconnect, or a
  relay `410` as a deliberate end: show the ended state with an explicit reconnect and try no more (1.5 item T).
- On a `TUNNEL` endpoint expect no BYE: read a tunnel `connect` refused `ended` as a deliberate end and `unavailable` as
  none; read the directory's removal when the stream stops, never stopping a stream that still delivers, and let it
  end a bounded reconnect already running; a producer lost past the keepalive window reads as ended, a recorded limit
  (1.6 item U, S4445; client work S4448).

## Where it lives here

- Descriptor: `app_v2/.../data/broadcast/` (`BroadcastDescriptorDto.kt`, `BroadcastDescriptorParser.kt`,
  `BroadcastDescriptorSerializer.kt`, `BroadcastEndpointDto.kt`) and
  `app_v2/.../domain/usecase/broadcast/EncodeBroadcastDescriptorUseCase.kt`.
- Watch: `wear/.../data/broadcast/` and `wear/.../service/VoiceRecordingService.kt`.
