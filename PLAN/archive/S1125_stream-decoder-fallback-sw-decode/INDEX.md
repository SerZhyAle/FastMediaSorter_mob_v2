# S1125 - Tactical plan INDEX

**Ticket:** S1125
**Strategic spec:** `PLAN/S1125_stream-decoder-fallback-sw-decode.md`
**Status:** Tactical

## Scope

Two independent decoder-profile fixes on the stream paths (strategic §2):

1. Real stream player builds with the shared renderers factory (decoder fallback ON, extension
   PREFER), matching every other playback path.
2. Headless preview grabber decodes with a software-preferred `MediaCodecSelector`, so it stops
   competing for the hardware decode-surface pool the real player needs.

## Phases

- `PHASE_01__renderers-profile-wiring.md` - both edits + device-verification probe insertion.

## Research

- `research/01__force-software-decode-mechanism.md` - resolves §6 (software-decode mechanism).

## Verification gate

On-device: the grabber's software-decode change touches a path with a native-crash history on real
hardware (S0700/S0900/S0933), so criterion §11.4 requires a real device (Samsung/Exynos). Emulator
verifies wiring + no-regression; real-device confirms no native process kill.
