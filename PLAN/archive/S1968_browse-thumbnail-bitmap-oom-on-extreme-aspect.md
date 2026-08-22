# S1968 - Browse thumbnail allocation OOMs on an extreme-aspect image

**Status:** Archived

## 0. Symptom and evidence

Browsing a folder that contains one particular image floods the log with bitmap allocation failures.
The app does not crash - the decode fails, the cell shows no thumbnail - but the attempt repeats every
time the row is bound.

From the pre-release sweep of 2026-08-22 (emulator-5554, Pixel 9, API 35, standard-debug):

- `E/HWUI: OOM allocating Bitmap with dimensions 300 x 1970400`, **254 occurrences**, always that
  exact pair of dimensions.
- Interleaved with `D/HWUI: --- Failed to create image decoder with message 'unimplemented'`.
- Attributed to the app's own pid by `prerelease-log-audit.ps1` (`attribution: pid`), which reported
  it as the run's single actionable cluster.
- First at 15:05:55.689, last at 15:18:34.129 - it spans most of the suite, so it is not one screen.
- The nearest app line is `BrowseScrollButtonManager: submitList post: notifyItemRangeChanged(0, 8,
  LOAD_THUMBNAILS)` inside `BrowseActivity`, so the caller is the browse thumbnail path.

Extracts and the reproducing command: `PLAN/S1968_browse-thumbnail-bitmap-oom-on-extreme-aspect/EVIDENCE.md`.

## 1. First reading

The width is constant at 300 px, which looks like a fixed thumbnail target width. The height then
appears to be derived from the source aspect ratio with no upper clamp: 1 970 400 px of height is
2.4 GB at ARGB_8888, so the allocation can only fail. A source image with an extreme aspect ratio (or
a header declaring one) is enough to trigger it.

That the dimension pair never varies across 254 attempts says one file is responsible, and that
nothing caches the failure - the same doomed allocation is retried on every bind.

## 1a. Research (2026-08-22) - the mechanism, and why 300 x 1970400

`AdapterThumbnailLoader.loadImage()` builds every browse thumbnail request. Its local-file branch
(`AdapterThumbnailLoader.kt:583-614`) ends in `.override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)`
followed by `.centerCrop()`, where the constant is `300` (`AdapterThumbnailLoader.kt:74`). That is
where the constant width in the log comes from, and it is doing exactly what it was asked to.

`centerCrop` must **cover** the target, so it scales by the larger of the two per-axis factors. For a
source that is extremely narrow relative to its height, covering 300 px of width forces an upscale, and
the height follows the aspect ratio without ever being checked on its own. 300 x 1 970 400 at
ARGB_8888 is about 2.4 GB, so the allocation cannot succeed - the size is derived, not read from the
file, which is why it is identical on all 254 attempts.

Nothing anywhere clamps the absolute pixel count or byte cost of a decode target on this path. The
codebase does gate decodes by size, but only for documents - `SMB_PDF_LARGE_MAX_SIZE` and its
neighbours (`AdapterThumbnailLoader.kt:76-84`) reject a PDF or EPUB outright and show a generated
placeholder. Images have no equivalent.

The repetition has its own separate cause. A negative cache exists and works -
`NetworkFileDataFetcher.isThumbnailFailed` / `markThumbnailAsFailed`
(`NetworkFileModelLoader.kt:194-208`), persisted across runs - but it is consulted and populated only
in the network and cloud arms (`AdapterThumbnailLoader.kt:360, 386, 443, 477, 536, 568`). The
local-file arm's failure listener only logs and shows a placeholder, so every
`notifyItemRangeChanged(.., LOAD_THUMBNAILS)` rebind reissues the same doomed request.

**The `unimplemented` decoder line is not attributable from code.** It is native HWUI/Skia text. The
only app-registered `ImageDecoder` use is `AnimatedImageDecoder.kt`, reached only when the header
sniffs as animated WebP or APNG (`AnimatedImageDecoder.kt:34-40, 60-69`), so a plain image never
touches it. Whether the two lines share a cause needs the actual file.

**Neither `AdapterThumbnailLoader` nor `MediaFileAdapter` has any unit test.** `MediaFileAdapter.kt` is
1319 lines against the 1500 ceiling, so a fix should not add logic there.

## 2. Open questions

- Which file in `/sdcard/Download/FastMediaSorter_Test` produces it, and is its header honest or
  corrupt? A corrupt header needs a different fix from a genuinely long panorama.
- Where is the thumbnail target size computed - and should the fix clamp the derived dimension, hand
  Glide an `override()`, or reject a decode whose declared size exceeds a byte budget?
- Should a failed decode be remembered so the same file is not retried on every rebind?

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0484 (the pre-release sweep whose log audit surfaced this cluster), S1612 and
  the Maestro suite (the browse flows that walk these folders), S1934 (feature-inventory flavor gating -
  this path is flavor-neutral and needs no gate)
- **Device:** emulator-5554, Pixel 9, API 35, standard-debug, browsing
  `/sdcard/Download/FastMediaSorter_Test` - the configuration the 254 occurrences were recorded on
- **Localization:** no user-visible string changes; a clamped thumbnail shows the existing placeholder

---

## 4. Answers to the open questions

**Where to clamp - and it is not the aspect ratio.** Bound the decode by **total pixels**, in
`AdapterThumbnailLoader`'s image branch, before the request is issued. An aspect-ratio rule would have
to guess which panoramas are legitimate; a pixel budget does not care why an image is shaped as it is
and refuses only what cannot be allocated. This keeps the change in the one file that already owns
every thumbnail request and away from `MediaFileAdapter`, which is near its size ceiling.

**Remember the failure - reuse, do not invent.** The negative cache already exists, is already
persisted, and is already consulted three lines away in the network arm. The local arm should call the
same pair. This is what turns 254 attempts into one.

**A corrupt header and an honest panorama need the same fix.** Both produce a target that cannot be
allocated, and the app's answer to both is the same placeholder. Identifying the file is still worth
doing as evidence that the fix silences the log, but the design does not depend on the answer.

## 5. Acceptance criteria

1. Browsing the folder that produced the cluster logs zero `OOM allocating Bitmap` lines from the app's pid.
2. A thumbnail that exceeds the pixel budget shows the existing placeholder, not an empty cell.
3. The rejected file is attempted once per session at most, not once per rebind.
4. Ordinary images, including tall screenshots well inside the budget, still get thumbnails.
5. The clamp is covered by a unit test - the first automated coverage this path has.

---

## 3. Notes

Not release-blocking on its own - no crash, no ANR, the rest of the suite passed - but it is the one
actionable cluster the mandatory log audit found, so it is parked rather than waved through.

## Last Audit

**Date:** 2026-08-22
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Re-audited after the second fix. Same harness, same device, same folder as the run that produced the
cluster: full Maestro suite on emulator-5554, standard-debug `v2.60.8221.941`.

### What the first fix missed

The premise in §0/§1a - "an extreme-aspect **image**" - was wrong. Every OOM followed
`DCIM/S1060_mpeg2_test.mpg`, a **video**, and MediaStore reports `width=NULL, height=NULL` for it: Android
cannot measure that container at all. So the pixel budget could never have fired here on two counts - a
video is dispatched to `loadVideo`, not `loadLocalImage`, and `exceedsDecodeBudget` deliberately abstains
on unknown dimensions. Measuring the source was the wrong instrument; the target had to be bounded instead.

### What changed

- `loadLocalVideo` extracted from `loadVideo` (same reason `loadLocalImage` was extracted).
- `DownsampleStrategy.AT_MOST` on the local-video request, placed **after** `centerCrop()` because
  `centerCrop()` itself sets `CENTER_OUTSIDE` - the strategy that upscales to cover and so derives an
  unbounded height from a degenerate source. AT_MOST never upscales, so the decode cannot exceed the box
  whatever the source claims; CenterCrop still fills the cell.
- The same bound on `thumbnailErrorRequest`. This one was found by measurement, not by reading: with only
  the primary bounded, the count fell 318 -> 3, and those last 3 came from the unbounded fallback, which
  runs precisely when the primary already failed.
- The local-video arm now marks the negative cache on failure, which it never did.

### Evidence

- §5.1 PASS - `OOM allocating Bitmap`: **318 -> 0** over the full 22-flow suite. The file was genuinely
  exercised in that run (66 log mentions), so this is not a green that observed nothing.
  `prerelease-log-audit.ps1` exit **0**, `actionableCount: 0`, attribution `pid` - the run's single
  actionable cluster is gone. Extract: `PLAN/S1968_browse-thumbnail-bitmap-oom-on-extreme-aspect/EVIDENCE.md`.
- §5.2 PASS - the cell shows the generated `MPG` placeholder, not an empty cell:
  `PLAN/S1968_browse-thumbnail-bitmap-oom-on-extreme-aspect/mpg_placeholder.png`.
- §5.3 PASS - 0 extraction attempts and 45 cache skips across the suite; on a clean install
  2 attempts total, and the count did not grow across 6 scroll round-trips. Counts in `EVIDENCE.md`.
- §5.4 PASS - `S1060_hevc_test.mp4` shows a real frame in the same screenshot; Maestro 22/22.
- §5.5 PASS - `ThumbnailDecodeBudgetTest`, 7 tests.

### Note for later

The identical unbounded `centerCrop` shape exists on the local-image arm, where the budget abstains
whenever a header cannot be parsed. No occurrence has been observed there, so it is left alone rather than
changed speculatively - but that arm is where this would reappear.
