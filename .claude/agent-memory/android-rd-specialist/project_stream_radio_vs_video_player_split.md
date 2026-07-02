---
name: stream-radio-vs-video-player-split
description: Radio (AUDIO) and live/VOD video streams use SEPARATE players/LoadControls; radio runs on the untuned Media3 default (S0689 archived obsolete)
metadata:
  type: project
---

Stream playback splits by `StreamSourceEntity.mediaKind` at `StreamsActivity.onPlay`:
`"AUDIO"` (radio/ICY) -> `StreamInlineAudioManager` (in-app OFF-path `ExoPlayer` + background
`AudioPlaybackService`), both built with NO `setLoadControl` (untuned Media3 default ~50s/50s/2.5s/5s).
`"VIDEO" | "RTSP"` -> `PlayerActivity` -> `playStreamVideo` -> `BandwidthAdaptiveLoadControl`
(live-edge clamp vs VOD ceiling). Radio NEVER reaches `BandwidthAdaptiveLoadControl`.

**Why:** S0689 ("split the shared stream LoadControl: radio vs live") was archived obsolete 2026-06-30
because its premise was false - there is no shared stream LoadControl; radio and video were never on the
same one. Verified against live code, not assumed.

**How to apply:** Do NOT assume `playStreamVideo` / `BandwidthAdaptiveLoadControl` handles radio. If radio
start latency or micro-pauses ever need fixing, the lever is a lean audio profile on
`StreamInlineAudioManager`'s OFF-path player (and carefully on `AudioPlaybackService`, shared with general
audio-file playback) - NOT a change to `BandwidthAdaptiveLoadControl`. Open tech-debt: radio audio players
still run on the untuned Media3 default LoadControl (owner accepted as-is 2026-06-30). See also
[[streams-device-test-gate]].
