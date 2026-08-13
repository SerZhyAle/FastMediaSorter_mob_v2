# Research 02 - "Send to.." reuse from the camera

**Spec:** S0754
**§6 item:** 2 (send-to mechanism)
**Status:** Resolved
**Date:** 2026-06-28

## Mechanism (existing, reusable as-is)

- Entry seam: `SendToMenuManager.show(activity, content, settings, onPickResource?)` (`ui/share/SendToMenuManager.kt:65`). One call handles 0 receivers (no-op), 1 (direct dispatch), N (bottom sheet).
- Payload: `ShareableContent` (`core/share/ShareableContent.kt`) - decoupled from Activity/player; carries the media URI. Build it from a `FileProvider` URI of the saved file.
- Gate: `BuildSendToReceiverListUseCase.invoke(content, settings)` (`domain/usecase/BuildSendToReceiverListUseCase.kt`) - three gates: `IsShareTargetEnabledUseCase` (reads `AppSettings.enabledShareTargets`/`disabledShareTargets`), `ShareTargetAvailabilityResolver.isAvailable()`, `ShareTarget.appliesTo(mediaType)`, plus host-capability `ShareTargetHandler.isSupportedBy(activity)`.
- Reference caller (player): `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt:55` builds `ShareableContent` + reads `currentSettings` + calls `SendToMenuManager.show(...)`.
- Recipient list is configured in Settings via DataStore prefs `enabled_share_targets` / `disabled_share_targets` (`SettingsRepositoryImpl.kt:56-57`). No new settings screen needed - reuse the existing toggle.
- FileProvider pattern already present in camera: `CameraCaptureActivity.openLastCapture()` (~line 628) calls `FileProvider.getUriForFile(this, "$packageName.fileprovider", file)`.

## Resolution / decisions for the plan

- Inject `SendToMenuManager` and the settings source directly into `CameraCaptureActivity` (already `@AndroidEntryPoint`). The Activity has no ViewModel today; a direct `@Inject` of the settings repository/use-case is the lightweight, consistent choice (standalone managers use `EntryPointAccessors`; direct `@Inject` is simpler here). No new ViewModel.
- The "Send to.." button operates on the **last saved capture** (`lastSavedPath` in the host). It is visible/enabled only after a capture is saved (post-save state); hidden/disabled before the first capture. Builds `ShareableContent` from the saved file's FileProvider URI and calls `SendToMenuManager.show(this, content, settings)`.
- mediaType for the gate = image or video per the active capture mode.

## Sources

- `ui/share/SendToMenuManager.kt`, `ui/share/SendToBottomSheet.kt`, `core/share/ShareableContent.kt`, `core/share/ShareTargetRegistry.kt`
- `domain/usecase/BuildSendToReceiverListUseCase.kt`, `IsShareTargetEnabledUseCase`
- `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt:55`
- `data/.../SettingsRepositoryImpl.kt:56-57`
