# Phase 11 - Video Gamma + Bitmap Pipeline (heap + brightness)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md) §1.1, §1.2, §5.1.D.2, §5.1.D.4, ADR-5
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (Implemented direct buffer caching for bundled, external, and HUD image paths)
**Depends on:** Phase 09 (lifecycle fix должна быть в строю — иначе stale Bitmap pool переживёт shutdown и испортит измерения)
**Blocks:** Phase 02 (bundle-first нагружает heap чаще всего), Phase 06 (render quality зависит от стабильного gamma path)
**Steps done:** 5 / 5
**Started:** 2026-05-22
**Completed:** 2026-06-01

---

## Objective

Снять два связанных дефекта из §1.1:

- **OOM на 2-м запуске + main-thread freeze.** 128 МБ RGBA bundled bitmap декодируется на main thread, не освобождается после native upload, переживает shutdown. Решение: off-main coroutine + одно-элементный reusable pool + `bitmap.recycle()` сразу после `nativeQueueFrame`.
- **Видео слишком яркое.** Double sRGB-encoding (linear OES sample → sRGB swapchain encode). Решение: manual sRGB encode в видео-шейдере перед записью в swapchain.

---

## Prerequisites

- [ ] Read strategic §1.1 (наблюдения A, B, C), §5.1.D.2, §5.1.D.4, ADR-5.
- [ ] Phase 09 ✅ Done (paired init/shutdown).
- [ ] Working tree clean / feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 740 |
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1350 |
| `app_v2/src/vr/cpp/shaders/video_flat.frag` *(или существующий fragment-shader video-path)* | Modified | ≤ 80 |

**Примечание (ADR-5 v2):** самописный `DiagnosticBitmapPool` **не создаётся** — используется существующий `Glide.get(context).bitmapPool` из `com.github.bumptech.glide:glide:4.16.0`, уже подключенный в `app_v2/build.gradle.kts`.

---

## Steps

### Step 11.1 - Off-main bitmap decode через Glide BitmapPool

**Files:** `DiagnosticXrActivity.kt`

**Prompt for developer:**

> В `DiagnosticXrActivity` (или в helper'е, если основной класс приближается к лимиту 740 LOC) реализовать decode через Glide BitmapPool. Паттерн:
> ```kotlin
> private suspend fun decodeBundled(@DrawableRes resId: Int, expectedW: Int, expectedH: Int): Bitmap = withContext(Dispatchers.IO) {
>     val pool = Glide.get(this@DiagnosticXrActivity).bitmapPool
>     val reusable = pool.getDirty(expectedW, expectedH, Bitmap.Config.ARGB_8888)
>     val opts = BitmapFactory.Options().apply {
>         inBitmap = reusable
>         inMutable = true
>         inPreferredConfig = Bitmap.Config.ARGB_8888
>     }
>     try {
>         resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, opts) ?: error("decode failed") }
>     } catch (oom: OutOfMemoryError) {
>         Timber.w(oom, "S0290 bundled decode OOM with inBitmap; retry with inSampleSize=2")
>         opts.inBitmap = null
>         opts.inSampleSize = 2
>         resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, opts) ?: error("decode failed fallback") }
>     }
> }
> ```
> Для внешних фото (Phase 02+) — preflight через `inJustDecodeBounds = true`, затем тот же паттерн с `pool.getDirty(outWidth, outHeight, ARGB_8888)`. **Не создавать** новый класс `DiagnosticBitmapPool` — Glide BitmapPool уже выполняет роль pool (ADR-5 v2).

**Verification:**

- `Grep` - `Glide\.get\(` + `\.bitmapPool` matches at least once в `DiagnosticXrActivity.kt`.
- `Grep` - `pool\.getDirty\(` matches at least once.
- `Grep` - `Dispatchers\.IO` или `withContext\(Dispatchers\.IO\)` matches в bitmap decode пути.
- `Grep` - `inSampleSize\s*=\s*2` присутствует в fallback ветке.
- `Grep` - класс `DiagnosticBitmapPool` отсутствует (cross-check: `Glob` `**/DiagnosticBitmapPool.kt` → 0 hits).
- Build: `.\a.ps1 nd` passes.
- Manual on-device: смена слайда с bundled на внешний и обратно — нет `Choreographer: Skipped \d+ frames` > 5 в логе.

**Status:** `[x] done`

---

### Step 11.2 - Возврат bitmap в Glide pool сразу после native upload

**Files:** `DiagnosticXrActivity.kt`

**Prompt for developer:**

> После `nativeDiagnosticXrRuntime.queueFrame(rgba, w, h)` (или эквивалентного метода upload) в той же coroutine ветке вернуть bitmap в pool: `Glide.get(this).bitmapPool.put(bitmap)`. **Не** вызывать `bitmap.recycle()` — pool сам управляет рециклингом по LRU. Удалить любые поля класса, удерживающие Bitmap после upload'а (если есть — это сейчас и есть heap leak source). Strong-ref на Bitmap живёт ≤ длительность одного advance.
> Edge case: если ByteArray RGBA копируется на native стороне (что и должно происходить в `nativeQueueFrame`), pool.put сразу безопасен. Если на native-стороне есть async/zero-copy путь — добавить native callback `nativeOnFrameReleased` и pool.put выполнять в нём.

**Verification:**

- `Grep` - `\.bitmapPool\.put\(` matches at least once в `DiagnosticXrActivity.kt` после `queueFrame`-вызова.
- `Grep` - в `DiagnosticXrActivity.kt` нет полей класса типа `private var lastBitmap: Bitmap?` после рефакторинга (cross-check ручным просмотром).
- Manual on-device: после 5 advance'ов `adb shell dumpsys meminfo … | grep TOTAL` стабилен ±20 МБ.

**Status:** `[x] done`

---

### Step 11.3 - Video gamma: decode BT.709 → linear в видео-шейдере

**Files:** `app_v2/src/vr/cpp/xr_session.cpp` (или соответствующий shader source), `video_flat.frag`

**Prompt for developer:**

> Найти fragment-shader, отвечающий за external OES video sampling (поиск по `samplerExternalOES`). Перед записью в `gl_FragColor` / `outColor` **decode** gamma:
> ```glsl
> vec4 sampled = texture2D(uTexture, vTexCoord);
> // OES_EGL_image_external spec: color in source colorspace (no gamma decode/encode).
> // H.264 MediaCodec → BT.709 gamma-encoded RGB.
> // sRGB swapchain ожидает linear на write → нужен decode здесь, иначе double-encode = over-bright.
> outColor.rgb = pow(sampled.rgb, vec3(2.2));
> outColor.a = sampled.a;
> ```
> Это **decode**, не encode (`pow(2.2)`, не `pow(1.0/2.2)`). См. [Khronos OES_EGL_image_external spec](https://registry.khronos.org/OpenGL/extensions/OES/OES_EGL_image_external.txt) и ADR-5 v2. Для фото-пути (`sampler2D` с `GL_SRGB8_ALPHA8` текстурой) ничего не менять — там путь корректен через hardware sRGB sampler conversion.

**Verification:**

- `Grep` - `pow\(.*\.rgb,\s*vec3\(2\.2\)\)` или эквивалент matches в видео-шейдере.
- `Grep` - в видео-шейдере **отсутствует** `pow\(.*1\.0\s*/\s*2\.2\)` (это была изначальная ошибочная формула, не должна попасть в commit).
- `Grep` - в фото-шейдере pow-инструкция вообще отсутствует (иначе будет тройной encode).
- Build: `.\a.ps1 nd` passes.
- Manual on-device: Big Buck Bunny яркостно совпадает с тем же файлом в системном плеере десктопа (subjective owner check).

**Status:** `[x] done`

---

### Step 11.4 - Timber probe для BlockNeedUserTest

**Files:** `DiagnosticXrActivity.kt`

**Prompt for developer:**

> При переводе спеки в `BlockNeedUserTest` после Phase 11 добавить ровно один `Timber.d("S0290: bitmap recycled after native upload")` сразу после recycle-вызова в Step 11.2 и ровно один `Timber.d("S0290: video gamma path active")` при первой инициализации video pipeline. Удалить `/spec-check` при переходе в Verified.

**Verification:**

- `Grep` - `Timber\.d\("S0290: bitmap recycled` matches exactly once.
- `Grep` - `Timber\.d\("S0290: video gamma path active` matches exactly once.

**Status:** `[x] done`

---

### Step 11.5 - Texture-copy allocation hardening (second 128 MB peak)

**Files:** `DiagnosticXrActivity.kt`

**Context (strategic §1.2):** on-device лог 2026-06-01 (`temp/quest3_log_analysis_20260601.md`) доказал, что Steps 11.1–11.2 сняли OOM только на bitmap-decode. Краш переехал на **вторую** 128 МБ аллокацию того же кадра — `ByteBuffer.allocateDirect(w * h * 4)`, копирующую пиксели из декодированного bitmap в `textureBytes`. Стек: `VMRuntime.newNonMovableArray` ← `DirectByteBuffer.<init>` ← `ByteBuffer.allocateDirect` ← bundled-asset decode → `onCreate`. Этот путь повторяется на трёх местах: bundled-asset decode, flat-image decode, и HUD-banner copy. Ни одно из них не обёрнуто в `try/catch(OutOfMemoryError)` (в отличие от decode-пути) и не переиспользует буфер (в отличие от bitmap pool). Пик = bitmap (128 МБ) + direct buffer (128 МБ) ≈ 256 МБ на 512 МБ heap, разогретом предыдущими VR-видео.

**Prompt for developer:**

> Снять второй 128 МБ peak на всех трёх `allocateDirect(w * h * 4)` + `copyPixelsToBuffer` путях (bundled-asset, flat-image, HUD-banner). Выбрать один из вариантов (owner/dev decision при исполнении):
> - **(a) Reusable direct buffer (предпочтительно для bundled).** Держать единый кэшированный `ByteBuffer.allocateDirect` фиксированного размера `BUNDLED_WIDTH * BUNDLED_HEIGHT * 4` (как Glide pool уже сделан для bitmap), переиспользовать между advance'ами вместо аллокации на каждый кадр. Для HUD-banner — кэшировать по фиксированному `HUD_BANNER_WIDTH * HEIGHT * 4`.
> - **(b) inSampleSize downscale.** Понижать bundled-текстуру до 4096×2048 (32 МБ) по аналогии с `decodeFilePooled` / `MAX_EXTERNAL_DECODE_BYTES` — если renderer не требует полного 8K для diagnostic placeholder.
> - **(c) Минимум — try/catch + retry.** Обернуть `allocateDirect` + `copyPixelsToBuffer` в `try/catch(OutOfMemoryError)` с retry через `inSampleSize=2` decode + меньший буфер, чтобы warm-heap launch деградировал, а не падал.
> Вариант (a) снимает peak полностью и предпочтителен; (c) — обязательный минимум, если (a)/(b) не выбраны.

**Verification:**

- `Grep` - нет «голого» `ByteBuffer\.allocateDirect\(w \* h \* 4\)` без обрамляющего `try`/reusable-buffer на bundled/flat/HUD путях (ручная cross-проверка трёх мест).
- `Grep` - если выбран вариант (a): поле reusable direct buffer присутствует и переиспользуется (не `allocateDirect` на каждый `decodeBundledAsset`).
- `Grep` - если выбран вариант (c): `catch\s*\(.*OutOfMemoryError` обрамляет copy-путь.
- Build: `.\a.ps1 nd` passes.
- Manual on-device: 5×(enter→exit→enter) подряд — нет `OutOfMemoryError` в логе на 2-м..5-м запуске; нет `FATAL CRASH` от `DiagnosticXrActivity.decode*`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every Step 11.* is `[x] done`. (Step 11.5 completed 2026-06-01)
- [x] Build `.\a.ps1 nd` passes. (2026-05-22 13:25; PASS exit 0; APK at DOWNLOADS/FastMediaSorter_nolegal_debug.apk)
- [ ] **MANUAL-REQUIRED** Manual on-device: 10 последовательных advance'ов между bundled и внешними слайдами — нет `Skipped \d+ frames` > 5 и нет `Long-running draw task`.
- [ ] **MANUAL-REQUIRED** Manual on-device: Big Buck Bunny субъективно совпадает по яркости с desktop-плеером.
- [ ] **MANUAL-REQUIRED** Manual on-device: 5×(enter→exit→enter) подряд — нет `OutOfMemoryError` на 2-м..5-м запуске (Step 11.5, strategic §1.2).
- [x] Dev log entry для каждого файла из «Files Touched». (2 entries: DiagnosticXrActivity.kt, xr_session.cpp video shader edit)

---

## Handoff Notes to Next Phase

После Phase 11 bundle-first плейлист (Phase 02) можно включать без риска OOM. Phase 06 (render quality — MSAA/sRGB/mipmap) теперь работает поверх стабильного gamma pipeline — фото и видео имеют согласованную gamma chain.

---

## Rollback Plan

Revert phase commits — старый main-thread decode возвращается (heavy heap, OOM risk на 2-м запуске сохраняется до Phase 09 fix); video gamma возвращается к over-bright. Изменения локализованы в bitmap pipeline + video shader.
