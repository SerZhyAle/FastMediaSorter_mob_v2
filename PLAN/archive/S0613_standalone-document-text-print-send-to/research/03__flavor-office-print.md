# Research 03 - Flavor and office print behavior

**Question (spec §6.3):** How does print behave across flavors, in particular the noLegal internal office viewer?

## Flavor matrix (documents)

- `SUPPORT_DOCUMENTS = true`: standard, legacy (minSdk 23), vr, noLegal.
- `SUPPORT_DOCUMENTS = false`: lite, photos -> document host not routed; print receiver never reached for documents there.

> Correction (impl): an earlier draft listed `lite` as document-supporting; verified in `app_v2/build.gradle.kts` that `lite` is `SUPPORT_DOCUMENTS=false`.

## Print receiver type gate

`ShareTargetModule.printTarget()` registers Print with `applicableTypes = {IMAGE, GIF, PDF, TEXT, OFFICE_DOCUMENT}`. **EPUB is intentionally absent** -> Print never appears for EPUB on any host (parity with in-app, where EPUB is also not printed). So in the standalone document host, Print shows for PDF and OFFICE only.

## Office print per flavor

- **noLegal:** ships an internal office viewer; its print returns true. `DocumentStandaloneActivity` holds the office viewer host.
- **market flavors (standard/lite/legacy):** office viewer host is a NoOp; its print returns false. In-app `DocumentPrintManager` then falls back to `PlayerPrintFallbackManager.shareForPrint()` (ACTION_SEND with the materialized file). Standalone must mirror this fallback for parity.

## PDF / text per flavor

PDF (`PdfPrintDocumentAdapter`) and TEXT (ephemeral WebView) print on all flavors that support documents/text - no flavor fork in the mechanism itself.

## Implication for S0613

- pdf + text print: shared code, no flavor source set.
- office print: reuse the existing flavor-gated office-viewer capability through the seam (noLegal internal print; market fallback to share-for-print).
- EPUB: out of scope (non-goal), consistent with applicableTypes and in-app.
- Defensive guard: in-app gates on `mediaCapabilities.supportsDocuments`; the `applicableTypes` + host-capability gate already prevent wrong types reaching the standalone host, but the seam may keep the same guard for safety.

## Source

Read-only research agent over flavor source sets + `core/share/di/ShareTargetModule.kt` + `DocumentPrintManager`, 2026-06-22 (spec-all F1).
