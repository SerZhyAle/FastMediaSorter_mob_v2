# S1262 research 02 - Sport profile short-exposure recipe (strategic §6.2)

**Question:** is short-exposure priority reachable on devices without manual sensor control, and what is the hide predicate.

## Findings (from the working tree, 2026-07-28)

- `CameraRuntimeCapabilities` already exposes the manual-exposure primitives: `supportsManualSensor`, `isoRange: Range<Int>?`, `shutterRangeNs: Range<Long>?` (probed by `CameraCapabilityProbe`).
- `CameraCaptureSessionManager` already applies per-request Camera2 interop options (`setCaptureRequestOption`, used for the macro `LENS_FOCUS_DISTANCE` lock around line 757-760), and re-applies intents after every rebind - the same slot fits sport options.
- CameraX AE offers no shutter-priority mode: with AE on, exposure time cannot be bounded; a negative AE compensation darkens but does not shorten exposure. True short exposure requires AE off + manual `SENSOR_EXPOSURE_TIME` + manual `SENSOR_SENSITIVITY` together (Camera2 contract).

## Conclusion

- Sport recipe (applied via the existing interop slot): AE off; exposure time = target 4 ms (1/250 s) clamped into `shutterRangeNs`; sensitivity = high bias, `min(1600, isoRange.upper)` but not below `isoRange.lower`; continuous autofocus `CONTROL_AF_MODE_CONTINUOUS_PICTURE`.
- Hide predicate for the Sport menu entry: `supportsManualSensor && shutterRangeNs != null && isoRange != null && shutterRangeNs.lower <= 4ms`. Devices that cannot do it simply do not show the entry (no fallback - a fake "sport" that cannot freeze motion is a dead control).
- Numeric constants live as named companion consts (detekt-clean-first, S0826); exact values are tuning knobs verified on device at acceptance.
- Trade-off (owner-approved 2026-07-28): frames go darker in low light - the entry description must say so honestly (COMMUNICATION_POLICY tone gate).

**Status:** Resolved.
