# Streams Source Spec - 11 - Live Broadcast Contract

**Status:** draft contract, 2026-09-12 (ticket S3050). Sections marked **[CONTRACT]** bind every consumer
now; sections marked **[PROPOSED]** describe the shape the producer is moving to and bind nobody until a
later revision of this file flips them.
**Audience:** StreamsPlayer (Windows) and any other consumer of a live stream published by FastMediaSorter
on a phone or a watch.
**Scope:** live broadcasts only. The on-demand catalog (`stream-catalog.zip`) is files `01`-`10`.

---

## 1. What a live broadcast is

A device running FastMediaSorter captures its microphone (and later its camera) and serves the result
itself. The consumer connects to the device. Nothing is stored and nothing is replayed: a listener hears
what is captured from the moment it connects.

Owner decisions this contract rests on (2026-09-12):

- Four stream kinds, all to be delivered: phone audio, watch audio, phone audio + video with a camera
  choice, phone video without audio.
- Three hand-off channels, carrying the same description: a link, an import file, a barcode.
- Transport today is a direct address in the local network. A data-exchange server that relays or opens a
  P2P path to any consumer anywhere is a planned second transport. This supersedes the LAN-only ruling of
  2026-09-05 (S2508, S2068 ADR-3) as a direction; the LAN transport stays.

---

## 2. Stream kinds

### 2.1 Phone audio **[CONTRACT]** - shipped

- Flavors that can broadcast: `standard`, `noLegal`, `legacy`.
- URL: `http://<lan-ipv4>:<port>/live-audio.aac`; `/live-audio` is accepted too. Default port `8768`,
  user-configurable, no fallback port - if it is taken the broadcast does not start.
- The producer never publishes a loopback address. If no reachable LAN IPv4 address is available on
  Wi-Fi, hotspot, Ethernet or VPN, the broadcast does not start and no descriptor is created.
- Response: `HTTP/1.1 200 OK`, `Content-Type: audio/aac`, `Transfer-Encoding: chunked`,
  `Accept-Ranges: none`, `icy-name`, `icy-genre`, `icy-pub`, `icy-br`. No `icy-metaint`: in-stream
  metadata is never offered, and a consumer that asks for it gets none. That is expected, not an error.
- Payload: ADTS-framed AAC-LC, mono, 44 100 Hz, 128 kbit/s by default (sample rate and bitrate are user
  settings). Every frame carries its own ADTS header, so a late joiner decodes from the next frame.
- Listeners: no cap.
- End of broadcast: the chunked body ends cleanly.

### 2.2 Watch audio **[CONTRACT]** - shipped

- URL: `http://<lan-ipv4>:<port>/listen`. The server ignores the path and method. The port is OS-assigned
  or a remembered preferred port, so it can change between broadcasts.
- Response, verbatim: `HTTP/1.0 200 OK`, `Content-Type: audio/aac`, `Cache-Control: no-cache`,
  `Connection: close`. No `Content-Length`, no chunking: raw bytes until the socket closes.
- Payload: ADTS AAC-LC, mono, 44 100 Hz, 64 kbit/s.
- Listeners: at most 4. A fifth connection gets `HTTP/1.0 503 Service Unavailable` and is closed.
  Every extra connection a consumer opens to the same URL (an ICY metadata probe, a health check) spends
  one of the four.
- End of broadcast: the socket is closed without a terminator; the consumer sees EOF or a reset.

### 2.3 Phone audio + video, camera selectable **[PROPOSED]** - in development (S3038)

- Transport: RTSP, H.264 video + AAC audio, default port `8554`, RTP over TCP.
- The camera is chosen before the start and can be switched on air.
- Not shipped. A consumer must not assume the address, path or SDP until this section is `[CONTRACT]`.

### 2.4 Phone video without audio **[PROPOSED]** - in development (S3038)

- As 2.3 with no audio track.

---

## 3. The broadcast description (descriptor)

### 3.1 Shipped shape, `schemaVersion` 1 **[CONTRACT]**

A UTF-8 JSON object:

- `schemaVersion` - integer, `1`.
- `url` - string, required, non-blank: the address of section 2.
- `mode` - string, required, non-blank: `AUDIO_ONLY` today; `VIDEO_AUDIO` and `VIDEO_ONLY` are reserved for
  2.3 and 2.4.
- `title` - string, optional.
- `sourceId` - string, optional: a stable id of the broadcasting device. A consumer that stores broadcasts
  replaces the stored one with the same `sourceId` instead of adding a duplicate.

Rules:

- Ignore members you do not know. The producer adds optional members without raising the version.
- Refuse a `schemaVersion` above the one you support, and say "update the app" rather than "invalid".
- Refuse a `mode` you do not support with a clear message; never guess the transport from it.

### 3.2 Descriptor v2 fields **[CONTRACT]** - shipped (S3051)

- Optional `endpoints` list where each endpoint declares `url`, `transport` (`HTTP`/`RTSP`/`P2P`), `mode`, `videoCodec`, `audioCodec`, `sampleRate`, `bitrate`, `isLive`, and `targetLatencyMs`.
- Optional top-level `isLive` boolean and `targetLatencyMs` long fields.
- `schemaVersion` remains `1` to guarantee backward compatibility with legacy consumers; legacy consumers read top-level `url` and `mode` while ignoring unrecognized JSON fields.

---

## 4. Hand-off channels

### 4.1 Barcode **[CONTRACT]**

- Payload: `FMSBCAST1:` followed by Base64 (standard alphabet) of the GZIP of the section 3 JSON.
- Shown by the phone after the start and by the watch on its broadcast screen.

### 4.2 Import file **[CONTRACT]**

- Content: the section 3 JSON, uncompressed.
- The phone names it `broadcast_<n>.fmsbcast` and declares MIME type `application/vnd.fms.bcast+json` (shipped S3052).
- A consumer's import accepts both forms - plain JSON and the `FMSBCAST1:` text - and reads at most 64 KiB.

### 4.3 Link **[CONTRACT]** today, **[PROPOSED]** next

- "Send link" shares an Android `intent://` URI carrying the URL-encoded `FMSBCAST1:` descriptor. Its
  `fmsbcast://import?payload=` target opens FastMediaSorter import; Chrome's browser fallback opens
  `broadcast-import.html`, which offers both installation and an explicit app-open tap when the app is
  absent. The plain stream URL of section 2 also works on its
  own in any player.
- The link's `package=` hint names the **sending** build's own application id, so a link shared from a debug
  or `noLegal` build opens that same build. A third-party producer targeting the store app writes
  `package=com.sza.fastmediasorter`.
- The fallback page never auto-navigates to `fmsbcast://`: with no handler an unresolvable custom scheme
  replaces the page and leaves the receiver with a browser error and no install route.
- A plain URL carries no description: no title, no `sourceId`, no kind. A consumer that has only the URL
  applies section 5 when the user marks the stream as live, or when it can tell the stream is one of ours.
- Next: a clickable link form that opens the importer directly. Its scheme is not decided.

---

## 5. Latency and buffering **[CONTRACT]**

### 5.1 What the producer adds

Measured 2026-09-12 from a PC on the same Wi-Fi:

- First byte after the request: 6 ms from the phone, 11 ms from the watch.
- Bytes arrive at the real-time rate from the first moment: 17 KB in the first second from the phone at
  128 kbit/s. There is no backlog burst - the producer keeps no history to send.
- The phone's capture buffer is 8 192 bytes of 16-bit mono PCM, about 0.09 s; AAC framing and Wi-Fi add
  tenths of a second, not seconds.

Anything above half a second of end-to-end delay on a LAN is added by the consumer.

### 5.2 What the consumer must do

The owner accepts that a player's first duty is to keep a broadcast playing without interruption. For a
live FastMediaSorter broadcast that duty is met by reconnecting, not by a deep buffer:

1. **Start at the live edge with a small buffer.** Aim for playback within 1 s of connecting and no more
   than 2 s behind the source in steady state on a LAN transport.
2. **Do not let the delay grow.** After a stall or a reconnect, rejoin at the live edge and drop what was
   missed. Resuming where playback stopped turns every hiccup into permanent extra delay, and nothing is
   lost by skipping: a broadcast has no content worth catching up on.
3. **Keep your resilience in reconnects.** Bounded reconnect with backoff is welcome and is the right tool;
   on a LAN, jitter is milliseconds and a multi-second buffer buys nothing.
4. **Raise the buffer only where the transport needs it.** A future relay or P2P endpoint (section 3.2) may
   justify more, and it will be marked as such. Even then, correct the drift back towards the target.
5. **Open one connection per listener.** An additional probe connection to the same URL is a listener slot
   on the watch (2.2).

### 5.3 How the consumer knows a stream is live **[CONTRACT]** - shipped (S3051)

- Descriptor v2 carries explicit `isLive: true` marker and optional `targetLatencyMs` (e.g. `1000`) per descriptor or per endpoint entry in `endpoints`.
- Every stream that arrived through a section 4 channel is live, and every URL of the section 2 shapes is live.


### 5.4 StreamsPlayer, observed 2026-09-12

- Phone audio added as an ordinary audio station played through WPF `MediaElement`: `AUDIO OPEN` at
  23:04:47.4, `AUDIO LIVE` at 23:04:51.8 - 4.4 s to open - and the owner heard about 6 s of delay.
- `MediaElement` exposes no buffer control. The video path's engine already runs with a latency target
  (`FlyleafVideoBackend`, `MaxLatency = 0`). Routing live broadcasts through an engine that exposes its
  buffer is the likely way to meet 5.2; this is a recommendation, not a measurement.
- The owner also found the phone's audio quiet. That is a producer matter, tracked in this repository as
  S3049 (microphone level control); a consumer must not apply its own gain to compensate.

---

## 6. Verification handshake

- When a stream kind moves from `[PROPOSED]` to `[CONTRACT]`, the producer sends one line naming the section.
- The consumer checks against a live broadcast: the description parses from all three channels, playback
  starts, the delay behind the source is measured (a clap in front of the device is enough), and the delay
  after a forced reconnect is measured again against 5.2.

---

## 7. Ticket index for this file

- S3050 - this contract.
- S3038 - phone camera and microphone modes (2.3, 2.4).
- S3049 - microphone level control on phone and watch.
- S2508, S2793, S2817, S2707, S2814 - phone audio broadcast, its settings, its descriptor and `sourceId`.
- S2509, S2813, S2878, S2550 - watch audio broadcast, its stable address, its barcode, paired listening.
- S2068, S2662 - the original camera and microphone streaming design and the RTSP spike.
- S3051 - descriptor v2: endpoint list, codec fields, live marker and latency target (3.2, 5.3).
- S3052 - import file extension and MIME type agree (4.2).
- S3053 - clickable link that opens the importer (4.3).
- S3054 - the phone never publishes 127.0.0.1 as its broadcast address (2.1).
- S3055 - tests that hold the phone wire format of 2.1 and 4.
- S3056 - the watch descriptor mirror cannot drift from the phone schema (3.1, 4.1).
- S3057 - relay or P2P through a data-exchange server (1, 3.2).

For the consumer side, `CONSUMER_PROMPT_live_broadcast.md` in this directory is the prompt to hand over.
