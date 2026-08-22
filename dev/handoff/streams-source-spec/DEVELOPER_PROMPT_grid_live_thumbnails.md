# Developer Prompt (add-on) - Grid Mode: Live-TV Preview Thumbnails

> A focused, self-contained brief for one feature: the Streams "grid mode" that shows each live TV (video)
> channel as a tile with a **real captured current frame**, sweeping channels **one at a time**. Hand this
> to whoever builds the grid preview in FastMediaSorter for Windows. It is an add-on to the main Streams
> feature (see `DEVELOPER_PROMPT.md`); the authoritative deep reference is
> `dev/handoff/streams-source-spec/04_favicon_atlas.md` §9 (Android ticket S0675).

---

## 0. What this feature is

The Streams screen has a **list** mode and a **grid** mode (a user-toggle, persisted). In **grid mode**,
each channel is a tile. For a **live internet-TV (video) channel**, the tile shows a **snapshot of the
channel's actual current picture** - so the grid reads like a wall of live TV thumbnails. The app produces
those snapshots itself by briefly, invisibly, tuning into each visible video channel, grabbing one frame,
and moving on to the next - **one channel at a time**.

Audio (radio) and RTSP channels are **not** snapshotted - their tiles keep showing the channel favicon.

Think "channel mosaic / multiview preview", built by a background sweep, not by any server-provided
thumbnails.

---

## 1. Scope - what is captureable

**Only** channels that are **VIDEO** *and* whose URL is `http://` or `https://` are snapshot-captureable.
- **AUDIO** channels: never captured -> the tile shows the favicon (permanent).
- **RTSP** channels: never captured -> the tile shows the favicon (permanent).
- A captureable video channel shows the favicon only as a **"not captured yet" placeholder**, until its
  first frame lands.

(This restriction keeps the capture engine simple and off any flavor-gated RTSP decoder.)

---

## 2. Rendering precedence (per tile)

For each tile, in order:
1. **A cached frame for this channel URL exists** -> show it (any age - see §4 for why "any age").
2. Else -> **show the favicon tile** (from the shared atlas; see the main prompt / `04`).
3. Else -> a plain blank tile (no country-flag fallback in grid mode).

Tile shape: **16:9**, image scaled to fill (center-crop). Overlays on the tile: a small **play-status dot**
(bottom-left), a **title** on a gradient scrim (bottom), a **"Pinned" badge** (top-left, when pinned), and
an **overflow menu button** (top-right).

Once a real frame has been shown for a URL, the tile **must not revert to the favicon** while re-capturing;
show the previous (possibly stale) frame until a fresher one replaces it **in place**.

---

## 3. The capture engine (how one snapshot is taken)

To snapshot one video channel, off-screen and silently:
1. Create a small **off-screen video render surface**, e.g. **640 x 360 px** (16:9), attached to the app
   window but pushed far off the viewport (never visible - the Android app translates it by -10,000 px).
   The surface must keep receiving frames while "drawn" but never paint over the UI.
2. Build a **muted** player against the channel URL, with a **shallow buffer** (start fast, don't fill deep)
   and a **live configuration** (target ~10 s behind live, window 4-20 s, catch-up speed cap 1.02x - same
   values as the main player, Part D of the main prompt).
3. **Wait for the first rendered video frame**, bounded by a **12-second timeout** (a slow HLS manifest or
   software decoder can take several seconds; 12 s was tuned up from an earlier 6 s that timed out too often).
4. On success: grab the current frame as a **640 x 360 bitmap**; store it (see §4).
5. **Always tear down** in a `finally`: detach the video surface first, then release the player, then remove
   the off-screen surface - even on timeout/cancel/error. No leaked decoder session or surface, ever.
6. The capture result (**got a frame / did not**) doubles as the tile's **reachability status** (green if a
   frame decoded, otherwise it stays amber) - no separate network probe is needed for video grid tiles.

**Robustness lesson (do not skip)**: on some hardware decoders an off-screen `ImageReader`-style surface can
**hard-crash the process natively** (uncatchable by normal error handling). The Android app switched to a
`TextureView`-style surface for exactly this reason (ticket S0933, after native kills on Samsung Exynos /
Android API 36). On your platform, pick a capture surface/decoder path proven not to crash the host, and
keep a global **kill-switch flag** so the whole capture feature can be disabled without touching the rest of
the grid.

---

## 4. Two-layer frame cache

### 4.1 In-memory cache (fast, per-session)
- Keyed by **channel URL**. LRU, capacity **64 entries** (evict least-recently-used over 64).
- `get(url)` returns the **last captured frame once any exists**, regardless of age (that is why the tile
  never reverts to the favicon after its first frame).
- `isFresh(url)` is true only for a **live** entry younger than the **60 s TTL**. TTL governs *freshness*
  (whether to re-capture), **not** eviction - a stale frame stays visible until replaced or LRU-evicted.
- A frame restored from disk (§4.2) is seeded as **not fresh**, so the engine always attempts at least one
  real capture for it.

### 4.2 On-disk cache (survives restarts, cold-start warmth)
- One JPEG per channel, **quality ~75**, in a dedicated folder. **Filename = a hash of the URL** (the app
  uses `SHA-256(url)` hex + `.jpg`) - no separate index file; the hash is the lookup key.
- Cap the folder at **150 MB total** (a size budget, not a file count), evicting oldest-by-modified-time
  until back under budget.
- All disk I/O off the UI thread; any failure just falls back to the favicon on the next bind (never fatal).
- On entering grid mode, **pre-warm** the in-memory cache from disk so the grid shows last session's frames
  immediately, then refresh them live.

---

## 5. The sweep - concurrency and cadence ("one after another")

- **One capture at a time.** A single-permit semaphore (max concurrent captures = **1**) means channels are
  snapshotted sequentially - one decoder session at a time, never a burst. This is the "одно за другим"
  behaviour and keeps a modest device from thrashing.
- A **queue + a pending-set dedup** so the same URL is never queued twice while its capture is outstanding.
- **What triggers captures:**
  - **Entering grid mode**: pre-warm from disk, then queue captures for the visible captureable tiles.
  - **Periodic refresh**: a **60-second** timer re-queues captures for the **currently visible** captureable
    tiles only, `force = false` (skip tiles whose frame is still fresh).
  - **On scroll**: as new tiles become visible, queue captures for the newly-visible range, `force = false`.
  - **Pull-to-refresh / explicit Refresh**: `force = true` - re-capture every visible captureable tile even
    if its frame is still fresh.
- **Only visible tiles** are ever captured (never the whole catalog). A refreshed frame repaints **just its
  own tile**, by URL.
- **Leaving grid mode / backgrounding**: cancel the queue, the pending set, and any in-flight capture; stop
  the periodic timer. Captures never run while the screen is backgrounded or in list mode.
- Grid column count: derive from width (the Android app uses a ~160 dp minimum tile width, min 2 columns).

---

## 6. Constants (Android values - mirror or tune)

| Setting | Value |
|---|---|
| Captureable set | http(s) `VIDEO` only |
| Capture surface size | 640 x 360 px (16:9) |
| First-frame timeout | 12 s |
| Max concurrent captures | 1 (sequential) |
| Player buffer (min/max/for-playback/after-rebuffer) | 2000 / 8000 / 1000 / 1000 ms, muted |
| Live config (target / min / max / speed) | 10 s / 4 s / 20 s / 1.02x |
| In-memory frame TTL (freshness) | 60 s |
| In-memory cache capacity | 64 entries (LRU) |
| Periodic re-capture interval (visible tiles) | 60 s |
| On-disk JPEG quality | ~75 |
| On-disk cache cap | 150 MB total (oldest-by-mtime evicted until under budget) |
| On-disk filename | `SHA-256(url)` hex + `.jpg` |
| Grid min tile width | ~160 dp (min 2 columns) |

---

## 7. Acceptance criteria

1. In grid mode, live **video/HLS** channel tiles show a **real current frame** of the channel, not a logo.
2. **Audio** and **RTSP** tiles show the favicon (never a captured frame).
3. Captures run **one at a time**; the UI stays responsive; only **visible** tiles are captured.
4. Frames **persist across app restarts** (disk cache) and appear immediately on re-entering grid mode, then
   refresh live.
5. A tile that has shown a real frame **does not flash back to the favicon** during a re-capture.
6. Periodic refresh updates visible tiles about every 60 s; pull-to-refresh forces an immediate re-capture.
7. A failed/timed-out capture leaves the favicon in place and never crashes or leaks a decoder/surface; the
   whole capture feature can be turned off by one flag.
8. Leaving grid mode or backgrounding stops all capture work.

---

## 8. Suggested first milestone

Build the **capture-one-frame** primitive in isolation: given a single live HLS/http video URL, open a muted
off-screen player, grab one 640x360 frame within 12 s, write it to a `SHA-256(url).jpg` file, and tear
everything down cleanly. Prove it does not crash or leak on your platform's decoders **before** wiring the
cache, the sweep cadence, and the grid UI on top.
