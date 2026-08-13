# Phase 02 - Wiring and fallback

**Strategic spec:** [`../S1276_pdf-select-words-directly-on-the-page.md`](../S1276_pdf-select-words-directly-on-the-page.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Route the long-press pre-selection through the native layout on API 35+, keep OCR as the fallback, and stop the overlay from opening silently when neither route resolves anything.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `CODE.LOCK` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt` | Modified | <= 340 |
| `app_v2/src/main/res/values/strings.xml` + `-ru` + `-uk` | Modified | - |

---

## Steps

### Step 02.1 - Produce the layout during native extraction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `extractTextNative` currently returns only a joined string and throws the geometry away. Make it also produce the layout.
>
> Introduce a private `data class PageText(val text: String, val nativeLayout: PdfNativeTextLayout?)` and change `extractPageText` to return it. `extractTextNative` builds the layout via `PdfNativeTextLayout.from(page.getTextContents(), page.width, page.height, bitmap.width, bitmap.height)` inside the same `openPage`/`close` block - the page must not be reopened for it, and the bitmap dimensions come from the `bitmap` parameter, which is null on the TXT-button path.
>
> `nativeLayout` stays null on the OCR path, on API < 35, when the native text is blank (scanned PDF - the existing blank check already falls through to OCR), and when `bitmap` is null.
>
> `extractPageTextForTts` keeps its `String` signature - TTS wants the text only. Return `.text` from the new type rather than duplicating the extraction.

**Verification:**

- `Grep` - `PdfNativeTextLayout.from` matches exactly once in `PdfTextSelectionManager.kt`, inside the `openPage` block.
- `Grep` - `extractPageTextForTts` still returns `String`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 02.2 - Prefer the native layout in `preselectWordAt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> `preselectWordAt` takes the layout as a new parameter. When it is non-null, map the view point to bitmap coordinates with the existing `PdfSelectionCoordinateMapper.viewToBitmap` and call `layout.charRangeForPoint` - **no OCR call at all on this branch**. When it is null, keep today's `recognizeTextBlocksForSelection` path exactly as it is.
>
> The OCR call is the thing this ticket removes from the owner's device; the comment above it must say so, not merely say what the branch does.
>
> Return a Boolean so the caller knows whether anything was selected, and use it in Step 02.3.

**Verification:**

- `Grep` - `recognizeTextBlocksForSelection` matches exactly once in `PdfTextSelectionManager.kt`, inside the null-layout branch.
- Read `preselectWordAt` and confirm the native branch returns before reaching the OCR call - `expected: no OCR on the native branch | actual: <observed>`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 02.3 - Say so when nothing could be pre-selected

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfTextSelectionManager.kt`, `app_v2/src/main/res/values/strings.xml` + `-ru` + `-uk`
**Depends on:** Step 02.2

**Prompt for developer:**

> When a point was supplied but Step 02.2 returned false, show a short non-blocking notice on the overlay - not a Toast on top of a Toast, the extraction one already fired. Reuse whatever inline notice the overlay layout already has; add one only if it has none.
>
> New key `pdf_text_selection_word_not_found`, EN "Could not find that word - select it manually", with real RU and UK translations. House style: `..` never `...`, plain hyphen, Russian `ё` where grammatical.
>
> Add the string with the tool, not by hand - these live in `src/main/res`, which is what `set-android-string.ps1 -Action add` covers: `-Action add -Key pdf_text_selection_word_not_found -En "<en>" -Ru "<ru>" -Uk "<uk>"`.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "pdf_text_selection_word"` - exit 0.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 02.4 - Closure

**Files:** repository-wide
**Depends on:** Step 02.3

**Prompt for developer:**

> Record the capability through `scripts/all_features/add.ps1`, area read off the sibling PDF records. `-FeatFlavors` is `standard,noLegal,legacy,vr` - the flavors where `SUPPORT_DOCUMENTS` is true per the strategic spec section 3.3; confirm against `app_v2/build.gradle.kts` rather than copying that line.
>
> Then `scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile` for the touched files, `catalog_sync.ps1 -Module app_v2` once, and `set.ps1` for the new class.
>
> This ticket is emulator-verifiable, so it does **not** park in `BlockNeedUserTest` for lack of hardware. Either run the device check (API 35+ image, a text-layer PDF, long-press a repeated word) and let `/spec-check` close it, or park it with a status note naming the two images needed.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -ChangedFiles <every touched .kt> -Gate` - PASS.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes and the Phase 01 unit test still passes.
- [x] Dev log entry added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

None - final phase.

---

## Rollback Plan

Revert `PdfTextSelectionManager.kt`; the Phase 01 class and its test become unreferenced but still compile and pass.
