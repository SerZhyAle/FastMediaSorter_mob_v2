# Phase 01 - PDF long-press selection entry

**Strategic spec:** [`../S0323_document-double-tap-text-selection.md`](../S0323_document-double-tap-text-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-06-01
**Completed:** 2026-06-01

> **Step Log:** 2026-06-01 - Steps 01.1-01.3 verification PASS (greps), 01.4 build standard debug PASS (BUILD SUCCESSFUL 1m42s). Files: PdfViewerManager.kt, PlayerCommandPanelCallbackImpl.kt. Dev logs recorded.

---

## Objective

Re-route PDF page long-press from "enter fullscreen" to "open the text-selection overlay" (the existing TXT-button overlay where native selection + the unified floating Copy already work), and keep PDF page-fullscreen reachable via the existing overflow `menu_fullscreen`.

---

## Prerequisites

- [ ] Strategic §6 items resolved (see INDEX blockers - all checked).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | ≤ 1030 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 500 |

> `PdfViewerManager.kt` is at 1009 LOC. Net change here is small (re-target one handler + make one method callable + a routing branch). If projected >1500 after Phase 02, extract a `PdfSelectionEntryManager` first. Backup `PdfViewerManager.kt` to `temp/` before edit (>500 LOC).
> No layout edits in this phase - no landscape parity step needed.

---

## Steps

### Step 01.1 - Make PDF page-fullscreen callable from the menu

**Files:** `PdfViewerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `enterFullscreenMode()` is `private` and reachable only from `handlePdfLongPress()`. Add a public entry `fun requestPdfFullscreen()` that calls `enterFullscreenMode()` (no behavior change to the fullscreen logic itself). This decouples fullscreen from the long-press gesture so the gesture can be repurposed in Step 01.3.

**Verification:**

- `Grep` - `fun requestPdfFullscreen()` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - body of `requestPdfFullscreen` calls `enterFullscreenMode(`.
- `Grep -n "Log\.d\("` on `PdfViewerManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.2 - Route overflow `menu_fullscreen` to PDF page-fullscreen when PDF active

**Files:** `PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> The overflow item `R.id.menu_fullscreen` is handled in `CommandPanelController` → `callback.onFullscreenClicked()`. In the callback implementation (`PlayerCommandPanelCallbackImpl.onFullscreenClicked`), when the current file type is `MediaType.PDF`, call `activity.pdfViewerManager.requestPdfFullscreen()` and return; otherwise keep the existing immersive-fullscreen behavior unchanged. This guarantees PDF page-fullscreen stays reachable after the long-press gesture is repurposed.

**Verification:**

- `Grep` - `requestPdfFullscreen()` referenced in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` - `MediaType.PDF` present in the `onFullscreenClicked` path of that file.
- `Grep -n "Log\.d\("` on the modified file returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.3 - Repurpose PDF long-press to open the text-selection overlay

**Files:** `PdfViewerManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Change `handlePdfLongPress()` so it no longer calls `enterFullscreenMode()`. Instead it opens the existing text-selection overlay for the current page by calling the same entry the TXT button uses (`pdfTextSelectionManager.enterTextSelectionMode(currentPdfPageIndex, currentPageBitmap, pdfRenderer)`). Keep returning `true` (handled). Inside the existing TXT-button click handler and this long-press path, route both through one private helper `openPdfTextSelection()` so the "Извлечение текста.." toast (string `pdf_text_extracting`) and the overlay open from a single place. Do not duplicate the toast logic.

**Verification:**

- `Grep` - `fun openPdfTextSelection(` matches exactly once in `PdfViewerManager.kt`.
- `Grep` - `handlePdfLongPress` body calls `openPdfTextSelection(` and does NOT call `enterFullscreenMode(`.
- `Grep` - `btnSelectTextPdf` click handler calls `openPdfTextSelection(`.
- `Grep` - `R.string.pdf_text_extracting` appears exactly once in `PdfViewerManager.kt` (single toast source).

**Status:** `[ ]` not done

---

### Step 01.4 - Build gate

**Files:** -
**Depends on:** Steps 01.1-01.3

**Prompt for developer:**

> Run `/build` → standard debug (`a.ps1 dq`). Resolve any compile error from the re-targeted handler. PASS required before Phase 02.

**Verification:**

- `/build` standard debug exits 0.
- `Grep` - `TODO(phase-01)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

PDF long-press now opens the text-selection overlay via `openPdfTextSelection()`. Phase 02 extends `PdfTextSelectionManager.enterTextSelectionMode` to accept tap coordinates and pre-select the word under the long-press point; `openPdfTextSelection()` is the place that will forward the coordinates.

---

## Rollback Plan

Revert phase commit(s) - restores long-press→fullscreen and menu routing. No data migration or persisted state changed.
