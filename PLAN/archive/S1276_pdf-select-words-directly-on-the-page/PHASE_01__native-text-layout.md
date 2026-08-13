# Phase 01 - Native text layout

**Strategic spec:** [`../S1276_pdf-select-words-directly-on-the-page.md`](../S1276_pdf-select-words-directly-on-the-page.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-29
**Completed:** 2026-07-29

---

## Objective

Turn the API 35+ page content into a structure that answers "which characters are under this point" without OCR, and prove the answer with a JVM unit test. Nothing is wired to the UI in this phase.

---

## Prerequisites

- [x] `scripts/utils/lock-status.ps1 -Name Build` shows no live build; acquire `CODE.LOCK` before edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfNativeTextLayout.kt` | New | <= 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PdfNativeTextLayoutTest.kt` | New | <= 180 |

---

## Steps

### Step 01.1 - The layout model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfNativeTextLayout.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PdfNativeTextLayout` - a pure data holder plus one lookup, no Android framework calls beyond `RectF`, so it is JVM-testable.
>
> - `data class Item(val range: IntRange, val boxes: List<RectF>)` - the char range this item occupies in the assembled page text, and its bounding boxes in **bitmap pixel** coordinates.
> - `class PdfNativeTextLayout(val text: String, val items: List<Item>)`.
> - `fun charRangeForPoint(x: Float, y: Float): IntRange?` - return the range of the item one of whose boxes contains the point; when none contains it, the nearest item by squared distance from a box centre, mirroring the existing OCR mapper's behaviour so the two paths feel the same. Null only when `items` is empty.
>
> Do not reuse `PdfSelectionCoordinateMapper.charRangeForPoint`: that one searches the page text for a word string, which is the first-occurrence bug this path exists to avoid. State that in the KDoc so a later reader does not "unify" the two.

**Verification:**

- `Glob` - `PdfNativeTextLayout.kt` exists.
- `Grep` - `indexOf` does NOT match in `PdfNativeTextLayout.kt`.
- `Grep` - `import android.graphics.RectF` is the only `android.` import in the file.

**Status:** `[x]` done

---

### Step 01.2 - Build it from the platform content items

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfNativeTextLayout.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a companion factory `fun from(contents: List<PdfPageTextContent>, pageWidth: Int, pageHeight: Int, bitmapWidth: Int, bitmapHeight: Int): PdfNativeTextLayout`, annotated `@RequiresApi(35)`.
>
> - Assemble the text by joining item texts with a single space, exactly as `extractTextNative` does today, and record each item's `IntRange` as it is appended. The join separator must stay one space or the ranges drift from the text the overlay shows.
> - Scale each `RectF` from PDF page points to bitmap pixels: `sx = bitmapWidth / pageWidth.toFloat()`, `sy = bitmapHeight / pageHeight.toFloat()`, applied to left/right and top/bottom respectively. Guard a zero or negative page dimension by returning an empty layout rather than dividing.
> - Skip items whose text is blank so a spacer item cannot become the nearest match.
>
> Keep every line at 120 characters or less and use named constants for any literal other than -1/0/1/2 (S0826).

**Verification:**

- `Grep` - `@RequiresApi(35)` matches on the factory.
- `Grep` - `PdfPageTextContent` matches in `PdfNativeTextLayout.kt`.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test the lookup

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PdfNativeTextLayoutTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Pure JVM test over `PdfNativeTextLayout` constructed directly (not through the `@RequiresApi` factory, which needs a platform type):
>
> - a point inside an item's box returns that item's range;
> - a point outside every box returns the nearest item's range, not the first;
> - **the repeated-word case**: three items all reading "the", at different boxes - pressing the third returns the third range, which is the regression the OCR mapper cannot pass;
> - an item spanning two lines (two boxes, one range) matches from either box;
> - an empty item list returns null.
>
> `RectF` is a framework class, so the test needs Robolectric or the `unitTests.returnDefaultValues` path; check which the neighbouring player-helper tests already use and follow it rather than introducing a second convention.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.player.helpers.PdfNativeTextLayoutTest"` - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes.
- [x] The new unit test passes.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`PdfNativeTextLayout` is referenced nowhere yet. Phase 02 makes `extractTextNative` produce one and routes `preselectWordAt` through it.

---

## Rollback Plan

Delete both new files - no existing call site references them.
