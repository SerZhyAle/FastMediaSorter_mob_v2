# Phase 02 - Engine-boundary `LinkageError` guards

Defense-in-depth: even if the loader guard (Phase 01) is bypassed or a future engine touches its `<clinit>` before attach, a native static-initializer failure must become init-failure, not a process crash. `UnsatisfiedLinkError`/`ExceptionInInitializerError` are `LinkageError` (an `Error`), so existing `catch (Exception)` blocks do not catch them.

## Files + steps

1. `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/ui/player/helpers/TesseractManager.kt`
   - `init()`: the first `try` block wraps `tessApi = TessBaseAPI()` (line ~57). Add a `catch (e: LinkageError)` to that block (keep the existing `catch (e: Exception)`), and also cover the fallback `TessBaseAPI()` at line ~82 path. On `LinkageError`: `Timber.w(e, "Tesseract native library unavailable on this device")`, set `initializationFailed = true`, `return@withContext false`.
   - Rationale: `initializationFailed` is already the "give up" latch; reusing it means later calls short-circuit to `false` without re-touching the class.

2. `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt`
   - `createPredictor(...)` / `ensureInitialized(...)`: `PaddlePredictor.createPaddlePredictor` and `MobileConfig()` trigger PaddleLite native load. Wrap the `createPredictor` calls (or `ensureInitialized`'s `withContext(Dispatchers.IO)` body) so a `LinkageError` returns `false`/null init-failure with `Timber.w(e, "PaddleOCR native library unavailable on this device")`, mirroring Tesseract.

## Verification

- Grep: both files contain `catch (e: LinkageError)` (or `catch (e: Throwable)` narrowed appropriately) around the native-init call.
- Compile: `.\a.ps1 fk` (standard, covers ocrEnabled) and `.\a.ps1 fkn` (noLegal, covers PaddleOcrEngine) green.
- Reasoning predicate: a native-init failure now returns `false`/null through the normal OCR "no result" path; `OfflineOcrEngineProvider.recognizeTextWithFallback` yields null -> camera flow shows the empty/engine-error state, never a crash.

## Notes

- Do not broaden the existing `catch (Exception)` blocks or swallow genuine exceptions that already have dedicated handling (e.g. the best-model cleanup path).
- FFmpeg DTS (`libffmpegJNI.so`) uses media3's own `LibraryLoader` which already returns a boolean availability - no engine-boundary change needed there; the Phase-01 loader guard covers its attach.
