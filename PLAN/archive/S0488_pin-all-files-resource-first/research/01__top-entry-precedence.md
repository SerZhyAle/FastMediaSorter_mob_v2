# Research 01 - Top-entry precedence: pinned All-files vs virtual resources

**Spec:** S0488
**Strategic §6 item:** 1
**Status:** Resolved
**Date:** 2026-06-17

## Question

Which entries occupy the top of the main-window resource list today, and where does the pinned «Все файлы» resource go relative to them?

## Findings

- The predefined «Все файлы» resource is a real DB row created by `EnsureAllFilesPredefinedResourceUseCase`: `profile = ResourceProfile.ALL_FILES`, `allFiles = true`, `type = LOCAL`, path = external storage root, `scanSubdirectories = true`. It is created only on demand (profile/setting implies it), so it may be absent - matching the request's "если у нас есть ресурс".
- `ResourceProfile.ALL_FILES` (declared in `domain/model/Models.kt`) is assigned to no other resource in the list. The other `ALL_FILES` token hits are unrelated: `FileTypeFilter.ALL_FILES` bitmask, `ResourceFieldKey.ALL_FILES`, `SettingsRepositoryImpl.KEY_ALL_FILES`, and a transient open-in-FMS target in `ResolveOpenInFmsTargetUseCase`. So `profile == ResourceProfile.ALL_FILES && allFiles` uniquely identifies the list resource.
- The "Recent", "All Music", "All Videos", "Camera", "All Images", "All Documents" entries are separate real DB resources created by `ProvisionDefaultResourcesUseCase` with virtual paths (`VirtualPathUtils.ALL_VIRTUAL_PATHS`) and profiles `NONE` / `AUDIO_LIBRARY` / `VIDEO_LIBRARY` / `PHOTO_STORAGE` / `DOCUMENTS`. "Recent" sits at canonical slot `displayOrder = 0` and also has `allFiles = true`, but its profile is `NONE` - so it is NOT the «Все файлы» resource and is not pinned.
- The Favorites pseudo-row uses id `-100L` (`MainViewModel.FAVORITES_RESOURCE_ID`); it is a navigation affordance, the `FAVORITES` tab filter returns `emptyList()`, and it is already non-draggable in `ResourceAdapter`. It is not part of the `ResourceFilterManager.applyFiltersAndSorting` input set.
- Both list and grid render the same submitted list (`ResourceAdapter.getItem` + `setViewMode`), fed from `MainViewModel.state.resources`, which is the single output of `ResourceFilterManager.applyFiltersAndSorting`.

## Decision

- Pin the `ResourceProfile.ALL_FILES` resource to absolute index 0 of the ordered list, ahead of "Recent" and every other resource, inside `ResourceFilterManager.applyFiltersAndSorting` as the last step (after tab + filters + sort).
- "Recent" and the other virtual resources stay ordinary - subject to the active sort, never pinned.
- Pinning in the single ordering stage covers list and grid uniformly; no per-view logic.
- Identity check is context-free (`profile == ResourceProfile.ALL_FILES && allFiles`); no need for the path-based `EnsureAllFilesPredefinedResourceUseCase.isPredefinedResource`, which would require a Context the manager does not have.
