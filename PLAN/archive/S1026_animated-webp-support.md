# Стратегическая спецификация: S1026 - Поддержка анимированных WEBP

**Ticket:** S1026
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** `PLAN/S1026_animated-webp-support/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-13

**Текст:**

нужно добавить поддержку анимированных WEBP

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» Если фича новая - одно предложение для FEATURES + _RU + _UK.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>

---

## Last Audit

### Manual device test (2026-07-27, on-device verification of the `getEncodeStrategy` hotfix)

Device `emulator-5554`, `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424. Build under test
`com.sza.fastmediasorter.debug 2.60.7270.028-DEBUG`, installed from the supplied APK and confirmed via
`dumpsys package .. | grep versionName`; no rebuild. Evidence: `temp/S1026/s1026_run.log`,
`temp/S1026/frameprobe_1..4.png`. Fixtures on device: `/sdcard/Pictures/s1026b_anim.webp`,
`/sdcard/Pictures/s1026b_anim_apng.png` (MediaStore rows 33/34 after a forced volume rescan).

Result: 2 PASS, 1 NOT EXERCISED.

- [x] **1. Animated `.webp` opens in the full-screen viewer without the load-error message - PASS.** expected: the image appears, no "Could not load the image" | actual: viewer opened `/storage/emulated/0/Pictures/s1026b_anim.webp` (`PlayerMediaLoaderManager.displayImage`, `ImageLoadingManager: WebP display request .. mediaType=IMAGE`, `WebP display mode: animated=true, usePhotoView=true`) and rendered actual frame content; no error text, no empty `photoView`. This is the first run since 2026-07-21 where the animated WebP renders at all.
- [x] **2. Zero `Unknown strategy` and zero viewer-path `GlideException` for that file - PASS.** expected: 0 / 0 | actual: `Unknown strategy` = **0** occurrences. `GlideException` = **1** occurrence total, and it is not on the viewer path: it is logged at 00:34:21 by `AdapterThumbnailLoader$loadImage` (browse-list thumbnail), 7 s before the viewer opened at 00:34:28, with root cause `Unable to convert AnimatedImageDrawable to a Bitmap` - the separate unfixed thumbnail leg below. No `GlideException`, no `onLoadFailed` after `ImageLoadingManager.displayImage: START`.
- [ ] **3. Frames actually animate - NOT EXERCISED.** Mechanical reason: `MemoryTier.detect: tier=LOW, totalRAM=2.42GB, heapMax=576MB` (logged twice this run), and `ImageLoadingManager.kt:706/811/926` applies `request.dontAnimate()` whenever `memoryTier == MemoryTier.LOW`, i.e. before the decoder is consulted. A static first frame is the expected result on this AVD. Observation only, not a verdict: four `screencap`s over ~1.4 s were byte-identical apart from the status-bar clock (md5 `990835B8..` x3, then `D9E30831..` differing only in the minute digit), so no motion was observed - consistent with `dontAnimate()`, not evidence about the fix.

Probe `S1026: decoded animated drawable` did **not** fire, and that is consistent rather than a failure.
The probe sits in `AnimatedImageDecoder.kt:97` inside `decodeAnimatedDrawable`, which is only reached when
`isAnimationDisabled(options)` is false; with `dontAnimate()` applied the custom decoder declines and hands
the file to the built-in downsampler. So the code path that would log it was never entered on this tier.
Corollary worth recording: because the custom decoder declined, this run does **not** directly witness
`AnimatedImageDrawableNoOpEncoder.getEncodeStrategy()` returning `SOURCE`; what it witnesses is that the
observable regression (viewer load error) is gone. A tier `>= NORMAL` device is still needed to exercise
the animated decode + encode path end to end.

Separate unfixed leg (count only, no verdict): `Unable to convert .. AnimatedImageDrawable .. to a Bitmap`
appears **2 log lines / 1 incident** - `E GlideExecutor` at 00:34:21.088 and the restating
`W AdapterThumbnailLoader$loadImage` at 00:34:21.155, same drawable instance `@2345402`, for
`s1026b_anim.webp` only. Note the literal string is `Unable to convert android.graphics.drawable.AnimatedImageDrawable@<hash> to a Bitmap`, so a grep for the phrase without the FQCN+hash returns 0 and reads as a false clean.

**2026-07-26 - /spec-test-device** (device: emulator-5554, Android 15, SDK 35; standard debug 2.60.7262.102-DEBUG, freshly rebuilt and installed for this run)

Verdict: **FAIL** - third consecutive on-device failure. Animated WebP still cannot be viewed; animated APNG now loads but silently never animates.

### Manual / on-device

- [!] Animated `.webp` animates + badge + play/pause - failed on-device 2026-07-26. Expected: image animates, `tvAnimatedBadge` visible, play/pause toggles, probe `S1026: decoded animated drawable software-allocated`. Actual: "Не удалось загрузить изображение"; `photoView` empty; `ImageLoadingGlideListeners$createDrawableListener: WebP display failed: file=s1026b_anim.webp source=local firstResource=true`; `class com.bumptech.glide.load.engine.GlideException: Failed to load resource`; `onLoadFailed triggered`. Identical failure signature to 2026-07-24 and 2026-07-21 - regression unchanged.
- [!] Animated `.apng` animates + badge + play/pause - failed on-device 2026-07-26. Expected: animates + badge + play/pause + probe fires. Actual: image loads without error (no GlideException, no toast) but renders a frozen "frame 0" - two screenshots 2s apart are pixel-identical, no animation. `mobile_list_elements_on_screen` dump of the open viewer contains no `tvAnimatedBadge` anywhere in the hierarchy. No `S1026:` probe of any kind fired for this file - the APNG path does not enter `AnimatedImageDecoderKt` at all in this build (unlike webp). This is a silent fallback to a static frame, which per the test brief counts as FAIL, not PASS - and is a new failure mode vs 2026-07-24, when APNG used to fail loudly with the same `GlideException` as webp.
- [x/!] Browse-list thumbnails render a static first frame, not a failure placeholder - mixed on 2026-07-26. Visually both `s1026b_anim.webp` and `s1026b_anim_apng.png` show a static first-frame thumbnail across every list screenshot (no broken-image icon). But logcat shows the webp thumbnail decode throwing `GlideException` 5 separate times (`AdapterThumbnailLoader$loadImage: Local image load failed: s1026b_anim.webp` / `Unable to convert android.graphics.drawable.AnimatedImageDrawable to a Bitmap`) - the visible thumbnail apparently comes from a different/retried request, but the primary decode path still errors every time, which violates the zero-`GlideException` requirement regardless of what ends up on screen.
- [x] Static `.webp` unchanged - verified 2026-07-26 via pre-existing library fixtures `photo_webp_001.webp`/`photo_webp_002.webp` (freshly-pushed `s1026_static.webp` was indexed by MediaStore per direct `content query` but did not surface in the app's own "Все изображения" list of 33 files - an app/list-side exclusion of a near-empty 108-byte file, unrelated to S1026). Both render normally, no badge, no errors.
- [x] GIF unchanged - verified 2026-07-26 with `s1026_control.gif` (fresh copy of `c:/Common/test_media/GIF-Smiles.gif`). Opens with `GIF` badge; two screenshots 2s apart show materially different frame content - confirmed animating; zero errors in logcat for this file.

Probe counts (full-session logcat): `S1026: decoded animated drawable software-allocated` (viewer positive) = **0** - never fires. `S1026: declined animated decode, request asked for a still` (thumbnail/still path) = **44** - fires broadly and correctly.

GlideException count: 6 real thrown instances (`class com.bumptech.glide.load.engine.GlideException`), all attributable to `s1026b_anim.webp` (1 direct viewer open + 1 viewer-adjacent prefetch + repeated thumbnail-decode failures). Acceptance requires zero. Spec remains `BlockNeedUserTest`.

Evidence: `temp/S1026/mobile_test_scenario_20260726_2148.md`, `temp/S1026/run_20260726_final.log`, `temp/S1026/screens/webp_viewer_FAIL_20260726.png`, `temp/S1026/screens/apng_viewer_static_frame0_20260726.png`. Fixtures reused from the 2026-07-24 run's `temp/S1026/media/` (`s1026b_anim.webp`, `s1026b_anim.png`) plus a fresh GIF copy from `c:/Common/test_media/GIF-Smiles.gif`.

**2026-07-24 - /spec-all** (устройства нет; правка по первопричинам из прогона того же дня)

Обе первопричины из предыдущего аудита закрыты в одном месте - в самом декодере, без правки опций запроса на местах вызова.

- **Миниатюры.** `handles()` теперь отказывается от файла, если запрос выставил `GifOptions.DISABLE_ANIMATION` (это делает `dontAnimate()`, который `AdapterThumbnailLoader` уже вызывает во всех ветках). Файл уходит встроенному даунсемплеру и декодируется первым кадром - ровно то поведение, что было до S1026. Причина, по которой прежний путь падал, подтверждена по исходникам Glide 4.16.0: `DrawableToBitmapConverter.convert()` отказывает `Animatable`-drawable явной проверкой, а не пытается отрисовать его в канву, поэтому загрузка миниатюры валилась целиком.
- **Вьюер.** Декодер выставляет `ImageDecoder.ALLOCATOR_SOFTWARE`. По умолчанию `ImageDecoder` отдаёт drawable поверх HARDWARE-буфера, и шаг трансформации/кэширования Glide падал на `Cannot create a mutable Bitmap with config: HARDWARE`. Программная аллокация стоит heap, но это единственная конфигурация, которую тот шаг умеет копировать.

Проверено без устройства: `AnimatedImageDecoderHandlesTest` - 6 тестов на `handles()` (claim по умолчанию, отказ при `dontAnimate()`, claim при явно включённой анимации, статический WebP не захватывается никогда), `check-standard-fast.ps1 -Mode Unit` BUILD SUCCESSFUL; `-Mode CodeAndResources` BUILD SUCCESSFUL.

Не проверено: сама анимация и бейдж во вьюере - только на устройстве. Зонды `S1026:` возвращены, статус `BlockNeedUserTest`.


**2026-07-24 - /spec-test-device** (device: emulator-5554 / Pixel 9, Android 15, SDK 35; standard debug 2.60.7220.314-DEBUG)

Verdict: **FAIL** - animated WebP and APNG still do not display in the viewer after the claimed S1026 no-op-encoder fix. Regression persists; root cause unchanged from 2026-07-21.

### Manual / on-device

- [!] Animated `.webp` loads + animates + badge + play/pause - failed on-device 2026-07-24. Expected: image animates, `tvAnimatedBadge` visible, play/pause, probe `S1026: image ready animatable=true`. Actual: app correctly detects the file as animated (`ImageLoadingManager: WebP display mode: animated=true, usePhotoView=true`) but the load fails - `WebP display failed: file=s1026b_anim.webp source=local firstResource=true`; `GlideException: Failed to load resource`; `onLoadFailed triggered`; viewer `photoView` empty, no `tvAnimatedBadge` in the hierarchy; "Could not load the image" snackbar. Tested with a robust 320x320 animated WebP (real inter-frame motion, valid ANMF chunks) to rule out a malformed fixture.
- [!] Animated `.apng` (`.png`-wrapped, valid `acTL`) loads + animates + badge + play/pause - failed on-device 2026-07-24. Expected: animates + badge + play/pause. Actual: identical `GlideException: Failed to load resource`; "Could not load the image"; empty viewer.
- [ ] Static `.webp` shows no badge - not re-verified this run (prior run confirmed PASS); out of the failing path.
- [ ] GIF unchanged - not re-verified this run (prior run confirmed PASS; GIF ships its own encoder and is unaffected).

Root cause (confirmed, unchanged): Glide DOES decode animated WebP/APNG to an `AnimatedImageDrawable` (the decoder from the fix is present), but the load pipeline still fails at the resource-encode / bitmap-convert step, so `onResourceReady` is never reached and the S1026 probe never fires.
- Viewer root cause: `java.lang.IllegalArgumentException: Cannot create a mutable Bitmap with config: HARDWARE (Consider setting Downsampler#ALLOW_HARDWARE_CONFIG to false ..)` - Glide attempts a mutable-Bitmap conversion of the HARDWARE-backed `AnimatedImageDrawable` for disk caching and fails.
- Browse-thumbnail root cause: `java.lang.IllegalArgumentException: Unable to convert android.graphics.drawable.AnimatedImageDrawable to a Bitmap` (`AdapterThumbnailLoader`).
- Fix direction (per the message itself + prior audit): disable HARDWARE config for the animated request (`ALLOW_HARDWARE_CONFIG=false` / `disallowHardwareConfig()`) and/or request `DiskCacheStrategy.DATA`/`NONE` for animated drawables and register a real passthrough encoder so the cache-encode step does not attempt a bitmap conversion. The current `di/GlideAppModule.kt` no-op-encoder registration does not prevent the HARDWARE-bitmap conversion attempt.

Probe: `Timber.d("S1026: ..")` did not fire at all this run (grepped `run_20260724*.log`) - the animated positive path fails before `onResourceReady`, so the `animatable=true` probe is unreachable. Spec remains `BlockNeedUserTest`; the acceptance criterion is not met.

Evidence: `temp/S1026/run_20260724.log` (APNG viewer), `temp/S1026/run_20260724_webp.log` (animated-WebP viewer + thumbnail root causes), `temp/S1026/screens/webp_anim_FAIL_20260724.png`. Test fixtures: `temp/S1026/media/s1026b_anim.webp`, `temp/S1026/media/s1026b_anim.png`.

**2026-07-21 - /spec-test-device** (device: emulator-5554 / Pixel 9, Android 15, SDK 35; standard debug 2.60.7211.441-DEBUG)

Verdict: **BROKEN** - animated WebP and APNG do not display in the viewer (regression vs the pre-S1026 static first-frame behavior).

### Manual / on-device

- [!] Animated `.webp` animates + play/pause works - failed on-device 2026-07-21. Expected: image animates, animated badge visible, play/pause toggles. Actual: "Could not load the image"; Glide throws `Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class android.graphics.drawable.AnimatedImageDrawable`; `onResourceReady` never reached, no badge, nothing rendered. Browse-list thumbnail also fails (`AdapterThumbnailLoader: Unable to convert AnimatedImageDrawable to a Bitmap`).
- [!] Animated `.apng` animates + play/pause works - failed on-device 2026-07-21. Expected: animates + badge + play/pause. Actual: identical `NoResultEncoderAvailableException` for `AnimatedImageDrawable`; "Could not load the image"; thumbnail fails too. Both `.apng` and `.png`-wrapped APNG affected. (Note: `.apng` did enumerate in All Images as `image/apng`.)
- [x] Static `.webp` shows no animated badge and no no-op toggle - verified on-device 2026-07-21. Expected: no badge, no toggle. Actual: decodes to a static drawable; probe `S1026: animated-image decoded animatable=false ext=webp`; no `tvAnimatedBadge`; dynamic-background path runs normally.
- [x] GIF still animates unchanged - verified on-device 2026-07-21. Expected: animates, unchanged. Actual: loads and animates; `tvAnimatedBadge`="GIF"; `AFTER GIF onResourceReady`; zero load errors.

Root cause: `di/GlideAppModule.kt` registers `AnimatedImageDecoder` (produces an `AnimatedImageDrawable`) but no matching `ResourceEncoder<AnimatedImageDrawable>` is registered, and the request caches the decoded resource on disk. Glide's `DecodeJob` fails at the cache-encode step -> `NoResultEncoderAvailableException` -> load fails. Fix options (per Glide's own message): register a passthrough encoder for `AnimatedImageDrawable`, or request animated content with `DiskCacheStrategy.DATA`/`NONE`, or skip resource caching for animated drawables. GIF is unaffected because Glide ships a `GifDrawableEncoder`.

Probe: `Timber.d("S1026: ..")` fired 1x total, only for the static-webp negative case (`animatable=false`). It never fired for the animated positive cases because those loads fail before `onResourceReady` - so the animated code path is unverified on-device.

Evidence: `temp/S1026/run_20260721.log`; `temp/S1026/screens/step02_anim_webp_FAIL.png`, `step03_apng_FAIL.png`, `step04_gif_PASS.png`, `step05_static_PASS.png`.
