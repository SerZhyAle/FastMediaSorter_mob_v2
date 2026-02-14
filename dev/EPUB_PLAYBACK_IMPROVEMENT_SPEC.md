# EPUB Document Playback Improvement Specification

## 1. Purpose

To elevate the EPUB reader from a simple "HTML Viewer" to a rich "E-Book Reader" experience comparable to Moon+ Reader or Apple Books. The focus is on navigation (TOC), structural awareness, and reading comfort.

## 2. Scope

### In Scope
1.  **Navigation:**
    *   **Table of Contents (TOC):** A side drawer or bottom sheet showing the book's hierarchy, allowing one-tap jumping to chapters.
    *   **Detailed Progress:** Show "Chapter X of Y" and "Page Z of Total" (estimated).
2.  **Search:**
    *   **Full Book Search:** Searching text across *all* chapters, not just the current one.
    *   **In-Page Search:** Highlighting occurrences in the current WebView.
3.  **Visual Customization:**
    *   **Themes:** extended themes (Sepia, OLED Black, Blue Light Filter).
    *   **Margins & Line Height:** User-adjustable layout settings.
4.  **Book Experience:**
    *   **Horizontal Paging:** Option to switch from Vertical Scroll to Horizontal "Page Snap" mode (CSS column-width trick).

### Out of Scope
1.  **DRM Support:** We will not support Adobe DRM or other encrypted books.
2.  **Cloud Sync:** Syncing reading position across devices (unless via simple file timestamp).

## 3. Current State Analysis

### 3.1 Architecture
*   **Manager:** `EpubViewerManager` uses `epub4j` to parse and `WebView` to render.
*   **Navigation:** Only "Next/Prev Chapter" buttons. No list of chapters.
*   **Styling:** Hardcoded CSS injection in `preprocessHtml`. Limited to Font Size and Font Family.

## 4. Objectives

1.  **Structural Navigation:** I can open the TOC and jump to "Chapter 5: The End" immediately.
2.  **Search:** I can find where "Harry Potter" is mentioned in the whole book.
3.  **Reader Comfort:** I can set the background to Sepia and increase line spacing for night reading.

## 5. Technical Proposal

### 5.1 Table of Contents (TOC)

Extract TOC from `book.tableOfContents`.
*   **UI:** `RecyclerView` in a navigation drawer.
*   **Data:** Flatten the nested TOC tree into a linear list with indentation.

### 5.2 Full Text Search

Running search on `epub4j`'s content without loading into WebView.
*   **Indexer:** On first open, (optionally) build a Lucene index or simple regex scan of all resources in a background thread.
*   **Result UI:** A list showing "Chapter X: ...found text context...".

### 5.3 Advanced Styling

Enhance `css` injection in `preprocessHtml`.
*   Add customizable CSS variables: `--line-height`, `--margin-x`, `--bg-color`, `--text-color`.
*   **Horizontal Paging:** Inject `html { height: 100vh; column-width: 100vw; }` to force horizontal layout in WebView.

## 6. Implementation Stages

### Phase 1: Navigation Upgrade
*   Implement `TocAdapter` and `TocBottomSheetFragment`.
*   Populate TOC from `epubReader`.

### Phase 2: Styling Engine
*   Create `EpubStyleManager` class to generate CSS.
*   Add settings dialog for Margins, Line Height, and Theme selection.

### Phase 3: Search
*   Implement `EpubSearchUseCase`.
*   Add Search UI overlay.

## 7. Migration Risks

*   **Risk:** `epub4j` performance on large books.
    *   *Mitigation:* Lazy load chapters. Search should be an explicit user action ("Scan whole book"), not automatic.
*   **Risk:** WebView Horizontal Paging quirks.
    *   *Mitigation:* This CSS trick is standard but can glitch with large images. Need CSS `img { max-width: 100%; height: auto; }` overrides.

## 8. Acceptance Criteria

1.  User can open TOC and navigate to a nested chapter.
2.  User can change line height and margins.
3.  User can search for a string and see results from multiple chapters.
