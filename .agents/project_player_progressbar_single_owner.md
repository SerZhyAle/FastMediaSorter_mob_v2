---
name: player-progressbar-single-owner
description: Unified player progressBar is owned by PlayerLoadingIndicatorCoordinator (S0704); PdfViewerManager is the remaining rogue direct writer
metadata:
  type: project
---

The unified player's shared `R.id.progressBar` spinner is owned by a single source-counted
coordinator: `ui/player/helpers/PlayerLoadingIndicatorCoordinator` (S0704, 2026-06-26). Bar is
visible iff its `MutableSet<LoadingSource>` is non-empty. Former direct writers (reactive
`viewModel.loading` driver, image Glide cycle, ExoPlayer buffering, OCR, translation, PDF export,
TEXT/EPUB load) now call `show/hide/showDelayed/armSafetyTimeout/reset/clearAll` by source instead
of writing `progressBar.isVisible`.

**Why:** before S0704 ~11 sites raced on `isVisible` with no precedence contract -> stuck/flickering
spinner on fast nav and media transitions (parent audit S0703).

**How to apply:**
- Never add a new direct `progressBar.isVisible =` write in the unified player; add/extend a
  `LoadingSource` and route through `activity.loadingIndicatorCoordinator`.
- `PdfViewerManager` is still a DIRECT writer of the same bar (~11 sites via
  `safeViews.playerProgressBar`) - intentionally left out of S0704 scope (PDF page display has a
  single owner today since the reactive driver is gated off for PDF). If a stuck/flickering PDF
  spinner shows up, this is the bypass. A follow-up could add a `PDF_VIEW` source.
- Standalone activities (`StandalonePlayerActivity`, `*StandaloneActivity`) have their OWN separate
  progressBar + single reactive `state.isLoading` driver - out of scope. The TEXT/EPUB helpers
  (`TextViewerManager`+sub-helpers, `EpubViewerManager`, `EpubWebViewLifecycle`) are shared with
  standalone, so they take the coordinator as a NULLABLE param: non-null = unified (route through
  it), null = standalone (direct write).
- `reset(source)` (cancels pending show+safety) vs `hide(source)` (only removes source + cancels
  safety): use `reset` at load-completion/transition sites so a sub-1s load can't resurrect a
  pending delayed-show. The Glide completion sites and `bindServicePlayerToView` rely on this.
