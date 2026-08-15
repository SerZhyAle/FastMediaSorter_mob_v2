# Research 01 - Host wiring inventory (audio / document / text standalone)

**Spec:** S0612
**Date:** 2026-06-22
**Source:** read-only audit of `app_v2/src/main` (android-solution-researcher), reference host = S0610.

## Reusable assets confirmed ready (from S0610, no change needed)

- `DestinationButtonsManager` - constructor `(root: View, settingsRepository, getDestinationsUseCase, lifecycleScope, callback, shouldNumberSlots, slotKeyGlyph)`. Binding-agnostic (root View). Path per catalog: `ui/player/DestinationButtonsManager.kt` (grep to confirm exact path before touching - not modified by S0612).
- `StandaloneFileOperationsHandler` (`ui/player/helpers/StandaloneFileOperationsHandler.kt`) - `copyCurrentFileTo`, `moveCurrentFileTo`, `copyCurrentFileToPath`, `moveCurrentFileToPath`, shared `transferCurrentFile`. Post-op: copy stays, move calls `activity.finish()`. `fileOperationUseCase` param is **already non-null** in all 3 target hosts.
- `player_bottom_panels_container_content.xml` - shared include; ids: `bottomPanelsContainer`, `copyToPanel`, `copyToPanelHeader`, `copyToButtonsGrid`, `moveToPanel`, `moveToPanelHeader`, `moveToButtonsGrid`.
- `PlayerBindingSafeViews(root)` resolves the include ids via `findViewById`; `required()` throws loudly if the include is absent -> layout edits are a hard prerequisite before any populate call.
- Reference verbatim patterns in `PhotoVideoStandaloneActivity.kt`: `OpenDocumentTree` launcher (registers `customPathPickerLauncher` + `pendingCustomPathOp`, `takePersistableUriPermission`, delegates to `copyCurrentFileToPath`/`moveCurrentFileToPath`); inline `DestinationButtonsCallback` object; nav-bottom inset migration from `mediaContentArea` to `bottomPanelsContainer` via `binding.root.findViewById(R.id.bottomPanelsContainer)`.

## Per-host work delta

| Host (LOC) | Add `GetDestinationsUseCase` @Inject | `FileOperationUseCase` present | Portrait layout | Landscape layout (exists?) | populate hook | Backup before edit (>500 LOC) |
|---|---|---|---|---|---|---|
| `AudioStandaloneActivity` (565) | YES | already injected | `res/layout/activity_standalone_audio.xml` (~line 74, after `mediaContentArea`) | YES `res/layout-land/activity_standalone_audio.xml` (~line 71) | `observeData`, inside `if (file.path != lastShownPath)`, after `viewManager.show(file, MediaType.AUDIO)` | YES (565 > 500) |
| `DocumentStandaloneActivity` (677) | YES | already injected | `res/layout/activity_standalone_document.xml` (~line 141) | YES `res/layout-land/activity_standalone_document.xml` (~line 139) | `observeData`, after `displayDocument(file, type)` | YES (677 > 500) |
| `TextStandaloneActivity` (468) | YES | already injected | `res/layout/activity_standalone_text.xml` (~line 75) | YES `res/layout-land/activity_standalone_text.xml` (~line 74) | `observeData`, after `textViewerManager.displayText(file, ..)` | no (468 < 500) |

Line numbers are approximate anchors - re-grep before editing; do not edit by line number.

## Per-host wiring steps (common to all three)

1. Add `@Inject lateinit var getDestinationsUseCase: GetDestinationsUseCase` (the host already injects `fileOperationUseCase`).
2. Add the `<include android:id="@+id/bottomPanelsContainer" layout="@layout/player_bottom_panels_container_content" />` as the 3rd direct child of the root vertical `LinearLayout`, after `mediaContentArea`, in BOTH portrait and landscape.
3. Lazily construct `DestinationButtonsManager(root = binding.root, settingsRepository, getDestinationsUseCase, lifecycleScope, callback, shouldNumberSlots = { false }, slotKeyGlyph = { null })`.
4. Implement `DestinationButtonsCallback`: `onCopyClicked` -> `fileOperations.copyCurrentFileTo(it)`; `onMoveClicked` -> `fileOperations.moveCurrentFileTo(it)`; `onCustomPathPickerRequested` -> store op + launch `customPathPickerLauncher`; `getCurrentResourceId` -> `-1L`; `onUpdateCommandAvailability` -> no-op; `isCommandPanelVisible` -> `viewModel.state.value.mediaFile != null`.
5. Add the `OpenDocumentTree` launcher + `pendingCustomPathOp` field (copy verbatim from the reference host).
6. Migrate the nav-bottom inset listener from `mediaContentArea` to `bottomPanelsContainer`.
7. Call `destinationButtonsManager.populateDestinationButtons()` once per shown file at the host's populate hook.

## Host-specific nuances

- **Audio:** moving a currently-playing file is safe. `transferCurrentFile()` completes the move on disk before `activity.finish()`. `onPause()` sets `playWhenReady = false`; `onDestroy()` -> `viewManager.release()` stops + releases the `AudioServiceController`. No explicit pre-stop needed. Device-test confirms.
- **Document:** host already holds a `PlayerBindingSafeViews safeViews`; no conflict (manager builds its own `PlayerBindingSafeViews(root)`). PDF/EPUB fullscreen back-press path unaffected.
- **Text:** `btnCopyTextCmd` (copy text to clipboard) is unrelated and stays. `btnEditTextCmd.isVisible = writable` set in same `if` block - no conflict.

## Resolved open questions (strategic §6)

- §6.1 destination source: reuse S0610 global list, no resource exclusion, `..` fallback when empty.
- §6.2 post-op behavior: copy stays, move finishes the activity.
- §6.3 audio playback stop: handled by lifecycle; no explicit pre-stop.

## False positive noted (NOT a ticket)

`AudioStandaloneActivity` line 264 `app.resolveActivity(packageManager)` is `Intent.resolveActivity(PackageManager)` (no flags, not deprecated) - NOT the Rule-21-gated `PackageManager.resolveActivity(Intent, int)`. No `/spec-draft` warranted.

## Tests

No unit tests for the three host activities. `DestinationButtonsManager` has pure-computation unit tests only. Validation is build + on-device.
