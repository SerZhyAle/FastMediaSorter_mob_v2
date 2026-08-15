# Спецификация (compact): S0971 - на Play-standard нативные модули (OCR/DTS) недоступны + вводящее в заблуждение сообщение

**Ticket:** S0971
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-06
**Tier:** 2 - Easy

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Источник:** владелец через `/newlog` - «не загружались пакеты во время инсталяции». Уточнено: билд **standard**, установлен **из Google Play**, не загрузились **несколько/все** модули Extensions.

**Диагноз (по коду):** [RealDeliverableSetDownloader.kt:60,74-78](app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/RealDeliverableSetDownloader.kt#L60) - на Play-инсталляции для нативных `.so`-наборов (OCR Tesseract/Paddle, FFmpeg DTS) вызывается `downloadNativeSetOnPlay()`, который БЕЗ сетевого запроса сразу отдаёт `DownloadProgress.Failed("This module is delivered via Google Play and is unavailable on this build")` и `Timber.i("Native set ... delivered via Google Play ... unavailable")`. Причина: политика Play (Device & Network Abuse) запрещает тянуть исполняемый `.so` с GitHub-зеркала; store-сборки де-бандлят наборы и `.so` вырезается из base-APK.

Итог by design: на Play-standard OCR/DTS-модули **не скачиваются вообще** и, судя по коду, фактической Play-доставки (Feature/Asset Delivery) для них нет - они просто недоступны.

**Вложения:** нет.

---

## 1. Проблема / симптом

Пользователь Play-версии standard заходит в Extensions, пытается установить OCR/DTS - все нативные модули падают в «This module is delivered via Google Play and is unavailable on this build». Сообщение вводит в заблуждение: подразумевает, что Play их доставит, но фактической Play-доставки нет - модули недоступны на этой сборке в принципе. UX-тупик.

---

## 2. Корневая причина

Нативные `.so` де-бандлятся из store-сборок (Play-политика), а альтернативной Play-доставки (DFM/Asset Delivery) для них нет -> Play-пользователь не может получить OCR/DTS. Плюс формулировка сообщения обещает Play-доставку, которой не существует.

---

## 3. Исправление - РЕШЕНИЕ ВЛАДЕЛЬЦА 2026-07-06: вернуть `.so` в сборки (re-bundle)

Владелец выбрал: **вставить нативный код обратно в APK** (не докачка с GitHub). Для store-AAB Play раздаёт per-ABI - размер загрузки почти не растёт; sideload (noLegal all-ABI APK) растёт, но это приемлемо ради корректности.

План реализации:
1. `app_v2/build.gradle.kts` - снять packaging-strip `.so` для OCR/DTS (сейчас глобальный `excludes += "**/libtesseract.so"` .. `libffmpegJNI.so`, строки ~884-893). `.so` есть в AAR (обёртки на compile-path), exclude их вырезал - убираем exclude, `.so` пакуются. Для lite/photos no-op (нет deps).
2. Флейвор-модули (`Standard/Legacy/NoLegal/Vr BundledDeliverableSetsModule`) - перенести `OCR_ENGINES` + `FFMPEG_DTS` из `descriptors()` в `bundledSets()`. `AUDIO_VISUALIZATIONS` (.mp4 данные) и OCR `.traineddata` остаются on-demand.
3. `DeliverableInventoryImpl` - bundled-набор показывать как `Installed` (не предлагать докачку): OR-in `bundled.contains(set)` в статус.
4. Снять Play-гейт OCR: `WelcomeFunctionalityController.isOcrVisible` и `WelcomeEnableAllManager` - убрать `&& !installSource.isPlayInstall()` (OCR теперь забандлен, работает на Play).
5. Тесты: `RealDeliverableSetDownloaderGateTest`, `DeliverableInventoryFilterTest` - обновить под bundled-поведение.
6. Проверка на release/target-варианте (Rule 20): `.so` реально в APK; пред. DFM ломал release-bundle - подтвердить сборку.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0386 (де-бандлинг - частично реверсим), S0401 (Play-доставка так и не построена - этот тикет закрывает пробел ре-бандлом), S0423/S0432 (delivery-инфра), S0400 (OCR-тумблер - снимаем Play-гейт).
- **Owner decision (2026-07-06):** A -> re-bundle `.so` в сборки.

---

## 4. Проверка

- Пруф упаковки: `.so` OCR/DTS присутствуют в собранном APK (все 4 ABI) - см. Last Audit.
- Компиляция standard (APK) + noLegal зелёные; delivery unit-тесты зелёные; detekt по изменённым файлам чист; post-change все гейты PASS.
- On-device (follow-up): открыть OCR и проиграть DTS-файл на реальной Play-сборке - функциональный smoke.

---

## Last Audit

**2026-07-06 - Implemented (re-bundle, direction A).**

Изменения:
- `app_v2/build.gradle.kts` - снят packaging-strip `.so` (libtesseract/leptonica/pngx/jpeg, libpaddle_*, libffmpegJNI) -> они снова пакуются из AAR.
- 4 флейвор-модуля (`Standard/Legacy/NoLegal/Vr BundledDeliverableSetsModule`) - `OCR_ENGINES` + `FFMPEG_DTS` перенесены в `bundledSets()` (из `descriptors()`); `AUDIO_VISUALIZATIONS` остался on-demand.
- `RealDeliverableSetDownloader` - инъекция `BundledDeliverableSets` + short-circuit: bundled-набор -> `Queued, Installed` без сети (покрывает enqueue-and-enable и inventory-download).
- `DeliverableInventoryImpl.moduleStatusFlow` - bundled -> `Installed` (не предлагать докачку).
- `WelcomeFunctionalityController` + `WelcomeEnableAllManager` - снят Play-гейт OCR (`!isPlayInstall()`), удалено ставшее unused поле `installSource`.
- `RealDeliverableSetDownloaderGateTest` - учтён новый конструктор + добавлен тест bundled-short-circuit; Play-гейт оставлен как защита для будущих не-bundled `.so`.

Верификация:
- **Пруф упаковки:** APK `standard/debug` - `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libtesseract.so`, `libleptonica.so`, `libpngx.so`, `libjpeg.so`, `libffmpegJNI.so` присутствуют (до правки были вырезаны). 20 OCR/DTS `.so` (5 × 4 ABI).
- noLegal compile PASS; `:app_v2:testStandardDebugUnitTest --tests "..delivery.*"` PASS; scoped detekt PASS (мои файлы чисты); post-change все гейты PASS.

Follow-up (не блокер): release-AAB использует тот же `android.packaging`-блок + per-ABI split - спот-чек `.so` на следующей release-сборке; on-device функциональный smoke OCR/DTS на Play-сборке.

**2026-07-09 - Verified (re-audit по working tree).**

- Пруф упаковки перепроверен на текущем `standard/debug` APK: 20 OCR/DTS `.so` присутствуют (`lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/` × `libtesseract/libjpeg/libleptonica/libpngx/libffmpegJNI`). Strip-exclude в `build.gradle.kts` снят (осталась лишь описательная строка-комментарий).
- Код подтверждён: `RealDeliverableSetDownloader` инъектит `BundledDeliverableSets` и short-circuit'ит bundled-набор в `Queued, Installed` без сети (строки 43/58-59); `WelcomeFunctionalityController`/`WelcomeEnableAllManager` - Play-гейт OCR (`!isPlayInstall()`) снят.
- Delivery unit-тесты зелёные: `DeliverableInventoryFilterTest` 7/0/0, `RealDeliverableSetDownloaderGateTest` 6/0/0.
- Probe-тегов `S0971:` в коде нет. Остаётся необязательный on-device smoke OCR/DTS на реальной Play-сборке (не блокер - упаковка и логика доказаны статикой + тестами).
