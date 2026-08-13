# Phase 03 - Word Family Viewer

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-04-18
**Completed:** 2026-04-18

---

## Objective

Implement the noLegal internal Office viewer for the Word-family subset (`.doc`, `.docx`, `.rtf`, `.odt`) with read-only viewing, direct internal open, and safe hyperlink behavior.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Strategic §6.1, §6.2, §6.3, §6.6, and §6.7 blockers are Resolved.
- [x] The chosen engine contract is documented in the strategic spec or a linked research artifact.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt` | New | ≤ 520 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt` | New | ≤ 360 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentHyperlinkPolicy.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 860 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Implement the noLegal viewer manager around the chosen engine

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Build the noLegal Office viewer manager around the selected engine. The manager must own open/render/close lifecycle, stay read-only, and expose the same high-level state transitions that PDF/EPUB viewers use for fullscreen and visibility handling.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt` exists.
- `Grep` - `class OfficeDocumentViewerManager` matches exactly once in that file.
- `Grep` - `class OfficeDocumentEngineBridge` matches exactly once in `OfficeDocumentEngineBridge.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Materialize viewer sessions from local, content, and remote sources

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Reuse the existing materialize-to-local path before handing a file to the internal Office engine. Word-family documents from local storage, `content://`, network, and cloud must arrive as explicit viewer sessions that obey the agreed cache/temp-file ownership contract.

**Verification:**

- `Grep` - `prepareFileForRead` exists in `OfficeDocumentViewerManager.kt` or in the Office branch of `StandaloneViewManager.kt`.
- `Grep` - `OfficeDocumentViewerSession` exists in the Word-family open path.
- `Grep` - no `TODO(phase-03-materialize)` hits exist under `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers`.

**Status:** `[x]` done

---

### Step 03.3 - Enforce the hyperlink and active-content policy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentHyperlinkPolicy.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Allow ordinary hyperlinks from the internal Office viewer, but block active content, scripts, macros, OLE execution, and any implicit remote follow-up. Keep the link policy explicit and auditable in one helper instead of scattering ad-hoc checks.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentHyperlinkPolicy.kt` exists.
- `Grep` - `allowOrdinaryLinks` exists in `OfficeDocumentHyperlinkPolicy.kt`.
- `Grep` - `blockActiveContent` exists in `OfficeDocumentHyperlinkPolicy.kt`.

**Status:** `[x]` done

---

### Step 03.4 - Add lifecycle hooks for the Word-family viewer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Make Player and Standalone own the Office viewer lifecycle the same way they already own PDF/EPUB managers: lazy init, active-state checks, release on shutdown, and fullscreen exit on lifecycle transitions.

**Verification:**

- `Grep` - `officeDocumentViewerManager` exists in `PlayerActivity.kt`.
- `Grep` - `officeDocumentViewerManager` exists in `StandalonePlayerActivity.kt` or `StandaloneViewManager.kt`.
- `Grep` - `releaseOfficeDocumentViewer` or an equivalent release call exists in `PlayerLifecycleManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `assembleNoLegalDebug` BUILD SUCCESSFUL (7m10s) + `assembleStandardDebug` verified.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Word-family documents now have an internal read-only noLegal viewer path; the next phase expands the same stack to spreadsheets and presentations.

---

## Rollback Plan

Revert phase commit(s) - no data migration or public market surface changed.