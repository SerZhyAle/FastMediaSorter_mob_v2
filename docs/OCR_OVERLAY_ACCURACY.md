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
| Type size = **median of word heights**, not line box height | **as-is, and it is a live defect** | See §6.1. `RIL_WORD` is available; nothing blocks it. |
| Word dropped from the line box when it has no letter/digit **and** exceeds the median word height by `TypeSizeRatio` | **as-is, and it is a live defect** | See §6.2. Both conditions transfer together; their §16.3 states what each alone breaks. |
| `TypeSizeRatio = 1.6` | form only | Bracketed on their corpus (1.42 widest legitimate spread, 1.86 narrowest legitimate step). We have no corpus. Adopt as a starting value, marked inherited. |
| Coverage `0.52` **and** vertical line fill `0.72` to release an oversized plate | form only | The conjunction transfers as a shape. The numbers are bracketed against scenes we do not have and will never get - they state `accounts.jpg` will never ship. Also currently moot: a single Tesseract line rarely covers half a frame. |
| The **area** version of line fill | not applicable, refuted at source | They measured it and it separates nothing (0.5891 defect against 0.4582 legitimate). Recorded so we do not re-invent it. |
| Opaque backing on the plate rectangle, never a wrapper around the run | as-is | Their measurement: 17 % of source letters left visible against 93 % for the wrapper. We already draw the rectangle - but at 94 % alpha, which spends part of that win for nothing. Make it opaque. |
| Plate padding is load-bearing, not cosmetic | as-is | Their §14.7: `0.05em 0.15em` to `0.08em 0.28em` moved a scene from 0.2841 to 0.2705 with byte-identical rectangles. Our padding is `2..4sp` by box height ([line 500](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt#L500)), inherited. |
| Ink colour is a **median**, never a mean | form only, and we are further behind than "mean" | We sample one pixel. Their mean-vs-median measurement (rgb(61,61,61) against rgb(7,7,7) for source rgb(17,17,17) on rgb(253,253,253)) argues against the mean; a single corner pixel is worse than either. |
| Paper/ink orientation decided by a ring outside the block, `1/3` line height per side, floor 2 px, `>= 40` votes | not applicable **yet** | We never form an ink/paper pair - text colour is black-or-white by luma. The rule becomes applicable the same day we start sampling ink. |
| Display coordinate space only, EXIF applied before recognition | as-is, already satisfied | §3. Their JPEG-only limitation does not apply to us. |
| DPI declared, floor 70, upscale below 120 DPI | form only | `setVariable` is reachable, so the mechanism transfers unchanged. `11` inches as the assumed page is a book assumption; our inputs are photos, screenshots and comic pages, so the estimator itself needs re-thinking, not just its constant. |
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

### 6.2 The same artifact stretches the box

The plate rectangle is `getBoundingRect(RIL_TEXTLINE)`, artifact included
([TesseractManager.kt:244](../app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt#L244)).
There is no word-drop rule of any kind. Their counter-intuitive result is worth carrying over verbatim:
fixing the grouping alone **increased** their damage from 148 px to 160 px, because the corrected plate
then spanned both lines and inherited the stretched box. Type size and box are two quantities and both
need the fix. Their two-condition test (no letter and no digit, **and** taller than the line's median word
height by more than the ratio) transfers unchanged, including the reason each condition alone is harmful.

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
