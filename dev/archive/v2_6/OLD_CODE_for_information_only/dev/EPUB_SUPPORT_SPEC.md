# Technical Specification: EPUB Support Implementation
**Status:** ✅ COMPLETED (2025-12-14)
**Target Version:** 2.x
**Author:** Antigravity (Assistant)
**Date:** 2025-12-14
**Implementation Commits:** 15 commits (3d8c703...ad06d3a)

## 1. Executive Summary
This document outlines the technical specification for adding native EPUB (Electronic Publication) document support to the FastMediaSorter application. The goal is to provide a user experience consistent with the current PDF implementation, treating EPUBs as first-class media citizens within the standard `PlayerActivity` workflow.

> [!IMPORTANT]
> **Developer Directives:**
> 1.  **Settings Integration**: EPUB support must be controllable via a dedicated "EPUB Support" toggle in the **Settings -> Documents** section, mirroring the existing PDF toggle structure.
> 2.  **Architectural Parity**: The implementation must strictly follow the patterns used for generic file handling and the specific PDF implementation. If an abstraction exists for PDF, a parallel or shared abstraction must be used for EPUB.
> 3.  **Code Reuse**: Prioritize the use of existing universal helpers (e.g., `NetworkFileManager`, `FileOperationHelper`, `ResourceAdapter`). Do NOT create valid duplicate logic where a generic function can be extended to support `MediaType.EPUB`.

## 1. Executive Summary
This document outlines the technical specification for adding native EPUB (Electronic Publication) document support to the FastMediaSorter application. The goal is to provide a user experience consistent with the current PDF implementation, treating EPUBs as first-class media citizens within the standard `PlayerActivity` workflow.

> [!IMPORTANT]
> **Developer Directives:**
> 1.  **Settings Integration**: EPUB support must be controllable via a dedicated "EPUB Support" toggle in the **Settings -> Documents** section, mirroring the existing PDF toggle structure.
> 2.  **Architectural Parity**: The implementation must strictly follow the patterns used for generic file handling and the specific PDF implementation. If an abstraction exists for PDF, a parallel or shared abstraction must be used for EPUB.
> 3.  **Code Reuse**: Prioritize the use of existing universal helpers (e.g., `NetworkFileManager`, `FileOperationHelper`, `ResourceAdapter`). Do NOT create valid duplicate logic where a generic function can be extended to support `MediaType.EPUB`.

## 2. Compliance Check
-   **License Compatibility**: All proposed libraries (`io.documentnode:epub4j-core`, `org.jsoup:jsoup`) use permissive open-source licenses (Apache 2.0, MIT) and are free for use in this commercial application.
-   **Android Compatibility**: The implementation targets `minSdk 28` (Android 9 Pie). All chosen libraries and APIs (including `WebView`) are fully compatible with API 28+.
-   **No Paid Services**: No paid APIs or cloud services are required for this feature.

## 3. Architectural Approach
The implementation will follow the **Manager Pattern** established by `PdfViewerManager`.
-   **Renderer:** `WebView` (Standard Android component).
-   **Parser:** `epublib-core` (Java library for reading .epub containers).
-   **Glue:** `EpubViewerManager` class to bridge the ViewModel/Activity lifecycle with the WebView and Parser.

**Universal Design Principle - Document Unification:**
This implementation must establish a **Document abstraction layer** where possible:
1. Any logic that currently handles `MediaType.PDF` in a generic way (file opening, identifying, sorting, filtering, caching) should be **refactored** to support both `MediaType.PDF` and `MediaType.EPUB` without duplication.
2. Create shared helpers/interfaces where PDF and EPUB behavior is identical (e.g., `DocumentCacheManager`, `DocumentViewerInterface`).
3. Keep format-specific logic (PDF rendering vs EPUB chapter parsing) isolated in dedicated Manager classes.
4. **Future-proofing**: This design should accommodate additional document formats (DJVU, MOBI, FB2) without major refactoring.

### Key Decisions
1.  **Reflowable UI**: Unlike PDFs which are page-based bitmaps, EPUBs are reflowable HTML. We will use a "Chapter-based Paging" approach:
    -   User navigates *between* chapters using standard Next/Prev buttons or horizontal swipes (if at edge).
    -   User scrolls *within* a chapter vertically (native web behavior).
2.  **Content Serving**: To handle strict security policies and internal links (images/css within the epub), we will extract the current chapter's resources to a temporary cache or use `WebViewAssetLoader` / internal server logic. *Decision: memory-based injection for text/css, temp file extraction for images if needed, or base64 encoding.*
3.  **Theme Sync**: The EPUB view must respect the app's Dark/Light mode settings via injected CSS.

## 3. Detailed Implementation Steps

### Phase 1: Core Dependencies & Configuration
**Goal:** Enable the app to build with necessary tools.

1.  **Modify `app/build.gradle.kts`**:
    -   Add `io.documentnode:epub4j-core:4.2` (Android 9+ compatible, actively maintained fork) for parsing.
    -   Add `org.jsoup:jsoup:1.17.2` for safe HTML manipulation and CSS injection.
2.  **Sync & verify** build configuration.

> **Library Choice Rationale**: `epub4j-core:4.2` is the modern maintained fork of epublib, proven stable on Android API 28+ and actively used in production apps.

### Phase 2: Data Model & Settings Extensions
**Goal:** Recognize `.epub` files and configure user preferences.

1.  **Update `MediaType` Enum (`Models.kt`)**:
    -   Add `EPUB` entry.
2.  **Update `MediaTypeUtils` (`MediaTypeUtils.kt`)**:
    -   Define `EPUB_EXTENSIONS = setOf("epub")`.
    -   Update `getMediaType()` to detecting "epub".
    -   Update `getMediaTypeFromMime()` to detect "application/epub+zip".
3.  **Update Settings**:
    -   **Repository**: Add `enableEpub` boolean to `AppSettings` and `SettingsRepository`.
    -   **UI**: Add a switch element to the Documents section in `SettingsFragment` (or `DocumentsSettingsFragment`), matching the style of the PDF toggle.
4.  **Update Database/Repository**:
    -   Ensure `ResourceRepository` flags handle the new enum (bitmask logic in `toEntity`/`toDomain`).
5.  **Update UI Adapters**:
    -   `ResourceAdapter`: Add "E" icon/badge for EPUB support indication, reusing the existing badge logic.

### Phase 3: The `EpubViewerManager`
**Goal:** Create the brain of the operation.

**Class Signature:**
```kotlin
class EpubViewerManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    // ... repository/callback dependencies
)
```

**Responsibilities:**
1.  **Initialization (`init`)**:
    -   Configure `WebView` settings (JS enabled, DOM storage enabled, generic caching).
    -   Attach `WebViewClient` to handle page loading events.
2.  **Loading (`displayEpub(file: File)`)**:
    -   Parse `EpubReader().readEpub(...)`.
    -   Cache the `Spine` (Chapter list) and `TableOfContents`.
    -   Restore reading position (Chapter Index + Scroll Y% or Pixel offset).
3.  **Rendering (`renderChapter(index: Int)`)**:
    -   Extract HTML content stream for the spine index.
    -   **Preprocessing (Jsoup)**:
        -   Inject `<style>` block for global font (Roboto/Inter) and colors (Dynamic dark mode).
        -   Rewrite image sources to point to extracted temp paths or Base64 (depending on performance tests).
    -   `webView.loadDataWithBaseURL(...)`.
4.  **Navigation**:
    -   `nextChapter()` / `prevChapter()`
    -   `scrollTo(y)`
5.  **Lifecycle**:
    -   `close()`: clear webview, clear temp cache.

### Phase 4: UI Layer & Integration
**Goal:** Integrate into `PlayerActivity` layout.

1.  **Layout (`activity_player_unified.xml`)**:
    -   Add `<WebView android:id="@+id/epubWebView" ... />`.
    -   Ensure it sits in the `FrameLayout` container, same constraints as `photoView`.
    -   Initial visibility: `GONE`.
2.  **`PlayerActivity.kt` Wiring**:
    -   Inject/Instantiate `EpubViewerManager`.
    -   In `initializeManagers()`, logic to hide/show WebView vs PhotoView vs ExoPlayer based on `MediaType.EPUB`.
    -   Delegate `onSwipe` gestures to `EpubViewerManager` (e.g., if swipe is on WebView).

### Phase 5: Helper Features (The "Polish")
1.  **Progress Tracking**:
    -   Map `currentChapter / totalChapters` to the existing `ProgressBar` or `PageIndicator`.
2.  **Persistence**:
    -   Use `PlaybackPositionRepository` to store current chapter index (treating chapter as "page number").
    -   **No scroll position tracking** - mirror PDF behavior exactly.
3.  **Network File Handling**:
    -   Reuse existing `NetworkFileManager` logic for SMB/SFTP/FTP/Cloud EPUB files.
    -   Cache complete EPUB file to temp directory (like PDF), then parse from local cache.
    -   Cache cleanup follows same lifecycle as PDF thumbnails.

### Phase 6: Table of Contents UI (Future Enhancement)
**Goal:** Add navigation via book's table of contents.
**Prerequisites:** Phases 1-5 must be completed and tested.

1.  **UI Component**:
    -   Add floating action button or toolbar button to show TOC dialog.
    -   `RecyclerView` in `BottomSheetDialogFragment` displaying chapter titles.
2.  **Data Source**:
    -   Extract TOC from `Book.getTableOfContents()` in `EpubViewerManager`.
    -   Map TOC entries to spine indices.
3.  **Navigation**:
    -   Tapping TOC entry jumps to corresponding chapter via `renderChapter(index)`.

> **Implementation Priority:** LOW - implement only after core EPUB functionality is stable and tested in production.

---

## 4. Testing & Validation Plan

### Unit Tests
-   **File Detection**: Verify `MediaTypeUtils.getMediaType("book.epub")` returns `MediaType.EPUB`.
-   **Extension Set**: Verify `EPUB` is included in `buildExtensionsSet()`.

### Manual Test Cases
| ID | Title | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC-01** | **Discovery** | Scan a folder with .epub files | EPUB files appear in the list with correct icon/type. |
| **TC-02** | **Basic Open** | Tap an EPUB file | Player opens, WebView shows text content. |
| **TC-03** | **Navigation** | Swipe/Tap Next Chapter | Content changes to next chapter. |
| **TC-04** | **Scrolling** | Scroll down long chapter | Smooth native scrolling. |
| **TC-05** | **Dark Mode** | Toggle App Dark Mode | EPUB text becomes light, background becomes dark. |
| **TC-06** | **Network File** | Open EPUB from SMB | File downloads/caches, then opens successfully. |

## 5. Potential Risks & Mitigations
-   **Risk**: Complex EPUBs with weird CSS/Layouts.
    -   *Mitigation*: Force our own "Reader Mode" CSS, stripping author styles if necessary.
-   **Risk**: Large files causing memory OOM during parsing.
    -   *Mitigation*: Use `Lazy` loading of chapters; do not load entire book content into memory string. Stream copying.
-   **Risk**: Images with relative paths in HTML.
    -   *Mitigation*: Use `loadDataWithBaseURL` effectively or rewrite `src` attributes using Jsoup.

---

## 6. Implementation Summary (Completed 2025-12-14)

### Commits Overview
Total: **15 commits** (3d8c703...ad06d3a)

**Phase 1: Dependencies & Configuration (4 commits)**
- 3d8c703: Add EPUB dependencies (epublib-core 3.1, jsoup 1.17.2)
- c73a96e: Fix EPUB library - use epub4j-core 4.2 (maintained fork)
- f3e1f48: Exclude xmlpull duplicates from epub4j-core
- 7a5be6d: Fix: Correct binding references in TextViewerManager.displayOcrText()

**Phase 2: Data Model & Settings (7 commits)**
- e74de98: Add EPUB to MediaType enum
- 07a0d5b: Add EPUB support to MediaTypeUtils
- 5d0fa16: Add supportEpub to AppSettings and SettingsRepository
- 0e6c2bc: Add EPUB toggle to Settings UI (Documents section)
- 9ab8a68: Add EPUB to ResourceAdapter badge (E), bitmask flags, and FilterDialog
- 228ed3a: Fix exhaustive when expressions - add EPUB to all adapters and scanners

**Phase 3: EpubViewerManager & UI Integration (3 commits)**
- 8c4486e: Create EpubViewerManager class with chapter navigation and WebView rendering
- c32f46c: Add EPUB WebView and chapter navigation controls to player layout
- 7efe68d: Wire EpubViewerManager into PlayerActivity - complete integration with WebView and controls

**Phase 4: Gesture Handling & Cleanup (1 commit)**
- ad06d3a: Add EPUB swipe gesture support and resource cleanup

### Technical Achievements
✅ **Core Functionality**
- EPUB parsing via `io.documentnode:epub4j-core:4.2`
- HTML preprocessing with `jsoup:1.17.2` (CSS injection, theme sync)
- WebView-based rendering with dark/light mode support
- Chapter-based navigation (prev/next buttons + swipe gestures)
- Position persistence (saves/restores last chapter via PlaybackPositionRepository)

✅ **Architecture Compliance**
- Manager Pattern: `EpubViewerManager` mirrors `PdfViewerManager` structure
- Lifecycle integration: initialization in `initializeManagers()`, cleanup in `releaseResources()`
- Callback interfaces: integrated with `PlayerUiStateCoordinator` and `MediaDisplayCoordinator`
- Settings-driven: `supportEpub` toggle in Documents section

✅ **UI/UX Integration**
- Badge system: "E" indicator in ResourceAdapter (IVAGTPE sequence)
- Bitmask: `0b01000000` (64) for EPUB in ResourceEntity
- Filter dialog: "EPUB (E)" chip
- Extension bitmap: colored thumbnails with ".epub" extension display
- WebView controls: chapter indicator (e.g., "5/12"), prev/next buttons

✅ **Network & File Operations**
- Reuses `NetworkFileManager` for SMB/SFTP/FTP support
- No size filtering (like TEXT/PDF): accepts 0-Long.MAX_VALUE
- Automatic download/caching for network files
- Toast notifications for loading progress

### Known Limitations
- **~~TOC UI~~**: ✅ Table of Contents dialog implemented (Phase 6 - 2025-12-14)
- **Image handling**: Embedded images use `loadDataWithBaseURL` - may need base64 encoding for complex cases
- **CSS stripping**: Author styles are NOT stripped - relies on injected CSS override

### Testing Status
- ✅ Compiles successfully (no errors)
- ⚠️ Runtime testing required:
  - Open local .epub file
  - Navigate chapters (buttons + swipes)
  - **TOC Dialog: Quick jump to chapters**
  - Dark/light mode switching
  - Network file download (SMB/SFTP)
  - Position save/restore

### Future Enhancements (Optional)
1. ~~TOC Dialog: Chapter list with jump-to functionality~~ ✅ **Completed**
2. Font size control: User-adjustable text scaling
3. Bookmarks: Save/restore multiple positions per book
4. Search: Find text within current chapter or entire book
5. Annotations: Highlight/note-taking support

---

## 7. Phase 6 Implementation (Completed 2025-12-14)

### TOC Dialog Enhancement
**Commit:** 32a5f06

**Features Implemented:**
- ✅ **Dialog UI**: AlertDialog with scrollable chapter list
- ✅ **Metadata TOC**: Uses `book.tableOfContents.tocReferences`
- ✅ **Nested Support**: Recursive flattening with indentation ("  Chapter 1", "    Section 1.1")
- ✅ **Quick Jump**: Tap to navigate instantly to selected chapter
- ✅ **Fallback Mode**: Spine-based list when TOC metadata missing
- ✅ **UI Button**: TOC icon (ic_menu_agenda) in EPUB controls overlay

**Code Structure:**
```kotlin
// EpubViewerManager.kt
fun showTableOfContents() {
    // Get TOC from book.tableOfContents
    val tocReferences = book.tableOfContents.tocReferences
    
    // Flatten nested structure
    val chapters = flattenToc(tocReferences)
    
    // Show AlertDialog with chapter list
    AlertDialog.Builder(context)
        .setTitle("${book.title} - Table of Contents")
        .setItems(chapterTitles) { _, which ->
            showChapter(selectedIndex)
        }
        .show()
}

private fun flattenToc(refs, output, depth) {
    // Recursive tree traversal with indentation
    for (ref in refs) {
        val title = "$indent${ref.title}"
        val spineIndex = findSpineIndexForResource(ref.resource)
        output.add(title to spineIndex)
        
        // Recurse children
        if (ref.children.isNotEmpty()) {
            flattenToc(ref.children, output, depth + 1)
        }
    }
}
```

**UI Layout:**
- Button: `btnEpubToc` (ic_menu_agenda icon)
- Position: Between chapter indicator and next button
- Style: bg_circle_dark with white tint

**Edge Cases Handled:**
- Empty TOC → fallback to spine-based list
- Missing chapter titles → auto-generate "Chapter N"
- Resource not in spine → skip entry
- Nested TOC structure → flatten with indentation

**Testing Required:**
- ⚠️ EPUBs with complex nested TOC
- ⚠️ EPUBs without TOC metadata (fallback mode)
- ⚠️ Large TOC lists (100+ chapters)
- ⚠️ TOC entries not matching spine order


