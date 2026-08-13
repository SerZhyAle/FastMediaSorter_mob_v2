# Robust Playback of the FastMediaSorter Stream Bank — Recommendations

**Audience:** the FastMediaSorter (Android) developer.
**Source:** StreamsPlayer (Windows desktop, .NET/WPF, libVLC). These findings come from
instrumenting real playback of the **same published stream bank** FastMediaSorter ships, so
the empirical results transfer directly even though the players differ.

The single most important message: **most "freezes" on these streams are not bandwidth.**
On our test streams the freezes were caused by (1) hardware-decoder surface starvation and
(2) broken stream timestamps (PCR/PTS). Bigger network buffers did *not* fix them, and
reconnecting to grow the buffer made things much worse. Measure before you tune.

---

## 0. What these streams actually are

- Mostly **HLS (`.m3u8`) live**, some **RTSP**, some audio (Icecast/Shoutcast).
- A large fraction are **third-party live sources of poor quality**: broken/discontinuous
  timestamps, variable bitrate, intermittent availability.
- **Design for bad inputs, not ideal ones.** Availability failures and timestamp defects are
  normal operating conditions, not edge cases.

---

## 1. Diagnose before you tune (highest-value habit)

We only found the real cause after adding structured diagnostics. Log, per playback:

- **Time-to-first-frame** (open → first rendered frame).
- **Stall start / resume** (buffering begins after playback was live; and when it recovers).
- **Periodic counters** (~every 2 s): input bytes, demux bytes, **decoded / rendered / dropped
  frames**.
- **The player's own warning/error log** — this is where the true cause shows up.

**Decision rule:**

| Observation | Conclusion | Action |
|---|---|---|
| Rendered frames frozen, bytes still arriving | Clock or decode problem | §2, §3 |
| Dropped/lost frames climbing | Decode or timestamp problem | §2, §3 |
| Input bytes frozen | Real network starvation | buffer/retry helps |

**HLS caveat:** the "input bytes / input bitrate" counter often reflects only the `.m3u8`
playlist read, **not** the segment downloads. For HLS, trust the **frame counters**
(rendered vs dropped), not input bitrate, to judge whether the network is the problem.

---

## 2. Video decode: hardware is not free — plan for it to fail

**What we saw:** repeated `d3d11va: not enough decoding slices in the texture`,
`hardware acceleration picture allocation failed`, `picture is too late to be displayed` →
dropped frames and stutter. The GPU decoder ran out of surfaces.

**What fixed it (desktop):** force **software decode**.

- libVLC: `--avcodec-hw=none` (global) **and** `:avcodec-hw=none` (per media). This alone
  removed the entire hardware fault family in our logs.

**Android mapping — important nuance:** on mobile, software decode costs **CPU, battery and
heat**, unlike a PC where it is free. So do **not** blindly force software everywhere. Prefer:

- **AndroidX Media3 / ExoPlayer:**
  - Enable **decoder fallback** so a hardware-decoder init failure automatically retries with
    another (often software) decoder: `DefaultRenderersFactory.setEnableDecoderFallback(true)`.
  - Ship the **software/FFmpeg extension renderer** as a backstop and set
    `setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)` for known-bad streams, or
    `EXTENSION_RENDERER_MODE_ON` as a fallback.
  - Detect the problem at runtime via `AnalyticsListener.onDroppedVideoFrames(...)` and
    `onVideoDecoderInitialized(...)` (the decoder name tells you HW vs SW), then switch that
    stream to software.
- **libVLC-Android:** identical option — `--avcodec-hw=none` — if you want the desktop behaviour.

**Recommendation for Android:** hardware-first, **decoder fallback on**, software extension
available, and force software only for streams you've measured as HW-problematic.

---

## 3. Tolerate broken stream clocks (PCR/PTS)

**What we saw:** `ES_OUT_SET_PCR is called too late (jitter of 8958 ms ignored)`,
`Could not convert timestamp 0`, `no reference clock`,
`playback way too early (-1011084): playing silence`. The streams emit inconsistent
timestamps; the player's clock sync thrashes, plays silence, and drops late pictures.

**What helped (desktop libVLC):**

- `--clock-jitter=0` — ignore clock jitter and play as soon as possible instead of stalling to
  silence on every timestamp jump. This dropped our worst jitter from ~9 s to <250 ms.
- `--no-drop-late-frames` — **the biggest win for the per-second micro-stutter.** On broken
  streams the video output was discarding ~1/3 of frames as "picture is too late to be
  displayed" (≈8 dropped frames/sec, seen as a small freeze every second). Displaying the
  slightly-late frames instead removed the drops entirely (35 → 0 in a controlled test on the
  same stream) at the cost of a few tens of ms of latency. Decode was never the bottleneck —
  it was the vout dropping late frames.

**What we tried and rejected:**

- `--no-ts-trust-pcr` (derive timing instead of trusting the stream PCR) — it *looked* good on a
  well-behaved stream but **deadlocked the display to 0 fps on the worst-timestamped streams**
  (decode raced ahead with no clock reference; the picture backlog grew until the vout froze).
  Do **not** ship this globally. If you need it, gate it behind a per-stream opt-in and watch
  for a growing decoded-vs-displayed gap.

**Lesson:** prefer options that make the player *tolerate* bad timing (don't drop, ignore
jitter) over options that *remove the clock reference* — the latter can turn a stutter into a
freeze.

**Android mapping:** ExoPlayer is generally more tolerant here and re-derives HLS timing across
discontinuities on its own. There is no exact 1:1 for `no-ts-trust-pcr`, but:

- For HLS live, let ExoPlayer manage timing; configure a **target live offset**
  (`MediaItem.LiveConfiguration` / `DefaultLivePlaybackSpeedControl`) rather than forcing a clock.
- Be lenient about restarting on discontinuities; do not recreate the player (see §4).

---

## 4. Buffering: pick one sane value; **never reconnect to grow it**

This is the counter-intuitive one, and we have hard numbers.

- **Bigger buffer did not prevent the stalls** — they were clock/decode, not starvation.
- We tried *escalating* the buffer by **reconnecting** with a larger cache on each stall. That
  was strictly worse: each reconnect meant re-buffering the whole new buffer from scratch —
  **18 s, then 26 s of black screen** — and it still stalled.
- Letting the player **rebuffer in place** recovered in **~5 s**.

**Recommendation:** choose one reasonable live buffer and let the player rebuffer in place.
**Do not tear down and reconnect on a stall.**

- **libVLC (desktop, what we shipped):** `network-caching=15000`, `live-caching=15000`.
- **ExoPlayer/Media3:** tune `DefaultLoadControl` —
  `setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs,
  bufferForPlaybackAfterRebufferMs)`. For flaky live, give a healthy
  `bufferForPlaybackAfterRebufferMs` so playback doesn't restart too eagerly, and **keep the
  same player instance** across stalls.

---

## 4a. Trade quality for smoothness — but only when the cause is bandwidth/CPU

If a stream is **adaptive** (its master `.m3u8` lists several renditions), forcing a lower one
reduces data and decode load and can eliminate stutter that comes from **bandwidth or a weak
CPU**:

- **libVLC:** `--adaptive-logic=lowest`, or cap with `--adaptive-maxheight=480` /
  `--adaptive-maxwidth`.
- **ExoPlayer/Media3:** `trackSelector.setParameters(builder.setMaxVideoSize(w, h)` /
  `.setMaxVideoBitrate(...))`. On mobile this is one of the highest-value levers because the
  bottleneck is usually the cellular link.

**Two caveats we learned the hard way:**

1. It does nothing for **timestamp-driven** stutter (see §3). Our worst stream stuttered with
   decode idle — lowering quality there would change nothing. Diagnose first (§1): is decode/net
   actually the bottleneck, or is the vout dropping late frames?
2. Many bank streams are **single-quality media playlists** (no `#EXT-X-STREAM-INF`, just
   `#EXTINF` segments) — there is simply no lower rung to pick. Detect this and don't promise a
   quality drop you can't deliver.

A reasonable automatic policy: on an *adaptive* stream that keeps stalling, step down one
rendition; leave single-quality streams to the §3/§4 tolerance settings.

## 5. RTSP: force TCP

UDP RTSP loses packets and stutters on lossy networks.

- **libVLC:** `--rtsp-tcp` (and `:rtsp-tcp` per media).
- **ExoPlayer/Media3:** `RtspMediaSource.Factory().setForceUseRtpTcp(true)`.

---

## 6. Thumbnails / previews: capture once, adopt what the user already saw, then persist

This is a full description of how StreamsPlayer builds and keeps grid thumbnails. The design
goal: **a channel that has ever shown a picture should never fall back to a bare favicon/letter
tile again**, and we should spend as little network/CPU as possible getting there.

### 6.1 Two capture paths (the second is the important one)

**A. Headless grid capture** — for tiles the user is *looking at* but hasn't opened.

- A dedicated off-screen libVLC instance (`VideoFrameCaptureService`) opens the stream **muted,
  `:no-audio`, software decode (`--avcodec-hw=none`)**, with a raw `RV32` video callback at
  **480×270** and short caching (`:network-caching=2000` / `:live-caching=2000`).
- It takes the **first displayed frame** (via the video display callback), copies those raw
  pixels into a frozen `BitmapSource`, and stops. **Hard 12 s timeout**; on failure the tile is
  marked unreachable. It never keeps a per-tile player alive.
- Software decode here is deliberate: the headless grabber must **never compete with the real
  player for GPU decode surfaces** (that starvation is the §2 freeze cause).

**B. Player-ingest** — the cheapest, highest-quality thumbnail, and the one to copy.

- When the user actually **opens a channel in the player** and a real picture appears
  (`reachedLive`), ~**700 ms** later the live player takes one VLC snapshot
  (`TakeSnapshot(width 480)`) and hands that frame back to the grid via
  `GridPreviewCoordinator.IngestFrame(url, frame)`, which **adopts it as the channel's
  thumbnail** — applies it to the grid tile *and* writes it to disk.
- Rationale (a direct product decision): **if you've seen the picture once, that becomes the
  thumbnail.** The first frame at open is enough — we don't chase the "latest" frame. This
  populates thumbnails for channels the headless grabber can't get (or that only ever had a
  favicon) as a free side effect of normal viewing.
- This path runs **even when auto-capture is off** (§6.4) — opening a channel is a deliberate
  act, so collecting its frame is always allowed.

### 6.2 When each path fires (triggers & rate-limiting)

- **First-time blank tile, on screen:** auto-captured via path A — but only for **visible,
  captureable** tiles, and only when the auto-capture setting is on. Stored tiles are never
  re-captured automatically.
- **Hover:** a fresh path-A frame after a **≈1 s dwell**, throttled to **once per 15 s per
  tile**. A casual mouse pass-over does nothing; deliberately resting on a tile refreshes it.
- **"Refresh previews" button:** force-recaptures **only the tiles currently on screen**, not
  the whole catalog.
- **Opening a channel:** path B, always (see above).

### 6.3 Persistence & right-sizing

- **Memory:** a plain LRU cache (`PreviewFrameCache`, 64 decoded frames) for instant redraw.
- **Disk:** `PreviewFrameStore` writes **JPEG, quality 70**, keyed by a hash of the URL, under
  `%LOCALAPPDATA%\StreamsPlayer`. Eviction is **by total-disk-size budget (150 MB), not a fixed
  file count** — an earlier 64-*file* cap silently hid previews for a 2 300-channel catalog.
- **Sizing:** 480×270 at JPEG q70 ≈ **22 KB/tile** (was 640×360 q70 ≈ 57 KB). 480 px gives DPI
  headroom over the largest grid tile (Large = 400 px wide) without paying for frames larger
  than any tile can show.
- On next launch, stored frames are restored and shown **instead of the channel symbol/favicon**;
  nothing is re-captured just to display.

### 6.4 Setting semantics — "off" does **not** mean "no thumbnails"

The Settings toggle *"Update stream thumbnails automatically"* only governs **background
auto-collection while browsing the grid**. When it is **off**:

- Stored thumbnails **still display**.
- Opening a channel **still ingests** its first frame (path B).
- Explicit refresh / hover **still work** on demand.

Only the unattended, first-time-blank auto-capture (path A) is suppressed. The coordinator keeps
running either way so restored thumbnails always show.

### 6.5 Concurrency & correctness caveats we hit

- **Suspend headless grid capture while a player window is open or audio is playing** — don't let
  background grabs fight the real player for network/CPU/decode.
- **WPF cross-thread imaging:** a frozen `BitmapDecoder`/`FormatConvertedBitmap` is **not** safe
  to JPEG-encode on a worker thread. Copy the raw pixels into a `BitmapSource.Create(...)` +
  `Freeze()` first; only that is worker-thread-safe. Marshal the live-player snapshot back to the
  UI thread before handing it off.
- **`--no-snapshot-preview`** on the live player — otherwise VLC paints the snapshot as a
  thumbnail overlay in the top-left corner of the video for a moment.

### 6.6 Android mapping

- **Headless grab (path A):** `MediaMetadataRetriever.getFrameAtTime(...)` or an ExoPlayer
  image/thumbnail output with a short timeout; cache to disk by URL hash; evict by size budget;
  only for tiles on screen.
- **Player-ingest (path B) — do this too:** when the user opens a stream, grab the first rendered
  frame (`AnalyticsListener.onRenderedFirstFrame(...)` + a `PlayerView`/`SurfaceView` bitmap
  capture, or a parallel lightweight `MediaMetadataRetriever` pass) and store it as that channel's
  thumbnail. Deliberate opens are the cheapest, most-reliable thumbnail source and cover streams
  the headless grabber can't.

---

## 7. Treat unavailability as normal

At any moment a meaningful fraction of the bank is down or refuses. Concretely:

- Record a **per-channel outcome** (ok / fail) and surface it in the UI.
- **Retry only on user action.** Do not background-hammer dead streams or auto-download.
- Fail fast with a clear message; move on.

---

## 8. The exact libVLC option set we shipped (verbatim)

For anyone using libVLC on Android, these map 1:1:

```
# LibVLC init (live player):
--no-video-title-show --no-osd --no-snapshot-preview --rtsp-tcp --clock-jitter=0 --avcodec-hw=none

# Per-media options (live player):
:network-caching=15000
:live-caching=15000
:rtsp-tcp
:avcodec-hw=none

# LibVLC init (headless thumbnail grabber — §6):
--no-video-title-show --no-osd --quiet --avcodec-hw=none
# Per-media: :no-audio :network-caching=2000 :live-caching=2000
```

(`--no-snapshot-preview` stops VLC drawing the grabbed frame as a corner overlay — see §6.5.
`--no-ts-trust-pcr` is the next lever to try for streams that still micro-stutter from bad PCR.)

---

## 9. Diagnostics worth adding on Android (so you can measure the same way)

- **Time-to-first-frame**, **stall count + durations**.
- **Rendered vs dropped frames:** `AnalyticsListener.onDroppedVideoFrames(...)`,
  `onRenderedFirstFrame(...)`.
- **Decoder chosen (HW vs SW):** `onVideoDecoderInitialized(...)` (inspect the decoder name).
- **Errors with codes:** `onPlayerError(PlaybackException)` — e.g.
  `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`, `ERROR_CODE_DECODER_INIT_FAILED`,
  `ERROR_CODE_PARSING_CONTAINER_MALFORMED`.
- Log the **full stream URL + host** so you can aggregate by source and find the systematically
  bad ones.

---

## 10. Measured evidence (same bad stream, before vs after)

Test stream: `http://88.212.15.19/live/test_ctsport_25p/playlist.m3u8` (HLS, 25 fps),
~35 s windows, StreamsPlayer / libVLC.

| Signal | Before (defaults) | After (§2 + §3 + §4) |
|---|---|---|
| Stalls | 8 | 1 |
| Reconnect black-screens (20–30 s each) | 2 | 0 |
| Hardware-decode errors | many | 0 (software decode) |
| Worst clock jitter | 8 958 ms | < 250 ms (one 1 000 ms) |
| Multi-second display freezes | 8–16 s | none |

The residual is the **stream's own defect** (invalid timestamps) — now *tolerated* (a few
percent dropped frames) rather than *fatal* (multi-second freezes).

---

### Priority order if you only do a few things

1. **Decoder fallback on** (Android) / software decode for known-bad streams (§2).
2. **Do not reconnect to grow the buffer**; rebuffer in place; one sane buffer value (§4).
3. **Tolerate bad clocks** — `clock-jitter=0`, and let ExoPlayer manage HLS live timing (§3).
4. **RTSP over TCP** (§5).
5. **Instrument** so the above are measured, not guessed (§1, §9).
