# Phase 02 - Rewrite core user docs

**Ticket:** S0366
**Status:** ✅ Done

**Completed:** 2026-06-06

## Steps

1. Update `docs/README.md`, `docs/QUICK_START.md`, `docs/HOW_TO.md`, `docs/FAQ.md`, `docs/TROUBLESHOOTING.md`, `docs/LIMITATIONS.md`, and `docs/MODULE_SELECTION.md` so the narrative matches `docs/FEATURES.md`.
   - Verification: `rg -n "Camera OCR|Widget|VR|OpenXR|Cloud|OCR|Translation|File Manager" docs/README.md docs/QUICK_START.md docs/HOW_TO.md docs/FAQ.md docs/TROUBLESHOOTING.md docs/LIMITATIONS.md docs/MODULE_SELECTION.md` shows the expected current-capability terms. expected: current terms present | actual: record.

2. Update the main landing pages `index.html`, `index-ru.html`, `index-uk.html`, `nolegal.html`, `nolegal-ru.html`, and `nolegal-uk.html` to reflect the current product story and current download/help flow.
   - Verification: `rg -n "FastMediaSorter|Quick Start|How-To|FAQ|Troubleshooting|Download" index.html index-ru.html index-uk.html nolegal.html nolegal-ru.html nolegal-uk.html` shows the updated navigation labels. expected: labels present | actual: record.

3. Replace stale terminology and old examples with the current feature names already documented in `docs/FEATURES.md`.
   - Verification: `git diff --name-only -- docs/README.md docs/QUICK_START.md docs/HOW_TO.md docs/FAQ.md docs/TROUBLESHOOTING.md docs/LIMITATIONS.md docs/MODULE_SELECTION.md index.html index-ru.html index-uk.html nolegal.html nolegal-ru.html nolegal-uk.html` returns the touched public docs. expected: touched file list | actual: record.

## Done criteria

- Core user docs and top landing pages describe the current app story instead of stale wording.
- The updated pages use the same terminology as the canonical feature list.

## Step Log

- 2026-06-06 - Refreshed the core public docs, normalized the current flavor matrix, and aligned the landing pages with the current documentation flow.
