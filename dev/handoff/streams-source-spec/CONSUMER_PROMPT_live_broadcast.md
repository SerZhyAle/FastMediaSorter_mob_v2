# Consumer Prompt - Play FastMediaSorter live broadcasts (phone and watch)

> Hand this whole file to the developer or AI coding agent of any application that should play live
> broadcasts published by FastMediaSorter - StreamsPlayer (Windows) first, any other player later. It is
> self-contained. The authoritative text is `11_live_broadcast_contract.md` in the FastMediaSorter
> repository (`dev/handoff/streams-source-spec/`); when this prompt and that file disagree, the contract
> wins, and a copy of it belongs in your repository.

---

## 0. Role and goal

You maintain a player application. FastMediaSorter, an Android app with a Wear OS companion, can broadcast
live from a phone or a watch: the device captures its microphone (and later its camera) and serves the
stream itself on the local network. Your application becomes a consumer of those broadcasts.

Your freedom: choose your own architecture, engines, UI and storage. Do not copy Android code. The wire
formats, the description format and the latency rule below are fixed; everything else is yours.

Work the way your own repository requires: its ticket catalog, its gates, its release rules. Open one
ticket per numbered task in section 3 unless your process groups them differently, and record in each
what was measured, not what was expected.

---

## 1. What you will receive

### 1.1 Stream kinds

- **Phone audio** - shipped. `http://<lan-ipv4>:<port>/live-audio.aac` (also `/live-audio`), default port
  8768. `HTTP/1.1 200 OK`, `Content-Type: audio/aac`, chunked body, `icy-name` / `icy-genre` / `icy-pub` /
  `icy-br`, no `icy-metaint`. ADTS AAC-LC, mono, 44 100 Hz, 128 kbit/s by default. No listener cap. The
  chunked body ends cleanly when the broadcast stops.
- **Watch audio** - shipped. `http://<lan-ipv4>:<port>/listen`, the port can change between broadcasts.
  `HTTP/1.0 200 OK`, `Content-Type: audio/aac`, `Cache-Control: no-cache`, `Connection: close`, no length,
  no chunking. ADTS AAC-LC, mono, 44 100 Hz, 64 kbit/s. At most 4 listeners; a fifth connection gets
  `503`. The socket simply closes when the broadcast stops.
- **Phone audio + video, camera selectable** - in development. RTSP, H.264 + AAC, RTP over TCP, default
  port 8554. Do not build against it until the contract marks it `[CONTRACT]`.
- **Phone video without audio** - in development, as above without audio.

No ICY metadata is offered by either device; "no metadata" is the correct outcome, not an error.

### 1.2 The description (descriptor), schemaVersion 1

UTF-8 JSON: `schemaVersion` (1), `url` (required), `mode` (required: `AUDIO_ONLY` today; `VIDEO_AUDIO`,
`VIDEO_ONLY` reserved), `title` (optional), `sourceId` (optional, stable id of the broadcasting device).

- Ignore members you do not know.
- Refuse a higher `schemaVersion` with "update the application", not "invalid file".
- Refuse a `mode` you do not support with a clear message; never guess the transport from it.
- When `sourceId` is present, a newly imported broadcast replaces the stored one with the same `sourceId`.

### 1.3 Hand-off channels - the same description in three forms

- **Barcode:** `FMSBCAST1:` + Base64 (standard alphabet) of the GZIP of the JSON.
- **Import file:** the plain JSON. Named `*.fmsbcast` and declared `application/vnd.fms.bcast+json` (shipped S3052). Read at most 64 KiB.
- **Link:** "Send link" shares an Android `intent://` URI. The embedded target is
  `fmsbcast://import?payload=<url-encoded FMSBCAST1 payload>`; consumers may read that payload directly.
  Its browser fallback opens the public import page when FastMediaSorter is absent, and that page offers
  installation plus an explicit app-open tap - never an automatic jump to `fmsbcast://`, which has no
  fallback of its own. A bare stream URL of section 1.1 also works.
  The `package=` hint names the sending build's application id; a consumer producing a link for the store
  app writes `package=com.sza.fastmediasorter`.

### 1.4 Transport

Today: a direct address on the local network. Later: a data-exchange server that relays the stream or
opens a P2P path to a consumer in any network. The next description revision will carry a list of
endpoints (transport, tracks, codec, live marker, latency target) instead of one `url`. Design your storage
so a broadcast can hold more than one address.

---

## 2. The latency rule - the part that matters most

The producer adds almost nothing. Measured 2026-09-12 from a PC on the same Wi-Fi: first byte after 6 ms
(phone) and 11 ms (watch); bytes flow at the real-time rate from the first moment, with no backlog burst;
the phone's capture buffer is about 0.09 s. Anything above half a second of end-to-end delay on a LAN is
added by the consumer.

StreamsPlayer, same evening: the phone stream added as an ordinary audio station played through WPF
`MediaElement` took 4.4 s to open (`AUDIO OPEN` 23:04:47.4 -> `AUDIO LIVE` 23:04:51.8), and the owner heard
about 6 s of delay.

The owner's position: he understands why a player buffers - its job is to keep a broadcast playing without
interruption - and six seconds is still too much. For a live FastMediaSorter broadcast, meet that duty by
reconnecting, not by a deep buffer:

1. Start at the live edge with a small buffer: playback within 1 s of connecting, no more than 2 s behind
   the source in steady state on a LAN.
2. Do not let the delay grow: after a stall or a reconnect, rejoin at the live edge and drop what was missed.
3. Keep resilience in bounded reconnects with backoff, not in seconds of buffer.
4. Raise the buffer only for a transport that is marked as needing it (a future relay or P2P endpoint), and
   still correct drift back towards the target.
5. Open one connection per listener. A metadata probe or health check against the same URL takes one of
   the watch's four listener slots.

Until the description carries a live marker, treat as live every stream that arrived through a section 1.3
channel and every URL of the section 1.1 shapes. Let the user mark a manually added URL as live.

Do not amplify the audio to compensate for a quiet source; the producer tracks microphone level on its side.

---

## 3. Tasks

1. **Latency.** Route live broadcasts through a playback path that exposes its buffer and meets section 2.
   Measure before and after: time from "open" to first sound, and delay behind the source (clap in front
   of the device, count against a stopwatch or a recording). Record both numbers.
2. **Import the description** from all three channels: scan or paste the barcode text, open or drop the
   file, paste the link. Apply the version and `mode` rules of 1.2 and the `sourceId` replacement.
3. **Route by `mode`,** not by URL extension: `AUDIO_ONLY` to your audio path, the video modes to your video
   path once they are `[CONTRACT]`.
4. **Show live broadcasts as live:** no seek bar, no "resume from position", a visible live state, and a
   clear message when the device stops broadcasting (clean end on the phone, closed socket on the watch)
   instead of an endless "connecting".
5. **Respect the watch's listener cap:** no second connection per listener to the same URL; show `503` as
   "the watch already has the maximum number of listeners".
6. **Keep the contract in your repository** and a check that fails when your parser disagrees with the
   examples in section 4.

---

## 4. Examples for your tests

Plain JSON (import file):

```json
{"schemaVersion":1,"url":"http://192.168.1.97:8768/live-audio.aac","title":"Galaxy S25 FE","mode":"AUDIO_ONLY","sourceId":"3f6c1f0e-8d2b-4f6e-9a57-2b1f0c9d4e11"}
```

Must be refused with "update the application":

```json
{"schemaVersion":2,"url":"http://192.168.1.97:8768/live-audio.aac","mode":"AUDIO_ONLY"}
```

Must be refused as unsupported (until video is `[CONTRACT]`), never played as audio:

```json
{"schemaVersion":1,"url":"rtsp://192.168.1.97:8554/live","mode":"VIDEO_AUDIO"}
```

Must be accepted, the unknown member ignored:

```json
{"schemaVersion":1,"url":"http://192.168.1.166:33559/listen","mode":"AUDIO_ONLY","futureField":true}
```

Descriptor v2 (shipped S3051) with multiple endpoints, codecs, and live markers:

```json
{"schemaVersion":1,"url":"http://192.168.1.97:8768/live-audio.aac","title":"Galaxy S25 FE","mode":"AUDIO_ONLY","sourceId":"3f6c1f0e-8d2b-4f6e-9a57-2b1f0c9d4e11","isLive":true,"targetLatencyMs":1000,"endpoints":[{"url":"http://192.168.1.97:8768/live-audio.aac","transport":"HTTP","mode":"AUDIO_ONLY","audioCodec":"AAC","sampleRate":44100,"bitrate":128000,"isLive":true,"targetLatencyMs":1000},{"url":"rtsp://192.168.1.97:8554/live","transport":"RTSP","mode":"VIDEO_AUDIO","videoCodec":"H264","audioCodec":"AAC","isLive":true,"targetLatencyMs":1000}]}
```


The barcode form of any of them is `FMSBCAST1:` + Base64(GZIP(the JSON bytes)); build the fixture in your
test by compressing the JSON rather than pasting a string, so the test never carries a stale blob.

The `sourceId`, addresses and title above are illustrative values, not live devices.

---

## 5. Acceptance - what to report back

- Delay before and after task 1, for the phone and for the watch, on the same network.
- Delay after a forced reconnect (turn Wi-Fi off and on for a few seconds on the PC).
- Import from all three channels works; the three refusal examples above are refused with their messages.
- A stopped broadcast ends in a clear message within your reconnect budget.

Send those lines to the FastMediaSorter owner. When the producer moves a stream kind or the link form to
`[CONTRACT]`, you will get one line naming the section; re-run the matching checks.
