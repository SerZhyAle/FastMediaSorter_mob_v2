# Pointer - `OCR-OVERLAY`

| | |
| --- | --- |
| **Id** | `OCR-OVERLAY` |
| **Version** | 1.0, active. Owner: shared (amendments through the domain page) |
| **Home** | `ocr-overlay/README.md` in the shared contracts catalog |
| **Role here** | consumer - the player's OCR + translation overlay |

## What this repository must do to stay conformant

- Recognize in display space and keep one image-to-viewport transform, so a plate never drifts
  (rules 1-4).
- Take boxes at line level and sample paper and ink from the image as medians (rules 5, 8).
- Never write a recognized string into user data, a filename or a catalog row (rule 11).
- Record every dropped line through the same predicate the filter uses (rule 12).
- Mark every constant as derived, with its report, or inherited, with the ticket that owns deriving it
  (rule 13).
- Keep OCR failure best-effort (rule 16).

Open deviations are recorded as dated exceptions in the catalog's registry, not here.

## Where it lives here

- `app_v2/.../domain/ocr/` - the filter, line splitter, geometry, plate colour sampler and
  `OcrDiscardRecorder.kt`.
- `app_v2/.../ui/player/helpers/RecognitionBackend.kt` - where the filter and the discard record meet.
