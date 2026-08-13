# Research 01 - Exhaustive outbound-share surface audit

**Strategic spec:** [`../../S0459_unified-send-to-menu.md`](../../S0459_unified-send-to-menu.md) §6 (item 1)
**Status:** Resolved
**Date:** 2026-06-16
**Method:** read-only sweep of `app_v2/` across all flavor source sets (ACTION_SEND/_MULTIPLE, createChooser, ACTION_VIEW open-with, ACTION_SENDTO/mailto, PrintManager/PrintHelper, Lens/Keep hand-off, ACTION_INSTALL_PACKAGE).

---

## Question

Enumerate every place that sends the current file/selection outward, so the consolidation phase replaces them with one menu and leaves no duplicate.

## Findings - IN-MENU surfaces (consolidate)

| # | Surface | Symbol | Mechanism | Type | Receiver |
|---|---------|--------|-----------|------|----------|
| 1 | Player command panel - Share | `PlayerCommandPanelCallbackImpl.onShareClicked()` | ACTION_SEND | any | System Share |
| 2 | Player panel - Telegram | `PlayerCommandPanelCallbackImpl.onSendToTelegramClicked()` → `PlayerShareManager.sendCurrentFileToTelegram()` | ACTION_SEND (`invokeFiles`) | any | Telegram |
| 3 | Player overflow - Google Lens | `PlayerShareManager.shareCurrentFileToGoogleLens()` | ACTION_SEND to Lens pkgs | image | Google Lens |
| 4 | Text editor toolbar - Save & Send | `TextEditorActionPanelCallbacks.onSaveAndSend()` | ACTION_SEND | text | System Share |
| 5 | Text editor toolbar - Send to Keep | `TextEditorActionPanelCallbacks.onSendToKeep()` | ACTION_SEND to Keep | text | Keep-text |
| 6 | Text viewer (reading) overflow - Send to Keep (S0431) | `TextViewerManager.sendCurrentTextToKeep()` | ACTION_SEND to Keep | text | Keep-text |
| 7 | Image draw editor - Share | `PlayerDrawingSaveHelper.shareCurrentDrawing()` | ACTION_SEND | image | System Share |
| 8 | Image draw editor - Keep export | `DrawKeepExportHelper.export()` | ACTION_SEND to Keep | image | Keep-drawing |
| 9 | Browse grid multi-select - Share | `BrowseManagerInitializer.onShareClicked()` → `BrowseFileOperationsManager.shareSelectedFiles()` | ACTION_SEND / _MULTIPLE chooser | any (multi) | System Share |
| 10 | Browse grid multi-select - Telegram | `BrowseShareOperationsHelper.sendSelectedFilesToTelegram()` | ACTION_SEND / _MULTIPLE | any (multi) | Telegram |
| 11 | Browse binary bottom sheet - Share | `BrowseBinaryFileHandler.shareFile()` | ACTION_SEND chooser | detected MIME | System Share |
| 12 | Standalone player command bar - Share (Photo/Video, Audio, Document, Text hosts) | `StandaloneFileOperationsHandler.shareCurrentFile()` (`btnShareCmd`) | ACTION_SEND chooser | any (incl. audio) | System Share |
| 13 | Standalone Office viewer - share fallback | `StandaloneViewManager.shareOfficeDocument()` | ACTION_SEND chooser | office | System Share |
| 14 | File-info dialog - Open in external player | `FileInfoLaunchManager.openInExternalPlayer()` (+ second open-with site) | ACTION_VIEW chooser | any | "Open in.." |

Print is a framework-routed surface (Android Print, not ACTION_SEND) - see "Print" below.

> **Correction (verification 2026-06-16):** rows 12-14 were missed by the first-pass audit, which claimed standalone players add no distinct entry points and that audio has no share affordance. Independent ACTION_SEND/createChooser sweep proved both wrong - the standalone hosts carry their own share button (`btnShareCmd` → `shareCurrentFile`, all four hosts including audio) and `FileInfoLaunchManager` carries an own open-with. They ARE consolidation targets.

## Findings - Print (IN-MENU receiver, framework-routed)

- `DocumentPrintManager` - `printPdf()` / `printImage()` (androidx `PrintHelper`) / `printText()` (`WebViewPrintAdapter`).
- `PlayerPrintFallbackManager.shareForPrint()` - ACTION_SEND chooser to print apps when `PrintManager` unavailable.
- `src/noLegal/.../OfficeDocumentPrintAdapter.print()` - WebView print adapter (flavor-specific).
- Maps to the **Print** receiver; invocation goes through the system Print dialog, not the chooser.

## Findings - OUT-of-menu (firewall, ADR-6)

- `OfficeDocumentOpenManager.openUri()` + `BrowseBinaryFileHandler.openWithDefaultApp()` - ACTION_VIEW. These ARE the **"Open in.."** receiver (external launch of the current file) - in menu, but as "Open in..", not as "share".
- `BrowseApkInstallHandlerImpl` (noLegal) - ACTION_INSTALL_PACKAGE. OUT (APK delivery, not content share).
- `SupportIntentFactory.reportProblem()` - mailto, no attachment. OUT (support channel).
- `SupportIntentFactory.openHelp()` / `leaveFeedback()` - ACTION_VIEW URL / market. OUT (not content).
- `LogExportHelper.exportLogs()` / `writeZipToUri()` - diagnostics ZIP. OUT (debug, not user content).
- Internal copy/move-to-resource destinations (`DestinationButtonsManager`, `data/transfer/strategy/*`) - OUT (separate mechanism, §2 non-goal).
- `ScrollableTextDialog` btnPrimary share (`error_dialog_share`, text/plain) - OUT (shares diagnostic/error text, not a media file).
- `MainEventHandler` resource export (`ResourceShareFormat.MIME_TYPE`, `resource_share_export_title`) - OUT (exports an FMS resource/config definition - a separate "share resource" feature, not media content).
- `BackupRestoreFragment` export (`application/json`, favourites/backup) - OUT (config/backup export, not media content).

> These three were also absent from the first-pass audit. They are correctly OUT (ADR-6: config/diagnostic exports are not "send the current media file outward") - listed so the consolidation phase does not touch them by mistake.

## Decision

- IN-MENU work-list = the 11 surfaces above + Print (4 sites) + "Open in.." (2 sites).
- They collapse to receivers: System Share (×6), Telegram (×2), Lens (×1), Keep-text (×2), Keep-drawing (×1), Print (×4), Open-in (×2). New receivers Email / WhatsApp / Instagram have no existing surface (greenfield registration).
- Standalone players (Photo/Video, Audio, Document, Text) DO carry their own outbound entry points - `StandaloneFileOperationsHandler.shareCurrentFile()` (the `btnShareCmd` button, all four hosts incl. audio) and `StandaloneViewManager.shareOfficeDocument()` - in addition to the shared Keep paths (`TextViewerManager`, `DrawKeepExportHelper`). These are consolidation targets: the unified «Send to..» command must replace `btnShareCmd` in the standalone command bar, not assume free inheritance.
- Audio is a first-class shareable type: the standalone audio host shows the share button and its overflow hides image/video-only items (Lens, Print, crop). So for audio the applicable receivers resolve to System Share / Telegram / Email / "Open in.." / WhatsApp (any-type) and exclude Lens / Print / Keep / Instagram - exactly what the type-applicability gate (research 02) yields. No model change needed; just confirms AUDIO must be in scope.
- No hidden surfaces in PDF/EPUB/audio viewers, image-edit dialogs, translation overlays, favorites, or resource-edit screens (verified zero outbound intents).

## Spec impact

- The consolidation pillar (§5.1) and §11.7 grep-audit criterion are satisfied by replacing exactly these sites.
- Phase plan: one consolidation step per host area - player command panel, browse grid action mode, browse binary sheet, editor toolbars (text/drawing) - each removes its ad-hoc outbound call and routes through the unified menu.
- S0431/S0362 Keep paths (rows 5/6/8) are re-homed, not deleted wholesale: their send actions are reused by the Keep-text / Keep-drawing receiver registrations (see strategic §10 Re-homes).
