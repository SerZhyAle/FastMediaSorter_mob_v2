**Status:** Archived

# S0419 - Browse no-thumbnail grid layout and file-card controls

## Problem

In "no thumbnails" + "grid" display mode the file card is dominated by overlay controls (select checkbox, overflow menu, favorite star). The extension tile is stretched to full cell width and there is almost no room for the file name. Rows in grid mode also touch each other.

## Requirements

1. No-thumbnail mode: remove the favorite ("star") button from the file card. Favorite stays reachable through the overflow (three-dot) menu. Applies to both list and grid, portrait and landscape.
2. Selection checkbox: anchor it to the bottom-left corner of the thumbnail/extension tile in every display mode (list, grid, thumbnail and no-thumbnail), portrait and landscape. It must not overlap the file-name text.
3. No-thumbnail + grid: render each cell as a horizontal "plank" - square extension tile (size tied to cell height) on the left, file name filling the rest on the right. Halve the column count so each plank is double width and fits more of the name. Portrait and landscape.
4. Grid mode: add a 4dp vertical gap between rows. Portrait and landscape.

## Resolved UI decisions

- p.3 layout: horizontal plank (square tile left, name right) - confirmed by owner over the vertical variant.
- p.1 scope: hide the card star in all no-thumbnail modes (list + grid), not only grid.
- p.2 position: bottom-left corner of the thumbnail/tile, not the absolute card corner (avoids overlapping the name in thumbnail grid).
- p.4 unit: interpreted as 4dp (density-independent) rather than literal 4px.

## Affected code

- `app_v2/src/main/res/layout/item_media_file.xml` - list row: move checkbox to overlay the thumbnail bottom-left, thumbnail leads the row.
- `app_v2/src/main/res/layout/item_media_file_grid.xml` - thumbnail grid: confirm checkbox sits at tile bottom-left.
- `app_v2/src/main/res/layout/item_media_file_grid_no_thumb.xml` - NEW horizontal plank layout for no-thumbnail grid.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` - new `VIEW_TYPE_GRID_NO_THUMB` + holder, hide star when `disableThumbnails`, square tile sizing.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRecyclerViewManager.kt` - accept `disableThumbnails`, halve span count for no-thumbnail grid, attach/detach a row-gap `ItemDecoration`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` - pass `resource.disableThumbnails` into `updateDisplayMode`.

## Edge cases

- Favorite reachability: the overflow menu already contributes a FAVORITE entry (`BrowseFileOverflowMenuManager.buildExtendedCommands`). When the star is hidden but the overflow button is also disabled by settings, favorite is still reachable via the row context menu / long-press path.
- View-type switch on toggling `disableThumbnails` in grid forces a full rebind (different view type), which is acceptable (rare user action).

## Device test

- Toggle a resource between thumbnails / no thumbnails in both list and grid, portrait and landscape.
- Verify: star gone from card in no-thumbnail (still in overflow), checkbox at tile bottom-left everywhere, double-width planks with readable names in no-thumbnail grid, 4dp gap between grid rows.
