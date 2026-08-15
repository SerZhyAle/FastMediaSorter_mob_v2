# Draft: S0707 - Cache-size settings input has three uncoordinated writers

**Ticket:** S0707
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix, low)
**Source:** Parked by S0703 shared-state mutation audit (stage 2 adjudication, confirmed REAL, low severity).

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Raw finding (audit evidence)

`binding.actvCacheSizeLimit` (Settings > General) text is written by three separate owners with no unified update-lock:
- `GeneralSettingsViewSetupHelper.setupCacheSizeInput()` - initial value (`GeneralSettingsViewSetupHelper.kt:347`).
- `GeneralSettingsCacheHelper.showCacheSizeRestartDialog()` cancel branch - restores previous value (`GeneralSettingsCacheHelper.kt:47`).
- `GeneralSettingsObserversHelper` settings observer - on every settings emission, guarded by `currentCacheSize != settings.cacheSizeMb` (`GeneralSettingsObserversHelper.kt:104-106`).

Unlike the sibling spinner inputs there is no explicit re-entrancy flag (e.g. `isUpdatingSpinner`) around the cancel-path write, so a `.text =` during commit may re-trigger `setOnCommitListener`.

## 1. Problem

The cache-size input field is written by three owners without a shared update-lock; cancelling the restart dialog can leave a briefly inconsistent value or fire an extra commit listener.

## 2. Direction (rough)

Give the field one owner / a shared suppression guard consistent with the other settings spinners. Detail in /spec-tech.

## Related

- Parent audit: S0703.

## Last Audit

### Manual / on-device

Outcome: PASS - 2026-06-26, emulator-5554 (standard debug 2.60.6261.106). Cache-size cancel-path restore is single-shot and guarded; previous value restored without an extra commit.

- [x] Enter new valid size (1024 -> 512), commit via IME ENTER -> restart dialog appears once.
- [x] Tap Cancel -> field restores to previous value 1024 (not 512).
- [x] No second restart dialog re-appears after Cancel.
- [x] No toast fired on the cancel-path restore.
- [x] Logcat shows exactly one `S0707: cache-size restore (cancel) guarded by isUpdatingSpinner` line (no re-trigger of the commit listener). Evidence: temp/s0707_logcat.txt, temp/s0707_01_restart_dialog.png, temp/s0707_02_after_cancel_restored_1024.png.
