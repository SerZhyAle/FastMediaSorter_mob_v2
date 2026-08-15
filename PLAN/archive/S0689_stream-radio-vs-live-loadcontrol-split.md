# Strategic spec: S0689 - Split stream LoadControl: radio (ICY) vs live video

**Ticket:** S0689
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Parked from S0685 (§13.3)

> **Scope:** STRATEGIC skeleton. Captured idea only - no research/approval/spec-tech chaining yet.

---

## Archive note (2026-06-30) - obsolete, premise invalid

Verified against live code before archiving. The §0 capture assumes one shared stream `LoadControl`
serves radio (ICY), live HLS/DASH and VOD alike via `playStreamVideo`. That premise is false - radio
and video never shared a `LoadControl`:

- `mediaKind == "AUDIO"` (radio/ICY) is routed by `StreamsActivity.onPlay` to `StreamInlineAudioManager`,
  never to `playStreamVideo`. Both of its players - the in-app OFF-path `ExoPlayer`
  (`StreamInlineAudioManager.play`) and the background `AudioPlaybackService` - are built with no
  `setLoadControl`, so radio runs on the untuned Media3 default (~50s min/max, 2.5s/5s start/rebuffer).
- `mediaKind == "VIDEO" | "RTSP"` is routed to `PlayerActivity` -> `playStreamVideo` ->
  `BandwidthAdaptiveLoadControl`, which already differentiates live (live-edge clamp, S0634/S0685) from
  VOD (deeper ceiling). Radio never reaches it.

There is nothing to "split": radio and live-video already sit on separate players with separate load
controls. The split this ticket proposed does not correspond to the codebase.

The kernel of the original idea (radio wants a fast start and few micro-pauses) maps to a different,
real lever this ticket never targeted: the radio audio players use the Media3 default `LoadControl`,
not a radio-tuned profile. If radio start latency or micro-pauses are ever found wanting on a real
device, the change is a lean audio profile on `StreamInlineAudioManager`'s OFF-path player (and
carefully on `AudioPlaybackService`, shared with general audio-file playback) - not a change to
`BandwidthAdaptiveLoadControl`.

**Owner decision 2026-06-30:** archive as obsolete - radio playback is acceptable as-is; no retune pursued now.

---

## 0. Captured material (inbox)

**Captured:** 2026-06-25 (parked while implementing S0685)

The stream player uses one shared `DefaultLoadControl` for every http(s) stream - progressive radio (ICY), live HLS/DASH, and VOD alike (see `StreamPlaybackHelper.playStreamVideo`). S0685 kept this single profile (with a robustness retune) deliberately. A dedicated profile per content kind is deferred: progressive radio benefits from a low `bufferForPlayback` for a fast start, whereas live video wants a deeper post-rebuffer cushion to ride jitter. A single compromise profile under-serves one of them.

Key reason this is a separate ticket: the right thresholds are a product/UX call that needs real-device testing of radio micro-pauses vs live-video stutter - not verifiable on a stable emulator. Also, live-ness for http(s) is only known after `prepare()`, so a clean split needs either a URL/kind heuristic up front or a post-prepare LoadControl swap.

**Open angles (for later research):**

- Detecting radio (ICY audio) vs live video before/at prepare to choose the profile.
- Whether a post-prepare profile swap is feasible in Media3 1.2.1 without re-buffering.
- On-device A/B of radio start latency and micro-pauses vs live stutter.

**Attachments: none.**

**Owner decision 2026-06-25 (during the S0689-S0701 batch): deferred - stays Draft.** Recon confirmed the
two blockers from the capture: Media3 1.2.1 binds `LoadControl` to the `ExoPlayer` at build time (no
post-`prepare()` swap without a full rebuild that restarts buffering), and the only pre-`prepare()` signal
is an unreliable URL heuristic. The threshold tradeoff (radio micro-pauses vs live stutter) needs
real-device A/B that the emulator cannot provide. Current `BandwidthAdaptiveLoadControl` already gives a
fast ~2.5 s start and a live-edge ceiling, so the single profile is acceptable until real data exists.

---

## 1. Problem

Void - the §0 premise does not hold against the codebase. See "Archive note (2026-06-30)".

---

## 10. Related specs

- **S0685** (adaptive-stream-buffering) - parent; kept a single shared stream LoadControl, deferred this split.
- **S0634** (stream-live-hls-robustness, archived) - live-edge recovery baseline that must not regress.
