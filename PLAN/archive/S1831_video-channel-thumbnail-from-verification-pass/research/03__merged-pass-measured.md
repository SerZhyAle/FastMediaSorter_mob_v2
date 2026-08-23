# Research 03 - The merged pass, measured on live channels

**Ticket:** S1831, research item 6.2
**Date:** 2026-08-20
**Authorised by:** owner, 2026-08-20, on the condition that this was not the run that had just finished in a
neighbouring session. Checked before starting: the frame cache had not been written since 2026-08-12 and no
ffmpeg or ffprobe process was alive. What had finished next door at 11:11 was a liveness sweep and a catalog
publication - a different pass, and it left the frame cache untouched.
**Harness:** `evidence/measure-capture-vs-probe.ps1`, `evidence/score-frames.ps1` - kept with the ticket so the
measurement can be repeated; neither is repository code and neither is wired into any pipeline.
**Raw data:** `evidence/measurement-400-channels.csv` (one row per sampled channel; the `name` and `url`
columns are dropped to stay inside the 64 KB per-file cap, the timings and outcomes are intact),
`evidence/frame-scores-360.csv`, `evidence/verdict-ab-24-rows.csv`.

---

## 1. Sample

400 video channels, **one per provider**, drawn round-robin across the 598 distinct hosts in the 2 763 VIDEO
rows of the catalog as published at 11:11. Provider-diverse rather than "first N": the catalog is heavily
skewed toward a few CDNs, so the first 400 rows would have measured one CDN's behaviour and called it the
population.

Both passes used byte-identical arguments to production - `Get-MediaStreamKinds` for the probe, and
`Invoke-ChannelPreviewCapture` for the capture, including the 8 s and 20 s timeouts and throttle 12 - so the
numbers describe the pipeline rather than the harness.

## 2. Cost - the prediction was wrong, and wrong in the useful direction

Research 02 predicted the merged pass would be **more expensive** per channel, reasoning that the capture
carries a bigger probesize and a longer timeout. Measurement says the two calls cost the same:

| | probe (ffprobe) | capture (ffmpeg) |
| --- | ---: | ---: |
| Median per channel | **2 682 ms** | **2 680 ms** |

Two milliseconds apart on 400 channels. The reasoning missed that the probe's budget is a floor as well as a
ceiling: `-analyzeduration 3000000` makes ffprobe keep reading to characterise the streams, while the capture
stops the moment it has one decodable frame. The bigger allowance is never spent on a healthy channel.

Aggregate over the sample - worker-seconds summed across the throttled workers, and wall clock:

| | today (probe, then capture) | merged (capture only) | change |
| --- | ---: | ---: | ---: |
| Aggregate worker time | 2 866.8 s | **1 493.8 s** | **-47.9%** |
| Wall clock | 246.0 s | **128.7 s** | **-47.7%** |

So strategic §3.2's constraint - the ticket must not lengthen a run already counted in hours - is not merely
satisfied. **The merged pass halves it.** Extrapolated to all 2 763 video channels at the measured wall rate,
the video portion of a sweep goes from roughly 28 minutes to roughly 15.

## 3. The capture is a better liveness criterion than the probe, not merely an equal one

This was not an anticipated result and it is the strongest argument for ADR-3:

| Outcome | Channels |
| --- | ---: |
| Probe confirmed media | 340 (85%) |
| **Capture produced a frame** | **360 (90%)** |
| Both agree | 338 |
| Probe said yes, capture produced nothing | **2** |
| **Capture produced a frame, probe said no** | **22** |

The capture finds 22 channels the probe misses and loses 2 the probe catches, a net gain of 20 channels - 5%
of the sample - on the *liveness* question alone, before any thumbnail is considered. That follows from what
the two calls actually assert: ffprobe says "this stream declares tracks I can characterise", ffmpeg says
"this stream handed me a decodable picture just now". The second is the stronger claim and is also the one
the owner's original wording asks for - test that it serves video, not that it answers 200.

**The 2 disagreements are why `Get-MediaStreamKinds` stays in the fall-through.** At 2 in 400 - 0.5% - they
would be roughly 14 channels across the full catalog. Without the ffprobe step in the chain they would drop to
the HTTP branches, and whatever those cannot confirm becomes `unknown`; an un-pinned deep-signal `-PruneDead`
run widens from `dead` to `dead,unknown` and would **delete them from the shipped catalog**. Keeping the
ffprobe call on the failure path costs nothing on the 90% that succeed and makes the change verdict-neutral by
construction. This is recorded in strategic ADR-3 and in phase 02 step 02.3.

## 4. Frame usability - the risk in strategic §7 did not materialise

Strategic §7 rated "the frame will be black or a slate" as **Medium** probability. Measured over all 360
captured frames, objectively and then by eye.

**Objective screen.** Each frame reduced to an 8x8 greyscale block; mean luminance says how dark it is, the
standard deviation across the 64 cells says how much picture is in it. Thresholds `mean < 24 and std < 12` =
black, `std < 12` = flat.

| Verdict | Frames | Share |
| --- | ---: | ---: |
| picture | 352 | 98% |
| black | 5 | 1% |
| flat | 3 | 1% |

**Every one of the 8 flagged frames was then looked at.** Seven are genuinely unusable: four black, two solid
colour fills (one purple, one red), one black with only a station logo and an EPG bar. The eighth is a **false
positive** - a Deutsche Welle nature shot of green foliage with a subtitle, scored `std = 10.3` because
foliage averages flat at an 8x8 reduction. So the screen over-flags slightly, and the true unusable count is
**7 of 360 = 1.9%**.

**The threshold was validated from the other side too.** The 24 lowest-std frames that passed as `picture`
(std 12.7 to 19.6) were inspected: an Egypt map, a spider close-up, cherry blossom, a concert, weather with a
temperature overlay, drama interiors, a Chinese strategy broadcast, end credits, several Arabic and Spanish
text cards, and a handful of channel idents. Nothing junk sits just above the line, so the 98% is not an
artefact of a lenient threshold.

**Verdict: 98.1% of captured frames are usable**, against a spec that expected the opposite risk to be
material. Strategic criterion 1 - a recognisable frame - is met for the overwhelming majority, and the
channels it is not met for are a fifty-times smaller population than the 877 the sheet cap was discarding.

One curiosity worth a line because it will confuse whoever meets it: one channel in the sample broadcasts what
is plainly its own upstream encoder's console, so the captured frame is a wall of `Decode error rate 1 exceeds
maximum` and `Nothing was written into output file` messages. It is real video content and passes every
mechanical test; it is simply a broadcaster shipping a crashed terminal. Not worth a ticket at one occurrence
in 400, and no automatic screen would catch it without also rejecting legitimate text cards.

## 5. What this resolves and what it changes

Research item 6.2 is **Resolved**, and on both halves the answer is better than the spec assumed:

- Cost: the merged pass is **48% cheaper**, not more expensive.
- Usability: **98.1%** of frames are usable, so §7's medium-probability risk is downgraded to low and its
  mitigation - "compare usability, not just time" - has been carried out with that result.
- Bonus not asked for: the capture is a **strictly better liveness test**, net +20 channels in 400.

Two spec statements are superseded and are corrected in place rather than left standing:

- Research 02 section 5's prediction that the merged pass trades "two cheap-ish passes for one expensive
  pass". It does not; the two calls cost the same and one of them disappears.
- Strategic §7's medium rating for frame quality.

## 6. Commands run

```text
evidence/measure-capture-vs-probe.ps1 -SampleSize 24  -PerProvider 1 -OutDir <scratch>/smoke   -> exit 0
evidence/measure-capture-vs-probe.ps1 -SampleSize 400 -PerProvider 1 -OutDir <scratch>/measure -> exit 0
evidence/score-frames.ps1 -FrameDir <scratch>/measure/frames                                    -> exit 0
ffmpeg tile=4x2 over the 8 frames flagged black/flat  -> contact sheet, read by eye (verdict in section 4)
ffmpeg tile=6x4 over the 24 lowest-std passes         -> contact sheet, read by eye (verdict in section 4)
ffmpeg tile=5x4 over the 20 smoke frames              -> contact sheet, read by eye (verdict in section 4)
```

Third-party cost of this research: 424 channels touched twice each - once by a probe, once by a capture - and
nothing else. No publication, no catalog write, no change to the production frame cache.
