# Text Document Playback Improvement Specification

## 1. Purpose

To transform the basic "Text Viewer" into a professional-grade "Document Reader" and "Code Editor". The goal is to handle large files (logs, books) without crashing, support various encodings (Windows-1251, ISO-8859-1), and offer a comfortable reading experience for Markdown, Code, and plain text.

## 2. Scope

### In Scope
1.  **Performance & Stability:**
    *   **Large File Support:** Implement "Paging" or "Chunking" to read files >10MB (currently hardcoded limit).
    *   **Encoding Detection:** Auto-detect charset (using library like `juniversalchardet` or simple BOM checks) instead of forcing UTF-8.
2.  **Readability (Reader Mode):**
    *   **Markdown Rendering:** Render `.md` files with headers, bold, code blocks (using `Markwon` or similar).
    *   **Code Highlighting:** Syntax highlighting for `.kt`, `.json`, `.xml`, `.py` (using `CodeView` or `Prettify`).
    *   **Theming:** Sepia / Dark / Light modes independent of system theme.
    *   **Text-to-Speech (TTS):** "Read Aloud" feature for books/articles.
3.  **Editing:**
    *   **Line Numbers:** Toggleable line numbers (already partially there, needs optimization).
    *   **Search in Text:** robust "Find on Page" (already in `SearchControlsManager`, needs refinement for large files).
    *   **Auto-Save:** Draft saving to prevent data loss.

### Out of Scope
1.  Full IDE features (Compile, Run, Debug).
2.  Rich Text (RTF) or Word (DOCX) editing (Viewing only via conversion/library if verified, otherwise out).

## 3. Current State Analysis

### 3.1 Architecture
*   **Manager:** `TextViewerManager` loads the entire file into memory string (`file.readText()`).
*   **Limit:** Hardcoded `appSettings.textSizeMax` check prevents opening large logs.
*   **Encoding:** Hardcoded `InputStreamReader(..., Charsets.UTF_8)`. Fails on legacy Windows-1251 text files.
*   **UI:** `ScrollView` + `TextView`. Inefficient for huge texts (laggy scrolling).

## 4. Objectives

1.  **Open Anything:** I can open a 500MB log file without OOM (Out Of Memory).
2.  **Read Comfortably:** Markdown renders properly; Code is colored.
3.  **Global Support:** My old Russian/European text files display correctly (not mojibake).

## 5. Technical Proposal

### 5.1 Large File Handling (The "Chunker")

Replace `readText()` with a `RandomAccessFile` based pager.
*   **Logic:** Read file in 50kb chunks.
*   **UI:** `RecyclerView` driven text adapter (infinite scroll) OR simple "Page 1 / 500" pagination logic.
*   *Recommendation:* Pagination is safer and easier to implement for a file viewer.

### 5.2 Encoding Engine

Introduce `CharsetDetector`.
*   Probe first 4KB of file.
*   Check BOM.
*   Heuristic check (if contains many unknown symbols, try localized charsets).
*   **UI:** Allow user to manually "Re-open with Encoding..." via menu.

### 5.3 Rich Renderer

*   **Markdown:** Use `io.noties.markwon:core`.
*   **Code:** Use `highlight.js` (via WebView) OR native spannable highlighter (e.g., `HighlightR`).
*   *Performance Note:* For large code files, disable highlighting or highlight only visible range.

## 6. Implementation Stages

### Phase 1: Core IO Improvements
*   Implement `TextFilePager` class.
*   Implement `CharsetDetector` utility.
*   Add "Encoding" option in Player Menu.

### Phase 2: Reader UI
*   Integrate Markdown rendering.
*   Add "Text Settings" dialog (Font, Size, Theme, Encoding).
*   Implement "Text-to-Speech" player control overlay.

### Phase 3: Editor Enhancements
*   Add Undo/Redo stack.
*   Implement "Find and Replace".
*   Add gutter for line numbers (using `RecyclerView` decoration).

## 7. Migration Risks

*   **Risk:** Pagination limits searching.
    *   *Mitigation:* "Search" must scan the file on disk (background thread) and jump to the specific page/chunk, not just search the current view.
*   **Risk:** Markdown parsing slowness.
    *   *Mitigation:* Parse on background thread, potentially cache rendered Spans for pages.

## 8. Acceptance Criteria

1.  Can open a 100MB text file within 2 seconds.
2.  Can toggle between "Raw Text" and "Rendered Markdown" for .md files.
3.  ANSI colors in log files are rendered (optional but nice).
4.  User can manually selecting "Windows-1251" to fix broken text.
