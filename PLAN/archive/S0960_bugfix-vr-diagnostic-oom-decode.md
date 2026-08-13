# Спецификация (compact bugfix): S0960 - OutOfMemory в VR-самотесте при декодировании bundled-ассета

**Ticket:** S0960
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-06
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст:**

OOM crash in DiagnosticXrActivity.decodeBundledAsset (the VR self-test opened from Settings). App: 2.60.7060.128-NoLegal-DEBUG, Quest 3 (API 34, heapMax 512MB). Crash file logs/fastmediasorter_logs/fastmediasorter_crash_20260706_021831.log at 02:18:31.

Evidence (verbatim):
```
java.lang.OutOfMemoryError: Failed to allocate a 134217744 byte allocation with 25165824 free bytes and 107MB until OOM, target footprint 449422136, growth limit 536870912
  at com.sza.fastmediasorter.ui.xr.DiagnosticXrActivity$decodeBundledAsset$2.invokeSuspend(DiagnosticXrActivity.kt:713)
  Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@..., Dispatchers.Main.immediate]
```

Root cause (from code read `app_v2/src/vr/.../DiagnosticXrActivity.kt:697-725`): decodeBundledAsset decodes an 8192x4096 equirectangular bundled JPG to a 128MB ARGB_8888 bitmap (comment line 722 confirms "128 MB ARGB_8888 allocation"), copies pixels into an off-heap direct ByteBuffer (getReusableDirectBuffer, line 710-711), then at line 713 copies that buffer back into an on-heap ByteArray(buf.remaining()) = another 128MB on the Java heap while the bitmap is still held (returned to pool only in the finally block at 723). On-heap peak = bitmap 128MB + ByteArray 128MB = 256MB against a 512MB growth limit already sitting at ~449MB target footprint / 25MB free -> OOM on the 713 allocation. The persistent textureBytes field also holds a 128MB on-heap array. Direct-buffer round-trip to an on-heap ByteArray defeats the off-heap intent.

Repro: open the VR test in Settings (VrSettingsBlockFragment -> DiagnosticXrActivity) on a device with heap already warm (Glide caches populated). Severity: P0 crash (OutOfMemoryError, main-thread coroutine). Not ticketed (dedup: DiagnosticXr / bundled asset / OutOfMemory / vr diagnostic all no records; S0290 vr-test-quality-overhaul and S0382 vr-immersive-launch-anr both Archived). Flavor: vr source set (noLegal/VR only).

**Вложения:**
- log excerpt: полный crash-репорт OutOfMemoryError (main thread, decodeBundledAsset) - `PLAN/S0960_bugfix-vr-diagnostic-oom-decode/attachments/01__crash-oom-decodeBundledAsset.log`

---

## 1. Проблема / симптом

VR-самотест, открываемый из настроек (Settings -> VR-блок -> диагностический XR-экран), падает с `OutOfMemoryError` при декодировании встроенного equirect-ассета 8192x4096. Наблюдается на Quest 3 (API 34, growth limit 512MB) в билде noLegal-DEBUG, когда куча уже прогрета (Glide-кэши заполнены). Флавор: только `vr` (noLegal/VR). Эвиденс - вложение §0.

---

## 2. Корневая причина

Декод-путь держит на Java-heap одновременно 128MB bitmap ARGB_8888 и 128MB `ByteArray`, полученный обратным копированием из off-heap direct-буфера; пик on-heap ~256MB на лимите 512MB при уже занятых ~449MB. Детали и номера строк - в §0 (расследование подтверждено чтением кода).

---

## 3. Исправление

Замысел: срезать пик on-heap с ~256MB до ~64MB, применив к бандл-ассету тот же байт-бюджет, что уже действует для внешних файлов (96MB -> `inSampleSize=2` -> 4096x2048 -> 32MB), и застраховать единственную небезопасную аллокацию (`ByteArray` из direct-буфера) OOM-guard'ом с мягкой деградацией - путь `DecoderFailed` уже существует в `prepareInitialFrame`.

Файл: `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` (единственный).

### Phase 1 - Budget-driven bundled decode

1. In `decodeBundledPooled`: compute `sample = pickSampleSizeForBudget(BUNDLED_WIDTH, BUNDLED_HEIGHT)` before the pool request; ask `pool.getDirty` for the sampled dimensions; set `opts.inSampleSize = sample`. OOM fallback doubles the preflight sample (`sample * 2`) instead of hardcoded `2`.
   - Verification: bundled decode path never requests an ARGB_8888 allocation above `MAX_DECODE_BYTES`.
2. Rename `MAX_EXTERNAL_DECODE_BYTES` -> `MAX_DECODE_BYTES` (now covers bundled + external); refresh stale comments: "96 MB leaves headroom for the bundled pool entry (128 MB)", the "128 MB ARGB_8888 allocation" notes at `decodeBundledAsset`/companion, and the `decodeBundledPooled` KDoc.
   - Verification: `grep MAX_EXTERNAL_DECODE_BYTES` returns 0 matches; no comment still claims a 128 MB bundled allocation.

### Phase 2 - OOM-guarded shared RGBA copy helper

3. Add `private fun copyBitmapToRgbaBytes(bitmap: Bitmap): ByteArray?` - direct-buffer round-trip (`getReusableDirectBuffer` -> `copyPixelsToBuffer` -> on-heap `ByteArray`), `catch (oom: OutOfMemoryError)` -> `Timber.e` + `null`. Add `RGBA_BYTES_PER_PIXEL = 4` companion const (detekt MagicNumber).
   - Verification: helper compiles; log line <= 120 chars.
4. Replace the triplicated round-trip at the 3 call sites (`decodeBundledAsset`, `decodeImageToActivityBytes`, `loadCurrentMediaItem`) with the helper. `null` result: `decodeBundledAsset`/`decodeImageToActivityBytes` -> `return@withContext false` (existing `DecoderFailed` path); `loadCurrentMediaItem` -> log + skip `queueFrame` (slide stays on previous frame).
   - Verification: no remaining `ByteArray(buf.remaining())` on the texture decode paths outside the helper. HUD banner paths (`generateFilenameHudBytes`, error HUD) intentionally keep their round-trip: they use the dedicated 512 KB `reusableHudBuffer` on the main thread, which may run concurrently with an IO-thread texture decode - sharing the helper's `reusableDirectBuffer` would race.

### Phase 3 - Probes + build gate

5. Insert `Timber.d("S0960: ..")` probes (BlockNeedUserTest): one in `decodeBundledPooled` logging the chosen preflight sample/dims, one in `copyBitmapToRgbaBytes` logging the array size.
6. Build gate: `.\a.ps1 fkn` (vr source set compiles only in noLegal) - PASS required.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Компиляция: `.\a.ps1 fkn` - exit 0.
- Девайс (Quest 3, noLegal debug): открыть Settings -> VR-блок -> самотест при прогретой куче (после листания больших папок с картинками) - нет `OutOfMemoryError`; бандл-ассет рендерится (4096x2048); в logcat видны probes `S0960:` с sample=2 и размером массива ~33.5MB.
- Негативный сценарий: при искусственно забитой куче самотест завершается мягко (`DecoderFailed` -> возврат в Settings), не крашем.
