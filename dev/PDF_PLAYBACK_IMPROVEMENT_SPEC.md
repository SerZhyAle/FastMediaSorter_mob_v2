# PDF Document Playback Improvement Specification

## 1. Purpose

To upgrade the PDF reading experience from a "Slide Viewer" to a proper "Book Reader". The current implementation treats PDF pages like individual images, which is clunky for reading text-heavy documents. The goal is to introduce continuous scrolling, night mode, and better navigation.

## 2. Scope

### In Scope
1.  **Viewing Modes:**
    *   **Continuous Vertical Scrolling:** The standard way to read documents on mobile.
    *   **Horizontal Swiping (Legacy):** Keep existing mode for presentation/slideshow PDFs.
2.  **Visual Comfort:**
    *   **Night Mode:** Invert colors or apply sepia filter for low-light reading.
    *   **Double-Tap Zoom:** Smart zoom to column width (if possible via detection) or standard zoom.
3.  **Navigation:**
    *   **Thumbnail Grid:** A visual overview of pages to quickly jump sections.
    *   **Scroll Handle:** Fast scrub bar allows jumping to page 500 in seconds.

### Out of Scope
1.  **PDF Editing:** Annotations, signatures, form filling.
2.  **Reflow Mode:** Extracting text and wrapping it (too complex and error-prone for generic PDFs).
3.  **Library Replacement:** We will stick to `android.graphics.pdf.PdfRenderer` to keep app size small, unless strictly necessary for stability.

## 3. Current State Analysis

### 3.1 Architecture
*   **Manager:** `PdfViewerManager` uses `PdfRenderer` to render one page at a time into `currentPageBitmap`.
*   **View:** Uses `PhotoView` explicitly. This forces "Single Page" behavior.
*   **Missing:** No continuous scroll, no dark mode, no way to see next page while reading bottom of current page.

## 4. Objectives

1.  **Seamless Reading:** I can scroll from the bottom of Page 1 to the top of Page 2 without a black screen or swipe gesture.
2.  **Eye Comfort:** Reading at night doesn't blind me.
3.  **Fast Navigation:** I can find a specific chart in a 100-page report using thumbnails.

## 5. Technical Proposal

### 5.1 Vertical Scroll Engine

Switch from `PhotoView` to `RecyclerView` for the Vertical Mode.
*   **Adapter:** `PdfPageAdapter`.
*   **ViewHolder:** Contains a `SubsamplingScaleImageView` (better for high-res text) or standard `ImageView`.
*   **Memory:** `PdfRenderer` is not thread-safe. A single background thread must handle rendering requests from the Adapter.
*   **Caching:** Use an LruCache for rendered bitmaps to prevent stutter.

### 5.2 Night Mode Implementation

Apply a `ColorMatrixColorFilter` to the `ImageView`.
*   **Invert:** Turns white paper black, black text white.
*   **Sepia:** Reduces blue light.
*   **Logic:** `imageView.colorFilter = ColorMatrixColorFilter(NEGATIVE_MATRIX)`

### 5.3 Thumbnails

Use the same `PdfRenderer` to render low-res thumbnails (e.g., 100x150px).
*   **UI:** Bottom Sheet or Side Drawer.
*   **Performance:** These render very fast. Can be done on a background coroutine easily.

## 6. Implementation Stages

### Phase 1: View Modes
*   Refactor `PdfViewerManager` to support multiple View strategies.
*   Implement `VerticalPdfStrategy` using `RecyclerView`.
*   Add toggle button in UI to switch between "Page" and "Scroll".

### Phase 2: Comfort Features
*   Add "Night Mode" toggle in settings/overlay.
*   Implement `ColorConversion` utility for the views.

### Phase 3: Advanced Navigation
*   Implement `ThumbnailSidebar` view.
*   Connect Scroll Handle to Page Number indicator.

## 7. Migration Risks

*   **Risk:** `PdfRenderer` thread safety.
    *   *Mitigation:* Use a dedicated `SerialExecutor` or Mutex for all `openPage()` / `render()` calls across main view and thumbnails.
*   **Risk:** Memory usage in RecyclerView.
    *   *Mitigation:* Aggressive recycling. Bitmaps for pages are large (e.g., 2000x3000 pixels = 24MB). Need to strictly limit cached bitmaps to 3-4 screens.

## 8. Acceptance Criteria

1.  User can scroll vertically through a 50-page PDF smoothly.
2.  Night mode inverts colors correctly without lagging scrolling.
3.  Thumbnail grid appears instantly and clicking jumps to page.
