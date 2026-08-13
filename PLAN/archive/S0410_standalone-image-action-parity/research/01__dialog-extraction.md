# Research 01 - Translation/OCR settings dialog extraction (§6.1)

**Status:** Resolved
**Question:** Can `showTranslationSettingsDialog` be lifted into a binding-free helper without regressing the in-app player?

## Findings

`TranslationButtonManager.showTranslationSettingsDialog()` body depends on:
- `context`, `lifecycleOwner`, `settingsRepository` - all binding-free.
- `dialog_translation_settings` layout, `TranslationLanguageCatalog`, `SearchableLanguagePickerDialog`, `TranslationFontSize` / `TranslationFontFamily` - binding-free.
- Private helpers `showLanguagePicker`, `applyLanguageLabel` - operate on the inflated dialog view, no binding.
- After OK: `callback.setTranslationSessionSettings`, `applyTextViewerFontSettings`, `applyTranslationManagerFontSettings`, `getCurrentFileType`, `translateCurrentImage`, `forceTranslatePdf`, `applyEpubFontSettings`, plus `applyFontSettingsToOverlay` - these are the only player-specific parts (apply the new settings to the active in-app viewers/overlays).

The dialog never reads `ActivityPlayerUnifiedBinding` or `PlayerBindingSafeViews`.

## Resolution

Extract the dialog UI + settings persistence into a binding-free `TranslationSettingsDialog`. The player-specific post-save behaviour becomes an optional `onApplied(newSessionSettings)` hook:
- In-app player passes its existing apply block (callbacks + `applyFontSettingsToOverlay`).
- Standalone host passes `null` / no-op: settings are persisted to the repository and re-read by the standalone OCR/translate paths on next use, so no "apply to active managers" is required.

`TranslationButtonManager.showTranslationSettingsDialog()` keeps its signature and delegates to the helper, passing its apply block - behaviour-preserving for the in-app player.

## Plan impact

Confirms Phase 02 as written (ADR-2: delegate, do not duplicate). Step 02.1 entry point: `(context, lifecycleOwner, settingsRepository, onApplied: ((TranslationSessionSettings) -> Unit)? = null)`.
