# Specification: OCR overlay accuracy - engine capabilities, plate geometry, colour, measurement

> Status: **Draft** - this is the opening contribution to a three-sided exchange. Nothing here has been
> measured on our own material yet; every number quoted from another project is marked as theirs.
> Date: 2026-08-15.
> **First round, 2026-08-15 (S10):** FastMediaSorter for Android enters an exchange that already ran five
> rounds between DOC2HTML (`doc-html-translate`) and FastMediaSorter Lite. This document answers the
> question their round 5 addressed outward, reports which of their rules reproduce here, and states what we
> cannot answer because we have no measurement apparatus at all.
> Origin: `P:\WINDOWS\EPUB_2_HTML\docs\ocr-pipeline.md`, `P:\WINDOWS\EPUB_2_HTML\docs\PARITY.md` (OCR section).
> Counterpart document: `P:\WINDOWS\FastMediaSorter_Lite\docs\specifications\SPECIFICATION_OCR_OVERLAY_ACCURACY.md`.
> **Language divergence, deliberate:** the counterpart document is written in Russian. This repository
> writes every artifact in English (`CLAUDE.md` section 1). The structure matches so the three documents
> can be read against each other; the language does not. Section numbering, the `§N.M` cross-reference
> form and the ISO date format are kept identical.
> Related documents: `docs/FLAVOR_MATRIX.md`, `docs/ALL_FEATURES.jsonl`, `docs/ARCHITECTURE.md`.

## 1. Why

An outside project measured OCR overlay quality for six weeks over a 46-scene corpus with hand annotation,
two independent implementations of one logic, and a laboratory that renders both and scores them against
ground truth. Every threshold they ship was derived from a measurement, and several were derived, measured
and then rejected. That body of work is portable in part. This document decides which part, states the
reason for every refusal, and records what we would have to build before any number in it becomes ours.

The result is a decision record, not a code dump. No behaviour changes with this document.

## 2. What our engine is and what it returns

The instruction that produced this document assumed our engine is ML Kit and that the whole
confidence-threshold construction therefore does not transfer. That assumption is wrong, and correcting it
changes roughly half the transfer verdict.

- **Our engine is Tesseract, the same engine they use.** `cz.adaptech:tesseract4android:4.8.0`, added per
  flavor at [build.gradle.kts:1530-1541](../app_v2/build.gradle.kts#L1530-L1541), driven through
  `TessBaseAPI` in [TesseractManager.kt](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt).
- **ML Kit text recognition is not in any build.** `com.google.mlkit:text-recognition` was removed
  outright by S0386 Phase 05, recorded at [build.gradle.kts:1513](../app_v2/build.gradle.kts#L1513). Only
  ML Kit *translate* and *language-id* remain. `docs/ALL_FEATURES.jsonl` still carries a record
  `ocr-translation.offline-ocr-engine-ml-kit` describing on-device ML Kit text recognition as active. That
  record is stale and is the first thing this exchange corrects.
- **A second engine exists on paper only.** `PaddleOcrEngine` runs detector, classifier and recognizer and
  then returns `emptyList()` from `postprocess`
  ([PaddleOcrEngine.kt:148-158](../app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt#L148-L158)).
  It is shipped in `noLegal` and `vr`, it downloads models, it spends the inference, and it always yields
  nothing, after which `OfflineOcrEngineProvider.recognizeTextBlocksWithFallback` silently falls back to
  Tesseract. Every observation below therefore describes Tesseract behaviour on every flavor.

What the engine gives us, verified against the artifact itself
(`tesseract4android-4.8.0.aar`, `classes.jar` symbol table) rather than from documentation:

- **Line confidence: yes.** We already read it - `iterator.confidence(RIL_TEXTLINE)`,
  [TesseractManager.kt:245](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L245).
  It is the same quantity, on the same 0..100 scale, from which their `50` and `80` were derived.
- **Word boxes and word confidence: available, and we never ask for them.** `RIL_WORD` and `RIL_SYMBOL`
  are both present in `TessBaseAPI$PageIteratorLevel`, alongside `getWords`, `wordConfidences` and
  `meanConfidence` on `TessBaseAPI`. Our iteration loop asks only for `RIL_TEXTLINE`
  ([TesseractManager.kt:238-254](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L238-L254)).
  This single omission is what makes both of their §16 defects unavoidable here - see §6.
- **Page segmentation mode: settable, and never set.** `setPageSegMode` / `getPageSegMode` exist and
  `PSM_AUTO`, `PSM_SPARSE_TEXT` are both in the enum. Neither appears anywhere in our sources. We run on
  the library default. The Tesseract C++ API default is `PSM_SINGLE_BLOCK` (6), not the CLI's `PSM_AUTO`
  (3) that they pin deliberately - **unverified for this wrapper**, and the check is one call to
  `getPageSegMode()` after `init`. Recorded here as a measurement to run, not as a fact.
- **Engine variables: settable, and never set.** `setVariable` exists. We declare no
  `user_defined_dpi`, so Tesseract falls back to its own resolution estimate, and we set no
  `thresholding_method`, so the whole binarization ladder is out of reach until one line is added.
- **Reading order, hyphenation, rotation: entirely the engine's.** We do no post-processing of any of
  them. Same answer as theirs.

Language routing has one property worth naming before anything else in this document is read.
`translationSourceLanguage` defaults to `"auto"`
([AppSettings.kt:127](../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt#L127)),
`mlKitToTesseractLang` maps `"auto"` to `"eng"` and maps every language outside `{ru, uk, bg, be, en}` to
`"eng"` as well
([RecognitionBackend.kt:41-51](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt#L41-L51)).
So the default path runs the **English** recognizer over whatever the user is looking at. Their §16.1
warns a third party that a confidence relaxation "must be tested on the wrong-language class, not on the
average". For us the wrong-language class is not an edge case - it is the default.

## 3. Coordinate space

Their invariant is that boxes live in the pixels a reader sees, and nothing else. We satisfy it in the
player, by construction rather than by design, and we satisfy it in the camera flow explicitly.

- Player: the bitmap handed to OCR is taken from the drawable already in the view
  ([PlayerImageTranslationManager.kt:75-91](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImageTranslationManager.kt#L75-L91)),
  which Glide has already oriented. There is no separate decode path that could disagree with the screen.
- Camera OCR: `CropRegionManager.loadOrientedBitmap` reads `TAG_ORIENTATION` and applies the full
  eight-case matrix before OCR
  ([CropRegionManager.kt:24-83](../app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CropRegionManager.kt#L24-L83)).
- We are ahead of them on one point they list as their own gap: they parse EXIF from JPEG only. We use
  `androidx.exifinterface`, which reads the `eXIf` chunk in PNG and in WebP as well.

The invariant nevertheless has a hole here, and it is ours alone - see §6.3.

## 4. Our pipeline as it stands

Stage names follow theirs where the stage exists, so the three documents can be read against each other.
Where a stage is absent it says so rather than being renumbered away.

### S1. Resolution estimate and staging - absent

No DPI estimate, no declaration, no upscale. `prepareBitmapForTesseract` only guarantees
`ARGB_8888`
([TesseractManager.kt:277-287](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L277-L287)).
There is one size guard, in a different layer and for a different purpose: any side above **2048 px** is
scaled down before OCR
([GoogleLensTranslationHelper.kt:28](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt#L28)),
to avoid OOM. That value is inherited, not derived.

### S2. Recognition pass

One pass, library-default PSM, no variables set. No rescue ladder, no grey pass, no halftone handling.

### S3. Lines

`RIL_TEXTLINE` only. Text, box and confidence are read per line; word boxes are never requested. Duplicate
and heavily overlapping lines are merged by `filterDuplicateAndOverlappingBlocks`
([TesseractManager.kt:310+](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L310)).

### S4. Filtering - four silent gates

Every surviving line passes four tests, all in
[RecognitionBackend.kt:157-171](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt#L157-L171):

- line confidence `>= 30`
- trimmed text length `>= 3`
- ratio of non-alphanumeric characters to letters `<= 0.5`
- box at least `20 x 10` px

None of the four is derived from any measurement. None of the four leaves a record: a rejected line is
`return@filter false` with no log, so a page where four lines were thrown away is indistinguishable from a
page where the engine found nothing. This is precisely the condition their §16.1 describes and solves.

The `30` is on the same scale as their `50`. It is not the same number reached differently - it is a
different number, lower, unexplained, on the same axis.

### S5. Clustering into plates - absent

There is no clustering step. One Tesseract text line becomes one plate. Pitch, leading, type size, plate
coverage and line fill are quantities we do not compute at all.

### S6. Plate geometry and colour

Drawn in [TranslationOverlayView.onDraw](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L468-L583):

- **The backing is a rounded rectangle on the plate box**, not a wrapper around the text run - the
  construction their §13.3 and §14.3 converged on independently. We already have the right shape.
- The rectangle is **not opaque**: alpha `240` of `255`, ~94 %
  ([TranslationOverlayView.kt:376](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L376)),
  and the default when sampling fails is `#F0FFFFFF`, the same 94 %.
- Font size targets `0.9` of the **line box height**, metric-corrected for the font's line height
  (`autoTextSizePx`, [lines 211-219](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L211-L219)).
  Their fit factor is `0.92` on the median line height of the block; the factor is comparable, the quantity
  it multiplies is not.
- The box may grow right and down to `2.7x` the source box, never shrink below it
  ([lines 534-541](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L534-L541)).
  Their equivalents are a `1.15x` grow cap with a release to `height:auto`, plus a `0.5x` shrink floor.
- **Colour is one pixel.** `sampleBackgroundColor` reads the single pixel at the box's top-left corner
  ([lines 364-387](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L364-L387)).
  There is no median, no mean, no ink sampling and no ring test. Text colour is not the source ink at all -
  it is black or white by a luma-128 threshold
  ([lines 393-404](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L393-L404)).

### S7. Diagnostics - absent, and worse than absent

`onDraw` unconditionally strokes a yellow rectangle plus a crosshair around the image display rect
([lines 569-582](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L569-L582)).
It is labelled `DEBUG` in a comment and gated by nothing. Every user who has ever used the Lens overlay has
seen it. Parked as a separate ticket; it is not an accuracy question.

## 5. Transfer verdict, rule by rule

Three verdicts, as instructed: **as-is**, **form only** (the shape transfers, the number must be
re-derived here), **not applicable** with a reason.

| Their rule | Verdict | Reason |
|---|---|---|
| Line confidence gate exists at all | as-is | Same engine, same scale, same quantity. We already have a gate. |
| The value `50` / `80` | form only | Their distribution is `tessdata` 4.0.0 with a pinned language set. We download `tessdata_fast` from `main` ([TesseractManager.kt:32](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L32)) and switch models by language. Same axis, different distribution. |
| The gate records what it rejected, through the same predicate | as-is | Nothing engine-specific. This is the highest-value single item in the whole exchange for us, because our four gates are all silent. |
| Grouping by pitch, not by ink gap | not applicable, for now | We do no grouping: one text line is one plate. It becomes applicable the moment we group, and their bracket (36 px leading recognized as 14-17 px boxes with 19-22 px gaps) explains why a gap rule would be wrong when we get there. |
| Reference pitch = image-wide median, same column, `<= 3` ink heights apart | not applicable, same reason | Depends on a clustering step we do not have. |
| Type size = **median of word heights**, not line box height | **adopted, S1711** | `OcrLineGeometry.typeSizePx` - median of the line's word heights, the lower of the two middle values on an even count. A line whose engine reported no words falls back to the box height, which is the behaviour that shipped before. |
| Word dropped from the line box when it has no letter/digit **and** exceeds the median word height by `TypeSizeRatio` | **adopted, S1711** | `OcrLineGeometry.isArtifactWord` joins both conditions with `&&`, and `tightenedBounds` rebuilds the box from the survivors. A line where every word is dropped keeps its original box. |
| `TypeSizeRatio = 1.6` | form only, adopted as `2.0` | `OcrLineGeometry.DEFAULT_MAX_HEIGHT_RATIO`, marked in code as inherited and not derived. We still have no corpus; **S1717** owns deriving it. Their bracket (1.42 widest legitimate spread, 1.86 narrowest legitimate step) is the starting evidence. |
| Coverage `0.52` **and** vertical line fill `0.72` to release an oversized plate | form only | The conjunction transfers as a shape. The numbers are bracketed against scenes we do not have and will never get - they state `accounts.jpg` will never ship. Also currently moot: a single Tesseract line rarely covers half a frame. |
| The **area** version of line fill | not applicable, refuted at source | They measured it and it separates nothing (0.5891 defect against 0.4582 legitimate). Recorded so we do not re-invent it. |
| Opaque backing on the plate rectangle, never a wrapper around the run | as-is | Their measurement: 17 % of source letters left visible against 93 % for the wrapper. We already draw the rectangle - but at 94 % alpha, which spends part of that win for nothing. Make it opaque. |
| Plate padding is load-bearing, not cosmetic | as-is | Their §14.7: `0.05em 0.15em` to `0.08em 0.28em` moved a scene from 0.2841 to 0.2705 with byte-identical rectangles. Our padding is `2..4sp` by box height ([line 500](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L500)), inherited. |
| Ink colour is a **median**, never a mean | **adopted, S1714** | `OverlayPlateColorSampler` takes both colours as per-channel medians over a decimated sample of the plate: paper over the whole sample, ink over the sampled pixels standing further than `INK_COLOR_DISTANCE_THRESHOLD` from paper. Below `MIN_INK_FRACTION` of the sample there is no ink median worth taking and a near-black or near-white ink replaces it. Those two numbers are ours and derived from nothing yet - `Carrier: S1717`. |
| Paper/ink orientation decided by a ring outside the block, `1/3` line height per side, floor 2 px, `>= 40` votes | **adopted, S1714**, plus a ceiling of our own | `OverlayPlateColorSampler.orientPair` votes with the ring and inherits all three numbers unchanged. The 16 px ceiling on the band is ours and is the one value in that ticket measured here - §13.2's free vertical band on `uniform-multiline-text`, above which the ring reads the neighbouring line's ink instead of paper. A pair that survives the vote still has to clear a 3:1 contrast floor (WCAG 2.1 SC 1.4.3, large text), or the sampled ink is replaced by a near-black or near-white one. |
| Display coordinate space only, EXIF applied before recognition | as-is, already satisfied | §3. Their JPEG-only limitation does not apply to us. |
| DPI declared, floor 70, upscale below 120 DPI | form only | `setVariable` is reachable, so the mechanism transfers unchanged. `11` inches as the assumed page is a book assumption; our inputs are photos, screenshots and comic pages, so the estimator itself needs re-thinking, not just its constant. That re-thinking landed in **S1876**: the scene width follows from EXIF subject distance and 35 mm-equivalent focal length by similar triangles, so a photo carrying both tags is computed rather than assumed (strategic §5.1). No new threshold enters here - the arithmetic replaces the assumed page, and the floor `70` is unchanged. A photo carrying neither tag still takes the floor, and which rule should serve it is carried by **S1716**. |
| Grey rescue ladder, strongest-rung-wins | form only | Every rung is reachable (`thresholding_method` via `setVariable`, `PSM_SPARSE_TEXT` in the enum). Worth having; but on a phone the cost of running four passes is not their desktop cost and has to be measured before it is shipped. |
| Halftone screen detection and Gaussian low-pass | not applicable, by input | Their inputs are scanned print, where a press screen is the norm. Ours are camera photos and screen captures. Revisit only if a real user log shows it. |
| Additive screen sweep | not applicable, follows the above | |
| Script detection via `--psm 0` OSD once per book | not applicable, no batch | We recognize one image on demand; there is no "book" to amortize the 0.43 s over. But their measurement of the detector's quality (right on a non-Latin script exactly twice, wrong at 3.81 and 5.00) is a reason not to reach for OSD, which is the more useful half. |
| `isTranslatable` - `>= 5` letters, a vowel, word-like share `0.5`, CJK bypass | form only | We have a cruder analogue: length `>= 3` plus a punctuation ratio. Theirs is better shaped and its CJK bypass is a real gap in ours. Their §15.4 lesson transfers with it: the vowel rule must not be applied to scripts that do not write vowels. |
| Runtime re-fit, shrink to `0.5x`, grow cap `1.15x`, release to auto height rather than clip | form only | We have a one-shot shrink at `0.6..1.0` and a `2.7x` growth cap and no release. The `1.15x` cap has a stated reason we share: a line box includes leading, so "fill the box" prints the translation larger than the words it covers. |
| `print-color-adjust: exact` | not applicable | Browser printing. We have no print path. |
| Lab annotation schema, `bounds` separate from `replaceArea` | as-is, if we ever build the lab | The separation is what lets a metric tell "covered its own text" from "painted over the drawing". Cheap to copy, impossible to retrofit. |
| "OCR output never becomes truth" | as-is | Their rule 1. Costless and load-bearing. |
| "No threshold outside a dated report" | as-is | Their rule 2. This document is the first dated report. |
| The concealment metric must distinguish "not measured" from "nothing visible" | as-is, pre-emptively | Their §16.5. We have no metric yet, so we can adopt the flag before the hole exists rather than after. |

## 6. Three defects that reproduce here

Their §16.2 and §16.3 predicted that two defects reproduce "by construction, if the code was written the
same way". Both do. The third is ours alone.

### 6.1 Type size is taken from the line box

`autoTextSizePx(scaledHeight - padding * 2)` is called with the height of the line's bounding box
([TranslationOverlayView.kt:508](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L508)).
A line box is the union of its words' boxes, so a single tall artifact - a balloon outline read as `|`, a
bubble contour, a stray stroke - sets the type size of the entire line. Their measurement on
`synth-adjacent-balloons`: `| NOT EVEN` boxed at 37 px against 13 px for the line below, a 2.85x step, from
an artifact.

We cannot even fall back the way they do, because their fallback ("use the line box when the engine
returns no word boxes") is our only mode: we never request `RIL_WORD`. The fix is one additional iteration
level plus a median. No constant moves.

Severity here is different from theirs. They lost a protected drawing region. We inflate the translated
text of the whole line to the height of the artifact, which is the most visible failure the overlay has.

**Fixed by S1711 (2026-08-16).** `TesseractManager` now walks the result at `RIL_WORD`, assembling one line
per `isAtFinalElement(RIL_TEXTLINE, RIL_WORD)` inside the pass that already ran - no second recognition. Each
line carries its words to `OcrTextBlock.words`, the type size comes from `OcrLineGeometry.typeSizePx`, and
`TranslationOverlayView.autoTextSizeSourcePx` uses it instead of the box height. The fallback described above
is still the behaviour when a recogniser reports no words - it is now the exception rather than our only
mode.

### 6.2 The same artifact stretches the box

The plate rectangle is `getBoundingRect(RIL_TEXTLINE)`, artifact included
([TesseractManager.kt:244](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L244)).
There is no word-drop rule of any kind. Their counter-intuitive result is worth carrying over verbatim:
fixing the grouping alone **increased** their damage from 148 px to 160 px, because the corrected plate
then spanned both lines and inherited the stretched box. Type size and box are two quantities and both
need the fix. Their two-condition test (no letter and no digit, **and** taller than the line's median word
height by more than the ratio) transfers unchanged, including the reason each condition alone is harmful.

**Fixed by S1711 (2026-08-16), together with §6.1 and for the reason stated there.** The plate rectangle is
now `OcrLineGeometry.tightenedBounds`, the union of the words that survive the two-condition predicate, and
the line keeps its original box when nothing survives. The ratio is `DEFAULT_MAX_HEIGHT_RATIO = 2.0`, marked
inherited; S1717 owns deriving it on our own material.

### 6.3 Colour is sampled from the wrong image

Ours, with no counterpart on their side.

`GoogleLensTranslationHelper` scales the bitmap down when a side exceeds 2048 px and runs OCR on the scaled
copy, so every returned box is in **scaled** coordinates. It then hands the **original** bitmap to the
overlay for colour sampling
([GoogleLensTranslationHelper.kt:61-90](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GoogleLensTranslationHelper.kt#L61-L90)),
where `sampleBackgroundColor` indexes it with the scaled box's `left`/`top`
([TranslationOverlayView.kt:369-373](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L369-L373)).
On any image above 2048 px on a side - which is every modern phone photo - the plate's colour is read from
a point that is not under the plate. The coordinates are merely clamped into range, so it never throws and
never logs.

Their §11.2 lesson applies exactly: a defect where every plate sat on a 1.9x-wrong picture passed their
gate at **0 px drift**, because drift was the only thing measured. A colour bug of this shape is invisible
to any positional metric.

## 7. What we do not have, and what we will not port

Stated plainly so it is not looked for later:

- **No corpus, no annotation, no lab, no metric, no acceptance bound.** There are seven test files touching
  OCR or translation in this repository and every one of them tests routing, language mapping or a font
  enum. Nothing tests geometry, colour or concealment. Every number in §4 is inherited or invented; not one
  is derived.
- **No vertical writing.** Same as them: the plate is a horizontal rectangle and the assumption is wrong by
  construction for vertical script.
- **No rotation handling** inside the image. Same as them.
- **No reading order, no de-hyphenation.** Entirely the engine's, same as them.
- **Halftone and press-screen work is refused on input grounds**, not on cost grounds - see §5.
- **PaddleOCR is not a second implementation.** Until `postprocess` is written it is not a parity partner,
  it is a stub that spends inference. Two implementations of one logic is what makes their parity table
  possible; we have one implementation and one placeholder.
- **A memory ceiling in pixels will be ours to derive.** Their tab crash produced a ceiling; on a phone the
  constraint is harder and per-device. Our `2048` is a guess in the right units.

## 8. What transfers as discipline rather than code

Four items, all of which cost nothing and none of which are engine-specific.

- **A constant is derived from two measurements taken from opposite sides, and set between them.** Not
  "tuned until the scene passed". Their `TypeSizeRatio` names the widest legitimate spread (1.42) and the
  narrowest legitimate step (1.86) and takes the geometric middle, with the margin stated as a percentage.
  Every one of our four filter values in §4/S4 fails this test today.
- **A decision the app takes silently must leave a record.** Their instrument writes the rejected line's
  text, its confidence, its box and *which* threshold it failed, through the **same predicate** the
  pipeline applies rather than a second copy of the condition, and it writes it **also for a page that
  produced no plates** - the case it exists for. Until such a record exists, "the engine found nothing" and
  "we threw four lines away" look identical in a bug report. This is the single change with the best ratio
  of cost to information for us.
- **A metric must distinguish "not measured" from "nothing visible".** Their scorer defaulted an unrendered
  scene's residual ink to `0`, the same value as perfect concealment, and every scene where recognition
  found nothing scored flawless. The aggregate read 0.2705 against a 0.28 bound; the honest number was
  0.9992. We can adopt the flag before we have the metric.
- **A negative result ships alongside a positive one.** Their most recent round implemented a rule that
  followed from a measured distribution, ran it over the corpus, and **rolled it back** because the entire
  difference was one scene where the wrong recognition language produced a 782x310 px plate of
  transliterated debris. The ticket stayed `Partial` rather than being closed. Given §2's finding that our
  default recognition language is `eng` for every script, that specific failure mode is not hypothetical
  here.

## 9. What we would need before any number in this document is ours

In dependency order, cheapest first. This is a list, not a plan; nothing here is scheduled.

1. The discard record of §8. It is the instrument, and everything after it is measured with it.
2. Word-level iteration (`RIL_WORD`). It unblocks §6.1 and §6.2 and it is a precondition for measuring
   anything about type size.
3. A read of `getPageSegMode()` after `init`, to learn which mode we have actually been shipping.
4. A corpus. Theirs is 46 scenes, 13 annotated, 8 of them synthetic with pixel-exact truth - and they say
   in writing that it has no holdout and that no number derived from it is settled. Ours should start with
   the synthetic half, because synthetic scenes carry exact ground truth for free and need no licence
   verification.
5. Only then, a bound.

## 10. Round 1 (2026-08-15): what we contribute, and what we ask

Our opening entry in the exchange. Numbers below are ours and are measured on our own sources; where a
number is quoted from another project it is named as theirs.

**What we contribute.**

- Confirmation that both defects handed over in their §16.2 and §16.3 reproduce in a third, independent
  implementation, on a different platform, in a different language, against the same engine - and the
  mechanism is identical in all three: the line box is the union of the words' boxes. That raises their
  finding from "two implementations wrote it the same way" to something closer to a property of the
  interface Tesseract offers.
- One stronger form of the same finding: an implementation that never requests word boxes has no fallback
  and no partial mitigation. Their rule degrades gracefully ("fall back to the line box when the engine
  returns no word boxes"). An implementation in our shape is permanently in the degraded case without ever
  noticing, because the degraded case is indistinguishable from the normal one from inside.
- A defect class neither side has: **the colour sampled from a different image than the boxes were
  measured in** (§6.3). It is invisible to a positional metric, invisible to a concealment metric that
  compares the rendered result against the source (the plate is opaque either way), and only visible to a
  metric that checks the plate's colour against the region it covers. If either side's lab scores colour,
  it is worth checking that the scored image and the recognized image are the same object.
- An answer to their §16.1 question, addressed to whichever side reads this: our confidence gate is `30`,
  it is undocumented, it is unmeasured, and it sits below both of their numbers on the same scale. We do
  not propose it as evidence for anything. We name it so that nobody quotes it later as a third data point.

**What we ask.**

- Their `TypeSizeRatio` bracket was measured on print and comic material. Does either side have a
  measurement on **screenshots** - UI text at one type size, tight leading, no leading variation at all?
  That is a large share of our input, and it is the shape where a type-size rule has the least to work
  with and the most to break.
- Their `0.72` line-fill bound is flagged in their own document as held by a **single** scene. Has it moved
  since? We would rather adopt it after that recomputation than before, and we would rather not brackets it
  ourselves on a corpus that does not exist yet.
- Is there a measurement of what **opacity** costs? We ship 94 % alpha; their construction is fully opaque.
  Their 17 %-against-93 % measurement compares rectangle against wrapper, both opaque. The residual ink a
  6 %-transparent rectangle leaves is a number neither side seems to have, and it is the cheapest single
  correction available to us.

## 11. The work this document produced

Every verdict in §5 and every defect in §6 that needs code is a ticket. All ten landed in release package
33, the current next release, by the catalog's own reconciliation - the owner reorders or repackages them
from `PLAN/RELEASE_QUEUE.md`, never from here. Listed in dependency order, which is not the queue's order:

- **S1711** `ocr-word-level-geometry` - request word boxes, take type size from the median of word heights,
  drop the artifact word from the line box. Fixes §6.1 and §6.2, deliberately in one ticket because their
  measurement shows fixing either alone makes the result worse.
- **S1712** `ocr-discard-record` - the instrument of §8, second bullet. One predicate, two readers; written
  also for a page with zero plates; influences nothing.
- **S1704** `bugfix-overlay-plate-colour-sampled-from-wrong-bitmap` - §6.3, ours alone.
- **S1713** `ocr-plate-opaque-backing` - opacity, padding as a load-bearing quantity, growth behaviour.
- **S1714** `ocr-plate-colour-sampling` - median paper, median ink, ring test, contrast floor. Blocked by
  S1704: improving the method while the coordinates are wrong measures nothing.
- **S1715** `ocr-engine-configuration` - read what segmentation mode we have been shipping, then set it;
  declare the resolution. The rescue ladder becomes reachable here and is deliberately not taken.
- **S1716** `ocr-accuracy-corpus-and-harness` - synthetic scenes first, annotation that separates "where the
  text is" from "where a plate may paint", metrics that distinguish "not measured" from "nothing visible".
- **S1717** `ocr-filter-thresholds-derived` - derive the four gates or mark them honestly. Blocked by S1712
  and S1716. May legitimately close without changing a single number.

Two more tickets came out of the same reading and are not accuracy work: **S1702** (a yellow debug frame
shipped in release) and **S1703** (the PaddleOCR stub of §2).

Deliberately not ticketed, with the reason, so it is not looked for later: the grey rescue ladder. Every
rung is reachable through `setVariable` and the PSM enum, and it is the single largest recall win they
measured - but four passes instead of one is a phone-battery decision taken on a number, and that number
does not exist yet. It waits for S1716.

## 12. Round 2 (2026-08-25): S1717 Derived vs Inherited Thresholds & Translatability Filtering

The second round of the accuracy exchange reviews the four OCR discard thresholds in `OcrBlockFilter` (`MIN_CONFIDENCE = 30f`, `MIN_TEXT_LENGTH = 3`, `MAX_SPECIAL_TO_LETTER_RATIO = 0.5f`, `MIN_BOX_WIDTH = 20`, `MIN_BOX_HEIGHT = 10`) and reformulates the translatability filter.

### 12.1 Threshold derivation status

1. **`MIN_CONFIDENCE = 30f`**: **[INHERITED]**. Remains inherited from the legacy pipeline. Derivation on empirical material is deferred until the S1716 accuracy corpus and harness are operational.
2. **`MIN_TEXT_LENGTH = 3`**: **[INHERITED / REFORMULATED]**. Inherited for alphabetic text fragments. Reformulated for CJK/Ideographic text (Hanzi, Kanji, Kana, Hangul), allowing single and double character fragments (e.g. "出口", "止") since short CJK signs are valid words.
3. **`MAX_SPECIAL_TO_LETTER_RATIO = 0.5f`**: **[INHERITED / REFORMULATED]**. Integrated into the text-property translatability check `isTranslatableText`.
4. **`MIN_BOX_WIDTH = 20`, `MIN_BOX_HEIGHT = 10`**: **[INHERITED - measured, not derivable yet]**. Absolute pixel bounds remain inherited. S2036 built the measurement and ran it: `PLAN/S2036_ocr-line-height-relation-from-corpus/reports/2026-08-26__height-relation-report.md`. On every scene the corpus holds, the thresholds are 0.4167 and 0.2083 of the median annotated line height - and the spread of that fraction across scenes, which is the only thing that could decide ADR-3, is reported as not measured because all six scenes are 800x600. An absolute pixel bound is only wrong in that it fails to scale, so one resolution cannot show it failing. See ADR-3 below.
5. **`DEFAULT_MAX_HEIGHT_RATIO = 2.0f`** (in `OcrLineGeometry`, the artifact-rejection multiplier): **[INHERITED - lower bound measured]**. Same report. On the one scene annotated at word level the tallest real word reaches **1.43x** its line's median word height, so 2.0 does not currently drop real text on our material. That is a floor, not a derivation: rejection fires only on tokens carrying no letter or digit, and annotated ground truth holds real text by construction, so nothing in this corpus says how far **below** 2.0 the multiplier could go before it stops catching an artifact. Deriving the upper bound needs annotated artifacts, which the annotation format does not carry.

**ADR-3 (line-height relative box thresholds) - base fixed, verdict not yet reachable (S2036, 2026-08-26).**
No longer waiting on the S1716 corpus: those measurements exist and are cited above. Two of the three parts
are settled and the third is blocked on material, not on tooling.

- **The base is decided, and it came from the code rather than from the report.** If the thresholds become
  relative, they are relative to the **median annotated line height of the scene**. Not to the checked box's
  own height, which would compare a value with itself; and not to the type size, because the type size is
  the median of word heights produced by `OcrLineGeometry` *after* `OcrBlockFilter` has already run. Making
  the filter read the type size would mean swapping the two, which is a pipeline change and belongs to its
  own ticket, not to a threshold edit.
- **The verdict is not reachable on the corpus as it stands.** Every scene it holds is 800x600, so the
  fraction is identical on all six and its spread is zero by construction. The harness now refuses to print
  that zero, because "the fraction is stable, so relativity buys nothing" is exactly the conclusion a reader
  would take from it, and no one measured it.
- **What would settle it:** two or more registered real scenes at genuinely different resolutions. Then the
  spread is a finding either way - a stable fraction rejects ADR-3 with a number, a spreading one accepts it
  with the same number. `Carrier: S2065` - the corpus run is now a repeatable command, so settling this is
  registering material and re-running, not building anything further.

### 12.2 Text Translatability Reformulation

The translatability check is rewritten from a crude non-alphanumeric ratio to explicit text property analysis:
- **CJK / Ideographic bypass**: Scripts containing ideographs, Hiragana, Katakana, or Hangul bypass both the vowel requirement and the minimum length requirement.
- **Abjad script exemption**: Scripts that do not write vowels (Arabic, Hebrew) bypass the vowel check.
- **Vowel requirement for alphabetic scripts**: Text fragments in Latin, Cyrillic, or Greek with length >= 3 require at least one vowel character; vowel-less letter sequences (e.g., "bcdfgh", "бвгджз") fail translatability and report `Verdict.TOO_MANY_SPECIAL_CHARS`.


## 13. Round 3 (2026-08-25): S1716 rectangle corpus - the first bounds, and what they are not

The accuracy corpus S1716 promised is operational. It scores four axes on five deterministic synthetic
scenes without rasterising anything, because on this host nothing can be rasterised: Robolectric's legacy
graphics records draw calls instead of executing them, and its native graphics runtime ships no Windows
binary before 4.16.1. The pixel axis therefore did not get weaker - it left, to S1782, with the upgrade it
really costs.

Everything below comes from one report and names it. No number in this section exists outside it.

### 13.1 First acceptance bounds

Source report: `PLAN/S1716_ocr-accuracy-corpus-and-harness/reports/2026-08-25__overlay-rectangle-report.md`, build 2.60.8241.413-DEBUG
(standard debug), five synthetic scenes, zero real scenes registered.

1. **Plate-to-text overlap - floor 1.0000.** Measured 1.0000 worst and 1.0000 median over 5 of 5 scenes.
   A plate that does not fully cover its own annotated line is a regression, and today none does.
2. **Plate spill outside paintable area - ceiling 0.5433.** Measured 0.5433 worst (`uniform-multiline-text`)
   and 0.5037 median over 5 of 5 scenes. This is a **record of today's behaviour, not a target**: at a 1.6x
   translation more than half the plate area lands where the annotation forbids painting. It is the ceiling
   a later change must not exceed, and the number the plate-growth work should be judged against.
3. **Run duration - ceiling 2.0 ms per scene, from the median 4400 ns.** Worst measured 1094200 ns on the
   first scene of the run and 2300-6000 ns on the other four, so the worst figure is JVM warm-up rather than
   geometry. A bound taken from that worst value would measure the JIT; the median is what the arithmetic
   costs.
4. **Annotated text found - not established.** No recogniser is in this loop, so the corpus cannot measure
   recall at all. It is reported Unmeasured on all 5 scenes rather than as the 100 % that feeding a scene its
   own annotation would arithmetically produce.
5. **Source-ink concealment at any opacity - not established, and not establishable here.** It needs a
   rasterised composition of plate over source. Owned by **S1782**. The 94 %-alpha question this document
   raised in §10 stays open there; it is not a zero and not a pass.

### 13.2 The two rectangle research questions this corpus was asked to answer

**Band width around a line on a dense screenshot (S1714, carried as S1716 §6.4) - answered.** On the
`uniform-multiline-text` scene, four lines of 380x48 at a 64 px step:

- The free vertical band between lines is **16 px**. A colour-sampling ring taller than that reads the
  neighbouring line's ink rather than paper, which is the failure S1714 was opened for.
- The band a plate is **allowed** to paint is **0 px**: the annotation declares paintable exactly the line
  box. Sampling and painting are different permissions on the same pixels, and only the annotation's
  separation of "where text is" from "where a plate may paint" makes that difference expressible.
- 340 px of untouched paper remain to the right of the line. It is legitimate to sample colour there and
  illegitimate to paint there.
- Today's growth consumes the whole gap: at 1.6x the plate extends 244 px right and 16 px down, exactly the
  inter-line distance.

**Relation of heights on our material (S1711, carried as S1716 §6.5) - not answered, and stated as such.**
This corpus scores plate rectangles. The translated extent it feeds the geometry uses the source box's own
height by construction, so plate height never grows past the source and no height relation can come out of
the report - overlap is 1.0 everywhere and spill is driven entirely by width. The annotation format also
carries line boxes only, while the inherited number is the median of **word** heights, so there is nothing to
compare against. Answering it means extending the harness, not re-reading this report. Carried by **S2036**,
which also owns ADR-3 of §12.1 - whether `MIN_BOX_WIDTH` and `MIN_BOX_HEIGHT` should become line-height
relative.

### 13.3 How to reproduce

`pwsh -NoProfile -File scripts/ocrbench/run-corpus.ps1` - runs the corpus and prints the path of the dated
report it wrote. Real scenes are registered through `scripts/ocrbench/fetch-real-scenes.ps1`; their media
never enter this repository, their annotation always does.


## 14. Round 4 (2026-08-26): S2036 word-level geometry - two relations, and one number that refused to exist

Round 3 closed with the height relation unanswered and named the reason: the annotation carried line boxes
only, while the inherited multiplier is defined against the median of **word** heights. S2036 extended the
ground truth to the word, added the measurement, and ran it.

Report: `PLAN/S2036_ocr-line-height-relation-from-corpus/reports/2026-08-26__height-relation-report.md`
(6 scenes, 0 of them real, build 2.60.8260.551-DEBUG standard debug).

### 14.1 There are two relations, not one

This is the round's main contribution, and it is a correction rather than a measurement. Both this document
and S1716 called two different quantities "the inherited height ratio". They are:

- **line-to-word** - the line box height over the median word height of that line. It says how much taller a
  line box is than its own letters. This is the quantity an absolute pixel threshold has to be expressed
  against before it can be called relative, and it is what §13.2's open question was really asking about.
- **word-to-median** - the tallest word of a line over that line's median word. This is the empirical floor
  under `DEFAULT_MAX_HEIGHT_RATIO`: set the multiplier below what real text reaches and real text is dropped.

They are derived from different quantities and consumed at different points of the pipeline, so the corpus
reports them as two separate axes and aggregates them differently on purpose - line-to-word by the median
across lines, because it describes typical material; word-to-median by the **maximum**, because a floor is
set by the worst line and taking its median would understate exactly the case the multiplier must survive.

### 14.2 What the numbers are

On the one scene annotated at word level: line-to-word **1.71**, word-to-median **1.43**. Five scenes report
both axes unmeasured with the reason "no text area carries word-level geometry" - the version-1 annotations
predate word geometry and are deliberately not backfilled, because the cheap way to backfill is a
recogniser's own output and scoring a recogniser against itself measures nothing.

The thresholds of §12.1 come out as 0.4167 (`MIN_BOX_WIDTH`) and 0.2083 (`MIN_BOX_HEIGHT`) of the median line
height, identically on all six scenes, because all six are 800x600.

### 14.3 The number that refused to exist, and why that is the finding

The spread of that fraction across scenes is the only thing that can decide ADR-3, and the harness reports it
as **not measured** rather than as 0. An absolute pixel bound is wrong only in that it fails to scale, so one
resolution cannot show it failing; a printed zero would have been read as "the fraction is stable, so
relativity buys nothing", which is a conclusion nobody measured. The refusal is in code, not in a caveat
sentence: the spread is gated on two distinct resolutions, not on two scenes.

What this costs to fix is material, not tooling - see `Carrier: S2065`.

### 14.4 What this corpus still cannot say

Only a **lower** bound on `DEFAULT_MAX_HEIGHT_RATIO`. Rejection fires on tokens carrying no letter or digit,
and annotated ground truth holds real text by construction, so the corpus can say the multiplier must stay
above 1.43 on our material and can say nothing about how far below 2.0 it could go while still catching an
artifact. That needs annotated artifacts - a "this box is not text" mark the format does not carry. The word
level was left open to it, and no ticket claims it yet.

### 14.5 How to reproduce

Unchanged from §13.3 - the same one command. It now also prints both new axes and the fraction spread; it
used to filter its echo through a hardcoded list of four axis names, which would have shown neither of the
axes this round added while still exiting 0.


## 15. Round 5 (2026-09-12): a rule arrives that does not fit our shape, and what that says

The neighbouring project handed over a two-part rule against its worst overlay failure: an image with text
in two separated regions - two speech balloons either side of a figure, two columns, a caption and a margin
note - produces one plate that is a bar across the whole picture carrying both texts run into one sentence,
and a translator then translates the run-on as one sentence, so the damage survives into every language.
Their rule cuts the recognizer line between two consecutive words standing more than `MAX_WORD_GAP_RATIO`
median word heights apart, then regroups the resulting runs into columns to restore reading order. The
constant is `3.5`, bracketed over 46 corpus scenes plus the reported image on the 199 multi-word lines that
cleared their confidence floor (tesseract.js 7, PSM 3, `eng`): widest gap inside a line that really is one
line `2.57x`, narrowest cross-region stitch `4.80x`, and ratios `1.87-2.57x` declared undecidable by
geometry - comic balloons drawn side by side stitch there while real lines reach into it.

**The port was refused and the reason is structural.** It is recorded here rather than in a commit message
because the refusal is a finding about the three pipelines, not about one change.

### 15.1 Half the rule has no host here

Their part 2 - regroup the cut runs into columns and emit column by column - exists because cutting alone
makes a single-pass clustering worse: the runs interleave left, right, left, right down the page and the
clustering closes its open plate on the first line that does not belong to it, measured as one balloon going
from 1 oversized bar to 3 fragments. Both traps they paid a cycle for - the scope being the page rather than
the recognizer paragraph, and a floor-failing full-page "line" chaining two real columns into one - are
properties of that same clustering.

We have no clustering. §4 S5 has said so since round 1, and the code agrees:
`filterDuplicateAndOverlappingBlocks` in `TesseractManager` is a deduplicator keyed on text similarity and
box overlap, it groups nothing geometrically, and `TranslationOverlayView.onDraw` walks `translatedBlocks`
drawing one rounded rectangle per block. There is no pass, no open plate and no state between lines; the order blocks arrive in cannot
affect what is drawn. Their part 2 asks that the clustering's own x-overlap test be reused rather than
re-invented, and here there is nothing to reuse. Adopting it would mean writing the second overlap test
their own text forbids.

### 15.2 None of the three arguments for "nothing downstream can recover" applies

They name three rules that look like they should catch the stitched bar and explain why each fails, and they
ask a porter to check which exist before accepting the argument. Checked, 2026-09-12:

- a column or x-overlap test on the clustering: **absent**, see §15.1;
- a plate-coverage or oversize-release rule: **absent**. The nearest quantity is the `2.7x` growth cap on
  the plate against its own source box; no fraction of the frame is computed anywhere;
- a pitch or type-size comparison against neighbouring lines: **absent**. `OcrLineGeometry.typeSizePx`
  compares words inside one line and never looks at another line.

The conclusion survives the check but by a shorter route than theirs. On their side the stitched line defeats
three recovery rules; on ours it becomes a plate immediately, because one recognizer line is one plate. The
defect is the same and the argument for repairing it before the plate is formed is stronger here, not weaker.

### 15.3 Their constant is not adoptable, and the reason is a hole of ours

`3.5` was bracketed at PSM 3, which is where the cross-page layout walk that produces the stitch lives.
**We do not know what page segmentation mode we ship.** `PageSegMode`, `PSM_` and `setVariable` return zero
matches across `app_v2/src`. `getPageSegMode()` is present on the artefact - verified against
`tesseract4android-4.8.0.aar`, `classes.jar`, `public int getPageSegMode()`, and all fourteen `PSM_*`
constants are on `TessBaseAPI$PageSegMode` - so reading it costs one call, and nothing in this project has
ever made it.

Round 1 §9 listed that read as item 3 of five and S1715 was opened for it. S1715 is `Archived` and its spec
file is gone from the tree, but it left two comments in `TesseractManager.init` - "S1715 pillar 1: read the
mode we have been recognising in before anything sets it" on the best-model path, and "same reading on the
fallback path" on the other - **above log statements that print the language and not the mode**. The reading
was described, sited on both init paths, and never written. A comment claiming a measurement is worse than
the absence it replaces: it answers the question for the next reader, and the answer is not there. This is
the second time this exchange has recorded a defect of that shape - §13.1 has the corpus refusing to print a
zero that would have read as a measurement - and it is worth naming as a class, because both sides now have
one.

### 15.4 What this round changes

`S3039` carries the work, in dependency order, and nothing below it starts before the step above it reports.

1. The mode is read on both init paths and printed at `Timber.i` with the engine's own enum name beside the
   integer. Landed with this round; the value itself is a device reading and is **not yet in this document**.
2. A dump of real recognizer output on an image with two separated regions: for every line of two or more
   words, `max gap between consecutive word boxes / median word height` together with the line's confidence.
   It is taken through the app on a device, because this workstation has no `tesseract` on `PATH` and the
   `scripts/ocrbench` corpus holds no recognizer in its loop (§13.1) - six synthetic 800x600 scenes cannot
   produce this distribution.
3. Only then the cut itself, in `domain/ocr/` ahead of `OcrBlockFilter`, with the three expressions that
   carry the rule's meaning kept intact: the gap measured **between boxes** (`max(next.left - prev.right,
   prev.left - next.right)`, or a right-to-left line yields a negative gap on every pair and never cuts),
   weighed against the **median word height** rather than the line box (the box is the union of its words, the
   same reason §6.1 exists), and each run boxed to **its own** words with its own mean confidence. A line that
   is not cut returns unchanged by identity.

### 15.5 What we hand back

- **A third pipeline shape.** Their argument that the repair belongs before the clustering was derived
  against a pipeline that has one. It holds in a pipeline that has none, and for a simpler reason: with one
  line per plate there is no stage between the stitch and the artwork at all. Whoever ports this next can
  check §15.2's three absences quickly and skip part 2 outright if they come out the same way.
- **The comment that claims a reading nobody took.** Both of our projects gate thresholds on measurements;
  neither gates a *claim* of a measurement. A grep for the ticket id found the comments instantly; nothing
  found that the call beside them was missing, because nothing looks for it.
- **An open question on the constant's dependence on PSM.** If either side has the ratio distribution at a
  mode other than 3, it decides whether `3.5` is a property of the material or of the layout analysis. We
  will have exactly one such reading shortly and one reading is not a distribution.

### 15.6 The dump instrument, 2026-09-13

Item 2 of §15.4 now has its instrument and still has no reading.

- `OcrLineGap.measure` in `domain/ocr/` returns, for a line of two or more words, the widest gap between
  consecutive word boxes, the median word height and their ratio. It carries no threshold, so it does not
  breach rule 2 of the exchange.
- `RecognitionBackend` prints one `OCR line gap: ratio=.. gap=.. median=.. words=.. conf=..` line per such
  line, at `Timber.i`, **before** `OcrBlockFilter`, so a line the filter later drops is still in the dump.
  Recognised text is not printed: it is the content of the user's picture, the same constraint S1712 wrote.
- The S1712 discard channel could not serve as the dump. `OcrDiscardRecorder.setEnabled` has no caller in
  the tree, so the channel is off in every build, and it records rejected fragments only - an accepted
  stitched line, the defect itself, never reaches it.

What is still owed, unchanged in order: the mode reading from a device log, then the dump on material that
holds at least one stitching scene and one honest multi-word scene, then the bracket, then the cut.

## 16. Round 6 - the gap dump and the constant, 2026-09-13

§15.3 said a dump could only come from a device. It could not have been more wrong about the workstation:
`C:\Program Files\Tesseract-OCR\tesseract.exe` is installed, v5.4.0, and its `eng.traineddata` is 4113088 bytes,
byte-for-byte the size of the `tessdata_fast` model the app downloads. The dump below is that engine on that
model; the device reading of §15.4 item 1 stays owed as confirmation, not as a precondition.

### 16.1 The mode we ship is PSM 6, not PSM 3

- `tesseract --print-parameters` reports `tessedit_pageseg_mode 6` as the engine default. The command-line tool
  overrides it to 3; the API does not.
- `tesseract4android-4.8.0.aar`, `javap -c` on `TessBaseAPI`: none of the three `init` overloads invokes
  `nativeSetPageSegMode`, and nothing in `app_v2/src` calls `setPageSegMode`.
- So the app recognises in `PSM_SINGLE_BLOCK`. The neighbours bracketed `3.5` at PSM 3. Both modes were dumped.

### 16.2 Material

- The owner's scene `doc-html-translate/test_doc/1.png`, 2048x2048: ten speech bubbles on both sides of a
  standing figure, several pairs at the same height. `GoogleLensTranslationHelper.maxOcrDimension` is 2048, so
  it reaches the engine unscaled; a 1080x1080 copy was dumped too, for a player that decodes smaller.
- From the same folder: a comic page, 800x1091, with bubbles drawn side by side and bullet-separated title
  lists; a desktop settings dialog, 1226x882; a family-account list screenshot, 640x563, names left and roles
  right. A personal document in that folder was not used.
- Raw dump: `temp/S3039/dump-2026-09-13.txt`; the script mirrors `OcrLineGap.measure` exactly.

### 16.3 What the dump shows

- **At PSM 6 a photograph stitches almost every line.** On the owner's scene 28 of 34 multi-word lines carry a
  gap of 7.00-271.60: a bubble joined to the other side of the figure, or to junk glyphs read off the shirt and
  the wall. The six clean lines stay at or below 0.57. The account list stitches every name to its role at
  13.00-16.83; the dialog stitches a list cell to the next column at 11.56 and two buttons at 6.38.
- **At PSM 3 the same scene stitches five lines**, 36.43-99.86, and its honest lines reach 1.36.
- **Honest lines, widest:** 3.33 at full resolution (a comic heading whose bullet was lost), 3.23 at PSM 6 on the
  1080 copy, and 5.50 at PSM 3 on the 1080 copy - an eight-pixel median, where one misread space moves the
  ratio by a whole unit.
- **Stitches, narrowest:** 2.33 and 3.67 on the comic at PSM 3, both between speech bubbles drawn side by side;
  6.38 on the dialog; 7.00 on the owner's scene.
- **The unseparable band is 2.33-5.50.** Bubbles touching each other stitch below honest lines at low
  resolution. The neighbours' band was 1.87-2.57 - the same kind of geometry, measured wider here because our
  material holds a downscaled copy and theirs did not.
- **A cut list is not a harmful cut.** The comic's bullet lists (5.43-5.86) are honest single printed lines
  whose parts are separate titles; cutting them yields one plate per title.

### 16.4 The constant

`OcrLineSplitter.MAX_WORD_GAP_RATIO = 3.5`. It sits between the widest honest gap at full resolution (3.33) and
the narrowest region-to-region stitch above it (3.67), and every PSM 6 honest line of all four scenes, at both
resolutions, stays below it. It coincides with the neighbours' value; that agreement is a result, not the source.

What it buys and what it costs:

- Every PSM 6 stitch in the dump is cut, which at our default mode is almost every stitch there is.
- The 2.33 side-by-side stitch is not cut. No value can cut it without cutting honest lines.
- On a downscaled copy at PSM 3 one honest line (5.50) would be cut into two plates. Both pieces are longer than
  `OcrBlockFilter.MIN_TEXT_LENGTH` and are translated; the sentence is split, not lost. PSM 3 is not what we ship.

### 16.5 Where the cut sits

`RecognitionBackend.recognizeAndTranslateBlocks`: after the recogniser's lines and the gap log, before
`OcrBlockFilter`, so each piece faces the filter alone - a junk glyph cut off a real line now fails it instead of
widening the real line's plate. Each piece is boxed to its own words with the mean of its own word confidences.

### 16.6 What we hand back

- **The mode question has an answer, and it changes the size of the problem.** A consumer of the Tesseract API
  who never sets the mode runs PSM 6, where stitching on a photograph is the rule, not the edge case §15 took it
  for. Whoever else wraps the API should read the mode before deciding the defect is rare.
- **The constant survived a mode it was not bracketed at.** At PSM 6 honest lines sit far below 3.5 and stitches
  far above; the tight part of the bracket is PSM 3 in both projects.
- **Resolution widens the band.** Below roughly ten pixels of type size a single misread space costs a whole unit
  of ratio. A bracket taken only at native resolution overstates the separable range.

## 17. Round 7 - source-resolution player OCR, 2026-09-14

The 2048 px guard in `GoogleLensTranslationHelper` was not sufficient when the player had already decoded an
image at display size. With full-size image loading disabled, both direct image OCR and translated image OCR took
their bitmap from the player drawable; the owner's 2048x2048 `1.png` therefore reached the recogniser as
810x810. That erased the speech-bubble text before the line-gap measurement or splitter could act.

S3088 adds `OcrInputBitmapLoader` before both image OCR entry points. For local files and content URIs it decodes
the source with orientation applied, requests a software bitmap for native OCR, and constrains only images whose
longest side exceeds 2048 px. Remote sources that cannot be resolved locally retain the displayed bitmap as an
explicit fallback. The device handoff must confirm the `S3088: OCR input 2048x2048` probe before re-running
S3039's ten-bubble acceptance scene.
