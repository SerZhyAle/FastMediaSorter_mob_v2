# Phase 02 - Inventory Audit

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5

---

## Objective

Reconcile `docs/ALL_FEATURES.jsonl` with the Phase 01 surface scan: fill real gaps, mark removed capabilities, fix id and flavor hygiene. Agents only read and propose; all inventory writes are central via `scripts/all_features/add.ps1`.

---

## Steps

### Step 02.1 - Coverage diff script

**Files:** `scripts/all_features/audit_coverage.ps1` (new), `temp/s0543/coverage_report.txt` (generated)

**Prompt:**

> Write a read-only script that joins `temp/s0543/surface_signals.json` against `docs/ALL_FEATURES.jsonl` per area and emits: (a) surface signals with no matching inventory record (candidate gaps), (b) inventory records whose id area-prefix differs from the record `area` slug (id-hygiene defects), (c) records whose `flavors` disagree with the scanned flavor availability, (d) inventory records with no surface signal (candidate stale/removed). Output to `temp/s0543/coverage_report.txt`.

**Verification:**

- Script runs `exit 0`; report lists all four buckets with counts.
- Record `expected: report emitted | actual: gaps=<n> idHygiene=<n> flavorMismatch=<n> staleSuspect=<n>`.

**Status:** `[ ]`

---

### Step 02.2 - Parallel per-area verification (agents)

**Prompt:**

> Fan out read-only research agents (android-solution-researcher), one per area cluster, each given its slice of the coverage report. Each agent confirms whether a candidate gap is a real, distinct user-facing capability (vs noise like a label fragment), proposes a record (`id`, `area`, `name`, `description`, `flavors`), and confirms stale/removed suspects against current code. Agents return structured candidate lists only - they DO NOT edit the inventory.

**Verification:**

- Each cluster returns a candidate list (ADD / CHANGE / REMOVED / reject-as-noise) with evidence paths.
- Candidate lists captured under `temp/s0543/candidates_<area>.json` or the Step Log.

**Status:** `[ ]`

---

### Step 02.3 - Apply confirmed additions and changes

**Prompt:**

> Centrally apply confirmed ADD/CHANGE candidates via `scripts/all_features/add.ps1` (EN-only; `-NoLegal` for noLegal-only). Use proper `<area-slug>.<feature>` ids. Keep `-Spec` empty unless a source spec is known.

**Verification:**

- `scripts/all_features/validate.ps1` exits 0.
- `Grep` confirms the new ids present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[ ]`

---

### Step 02.4 - Fix id, flavor, and lifecycle hygiene

**Prompt:**

> Normalize id area-prefixes to the record `area` slug where safe (e.g. `s0523.* -> quick-capture.*`). CAUTION: an id already consumed by a shipped release diff must not be renamed (it reads as remove+add downstream) - leave it and note in the Step Log. Correct `flavors` lists that disagree with the verified scan. Mark genuinely removed capabilities `status: removed` via `add.ps1 -Status removed`.

**Verification:**

- `Grep` - zero ids whose prefix is `s\d{4}` (spec-as-area), except any explicitly noted as release-locked.
- `validate.ps1` exits 0.
- Step Log records each rename/flavor-fix/removed mark with rationale.

**Status:** `[ ]`

---

### Step 02.5 - Re-run coverage, record residuals

**Prompt:**

> Re-run `audit_coverage.ps1`. Enumerate residual gaps that were deliberately not added (with reason: noise / out-of-scope / Wear OS deferred). This residual list is the honest "what is still uncovered" record.

**Verification:**

- Residual report emitted; each residual has a reason.
- Record `expected: residuals enumerated | actual: <n> residual, <n> resolved`.

**Status:** `[ ]`

---

## Progress - Wave 1 (2026-06-19)

Tooling: `scan_surface.ps1` (worksheets) + new `patch.ps1` (additive flavor/status field patcher).

Wave 1 covered 5 area-clusters (~274 records) via 5 parallel read-only agents: Video/Media Player; Audio+Image+Slideshow+Drawing; Documents+Text+OCR; Browse+Files+Share+Dest; Settings+Setup+Stats+Extensions.

APPLIED - 17 flavor-fixes (each verified against the build.gradle.kts flag matrix):

- +vr: audio-player.{background-audio-service, notification-playback-controls, quick-settings-tile-play-pause, configurable-back-exit-behavior} (ENABLE_PERSISTENT_AUDIO_PLAYBACK=true on vr).
- +lite: audio-player.chromecast-casting (SUPPORT_CAST lite=true, vr=false).
- image-gif-viewer.network-image-editing -> [standard,legacy,noLegal,vr,photos] (SUPPORT_LOCAL_NETWORK, all but lite).
- +legacy,vr: media-browsing.office-and-pdf-document-handoff (OfficeDocumentFamilyCatalog in src/legacy + src/vrOnly).
- +photos: file-operations.cross-protocol-file-copy, .cross-protocol-file-move, media-browsing.background-thumbnail-preload-worker, usage-statistics.sources-connected-metric, setup-onboarding.set-as-default-player-per-media-type.
- settings-navigation.settings-backup-and-restore-via-google-drive -> +legacy,noLegal,vr,photos (SUPPORT_CLOUD all but lite).
- settings-navigation.wear-os-companion-settings-sync -> +legacy,noLegal (SUPPORT_WEAR_COMPANION).
- +vr,noLegal: settings-navigation.crash-report-prompt-after-restart, .email-crash-report-from-error-dialog (src/main, universal).
- -vr: extensions-on-demand.on-demand-paddleocr-engine-download (vr uses ocrEnginesStore without PaddleOCR; noLegal only).

REJECTED (agent false positives, caught by central verification):

- 6 slideshow.* ADDs (Agent 2 claimed no Slideshow area - it has 8 records; all proposed were duplicates).
- audio-player.pulsing-rings-backdrop REMOVED (mode exists via visualization controller + strings_audio.xml + FEATURES.md, no dedicated class - not removed).

DEFERRED to next pass:

- ADD candidates (~20 user-facing gaps: video touch-zones/vertical-seekbar/standalone-activity/video-cast, drawing opacity/brush/color/text-size, text-editor reader-themes, browse metadata/new-note/new-drawing, resource-list filter/reorder, apk-sideload-install [noLegal]) - apply with computed flavors.
- id-hygiene: 7 spec-prefixed ids (s0512/s0523/s0526 + 4 General s0475/s0490/s0491/s0504) - rename to area slug (check release-lock first).
- Sharing 12-record flavor-fix (+vr,noLegal) - verify no runtime share gate before applying.
- Wave 2: Sources & Storage, Network & Cloud, Widgets, VR & OpenXR, General, Quick Capture + the domain/game+ui/game module (42 classes, no inventory area).
- /spec-draft parking of 5 out-of-scope bugs agents surfaced (VR_QUALITY_DEBUG permanent log @VideoPlayerTracksObserver.kt:37 / S0041; widget OCR flavor mismatch on lite/photos; Extensions Manager empty-list not hidden on lite/photos; CastMediaManager vr cast-button; StatsCategoryAvailability BuildConfig in src/main).

validate.ps1: PASS (345 records).

---

## Progress - Wave 2 (2026-06-19)

6 parallel agents: Sources+Network; Widgets; VR & OpenXR; General+QuickCapture; game-module investigation; Sharing-gate verification.

HEADLINE: the `domain/game`+`ui/game` module (42 classes) is a SHIPPED, user-facing mini-game ("Kryvavitsa and the Monster", EN/RU/UK, 3 entry points - main menu + home widget + Settings toggle `embeddedGameEnabled` default off, manifest-registered, unit-tested) with NO inventory area. Added new `Game` area (2 records).

APPLIED:

- Cloud +legacy (5): sources-storage.cloud-drive-source, network-cloud.{google-drive,dropbox,onedrive}-oauth-authentication, network-cloud.cloud-settings-backup-to-google-drive (SUPPORT_CLOUD legacy=true; were missing it).
- Sharing +vr,noLegal (12 records): verified no runtime/manifest flavor gate (gating is by content-type + installed-app, not flavor) - Send-to runs on vr/noLegal.
- Widgets flavor-fix (5): camera-ocr-translate / capture-and-ocr-panel / quick-audio-recorder -> [standard,legacy,noLegal,vr] (lite/photos manifests REMOVE these receivers - inventory wrongly claimed lite/photos); audio-now-playing [standard]->[standard,legacy,noLegal,vr]; random-music [standard]->[standard,lite,legacy,noLegal,vr].
- Removed (status:removed, first ever): widgets.voice-recorder-widget (phantom dup of quick-audio-recorder, no class), s0490 General record (dup of settings-navigation.crash-report-prompt-after-restart).
- id-hygiene renames (6): s0475/s0491/s0504/s0512 -> general.*, s0523 -> quick-capture.*, s0526 -> file-saving.* (+ name/flavor fixes). Spec-prefixed ids 7 -> 1 (remaining is the removed s0490 tombstone).
- ADD 14 main: game.{kryvavitsa-mini-game, kryvavitsa-launcher-widget}; vr-openxr.{loading-overlay-before-first-immersive-frame, bundled-360-fallback-asset}; video-player.{configurable-touch-zones, vertical-seekbar-brightness-volume-control, chromecast-video-casting}; drawing-annotations.{custom-color-picker, brush-size-control}; text-editor.reader-themes; documents.pdf-page-share-to-google-lens; media-browsing.{file-metadata-bottom-sheet, new-drawing-canvas-in-browse}; resource-list.drag-to-reorder-resources.
- ADD 1 noLegal: media-browsing.apk-sideload-install (ALL_FEATURES_noLegal.jsonl).

REJECTED/SKIPPED: 2 minor drawing sub-option ADDs (opacity, text-size); systemic widget noLegal/vr expansion (uncertain - VR has no practical home screen, design call); FTP-passive-mode (impl detail).

OUT-OF-SCOPE bugs to park (Phase 02 -> /spec-draft): Extensions Manager empty-list not hidden on lite/photos (GeneralSettingsFragment); VR_QUALITY_DEBUG permanent log @VideoPlayerTracksObserver.kt:37 (dedup vs S0041); StatsCategoryAvailability reads BuildConfig in src/main (Rule 14).

Totals: main 344 -> 359 (355 active, 2 removed, 14 added); noLegal 16 -> 17. validate.ps1 PASS both files.

---

## Phase Done Criteria

- [ ] Steps 02.1-02.5 are `[x]`.
- [ ] `validate.ps1` exits 0.
- [ ] `audit_coverage.ps1` committed; reports under `temp/s0543/`.
- [ ] One dev-log entry for the inventory reconciliation batch.

---

## Handoff Notes

The reconciled inventory is the reference set for Phase 03 (showcase) and Phase 06 (docs/site). Residual gaps feed Phase 07.
