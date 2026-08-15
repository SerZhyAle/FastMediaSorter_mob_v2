# Research 05 - Fragments without a landscape variant

Resolves strategic §6.5. Source: Glob of `layout-land/` (2026-06-22).

## Missing landscape variants

- `fragment_settings_documents.xml` - no `layout-land/` file.
- `fragment_settings_streams.xml` - no `layout-land/` file.
- `fragment_settings_media_container.xml` - no `layout-land/` file (the shell).

All three fall back to their portrait layout in landscape.

## documents

Portrait already pairs `rowSupportText`+`rowShowTextLineNumbers` and `rowSupportPdf`+`rowShowPdfThumbnails` into horizontal `weight=1` rows (lines ~9-67). A landscape variant is mostly the same; main benefit is consistency + any remaining solo toggle paired. Hosted inside the `containerDocuments` FrameLayout of media_container.

## streams

Tiny: a single toggle + a tonal shortcut button (~32 lines). A landscape variant adds little vertical saving on its own. Create for CLAUDE.md Rule 11 parity once media_container gets a land shell; low priority within the spec.

## media_container shell

Each card body is a `FrameLayout` filled at runtime by a child fragment (Images/Video/VR/Audio/Documents/Other/Streams), gated by capability/flavor in `MediaSettingsFragment.buildSections()` (`SUPPORT_DOCUMENTS`, `SUPPORT_STREAMS`). The shell itself is always inflated; gates run in code.

Decision: a landscape shell that places independent section cards two-up (side-by-side) is high value (cards are the tallest structure) but must handle conditionally-hidden cards gracefully - a fixed 2-column grid leaves holes when a card is `gone`. Lowest-risk approach: keep cards full-width stacked in the shell for iteration 1 and rely on each child fragment's own land variant for height saving; defer a 2-up card shell. Capturing the 2-up shell as an extensibility option, not iteration-1 work, to avoid hole-handling complexity and flavor regressions.

## Flavor note

documents/streams cards are hidden when `SUPPORT_DOCUMENTS` / `SUPPORT_STREAMS` are false (lite, photos). Their land variants live in shared `src/main/res` (layouts are flavor-agnostic; visibility is runtime-gated) - no flavor source set needed.
