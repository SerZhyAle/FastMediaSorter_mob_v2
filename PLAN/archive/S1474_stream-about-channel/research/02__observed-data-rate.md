# 02 - Where the observed data rate comes from

Resolves strategic §6 item 2 ("Наблюдаемая скорость данных").

## Options examined

- `DefaultBandwidthMeter.bitrateEstimate` - the transfer rate the engine actually measured on this connection. Already used in this repo twice: `BandwidthAdaptiveLoadControl.kt:61` and `StreamPlaybackHelper.kt:69`.
- `Format.bitrate` of the selected tracks - what the stream *declares*, not what arrived. Frequently `NO_VALUE` on live manifests.
- The catalog's stored bitrate - a maintainer's note, explicitly ruled out by strategic §1 as a claim rather than a measurement.

## Decision

Report `bitrateEstimate` from a `DefaultBandwidthMeter` handed to the probe's own `ExoPlayer.Builder`, read once at the deadline. Report `Format.bitrate` separately when present, labelled as the stream's declared rate - the two answer different questions and merging them would produce a number that is neither.

For the already-playing case the estimate belongs to the playing engine's meter, so the value is read from that engine rather than measured again.

## Labelling constraint

Strategic §7 names this the risk most likely to mislead: the observed rate describes this connection at this moment, not the channel. The label must say so in EN/RU/UK, and it goes through the `docs/COMMUNICATION_POLICY.md` tone check like every other user-visible string in this ticket.

An estimate of `BandwidthMeter.NO_ESTIMATE` (or a non-positive value) is reported as unavailable, never as zero - zero would read as "the channel sends nothing".
