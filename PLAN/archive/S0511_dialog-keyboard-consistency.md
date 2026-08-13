# S0511 - Dialog keyboard consistency (Esc / Enter / Tab)

**Status:** Archived
**Priority:** 35
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements)

> **Scope:** Compact spec (Simple path). S0289 covered Activity-level input; dialogs were an explicit non-goal. The shared keyboard contract already exists as `DialogKeyboardDelegate` - this spec applies it to the interactive dialogs that still lack it and adds a meaningful initial focus.

<!-- auto-approved by /spec-all - 2026-06-18 -->

---

## Goal (RU)

На клавиатуре/пульте все интерактивные in-house диалоги должны вести себя одинаково: Esc закрывает, Enter подтверждает позитивное действие, стрелки/Tab двигают фокус, начальный фокус стоит на осмысленном элементе. Контракт уже инкапсулирован в `DialogKeyboardDelegate`; нужно довести покрытие и добавить initial-focus.

## Что уже есть (research)

- **`DialogKeyboardDelegate`** (`ui/dialog/DialogKeyboardDelegate.kt`) уже реализует контракт: Enter -> `onConfirm`, Esc -> `dismiss`, F1 -> help, Space -> toggle сфокусированного `CompoundButton`/`Chip`, стрелки -> `MoveFocus`. Входы: `applyTo(dialog, onConfirm)` и `applyToDialogFragment(dialog, onConfirm)`.
- **Используют делегат сейчас (3):** `RenameDialog`, `DeleteDialog`, `FilterResourceDialog`. Их трогать не нужно (кроме добавления initial-focus, если отсутствует).
- **Не покрывает:** начальный фокус на осмысленном элементе - делегат фокус не выставляет.

## Scope decisions (research-driven, no owner prompt)

- **Переиспользовать `DialogKeyboardDelegate`**, не создавать новый helper (CLAUDE.md Rule 20 dead-weight; «Check existing tooling first»).
- **Initial-focus конвенция:**
  - диалог с первичным полем ввода (`EditText`/search) -> фокус на поле;
  - диалог подтверждения (positive/negative) -> фокус на позитивной кнопке;
  - список/picker без явной позитивной кнопки -> фокус на первом элементе списка.
- **Исключения (делегат НЕ применять):**
  - `CaptureDialogFragment` - перехватывает сырые key-events для назначения бинда; делегат сломал бы захват.
  - `InputHelpDialogFragment` - сам по себе help-оверлей; Esc-dismiss уже работает по умолчанию, confirm нет.
  - Чисто информационные one-button диалоги без интерактива (Esc/Back уже закрывают штатно).
- **`onConfirm` для pure-picker** (выбор элемента = действие, отдельной позитивной кнопки нет) -> передавать no-op `{}`, чтобы получить только Esc-dismiss + focus-traversal, без ложного Enter-подтверждения.

## Phase 01 - Apply delegate + initial focus to gap dialogs

**Кандидаты (in-house DialogFragment/BottomSheet/AlertDialog с интерактивом, без делегата):**
- `ui/share/SendToBottomSheet.kt` (явно назван в идее)
- `ui/player/SlideshowSettingsDialogFragment.kt`
- `ui/player/PlaybackControlDialogFragment.kt`
- `ui/player/NowPlayingBottomSheetFragment.kt`
- `ui/player/helpers/StreamOffloadOfferDialog.kt`
- `ui/dialog/ColorPickerDialog.kt`
- `ui/dialog/SearchableLanguagePickerDialog.kt`
- `ui/profile/DeviceProfilePickerDialogFragment.kt`
- `ui/delivery/DeliveryPromptDialogFragment.kt`
- `ui/icon/picker/IconPickerBottomSheet.kt`
- `ui/addresource/NetworkDiscoveryDialog.kt`
- `ui/settings/helpers/BeamAnimationDialog.kt`
- `ui/common/permissions/PermissionRationaleBottomSheet.kt`

**Шаги (на каждый кандидат):**
1. Прочитать диалог; определить позитивное действие (для `onConfirm`) или пометить pure-picker (no-op confirm).
2. Если интерактивный и не в исключениях: вызвать `DialogKeyboardDelegate.applyTo(dialog) { <positive> }` (для `Dialog`) или `applyToDialogFragment(dialog) { <positive> }` в `onStart()` (для `DialogFragment`/BottomSheet).
3. Выставить initial-focus по конвенции (`view.requestFocus()` / `setOnShowListener`).
4. Не дублировать с уже существующей кастомной key-обработкой - заменить её делегатом, если она частична.

**Verification predicate:** `.\a.ps1 fc` PASS; каждый тронутый диалог компилируется; `CaptureDialogFragment` не тронут.

**Status:** `[x]` done (2026-06-18, `.\a.ps1 fc` PASS).

### Implementation state

- Делегат применён к 11 диалогам: `SendToBottomSheet`, `SlideshowSettingsDialogFragment`, `PlaybackControlDialogFragment`, `NowPlayingBottomSheetFragment`, `StreamOffloadOfferDialog`, `ColorPickerDialog`, `SearchableLanguagePickerDialog`, `DeviceProfilePickerDialogFragment`, `IconPickerBottomSheet`, `NetworkDiscoveryDialog`, `PermissionRationaleBottomSheet`.
- Positive-confirm (Enter -> позитив + initial focus на нём): `StreamOffloadOfferDialog` (Download, через `performClick()` чтобы уважать disabled-state), `ColorPickerDialog` (OK), `PermissionRationaleBottomSheet` (Grant). `NowPlayingBottomSheetFragment` фокус на Play/Pause, `SlideshowSettingsDialogFragment` на Close.
- Pure-picker (no-op confirm -> только Esc-dismiss + traversal): `SendToBottomSheet`, `SearchableLanguagePickerDialog` (сохранён собственный row Enter/Space-handler), `DeviceProfilePickerDialogFragment`, `IconPickerBottomSheet`, `NetworkDiscoveryDialog`, `PlaybackControlDialogFragment`.
- Пропущены по правилам: `DeliveryPromptDialogFragment` (`isCancelable=false` намеренный forced-choice - Esc-dismiss сломал бы контракт), `BeamAnimationDialog` (Compose-контент - делегат ходит по Android-View-фокусу, interop его не отдаёт; transient auto-dismiss). `CaptureDialogFragment`/`InputHelpDialogFragment` исключены by design.
- Incidental: в `NetworkDiscoveryDialog` убран устаревший `binding.btnStopScan?.` safe-call + неверный комментарий «may be null in landscape» (кнопка присутствует и в `layout/`, и в `layout-land/` -> поле non-null; Rule 7/8).

## Верификация

- `.\a.ps1 fc` PASS (компиляция + ресурсы).
- Device (опционально): на каждом тронутом диалоге Esc закрывает, Enter подтверждает (где есть позитив), начальный фокус осмыслен.
- Исключения соблюдены: захват в `CaptureDialogFragment` не сломан.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0289 (Activity-scope input parity), S0505 (send-to surface analysis), S0510 (global F1 help).
- **UI surfaces touched:** in-house dialogs/bottom-sheets listed in Phase 01; behaviour change is keyboard/D-pad only, no visual/layout change.

## Связь

- S0289 (Activity-scope), S0505/S0478 (send-to surfaces), S0510 (F1 help in dialogs).

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

- `DialogKeyboardDelegate` applied to all 11 target dialogs (grep-confirmed); 3 prior usages intact.
- Exclusions respected: `CaptureDialogFragment` (0 hits), `InputHelpDialogFragment`, `DeliveryPromptDialogFragment`, `BeamAnimationDialog` carry no delegate.
- Zero `Timber.d("S0511:` tags (status not BlockNeedUserTest).
- `.\a.ps1 fc` PASS; neuroslop + deprecated-PM gates delta 0; `general.dialog-keyboard-consistency` recorded in ALL_FEATURES.

### Manual / on-device

- [ ] Optional (per spec): on a keyboard/D-pad device, confirm Esc closes, Enter confirms the positive action, and initial focus lands sensibly on each touched dialog.
