# Phase 04 - Sheet / Slide Viewer

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Extend the noLegal Office viewer stack from the Word-family to spreadsheets and presentations while keeping explicit family-specific navigation and fallback behavior.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] Strategic §6.1, §6.2, §6.3, and §6.7 blockers are Resolved.
- [x] The chosen engine coverage matrix for spreadsheet and presentation families is documented.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeSpreadsheetViewerDelegate.kt` | New | ≤ 280 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficePresentationViewerDelegate.kt` | New | ≤ 280 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt` | Modified | ≤ 680 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 920 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt` | Modified | ≤ 120 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Add spreadsheet rendering delegates

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeSpreadsheetViewerDelegate.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Introduce a dedicated spreadsheet delegate for `.xls`, `.xlsx`, and `.ods` families. Keep sheet navigation and read-only workbook state separate from the Word-family flow so unsupported spreadsheet features can fall back cleanly.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeSpreadsheetViewerDelegate.kt` exists.
- `Grep` - `class OfficeSpreadsheetViewerDelegate` matches exactly once in that file.
- `Grep` - `.xlsx` exists in the spreadsheet delegate or manager family resolver.

**Status:** `[x]` done

---

### Step 04.2 - Add presentation rendering delegates

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficePresentationViewerDelegate.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Introduce a dedicated presentation delegate for `.ppt`, `.pptx`, and `.odp` families. Keep slide navigation and presentation-only state separate from the Word-family and spreadsheet flows.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficePresentationViewerDelegate.kt` exists.
- `Grep` - `class OfficePresentationViewerDelegate` matches exactly once in that file.
- `Grep` - `.pptx` exists in the presentation delegate or manager family resolver.

**Status:** `[x]` done

---

### Step 04.3 - Switch navigation and active-state handling by family

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Teach the Office viewer manager to switch among page, sheet, and slide navigation models and to report the active family back to the shared player stack. Any family the chosen engine cannot render must route to the explicit fallback dialog instead of silently failing.

**Verification:**

- `Grep` - `OfficeDocumentFamily.SPREADSHEET` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `OfficeDocumentFamily.PRESENTATION` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `showExplicitFallbackDialog` or equivalent exists in `OfficeDocumentViewerManager.kt`.

**Status:** `[x]` done

---

### Step 04.4 - Mirror the family expansion in Standalone mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Extend Standalone mode so spreadsheet and presentation documents use the same provider seam and explicit fallback behavior as PlayerActivity. Keep the standalone branch read-only and finish only after the provider result is handled.

**Verification:**

- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in the Standalone Office branch.
- `Grep` - `external app` / `share` / `cancel` fallback wiring exists in `StandaloneViewManager.kt`.
- `Grep` - no unconditional `activity.finish()` remains ahead of provider-result handling in the Standalone Office branch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `assembleNoLegalDebug` BUILD SUCCESSFUL (1m 5s).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every modified file via `./scripts/add_to_dev_log.ps1` (MediaDisplayCoordinator.kt not touched — manager already reports active family, no change needed).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

All Office families now have an internal viewer path or an explicit fallback path; the next phase aligns command-panel actions and document-specific UX with PDF/EPUB parity.

---

## Rollback Plan

Revert phase commit(s) - no data migration or public market surface changed.