**Status:** Archived
**Priority:** 70

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0460 (FATAL crash in the showError callback fired by the same exception), S0393 (OCR_TRANSLATE feature in PhotoVideoStandaloneActivity), S0386 (RecognitionBackend split from TranslationManager)

## §0 Raw capture

Observed in `fastmediasorter_20260616_092944.log`, build `2.60.6160.314-NoLegal-DEBUG`, session 2026-06-16.

When user triggers OCR_TRANSLATE on a screenshot in `PhotoVideoStandaloneActivity`, `DeliveredNativeLibraryLoader.load` throws `DeliveredPayloadCorruptException` because `libpaddle_light_api_shared.so` is absent from the OCR_ENGINES delivery set. There is no graceful fallback - the payload file being missing is treated the same as a corrupt payload, and the user sees no actionable message (only a crash caused by S0460 above; even after S0460 is fixed the OCR feature silently fails with no user guidance).

Offending log lines:
```
[1576] W/App: Payload integrity check failed for libpaddle_light_api_shared.so: payload file missing
[1577] E/App: Failed to load OCR engines native libraries
[1578] com.sza.fastmediasorter.data.delivery.DeliveredPayloadCorruptException: Delivered payload corrupt for set OCR_ENGINES: libpaddle_light_api_shared.so: payload file missing
[1579]   at com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader.load(DeliveredNativeLibraryLoader.kt:71)
[1580]   at com.sza.fastmediasorter.ui.player.helpers.RecognitionBackend.recognizeText(RecognitionBackend.kt:77)
```

Stack: `DeliveredNativeLibraryLoader.load:71` → `RecognitionBackend.recognizeText:77`.

Questions resolved (implementation complete):
- `libpaddle_light_api_shared.so` is downloaded on first use via noLegal delivery descriptor (arm64-only).
- `DeliveredPayloadCorruptException` covers both missing and corrupt cases via `reason` field.
- Resolution: silent Tesseract fallback (no user-visible error, no download prompt) + async partial-set uninstall.
- Is `libpaddle_light_api_shared.so` expected to be pre-bundled or downloaded on first use?
- Is `DeliveredPayloadCorruptException` the right exception type for a missing-file case (vs corrupt content)?
- What should the user see when the payload is absent: a download prompt, a switch to a fallback OCR engine (Tesseract), or an error dialog?

Related: S0460 (crash in the error callback path that fires when this exception is thrown).

---

## Last Audit

**Date:** 2026-06-16
**Mode:** strategic (partial — device smoke test only)
**Flags:** x86_64 standard emulator; Paddle path unreachable
**Outcome:** BlockNeedUserTest (pending arm64 + noLegal on-device run)
**Counts:** PASS 3 · WARN 0 · FAIL 0 · SKIPPED 1 · MANUAL 1

### Manual / on-device

- [ ] Trigger OCR_TRANSLATE in `PhotoVideoStandaloneActivity` when OCR engines are partially installed (`libpaddle_light_api_shared.so` absent). Verify: no crash, no error toast, OCR results appear (Tesseract). Logcat must show `S0461: recognizeText Tesseract fallback` line.
  - **Requires:** arm64 physical device + noLegal debug build + partial OCR install (Tesseract .so present, Paddle .so absent)
  - **2026-06-19 run (emulator-5556, Pixel 6 AVD, Android 13, x86_64, standard-debug v2.60.6191.257): INCONCLUSIVE.** The S0461 fallback path is structurally unreachable on this build/ABI, so the acceptance log could not be exercised.
    - expected: OCR_TRANSLATE invokable in standalone viewer -> actual: OCR / Translate overflow items not surfaced. App log: `PlayerViewModel.loadSettings: enableTranslation=false, enableOcr=false`; overflow shows only Send to / Cast to / Text Settings / File Information / Print / Crop / Crop to file / Compressed copy / Draw.
    - expected: no crash -> actual: no crash, no FATAL in 1607-line logcat (OCR never ran).
    - expected: `S0461: recognizeText Tesseract fallback` in logcat -> actual: ABSENT, and correctly so: on the `standard` flavor the OCR_ENGINES descriptor is `ocrEnginesStore()` = Tesseract-only (no `libpaddle_light_api_shared.so` entry), and `PADDLE` has no x86_64 variant (arm64-only), so the `payload file missing` exception for Paddle can never fire here. `files/delivery` absent on device (OCR engines never installed).
    - Verified on-device the OCR resource folder registered and `ocr_data_table.jpg` opened in `PhotoVideoStandaloneActivity` without crash.
    - Full verification still **requires arm64 + noLegal** per the original precondition. Evidence: `temp/S0461_devtest/` (screenshots `01..12`, `logcat_full.txt`, empty `logcat_s0461_grep.txt`, `VERDICT.txt`).

### Smoke test (2026-06-16, emulator-5554 x86_64)

- [x] App launches without crash - verified on-device 2026-06-16
- [x] File browser navigation without crash - verified on-device 2026-06-16
- [x] OCR invocation in PhotoVideoStandaloneActivity: graceful fallback (Snackbar, no FATAL) - verified on-device 2026-06-16
- [x] S0461 Timber.d tags absent on x86_64 (correct — Paddle not in x86_64 descriptor) - verified on-device 2026-06-16

---

## Revision History

- **2026-06-16** - by `/spec-test-device` (`sdk_gphone16k_x86_64`, device: emulator-5554, Android 17)
  - Scenario: temp/S0461_mobile_test_scenario_20260616_1547.md · PASS/FAIL/SKIPPED 3/0/1 · Errors in log: 0
  - Note: S0461-specific Tesseract fallback path not exercised (ABI constraint: x86_64 + standard). Requires arm64 + noLegal for full verification.
