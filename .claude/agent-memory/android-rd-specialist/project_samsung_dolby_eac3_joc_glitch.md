---
name: samsung-dolby-eac3-joc-glitch
description: Samsung vendor audio decoders (c2.sec.mp3, c2.dolby.eac3) emit glitchy PCM (~2 rips/s) with clean telemetry; software-preferred selector for ALL audio mimes (S1148)
metadata:
  type: project
---

Samsung vendor audio decoders on SM-S731B (Android 16/API 36) emit audibly glitchy PCM (~2 rips/s) that NO player-level signal can see (no underrun/state change/discontinuity, clean 5s telemetry). Confirmed 2026-07-22 by two same-broadcast A/Bs:
- `c2.sec.mp3.decoder.mpeg` ripped a 48 kHz MP3 radio stream (walmradio /jazz) while `/jazz_opus` (c2.android.opus) was clean; 44.1 kHz MP3 stations were clean too - 48 kHz MP3 is the trigger combo.
- `c2.dolby.eac3.decoder.eac3-joc` ripped an E-AC3-JOC (Atmos) 6ch stream.
- Local 44.1k mp3 clean; reboot did not help; speaker route (no BT).

**Why:** glitchy-but-continuous PCM is a diagnostic black hole - 5 iterations of buffer/LoadControl churn found nothing until `Audio diag: decoder initialized` + owner codec A/B exposed the decoder class. A targeted Dolby-only bypass (iteration 5) was insufficient - the defect is the vendor-decoder class, not one codec.

**How to apply:** `createPlaybackRenderersFactory` (`PlaybackRenderersFactory.kt`) has a `MediaCodecSelector` preferring software AOSP decoders for ALL `audio/*` mimes (`MimeTypes.isAudio`), reorder-only, fallback kept; video stays hardware-first. Do not remove without re-testing walmradio /jazz (MP3 48kHz) + an eac3-joc stream on real Samsung. For any "phantom audio glitch with clean telemetry": read `decoder initialized` line first; vendor decoder (`c2.sec.*`, `c2.dolby.*`) = prime suspect; ask the owner for a same-station codec A/B - it is the single most decisive experiment. Related: [[live-radio-loadcontrol-min-eq-max]].
