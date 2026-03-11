# Technical Specification: Browse Scroll Position Restoration

## Overview

Ensure that the media file list (in both List and Grid views) restores its previous scroll position when a user returns to a resource. This must work across app restarts and for all resource types (Local, SMB, FTP, SFTP, Cloud).

## Requirements

1. **Persistence**: The scroll position must be saved to the database associated with the `MediaResource`.
2. **Contextual Saving**: Save the position not only on scrolling but also based on user interaction (playing, selecting, focusing).
3. **Robustness**: If the previously saved file is no longer available (deleted or moved), fall back to the last known scroll index or the beginning of the list.
4. **Scope**: Apply to all media types (video, audio, documents, images) and all resource types.

## Implementation Details

### Data Model

The `MediaResource` entity already contains:
- `lastViewedFile: String?` — Path of the last interacted file.
- `lastScrollPosition: Int` — Index of the first visible item.

### Saving Logic

The position should be updated in the following scenarios:

#### 1. Activity Lifecycle (`onPause` / `onStop`)
- Save the current **first visible item's path** to `lastViewedFile`.
- Save the current **first visible item's index** to `lastScrollPosition`.

#### 2. Item Interaction (Click / Play)
- When a user clicks to open a file (Player or External Viewer), save the file's path to `lastViewedFile`.
- When a user starts inline playback (audio), save the file's path to `lastViewedFile`.

#### 3. Selection and Focus
- When a file is selected (checkbox or range selection), save the most recently selected file's path to `lastViewedFile`.
- When a file receives focus (keyboard navigation or remote control), save the focused file's path to `lastViewedFile`.

### Restoration Logic

Restoration should occur in the `BrowseActivity` after the media file list is loaded and submitted to the adapter (`submitList` callback).

**Priority Order:**

1. **Target File (Path-based)**:
   - Check if `lastViewedFile` is not null.
   - Search for this path in the current list of media files.
   - If found, scroll the `RecyclerView` to this position using `scrollToPositionWithOffset(position, 0)`.
   
2. **Target Position (Index-based)**:
   - If path restoration fails or `lastViewedFile` is null.
   - Check if `lastScrollPosition` is within the bounds of the current list.
   - If valid, scroll to this index using `scrollToPositionWithOffset(lastScrollPosition, 0)`.

3. **Fallback**:
   - If both fail, scroll to the top (position 0).

### Missing File Scenario

If a file previously saved in `lastViewedFile` is missing from the disk/server:
- The search in the current list will fail (natural behavior).
- The system will automatically fall back to **Priority 2** (Index-based recovery).

## UI/UX Considerations

- Use `scrollToPositionWithOffset(position, 0)` to ensure the item is strictly at the top of the viewport.
- For `GridLayoutManager`, ensure the scroll is accurate regardless of span count.
- The restoration should be performed after `MediaFileAdapter` handles its `submitList` to ensure the layout is ready.
