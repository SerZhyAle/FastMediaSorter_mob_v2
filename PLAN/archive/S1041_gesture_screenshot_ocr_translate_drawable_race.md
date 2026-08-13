**Status:** Archived

# S1041 - Gesture screenshot -> OCR/translate fires before image drawable is ready

## Goal

Жест "скриншот -> распознавание/перевод" открывает готовый PNG в `PhotoVideoStandaloneActivity`, но авто-перевод запускается синхронно сразу после старта асинхронной загрузки Glide, когда `photoView.drawable` ещё `null`. В результате пользователь получает ложный тост "Не получилось подготовить это изображение для распознавания", хотя картинка отображается через миг. Нужно отложить авто-действие (OCR/перевод) до момента готовности изображения, по образцу уже существующего колбэка `onVideoReady`.

## 0. Raw intake (verbatim)

Owner report (RU): жест -> скриншот -> распознавание-перевод. "Я вижу результат скриншота как изображение в плеере и вот такой тост о том что то-то не удалось для распознавания."

Toast text: "Не получилось подготовить это изображение для распознавания. Попробуйте ещё раз." (`R.string.ocr_extract_image_failed`).

Log context: `logs/fastmediasorter_20260713_144403.log` - `PhotoVideoStandaloneActivity` opens `screenshot_*.png` (image/png) on the auto-translate path.

## 1. Root cause (confirmed)

Race between async Glide decode and the synchronous auto-action:

- `ScreenshotGestureActionDispatcher` routes the capture into the viewer with `EXTRA_AUTO_ACTION = AUTO_ACTION_TRANSLATE` (post-save `OCR_TRANSLATE`) or pre-capture `TAKE_PHOTO_OCR_TRANSLATE`.
- `PhotoVideoStandaloneActivity` (~L877-882): `viewManager.show(file, type, onVideoReady)` starts an **async** Glide load into `binding.photoView`, then `maybeRunAutoAction(type)` runs on the **next line, synchronously**.
- `maybeRunAutoAction` -> `translateCurrentImage()` (L330-333) reads `binding.photoView.drawable?.toBitmap()`. Glide has not finished decoding -> `drawable == null` -> toast `ocr_extract_image_failed`, action aborts.
- `StandaloneViewManager.showImage()` (L394-402) is a plain `Glide.with(..).load(..).into(photoView)` with no ready callback, unlike the video path which already threads `onVideoReady`.

## 2. Fix direction

Mirror the existing `onVideoReady` pattern: give `show()`/`showImage` an `onImageReady` callback fired from a Glide `RequestListener<Drawable>.onResourceReady`, and defer the auto-action to it instead of firing synchronously after `show()`.

### 2a. Second-order fix (Glide listener ordering)

First implementation still toasted on device (log `fastmediasorter_20260713_151654.log`: `S1041: image ready -> maybeRunAutoAction(type=IMAGE)` fired, then 15s of silence - `translateCurrentImage` still took the null-drawable branch). Cause: `RequestListener.onResourceReady` runs **before** Glide binds the drawable into the `ImageView` (the target's `onResourceReady` sets it right after the listener returns `false`). So `photoView.drawable` was still null inside the callback. Fix: dispatch the callback via `photoView.post { .. }` so it runs on the next main-thread message, after the drawable is bound.

## Phases

### Phase 1 - Add image-ready callback to StandaloneViewManager

- [x] In [StandaloneViewManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt) add param `onImageReady: (() -> Unit)? = null` to `fun show(mediaFile, mediaType, onVideoReady, onImageReady)` and forward it to `showImage(mediaFile, onImageReady)`.
- [x] In `showImage`, attach `.listener(onImageReadyListener(..))`: a `RequestListener<Drawable>` whose `onResourceReady` invokes the callback and returns `false` (so Glide still sets the drawable into the target); `onLoadFailed` returns `false` and does not invoke the callback (a failed decode must not trigger OCR/translate).
- [x] Kept `showGif` / `reloadImage` untouched (GIF auto-OCR is out of scope; screenshots are PNG -> `MediaType.IMAGE`).
- **Verification:** `onImageReady` present in `show` signature + `showImage`; `RequestListener`/`Drawable` imports added; `a.ps1 fk` compiles (auto-build - PASS).

### Phase 2 - Defer auto-action until image ready in PhotoVideoStandaloneActivity

- [x] In [PhotoVideoStandaloneActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt) removed the synchronous `maybeRunAutoAction(type)` statement.
- [x] For `MediaType.IMAGE`, pass `onImageReady = { maybeRunAutoAction(type) }` into `viewManager.show(..)`; other types pass `null` (auto-action is a no-op for non-IMAGE anyway).
- [x] Named the video trailing-lambda calls `onVideoReady = { .. }` in both `PhotoVideoStandaloneActivity` and `StandalonePlayerActivity` - the new 4th param made a bare trailing lambda bind to `onImageReady` (compile fix).
- **Verification:** `maybeRunAutoAction` now invoked only inside the `onImageReady` lambda; `a.ps1 fk` compiles (auto-build - PASS); on-device gesture screenshot -> OCR/translate must no longer show the false `ocr_extract_image_failed` toast (device test).

## 3. Scope notes

- Flavor gating: OCR/translate is `ENABLE_TRANSLATION` (standard); no flavor change.
- No new user-visible string; the existing toast should simply stop firing spuriously.
- `show()` gains an optional param with a default -> existing callers (`reloadImage`, video path) are source-compatible.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI/UX:** no new UI; removes a spurious error toast on an existing auto-action flow. No layout, placement, or string change.

## 4. Manual verification (device)

Configure a screenshot-gesture band to "OCR / translate" (or the widget), trigger it over foreign-language content, confirm the translation dialog appears without the "couldn't prepare this image" toast. Repeat a few times to shake out the timing.

## Last Audit

2026-07-13 (rev 2) - code-level review after on-device retest (device confirmation still pending, `BlockNeedUserTest`).

- **Root cause was two-layered.** Rev 1 (defer auto-action to `RequestListener.onResourceReady`) still toasted: that listener fires *before* Glide binds the drawable into the `ImageView`, so `photoView.drawable` was still null. Rev 2 dispatches the callback via `photoView.post { .. }`, running it on the next main-thread message once the drawable is bound. Device log `fastmediasorter_20260713_151654.log` line 296 proved rev 1's gap (`S1041: image ready` fired, then the null-drawable branch, then 15s silence - no OCR pipeline).
- **Correctness (P3):** with `post`, `photoView.drawable` is non-null before `translateCurrentImage()`/OCR reads it - removes the race.
- **Display regression:** `onResourceReady` returns `false`, so Glide still binds the resource into the target; normal image display is unchanged. Video / audio / GIF / document paths untouched.
- **Failure path:** `onLoadFailed` returns `false` and does not fire the callback - a broken decode no longer triggers OCR/translate on a null bitmap.
- **Listener lifecycle:** the `RequestListener` is bound to the one-shot Glide request, not a long-lived registration; it is cancelled with the request on `release()` (`Glide.with(applicationContext).clear(photoView)`). No asymmetric register/unregister, no leak (audit protocol - Listener symmetry).
- **Threading:** `onResourceReady` runs on the main thread; `maybeRunAutoAction` reads the intent extra and launches a `lifecycleScope` coroutine - main-safe. Memory-cache hits may fire the callback synchronously inside `into()`, before `lastShownPath` is set, but the callback has no dependency on it.
- **One-shot:** `autoActionConsumed` still guards against a double auto-action.
- **Build:** `a.ps1 fk` PASS; `post-change -ScopeToFile` detekt PASS (none among changed files); neuroslop / listener-symmetry / pm-flags / flavor / ticket-log gates PASS.

**Verified 2026-07-13.** The `onImageReady` deferral was observed firing correctly on-device (the `S1041: image ready` probe fired after Glide bound the drawable). S1042 then superseded the OCR/translate auto-action (it now routes to the crop screen), so `onImageReady` is retained for the surviving `AUTO_ACTION_DRAW` / `AUTO_ACTION_CROP_AND_SHARE` auto-actions - the drawable-ready fix is flavor- and consumer-agnostic. Debug `S1041:` probe removed; status `Verified`.
