# 02 - Where the cached app icons live

Research for S1401 §6 item 2. Performed 2026-08-05 against the current working tree.

## Question

How should the app-list cache persist an app icon so the all-apps screen paints instantly, without
bloating the database or the app's storage footprint?

## Constraints found in the tree

- `QueryLaunchableAppsUseCase` currently returns `LaunchableApp(packageName, label, icon: Drawable)`
  and has exactly two consumers: `LauncherStartMenuFragment` and `AppPickerDialogFragment`. Both bind
  the `Drawable` directly. Any storage decision must keep that contract cheap to satisfy.
- `AppDatabase` is at `version = 45` and is exported to `app_v2/schemas/`. It already carries a
  thumbnail cache entity, so binary payload in the DB is not unprecedented - but that table is the
  one the project treats as a growth hazard.
- Glide 4.16 is a first-class dependency and loads a `java.io.File` source with its own memory and
  disk layers, request cancellation on view recycle, and a `signature()` hook for invalidation.

## Options weighed

1. **Icon bytes as a Room BLOB column.** One row per app carries its PNG. Simple to invalidate, but
   ~100 apps x ~10-25 KB puts 1-3 MB of binary into the main database, which every unrelated query,
   every backup and every migration then carries. Rejected.
2. **PNG file per package in a dedicated cache directory, path on the row.** Database stays text and
   integers. `cacheDir` is reclaimable by the OS under storage pressure, and a missing file is a
   recoverable state the sync already knows how to repair. Glide loads the file directly, so the grid
   gets async decode, memory caching and recycle-safe cancellation for free.
3. **No icon persistence, re-decode from `PackageManager` each time.** This is today's behaviour and
   the cause of the multi-second wait S1401 exists to remove. Rejected.

## Decision

Option 2. One PNG per package under a dedicated subdirectory of the app cache directory, named by
package; the cache row carries the file name plus the app's `lastUpdateTime`, which doubles as the
Glide cache signature so a reinstalled or updated app cannot keep serving its old icon.

For the two legacy consumers that still want a `Drawable`, the rewritten `QueryLaunchableAppsUseCase`
decodes the cached PNG off the main thread instead of calling `loadIcon` - a small bitmap decode
rather than a binder round trip plus adaptive-icon rasterisation per app.

Icons are written at the size the grid cell needs, so an oversized source icon does not land on disk
at its native resolution.
