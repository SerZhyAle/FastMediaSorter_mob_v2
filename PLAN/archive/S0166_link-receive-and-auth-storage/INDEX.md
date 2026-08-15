# Tactical Plan: S0166 — Link Receive + Auth Storage (rewrite from scratch)

**Strategic spec:** `PLAN/S0166_link-receive-and-auth-storage.md`
**Status:** Verified

---

## Phases

| # | File | Description | Status |
|---|------|-------------|--------|
| 00 | [PHASE_00__backup-and-delete.md](PHASE_00__backup-and-delete.md) | Backup broken code → `temp/`, delete from source | ✅ Done |
| 01 | PHASE_01__data-model-and-storage.md | Auth record data model + encrypted storage | ✅ Done |
| 02 | PHASE_02__extraction-pipeline.md | Link extraction pipeline + social-aware WebView strategy | ✅ Done |
| 03 | PHASE_03__coordinator-and-worker.md | LinkAutoDownloadCoordinator rewrite + Worker integration | ✅ Done |
| 04 | PHASE_04__auth-dialog-and-settings-ui.md | Auth dialogs, account picker, settings screen | ✅ Done |
| 05 | PHASE_05__di-and-integration.md | Hilt module, wire everything, fix compile | ✅ Done |
| 06 | PHASE_06__tests-and-logging.md | Unit tests, Timber logging per §5 of spec | ✅ Done |
| 07 | PHASE_07__docs-catalog-cleanup.md | strings.xml, FEATURES*.md, catalog scan | ✅ Done |

---

## Files KEPT from old code (general infrastructure, not deleted)

These files are NOT part of the broken auth/social flow and remain in source:

| File | Reason |
|------|--------|
| `data/link/DirectFileExtractionStrategy.kt` | Direct file download — unrelated to social auth |
| `data/link/LinkDownloadWriter.kt` | Writes downloaded bytes to storage — unrelated |
| `data/link/StreamingManifestSniffer.kt` | Streaming manifest detection — unrelated |
| `data/link/StructuredMediaSniffer.kt` | Structured media detection — unrelated |
| `domain/usecase/link/MediaMimeWhitelist.kt` | MIME whitelist — unrelated |
| `domain/usecase/link/streaming/StreamingPipeline.kt` | Streaming pipeline — unrelated |
| `worker/LinkDownloadWorker.kt` | Background worker skeleton — will be rewired in Phase 03 |
| `ui/share/ReceiveShareActivity.kt` | Entry point Activity — gutted and rebuilt in Phase 03 |
| `ui/share/UrlInTextDetector.kt` | URL detection helper — unrelated |
| `ui/share/LinkAutoDownloadProgressDialog.kt` | Progress dialog — will be reused |

> **Build is expected to be broken** after Phase 00 until Phase 05 completes. Do not attempt a full build between phases 00–04.

> **Audit result:** `Verified` — all phases done; explanatory block confirmed in layout; arbitrary-host auth-offer escalation confirmed in ReceiveShareActivity; unit tests (AccountSelectionManagerTest, CandidateSelectionPolicyTest extension) added and compile green.
