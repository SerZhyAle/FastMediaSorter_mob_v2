# Research 01 - Text print path

**Question (spec §6.1):** Does a text print path exist, and what does the standalone text host print?

## Finding

Text printing already exists in-app. `DocumentPrintManager` (in-app player helper) prints `MediaType.TEXT` by creating an **ephemeral `WebView`**, calling `loadData(...)`, and in `onPageFinished` invoking `WebView.createPrintDocumentAdapter(jobName)` -> `PrintManager.print(...)`. The WebView is never attached to the view hierarchy.

- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` (TEXT branch ~lines 250-268).
- `androidx.print.PrintHelper` is image-only and is NOT used for text.
- API: `WebView.createPrintDocumentAdapter(String)` is API 21+, cleared by both minSdks (23 legacy, 26 standard).

## Implication for S0613

The text print mechanism is **not net-new**. The only net-new work is letting the standalone text host declare the print capability (`SharePrintHost`) and route to the shared mechanism. Per ADR-2, the mechanism is reused via a host-agnostic seam rather than duplicated.

## Caveat (carried to risk list)

The ephemeral WebView dispatch happens in `onPageFinished` on the main thread without an `isFinishing/isDestroyed` guard - a pre-existing latent issue in `DocumentPrintManager` (logged as a `/spec-draft` candidate). The standalone path inherits the same code, so no new exposure beyond in-app.

## Source

Read-only research agent over `core/share/` + `ui/player/helpers/` + `ui/player/standalone/`, 2026-06-22 (spec-all F1).
