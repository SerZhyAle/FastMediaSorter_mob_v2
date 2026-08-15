# Draft: S0539 - Decompose BrowseDialogHelper

**Ticket:** S0539
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-19
**Tier:** 2 - Easy (ad-hoc)

> Draft inbox. Parked during S0538 research. No research/approval yet.

## 0. Capture (verbatim evidence)

Discovered during S0538 (dialog button unification) codebase research.

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt` is 774 LOC and approaching the 1500-LOC ceiling.
- The file mixes two dialog-builder types in the same class (`AlertDialog.Builder` and `MaterialAlertDialogBuilder`) with no architectural separation - it is a catch-all bucket for all browse-screen dialogs (filter, sort, rename, copy, move, delete).
- Symptom: continues to grow; mixed builder types complicate any single-theme styling override and future maintenance.

## 1. Problem (rough)

Aggregator dialog class for the browse screen is oversized and mixes builder strategies; should be split into focused sub-managers before it crosses the size limit.

## Next step

`/spec` (or `/spec-update`) to promote this Draft into a strategic spec when picked up.
