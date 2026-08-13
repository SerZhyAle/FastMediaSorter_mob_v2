# Research 02 - Document print materialization reuse

**Question (spec §6.2):** Does pdf/office print work from a standalone host where the file may be a network file?

## Finding

The in-app print path materializes a local copy before printing: `DocumentPrintManager.prepareLocalFile(mediaFile)` calls `activity.networkFileManager.prepareFileForRead(mediaFile)` inside `lifecycleScope.launch`, then dispatches by type (PDF -> `PdfPrintDocumentAdapter`, OFFICE -> office viewer print, TEXT -> WebView).

The standalone document host **already owns the same collaborator**:

- `DocumentStandaloneActivity` has `networkFileManager: NetworkFileManager` and already calls `networkFileManager.prepareFileForRead(mediaFile)` in `openOfficeInternally`/`openOfficeExternally`.
- `TextStandaloneActivity` has `networkFileManager: NetworkFileManager`.

So the materialization sequence the print mechanism needs is identical and already available in the standalone hosts.

## Coupling obstacle (drives ADR-2 + Столп C)

`DocumentPrintManager` is hardcoded to the player: constructor `private val activity: PlayerActivity`, and it reads `activity.networkFileManager`, `activity.officeDocumentViewerManager`, `activity.activityBinding.root` (Snackbar). Standalone hosts are not `PlayerActivity` subclasses. Direct reuse requires a **host-agnostic seam** exposing: a network-file manager, an office-viewer print capability (optional - text host has none), an error-message sink, and a lifecycle/Activity context. `PlayerActivity` + both standalone hosts implement the seam; the mechanism depends on the seam, not on `PlayerActivity`.

- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`.
- The `SharePrintHost` KDoc itself documents the coupling: "the player's DocumentPrintManager needs the player Activity".

## Fallback

On `PrintManager.print` failure, in-app falls back to `PlayerPrintFallbackManager.shareForPrint()` (ACTION_SEND with the materialized file). It is also hardcoded to `PlayerActivity`. The seam must carry the fallback too (or fold it into the same shared path) so standalone hosts get parity.

## Source

Read-only research agent, 2026-06-22 (spec-all F1).
