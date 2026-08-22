---
name: ocr-overlay-accuracy-exchange
description: Three-sided OCR-overlay measurement exchange with doc-html-translate and FastMediaSorter Lite - where the other two documents live on disk
type: reference
metadata:
  type: reference
---

OCR overlay quality is a **cross-project exchange**, not a local topic. Our side is
`docs/OCR_OVERLAY_ACCURACY.md` (registered as `ocr-overlay-accuracy`); the other two participants live
outside this repo, on the same machine:

- `P:\WINDOWS\EPUB_2_HTML` (`doc-html-translate`) - the origin of every measured constant. Mechanism written
  for porters: `docs\ocr-pipeline.md`; invariants: `docs\PARITY.md` "OCR"; per-question studies:
  `DEV\research\RESEARCH_INDEX.md`; the lab and its acceptance bounds: `tools\ocrlab\README.md` +
  `DEV\ocrlab\thresholds.json`.
- `P:\WINDOWS\FastMediaSorter_Lite\docs\specifications\SPECIFICATION_OCR_OVERLAY_ACCURACY.md` - the running
  exchange log, Russian, one numbered H2 per round, latest round §16 (2026-08-15).

**Why:** the constants there were each derived from a two-sided measurement over a 46-scene annotated
corpus, and several were derived, measured and then rejected. Re-deriving them here from scratch would cost
weeks; quoting them without naming the measurement they stand on is exactly what their rule 2 forbids.

**How to apply:** before touching `TranslationOverlayView`, the `RecognitionBackend` block filters or the
`TesseractManager` iteration level, read our document first - it already records the transfer verdict for
each of their rules. When a round arrives from either side, append a new numbered section rather than
editing an earlier one. Our documents stay English even though theirs are Russian; that divergence is
stated in our front matter deliberately.
