# S0521 - PDF thumbnails stopped showing in Browse (regression)

**Ticket:** S0521
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-18
**Tier:** 1 - Quick Win (ad-hoc)

## Problem

PDF thumbnails disappeared from the Browse list/grid and show the generic "PDF" extension placeholder instead. Both Glide thumbnail decoders for Browse (local and network PDF) build the render-target bitmap with the memory-pressure config, which can be `RGB_565`. `PdfRenderer.Page.render()` only accepts `ARGB_8888`; an `RGB_565` target throws `IllegalArgumentException`, which the decoders' `catch` blocks (only `IOException`/`SecurityException`) do not handle, so Glide treats it as a decode failure and paints the error placeholder. The in-app PDF viewer is unaffected because it already renders into `ARGB_8888`.

## Approach

- `data/glide/PdfPageDecoder.kt` - render the first page into a mandatory `ARGB_8888` bitmap; if the memory-pressure resolver requests a different config, down-copy the finished thumbnail to keep the reduced cache footprint; also catch `IllegalArgumentException` so any future render-config mismatch degrades to a placeholder instead of a silent failure.
- `data/glide/NetworkPdfThumbnailLoader.kt` - same `ARGB_8888` render target + optional down-copy (after the page-count badge is drawn) + `IllegalArgumentException` guard.

## Done criteria

- `PdfPageDecoder.decode()` no longer passes a non-`ARGB_8888` config to `PdfRenderer.Page.render()`; local PDF files show a real first-page thumbnail in Browse.
- `NetworkPdfThumbnailLoader.renderPdfPage()` no longer passes a non-`ARGB_8888` config to `PdfRenderer.Page.render()`; SMB/SFTP/FTP PDFs within the size limit show a real thumbnail.
