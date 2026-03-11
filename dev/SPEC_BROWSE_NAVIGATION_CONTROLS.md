# Specification: Browse Navigation Controls Enhancement

## 1. Goal
Improve the user experience during folder browsing by providing more granular navigation controls and optimizing the visibility of existing scroll-to-top/bottom buttons based on the current scroll position.

## 2. Requirements

### 2.1. Dynamic Visibility for Scroll-to-Top/Bottom
- **Scroll to Top (`fabScrollToTop`)**: 
    - Hide if the list is already at the beginning (first visible item position == 0).
    - Show otherwise (if `fileCount > 20`).
- **Scroll to Bottom (`fabScrollToBottom`)**: 
    - Hide if the list is already at the end (last visible item position == `itemCount - 1`).
    - Show otherwise (if `fileCount > 20`).

### 2.2. New "Page Navigation" Buttons
Add two new buttons to the scroll control group:
- **Page Up (`fabPageUp`)**:
    - Position: Slightly below `fabScrollToTop`.
    - Action: Scroll the list up by one viewport height (page).
    - Visibility: Hide if at the beginning of the list.
- **Page Down (`fabPageDown`)**:
    - Position: Slightly above `fabScrollToBottom`.
    - Action: Scroll the list down by one viewport height (page).
    - Visibility: Hide if at the end of the list.

### 2.3. Visual Consistency
- Use the same style as existing `fabScrollToTop`/`fabScrollToBottom` (circular buttons with translucent background).
- Icons: 
    - Page Up: `ic_keyboard_arrow_up` (or similar).
    - Page Down: `ic_keyboard_arrow_down` (or similar).
- Layout: Align buttons vertically on the right side of the `RecyclerView`.

## 3. Technical Implementation Details

### 3.1. Layout Changes (`activity_browse.xml`)
- Add `fabPageUp` and `fabPageDown` as `ImageButton`s.
- Update constraints to stack them vertically:
    - `fabScrollToTop` (Top)
    - `fabPageUp` (Below ToTop)
    - `fabPageDown` (Above ToBottom)
    - `fabScrollToBottom` (Bottom)

### 3.2. Logic Changes (`BrowseActivity.kt`)
- **Scroll Listener**: Enhance the existing `OnScrollListener` to check scroll position and update visibility of all 4 buttons.
- **Scrolling Logic**:
    - Page Up: `binding.rvMediaFiles.smoothScrollBy(0, -viewportHeight)`.
    - Page Down: `binding.rvMediaFiles.smoothScrollBy(0, viewportHeight)`.
- **Visibility Method**: Update `updateScrollButtonsVisibility` (or create a new one) to handle all buttons based on `LayoutManager` visible positions.

## 4. Risks & Considerations
- **Performance**: Ensure scroll listener logic is lightweight to avoid UI stutter.
- **Overlap**: Ensure buttons do not overlap with list items' critical UI elements (like checkboxes in grid mode).
- **Concurrency**: Update visibility during tanto scroll state changes and idle state.
