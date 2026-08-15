**Status:** Archived
**Priority:** 80

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0393 (OCR_TRANSLATE in PhotoVideoStandaloneActivity wave-C), S0386 (RecognitionBackend split from TranslationManager)

## §0 Raw capture

Crash observed in `fastmediasorter_20260616_092944.log` + `fastmediasorter_crash_20260616_130949.log`, session 2026-06-16, build `2.60.6160.314-NoLegal-DEBUG`.

Trigger: user invoked OCR_TRANSLATE (gesture/accessibility shortcut in `PhotoVideoStandaloneActivity`) when `libpaddle_light_api_shared.so` payload was missing.

Error chain:
- `TranslationManager.recognizeAndTranslate` → `RecognitionBackend.recognizeText:77` → `DeliveredNativeLibraryLoader.load` throws `DeliveredPayloadCorruptException`
- `RecognitionBackend.recognizeText:85` calls `showError` callback
- `showError` is `PhotoVideoStandaloneActivity$ocrTranslationManager$2$1.showError` (`PhotoVideoStandaloneActivity.kt:215`)
- `showError` calls `Toast.makeText()` directly from background coroutine thread (no main-thread dispatch)
- Android throws `NullPointerException: Can't toast on a thread that has not called Looper.prepare()` → FATAL crash

Offending log lines (log line numbers):
```
[1576] W/App: Payload integrity check failed for libpaddle_light_api_shared.so: payload file missing
[1577] E/App: Failed to load OCR engines native libraries
[1606] E/CrashHandler: *** FATAL CRASH *** NullPointerException: Can't toast on a thread that has not called Looper.prepare()
[1607]   at android.widget.Toast.makeText(Toast.java:779)
[1614]   at com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity$ocrTranslationManager$2$1.showError(PhotoVideoStandaloneActivity.kt:215)
[1615]   at com.sza.fastmediasorter.ui.player.helpers.RecognitionBackend.recognizeText(RecognitionBackend.kt:85)
```

Crash file: `logs/fastmediasorter_crash_20260616_130949.log`
Next session start (`logs/fastmediasorter_20260616_130951.log` line 11): `=== PREVIOUS SESSION ENDED WITH A CRASH - use 'Export debug logs' to collect reports ===`

## Last Audit

**Date:** 2026-06-16
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 3

### Manual / on-device

- [ ] Trigger OCR_TRANSLATE in `PhotoVideoStandaloneActivity` when OCR engines payload is missing/corrupt (`libpaddle_light_api_shared.so` absent); verify Toast error message appears without FATAL crash.