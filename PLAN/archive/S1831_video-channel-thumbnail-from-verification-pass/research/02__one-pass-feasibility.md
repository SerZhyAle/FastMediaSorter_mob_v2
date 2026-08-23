# Research 02 - Can the frame come from the liveness pass, and what shape must that take

**Ticket:** S1831
**Date:** 2026-08-20
**Method:** full read of the probe and capture paths in `scripts/streams/collect-stream-candidates.ps1`.
**Scope note:** this artifact answers the *shape* half of strategic §6.2. The *measurement* half - how long a
merged pass takes and whether its frames are usable - is not answerable from the tree and stays Open.

---

## 1. The finding that reshapes ADR-2

Strategic §0 states the premise plainly: "the run that proves a video channel is live already pulls video
data, so the thumbnail frame is taken from that same pulled stream". The first half is true. **The second half
is not implementable as written**, and the reason is worth stating before any plan is built on it.

The liveness pass proves video via `Get-MediaStreamKinds` (`:762-813`), which runs:

```text
ffprobe -v error -rw_timeout <8s> -analyzeduration 3000000 -probesize 1000000
        -print_format json -show_streams <url>
```

`-show_streams` prints **stream metadata** - `codec_type`, `codec_name` - to stdout. There is no `-frames:v`,
no output file, and no `FileStream` opened for writing anywhere in that function. ffprobe pulls up to 1 MB of
real stream to demux packet headers, so the network cost the spec objects to is genuinely being paid, but
**no pixels are ever decoded and nothing is persisted**. There is no frame sitting in a buffer waiting to be
handed to the packer.

So "hand the already-pulled frame to the packer" has nothing to hand over. ADR-2's *goal* survives intact; its
*mechanism* has to be different, and the difference is not cosmetic - it decides which function gets rewritten.

## 2. The shape that does work: capture-first, fall through on failure

Read `Invoke-SignalProbe`'s own control flow (`:815-1074`) and the answer is almost pre-arranged. Since S1830
the function asks the decoder first and treats everything else as a fallback (`:857-870`):

```powershell
# S1830: ask the decoder first. The playable majority then costs one ffprobe instead of a body
# pull, and only what it cannot confirm falls through to the branches below - whose job narrows
# to telling geo from dead from "we could not measure".
if ($ffprobe) {
    $media = Get-MediaStreamKinds -FfprobeExe $ffprobe -Url $url -TimeoutSec $timeout
    ..
}
if ($mediaConfirmed) { $status = 'alive'; $note = "media $mediaKinds [$mediaCodecs]" }
elseif ($url -like 'rtsp://*') { .. }   # geo / dead / unknown discrimination lives below
```

A successful **frame capture is a strictly stronger proof of the same proposition** than a successful
`-show_streams`: ffprobe says "this stream declares a video track", ffmpeg writing a 240x135 PNG says "this
stream delivered decodable video pixels just now". So for a VIDEO row the decoder-first call can *become* the
capture, and everything after it stays untouched:

- Capture succeeds -> `alive`, and the frame is on disk, from one open of the address. Criterion 3 satisfied
  for the healthy majority, which is the population the criterion is about.
- Capture fails -> control falls into the existing HTTP/RTSP/HLS/DASH branches exactly as today, and they do
  the geo-versus-dead-versus-unknown work they already do.

This is not a redesign; it is a substitution at one call site inside a structure that already isolates it.

## 3. The constraint that a naive merge would break, and that is not in the strategic spec

`Invoke-SignalProbe`'s verdict is not advisory. It feeds `-PruneDead`, which **deletes rows from the shipped
catalog**. And the verdict is not binary: S1117 split region-locked channels into their own `geo` status on
HTTP 403/451 precisely so a prune would stop deleting channels that play fine for a user in-region, and S1830 -
priority 95, closed 2026-08-20, the day this ticket was written - was the bugfix for a probe that pruned live
channels.

An ffmpeg capture cannot tell 403 from 404 from a timeout; it just fails. So a merge that **replaced** the
probe with the capture would collapse `geo`, `dead` and `unknown` into one failure and hand `-PruneDead` a
mandate to delete region-locked channels. That is a re-run of the exact incident S1830 just closed.

The capture-first-fall-through shape in §2 avoids it structurally: the discriminating branches are what a
failure falls *into*, so nothing that distinguishes the failure verdicts is removed. Any plan that instead
rewrites the probe wholesale is refused on this ground.

**This constraint belongs in the strategic spec** - it is a hard boundary on ADR-2, not an implementation
detail - and it is added there as part of this ticket rather than left in a research file.

## 4. What the merged call has to reproduce

`Get-MediaStreamKinds` returns `@{ Kinds; Codecs; Ok }` (`:769`), where `Kinds` is the `codec_type` set joined
with `+` (`audio+video`), `Codecs` the `codec_name` list, and `Ok` is `Kinds contains audio or video`. Those
land in the row as `media_kinds` / `media_codecs` (`:1044-1049`) and in the human-readable note
`"media $mediaKinds [$mediaCodecs]"`.

A capture-based replacement must produce the same three fields or every downstream consumer of those columns
changes meaning. ffmpeg can supply them: at `-loglevel info` it prints the same `Stream #0:0: Video: h264 ..`
lines ffprobe reports, so the fields are parseable from the stderr the capture already collects. The current
capture runs at `-loglevel error` (`:2095`) and would have to be raised. Recorded as the concrete unknown for
the implementing phase - it is a parsing job with a known input, not a design question.

## 5. Why this cannot be finished without the owner-gated run

Two things stay unmeasurable from the tree, and both are load-bearing:

- **Cost.** ffprobe's budget is `-probesize 1000000 / -analyzeduration 3000000` at an 8 s timeout and throttle
  48 under `-DeepSignal`. The capture's is `-probesize 5000000 / -analyzeduration 5000000` at a 20 s timeout
  and throttle 12. Per channel the capture is the more expensive call by construction, so the merge trades
  "two cheap-ish passes" for "one expensive pass". Whether that is a net win over the whole catalog - strategic
  §3.2's requirement that the ticket must not lengthen the run - is arithmetic over real timings nobody has.
- **Usability of the frame.** Strategic §7 already names the risk: the first decodable frame of a live stream
  is often black, a slate, or a station bumper. Criterion 1 asks for a *recognisable* frame, and no reading of
  the code can say what fraction of 2 672 channels yields one.

Both need the capture run over live third-party streams that strategic §6.2 describes and that the spec's own
header marks as needing the owner's go-ahead before an unattended session starts it. This artifact does not
start it.

**Consequence for sequencing:** the two pillars are independent. Pillars 2 and 3 - sheet height derived from
tile count, and a refusal that names the uncovered channels - touch only the packer, need no network, and
close strategic goals 2 and 3 plus criterion 4's mechanism on their own. Pillar 1 is the only part gated on the
run. Planning them as one phase would put the whole ticket behind the gate for no reason, so they are split.

## 6. Second finding: the merge alone does not remove the second pass

Even with §2 implemented, `-WithChannelPreviews` remains its own top-level mode (`:3011-3019`) that re-reads
`$ExistingCsv` from disk (`Invoke-BuildChannelPreviewAtlasRun:2255-2264`) and captures whatever it finds. So
after the merge there would be **two** places that capture frames unless the preview mode is changed to consume
the frame cache the probe now fills rather than filling it itself.

The frame cache makes this cheap: frames are keyed `SHA1(url).png` under `temp/channel-preview-frames`
(`Get-PreviewFrameFile:2046-2051`) and `Invoke-ChannelPreviewCapture` already skips a URL whose frame exists
(`:2075-2081`) unless `-RefreshPreviewFrames` is passed. So a probe pass that writes into that same cache makes
the subsequent preview build a no-network pack of what the probe collected, with no code change to the skip
logic at all - the caching that exists for resuming an interrupted capture turns out to be the handoff channel
ADR-2 needs. Worth stating explicitly in the plan, because it is the difference between "one address open per
channel" and "one address open per channel, twice".

## 7. Evidence index

| Claim | Where |
| --- | --- |
| Probe's media check is metadata-only, no frame | `Get-MediaStreamKinds` `:762-813`, args `:771-779` |
| Decoder-first with fallback branches below | `Invoke-SignalProbe` `:857-870` |
| Verdict feeds a destructive prune | `Invoke-CatalogMaintenance` `:2734`, `-PruneDead` `:106` |
| `geo` split from `dead` on 403/451 | script header S1117 note `:29-33` |
| Capture is a separate top-level mode | `:3011-3019`, `Invoke-BuildChannelPreviewAtlasRun:2255-2264` |
| Capture args, 20 s timeout, throttle 12 | `Invoke-ChannelPreviewCapture:2095-2111`, params `:157-158` |
| Frame cache keyed by SHA1(url), skip-if-present | `:2046-2051`, `:2075-2081` |
| Probe returns Kinds/Codecs/Ok, stamped onto the row | `:769`, `:1044-1049` |
