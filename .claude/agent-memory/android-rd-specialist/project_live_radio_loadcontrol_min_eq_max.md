---
name: live-radio-loadcontrol-min-eq-max
description: Radio stutter diagnosis toolkit (S1148) - two buffer profiles via streamsSmartBuffering toggle; engine telemetry proved clean playback, suspect layer is audio output/network path
metadata:
  type: project
---

Radio playback architecture after S1148 (2026-07-22), `RadioStreamBufferConfig`:
- Two profiles selected by `streamsSmartBuffering` streams setting (default OFF). OFF = stock factory `DefaultLoadControl` + stock error policy. ON = 5s/10s startup cushion + resilient `LoadErrorHandlingPolicy` (connectivity errors retry 2/4/8s backoff nearly forever while buffered audio keeps playing; data errors keep stock give-up threshold).
- Flag mirrored to SharedPreferences `stream_playback/smart_buffering` (writers: `AppStartupInitializer`, `SettingsRepositoryImpl.updateSettings`) because players are built synchronously; the error policy reads the flag at error time.
- Permanent `Audio diag:` AnalyticsListener in `AudioPlaybackService`: decoder init, format change, discontinuity, playWhenReady reason, suppression, volume, media load started/error, underrun; plus `AudioDeviceCallback` output added/removed and `route=bt|wired|speaker` in the S1148 telemetry probe.

**Why:** Owner's ~1/s radio skips (SM-S731B) survived every buffer change; 5s telemetry proved ExoPlayer output was gapless (0 underrun/suppression/discontinuity, stable bufAhead) while skips were audible -> the stutter layer is BELOW the app (BT/Wi-Fi coexistence, hotspot network) - status as of 2026-07-22, A/B on device pending. Earlier min<max hysteresis theory (read-pause -> relay drop) was NOT confirmed by field data: buffer on 1x live delivery never reaches max, so hysteresis rarely engages. Separate finding: DFM `hostingradio.ru` was TCP-unreachable for 21 min from a Samsung-hotspot subnet (192.168.107.x) - network path, not player.

**How to apply:** For radio stutter reports, read the `S1148: telemetry` + `Audio diag:` lines FIRST - they discriminate engine vs output vs network in one log. Do not churn LoadControl values without telemetry evidence; clean telemetry + audible skips -> check `route=`, BT device flap events, and which network the phone was on. Related: [[s1146-seektonext-live-noop]].
