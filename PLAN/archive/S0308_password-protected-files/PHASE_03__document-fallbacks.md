# Phase 03 - Document Protection Fallbacks

**Strategic spec:** [`../S0308_password-protected-files.md`](../S0308_password-protected-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Classify protected PDF, EPUB, and Office failures as unsupported protection instead of generic broken-file errors.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] No PDF, Office, or DRM engine dependency is added in this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt` | Modified | existing >500 - backup required |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt` | Modified | existing >500 - backup required |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` | Modified | ≤ 260 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt` | Modified | ≤ 300 |

---

## Steps

### Step 03.1 - Classify protected PDF failures

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfViewerManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt`
**Depends on:** Phase 02 done

**Prompt for developer:**

> Detect `SecurityException` during PDF renderer creation and surface unsupported-protection copy instead of generic PDF read errors. Keep thumbnail code non-interactive; it may return `null` but must log without the file password or ticket id.

**Verification:**

- `Grep` - `protected_file_unsupported` exists in `PdfViewerManager.kt`.
- `Grep` - `SecurityException` exists in each modified PDF file.
- `Grep` - `Log.d(` returns zero hits in each modified PDF file.

**Evidence:**

- `grep_search`: expected `protected_file_unsupported` present in `PdfViewerManager.kt` | actual present.
- `grep_search`: expected `SecurityException` present in `PdfViewerManager.kt` | actual present.
- `grep_search`: expected `Log.d(` count 0 | actual 0.

**Status:** `[x]` done

---

### Step 03.2 - Classify protected EPUB failures

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Classify encrypted or DRM-like EPUB parse failures as unsupported protection when the error can be identified safely. Do not attempt DRM bypass. Keep ordinary parse errors on the existing EPUB parse failure message.

**Verification:**

- `Grep` - `protected_file_unsupported` exists in `EpubViewerManager.kt`.
- `Grep` - `DRM` exists in `EpubViewerManager.kt`.
- `Grep` - `Log.d(` returns zero hits in `EpubViewerManager.kt`.

**Evidence:**

- `grep_search`: expected `protected_file_unsupported` present in `EpubViewerManager.kt` | actual present.
- `grep_search`: expected `DRM` present in `EpubViewerManager.kt` | actual present.
- `grep_search`: expected `Log.d(` count 0 | actual 0.

**Status:** `[x]` done

---

### Step 03.3 - Preserve Office external fallback for protected files

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Keep public Office behavior as external fallback. In noLegal internal rendering, classify encrypted OOXML/ODF containers as unsupported protection and route to the existing fallback dialog. Do not add Office engine dependencies.

**Verification:**

- `Grep` - `OfficeDocumentEngineBridge` classifies `ZipException` as protected or unreadable container before generic failure.
- `Grep` - `ZipException` exists in `OfficeDocumentEngineBridge.kt`.
- `Grep` - no `poi-ooxml` dependency exists in `app_v2/build.gradle.kts`.
- `Grep` - `Log.d(` returns zero hits in both modified Kotlin files.

**Evidence:**

- `grep_search`: expected `ZipException` present in `OfficeDocumentEngineBridge.kt` | actual present.
- `grep_search`: expected `poi-ooxml` dependency count 0 | actual 0.
- `grep_search`: expected `Log.d(` count 0 in modified Office path | actual 0.
- Existing `PlayerShareManager.showOfficeFallbackDialog` keeps external / share / cancel fallback for Office provider failures.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - targeted unit test command compiled `standardDebug` and exited 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase updates documentation and validates the full code path.

---

## Rollback Plan

Revert phase commits. Document viewers return to generic parse/read failure behavior.