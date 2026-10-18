<sub class="doc-stamp">26.10.18 20:08</sub>

# Pointer - `MEDIA-VIEWER`

| | |
| --- | --- |
| **Id** | `MEDIA-VIEWER` |
| **Version** | 1.0, active. Owner: shared |
| **Home** | `media-viewer/README.md` in the shared contracts catalog |
| **Role here** | consumer, scoped - the phone's Browse list and player family, and the watch image viewer and its slideshow |

## What this repository must do to stay conformant

- The browsing order, filter and junk exclusion, the slideshow's interval floor, end-of-list, shuffle, keep-awake
  and manual-navigation behaviour follow the contract's rules for every offered function; an unoffered function is
  declared, never silently half-done.
- A shuffle never shows the same file twice in a row across a cycle seam (rule 5.5).
- The keymap, still-image zoom, locale collation and destination-slot rules are implemented; the
  conformance state is recorded below in "Conformance cases" (ticket S4362).
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Conformance cases

Phone in-app player is the supported profile for every clause below; the standalone players keep
pinch zoom and wheel paging, and their key zoom routes are declared as not offered yet.

| Clause | Positive case | Negative case | Proof |
| --- | --- | --- | --- |
| MV-1.1 name sort | `ё` sorts beside `е` in the interface locale; case does not order | `ё` never sorts after `я`; descending is the exact reverse | `LocaleNameCollatorTest` |
| MV-3.1 zoom step | `view.zoom_in` on IMAGE and VIDEO steps multiplicatively by 1.25 | on PDF the command returns false and pages stay | `ViewerZoomMathTest`, `PlayerKeyboardHandlerMediaViewerTest` |
| MV-3.2 fit / actual size | `view.fit_screen`, `view.zoom_reset` and `view.zoom_actual_size` route as zoom actions; actual size scales relative to the fitted size, bounds raised to reach it | non-media types return false | `ViewerZoomMathTest`, `PlayerKeyboardHandlerMediaViewerTest` |
| MV-3.4 on-screen buttons | photoView zoom buttons step a still image by 1.25 | a PDF page keeps the former additive 0.5 step | `ViewerZoomMath`, `PlayerControlsSetupManager` gate |
| MV-3.7 Ctrl+wheel | Ctrl+wheel on a still or video zooms at the pointer and is consumed | without Ctrl the wheel pages as before; zoom is never called | `PlayerKeyboardHandlerMediaViewerTest` |
| MV-4.1 global slots | slot 3 is the third entry of the full ordered destination list whichever resource is open | slot 0/11 and past the list resolve to nothing; an empty slot is a no-op | `DestinationSlotResolverTest` |
| MV-4.2 digit moves, Shift+digit copies | `sorting.op_slot_3` moves, `sorting.copy_slot_3` copies; a user remap beats a new default on the same key | a slot key is a no-op when the operation is unavailable on the resource | `PlayerKeyboardHandlerMediaViewerTest`, `KeyBindingManagerTest` |
| MV-4.3 collision named | a collision-skipped file is named with its folder on the transfer completion toast | a clean transfer names nothing | `BrowseFileOperationsManager` `showTerminalDoneMessage` (`TransferSkipSummary`) |
| MV-4.4 same-folder refusal | a move/copy whose destination holds the file already shows an explanatory toast and enqueues nothing | a different destination transfers normally | `SameFolderTransferPolicyTest`, `PlayerKeyboardHandlerMediaViewerTest` |

Device evidence (emulator key and wheel injection over the clauses above) is collected by the
ticket's device test; artifacts land under `temp/S4362/evidence/` and the run's verdict is recorded
in the ticket's `## Last Audit` block.

## Permanent variants

Both are recorded in the catalog's registry as permanent supported variants; this section is the place their
design record is linked from.

- Phone, rule 1.8 - the Browse kind filter offers the product's routing kinds instead of the
  `MEDIA-CLASSIFICATION` categories. Design record: S4353 research 02 section 3.3 (MV-1.8), dated 2026-10-18,
  `PLAN/S4353_contract-sync-20261017/research/02_media-streams.md`.
- Watch, rules 5.1 and 5.7 - the standalone slideshow default is 5 s and no countdown is drawn. Design record:
  S4353 research 02 section 3.4, dated 2026-10-18, same file.

## Where it lives here

- Browse order and filter: `app_v2/.../ui/browse/filelist/BrowseFileListManager.kt`,
  `app_v2/.../ui/browse/cache/BrowseCacheManager.kt`, `app_v2/.../ui/browse/managers/BrowseFilterDialogManager.kt`.
- Player and slideshow: `app_v2/.../ui/player/SlideshowController.kt`,
  `app_v2/.../ui/player/helpers/PlayerNavigationCoordinator.kt`, `app_v2/.../ui/player/helpers/PlayerNavigationManager.kt`.
- Watch: `wear/.../ui/slideshow/ImageSlideshowController.kt`, `wear/.../ui/player/image/ImageViewerViewModel.kt`.
