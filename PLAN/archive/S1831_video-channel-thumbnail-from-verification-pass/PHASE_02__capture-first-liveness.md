# Phase 02 - Capture-first liveness

**Strategic spec:** [`../S1831_video-channel-thumbnail-from-verification-pass.md`](../S1831_video-channel-thumbnail-from-verification-pass.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

For a video channel, make the frame capture itself the proof of liveness, so one open of the address yields
both the verdict and the thumbnail - without collapsing the `geo` / `dead` / `unknown` verdicts that a
destructive prune depends on.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] **Owner has authorised the capture run over live third-party streams** (strategic §6.2). This phase's
      measurement steps load other people's servers for hours; the strategic spec's header marks the run as
      needing that go-ahead before an unattended session starts it.
- [x] `temp/CODE.LOCK` acquired immediately before each edit, released right after.
- [x] Backup of the publisher script in the ticket's scratch directory, refreshed after Phase 01's edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | +90 net |
| `dev/handoff/streams-source-spec/08_build_publish_pipeline.md` | Modified | +15 net |

---

## Steps

### Step 02.1 - Measure the merged pass against the two current passes

**Files:** none - measurement only; harness and raw data kept in `evidence/`
**Depends on:** owner go-ahead

**Prompt for developer:**

> On a sample of video channels drawn across providers, run three timed passes and record per-channel wall time and outcome: the deep-signal probe alone, the frame capture alone, and a capture used as the liveness decision. Record for the third pass how many channels produced a frame, and inspect a random subset of those frames by eye for whether the picture is recognisable broadcast content rather than black, a slate or a bumper.

**Why:**

Strategic §6.2 stays Open precisely because the answer is measured rather than derived, and both halves are
load-bearing: strategic §3.2 forbids the ticket from lengthening a run already counted in hours, and strategic
criterion 1 asks for a recognisable frame, which no reading of the code can predict.

**Verification:**

- `evidence/measurement-400-channels.csv` holds one row per sampled channel with the timings and the outcome.
- A written verdict states the merged pass's total against the sum of the two current passes.
- A written verdict states the share of captured frames judged recognisable, with the sample size.
- Strategic §6.2 is flipped to `Resolved` with this artifact linked, or the phase stops here.

**Status:** `[x]` done

---

### Step 02.2 - Give the capture a classify-and-capture return shape

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a function that captures one frame with ffmpeg and returns the same three fields `Get-MediaStreamKinds` returns - `Kinds`, `Codecs`, `Ok` - by raising the capture's `-loglevel` to `info` and parsing the `Stream #0:0: Video: ..` lines ffmpeg already prints to stderr. `Ok` is true only when a frame file was written and is non-empty.

**Why:**

Those three fields land in the row as `media_kinds` and `media_codecs` and in the human-readable note, so a
capture that does not reproduce them changes the meaning of columns other consumers already read; research 02
established ffmpeg prints the same stream information ffprobe reports, making this a parsing job with a known
input rather than a design question.

**Verification:**

- `Grep` - the new function returns an object with exactly the keys `Kinds`, `Codecs`, `Ok`.
- Run it against one known-good HLS url and one known-dead url; the first returns `Ok = $true` with a non-empty frame on disk, the second returns `Ok = $false`.
- `Grep` - `-loglevel error` is no longer used on the capture path, and the stderr is captured rather than discarded.

**Status:** `[x]` done

---

### Step 02.3 - Substitute the capture for the decoder call, for VIDEO rows only

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `Invoke-SignalProbe`, for a row whose `media_kind` is `VIDEO`, call Step 02.2's function where `Get-MediaStreamKinds` is called today, writing the frame to the cache path `Get-PreviewFrameFile` computes. On failure, call `Get-MediaStreamKinds` before falling through - so the fall-through chain becomes capture, then ffprobe, then the existing HTTP, RTSP, HLS and DASH branches, all of them untouched. Non-VIDEO rows keep calling `Get-MediaStreamKinds` directly and are unchanged.

**Why:**

Strategic ADR-3 fixes this as the only safe shape: the probe's verdict feeds `-PruneDead`, which deletes rows
from the shipped catalog, and an ffmpeg failure cannot tell 403 from 404 from a timeout, so replacing the
probe outright would collapse `geo` into `dead` and hand the prune a mandate to delete region-locked channels -
the incident S1830 closed on the day this ticket was written.

Keeping `Get-MediaStreamKinds` in the fall-through rather than dropping it is what makes the change
**verdict-neutral by construction**, and it is not a theoretical nicety. A channel can declare a video track
that ffprobe confirms while a one-frame capture still fails - the 400-channel measurement puts that population
at a few percent. Without the ffprobe step in the chain, every such channel would fall to the HTTP branches,
and any that those cannot confirm would land on `unknown`. An un-pinned deep-signal `-PruneDead` run widens
from `dead` to `dead,unknown`, so those channels would be **deleted from the shipped catalog** - a silent
regression of exactly the kind S1830 existed to fix. The extra ffprobe call costs nothing on the healthy
majority, because it only runs where the capture already failed.

**Verification:**

- `Grep` - the RTSP, HLS, DASH and generic-body branches are byte-identical to the backup taken in Phase 01.
- `Grep` - `Get-MediaStreamKinds` is still called on the non-VIDEO path.
- Run the probe over a fixed sample containing at least one known 403 channel; that channel is still classified `geo`, not `dead`.
- Run the probe over a sample of live video channels; each alive verdict has a frame on disk at its cache path.

**Status:** `[x]` done

---

### Step 02.4 - Let the preview build consume the cache instead of re-capturing

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Confirm `Invoke-ChannelPreviewCapture` skips every url whose frame the probe already wrote, and add a printed line stating how many frames were reused from the cache and how many were captured fresh. Do not change the skip logic itself.

**Why:**

Research 02 found the merge alone does not remove the second pass - `-WithChannelPreviews` remains its own
mode that re-reads the catalog and captures whatever it finds - so without this the address is still opened
twice and strategic goal 3.1.2 is not delivered; the existing `SHA1(url)` cache, built for resuming an
interrupted capture, is already the handoff channel and needs no new mechanism.

**Verification:**

- After a probe run, a `-WithChannelPreviews` run over the same rows reports zero fresh captures.
- The printed line names both counts.
- `Grep` - the skip condition in `Invoke-ChannelPreviewCapture` is unchanged from the Phase 01 backup.

**Status:** `[x]` done

---

### Step 02.5 - Record the merged pass in the handoff description

**Files:** `dev/handoff/streams-source-spec/08_build_publish_pipeline.md`
**Depends on:** Step 02.4

**Prompt for developer:**

> Describe the merged pass: for a video row the liveness decision is now a frame capture, a failure falls through to the unchanged classification branches, and the preview build packs what the probe cached. State that non-video rows are unaffected.

**Why:**

That document exists as the format and pipeline description for whoever reimplements the feature, so a change
to which pass produces the frame is exactly what it is for; leaving it stale would make the next reader
re-derive the probe's structure from a 3254-line script.

**Verification:**

- `Grep` - the file describes the capture-as-liveness path for video rows.
- `Grep` - it states that the classification branches are unchanged.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Strategic §6.2 is `Resolved` with the Step 02.1 artifact linked, or carries a `Carrier: Sxxxx` token.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Closure through `scripts/post-change.ps1 -Files "scripts/streams/collect-stream-candidates.ps1,dev/handoff/streams-source-spec/08_build_publish_pipeline.md" -ScopeToFile -ChangeType Mixed`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Measured Outcome (2026-08-20)

**Step 02.1, 400 channels, one per provider** (`research/03__merged-pass-measured.md`):

| | probe (today) | capture (merged) |
| --- | ---: | ---: |
| Median per channel | 2 682 ms | 2 680 ms |
| Channels confirmed | 340 (85%) | **360 (90%)** |
| Aggregate worker time | 2 866.8 s | **1 493.8 s** (-47.9%) |
| Wall clock | 246.0 s | **128.7 s** (-47.7%) |

Frame usability: 360 frames scored objectively (8x8 luminance mean and spread), then every flagged frame
looked at. **7 of 360 unusable (1.9%)**; one flagged frame was a false positive, and the 24 lowest-scoring
passes were inspected from the other side to confirm the threshold is not lenient. Strategic §7's
medium-probability "the frame will be black" risk is downgraded to low, measured.

**Verdict neutrality, proven rather than argued.** One 24-row sample - every `access = geo` channel in the
catalog plus 15 video rows - run down both paths, `-SkipCaptureFirst` against the default:
**24 of 24 verdicts identical, 0 differences.**

**Mechanisms verified individually on that run:**

- 18 frames rewritten within the run's window -> the capture rung really executes, it is not skipped.
- 1 channel kept a frame from an earlier run after its capture failed -> the `.new` staging works, a failed
  capture does not truncate a good thumbnail.
- 2 channels came back `alive` with no fresh frame -> the ffprobe rung below caught them, which is exactly
  the "2 in 400" population the measurement predicted and the reason that rung was kept.
- 0 leftover `.new` files -> staging cleans up.
- Step 02.4: after the probe pass, the sheet build reported **19 already captured, 0 to capture** - the
  handoff works and the address is not opened twice.

**Two defects were introduced and caught by this phase's own verification, not by review:**

1. **The cached-frame short-circuit.** The first implementation treated an existing cached frame as proof of
   liveness. The 24-row test came back "21 alive, 3 unknown" with no `geo` at all, because every url already
   had a frame from 12 August - a channel that died last week would have been reported alive forever, without
   a single request, to a verdict that feeds `-PruneDead`. Removed; the cache is the handoff to the sheet
   build, never evidence.
2. **Our own output codec leaking into the catalog.** ffmpeg describes its output with the same
   `Stream #0:0: Video:` shape as its input, so the first parser returned `aac,h264,png` and would have
   written `png` - our tile encoder - into the row's `media_codecs` as a codec the broadcaster sent. Fixed by
   cutting the text at `Output #`; verified `aac,h264` on two live channels.

---

## Handoff Notes to Next Phase

Final code phase. The invariant it establishes and that later work must not break: a failed capture is never
itself a verdict. Only the classification branches below it decide `geo` from `dead` from `unknown`, and any
future change that lets a capture failure short-circuit them re-opens S1830.

---

## Rollback Plan

Restore the backup refreshed in this phase's prerequisites. Nothing is published by this phase, so no pinned
asset and no burned revision is involved; the catalog on disk is only mutated by a `-PruneDead` run, which
this phase never invokes.
