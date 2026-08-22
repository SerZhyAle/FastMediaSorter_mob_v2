# Спецификация (compact bugfix): S1703 - PaddleOCR всегда возвращает пустой результат: postprocess не написан

**Ticket:** S1703
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-15
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

Найдено при исследовании точности OCR-оверлея (перенос знаний из соседнего проекта doc-html-translate,
`docs/OCR_OVERLAY_ACCURACY.md`). Вне области той задачи.

`PaddleOcrEngine` в `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt`
инициализирует три предиктора (detector, classifier, recognizer), скачивает модели, прогоняет инференс - и
затем `postprocess` логирует формы выходных тензоров и возвращает `emptyList()`. Разбора выхода нет.

Verbatim, строки 148-158:

```kotlin
    private fun postprocess(
        detector: PaddlePredictor?,
        classifier: PaddlePredictor?,
        recognizer: PaddlePredictor?
    ): List<OcrTextBlock> {
        val detectorShape = detector?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        val classifierShape = classifier?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        val recognizerShape = recognizer?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        Timber.d("PaddleOCR output shapes: det=$detectorShape, cls=$classifierShape, rec=$recognizerShape")
        return emptyList()
    }
```

Следствия, все три наблюдаемые:

1. `OfflineOcrEngineProvider.recognizeTextBlocksWithFallback` видит пустой результат и молча уходит на
   Tesseract (`Timber.w("Selected OCR engine returned no text blocks, falling back to default OCR")`). То
   есть выбор движка PaddleOCR в настройках не меняет ничего, кроме потраченного времени и трафика на
   модели.
2. `docs/ALL_FEATURES.jsonl` содержит запись `ocr-translation.offline-ocr-engine-paddleocr` со статусом
   `active`, описывающую PaddleOCR как рабочий дополнительный движок для noLegal и vr. Запись не
   соответствует коду.
3. Заглушка проходит гейт Rule 19 (shipped runtime stubs), потому что возвращает `emptyList()`, а не
   `TODO()`. Стоит подумать, ловится ли этот класс механически.

Развилка для владельца: дописать `postprocess` (разбор DB-выхода детектора, CTC-декод распознавателя, своя
таблица словаря) - это заметная работа; либо снять PaddleOCR с флейворов и убрать модели, зависимость и
запись в `ALL_FEATURES`. Второе резко уменьшает размер поставки. Решение не мной.

---

## Проверка постановки 2026-08-16

Все три следствия воспроизводятся сегодня, ни одно не устарело:

- `PaddleOcrEngine.postprocess` (строки 148-158) по-прежнему логирует формы тензоров и возвращает
  `emptyList()`. Разбора выхода нет.
- Запись `ocr-translation.offline-ocr-engine-paddleocr` в `docs/ALL_FEATURES.jsonl` жива, `status`
  `active`, описание - «Provides an additional OCR engine based on PaddleOCR native libraries for arm64,
  available in sideload and VR flavors». Инвентарь утверждает работающую способность, которой нет.
- Механически заглушка не ловится, и это подтверждено: гейт на shipped runtime stubs -
  `scripts/quality/assert-stub-todo.ps1` - ищет ровно `TODO()` и `NotImplementedError`. Функция,
  возвращающая пустую коллекцию вместо результата, под эти маркеры не подпадает ни при какой настройке
  ратчета: пустой список - легальное возвращаемое значение, и отличить «нечего вернуть» от «не написано»
  статически нельзя. То есть пункт 3 постановки решается не расширением гейта, а тем, что заглушка
  такого рода не должна доживать до инвентаря.

**Что можно сделать без владельца:** ничего существенного. Правка инвентаря сама по себе является
половиной развилки - если движок дописывают, запись верна; если снимают, она становится `removed`
вместе с моделями и зависимостью. Менять её вперёд решения значит принять решение молча.

Связанный контекст: memory `project_s0386_native_attach_broken_api36` и `project_native_so_bundle_standard_vs_ondemand_nolegal` - нативная привязка `.so` для PaddleOCR на arm64/API 36 и так ненадёжна (S0923 Layer 2 не сделан).

---

## 1. Проблема / симптом

Выбор движка PaddleOCR не даёт распознавания ни в одном сценарии; всегда происходит тихий откат на
Tesseract после полного цикла инициализации и инференса. Флейворы `noLegal`, `vr`.

---

## 2. Корневая причина

Функция разбора выхода моделей не реализована - `postprocess` возвращает пустой список.

---

## 3. Исправление

Решение владельца 2026-08-16: PaddleOCR снимается с поставки. Удаляются модели, зависимость и флейворная
обвязка; запись `ocr-translation.offline-ocr-engine-paddleocr` в `docs/ALL_FEATURES.jsonl` переводится в
`removed`. Повторное введение второго движка - отдельным тикетом, если понадобится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0288 (nolegal-paddleocr-paddlelite-bundle, Archived), S0386 (ondemand-ocr-translation-delivery, Archived), S0923 (ocr-native-attach-arm64-crash, Archived).
- **Owner decision:** PaddleOCR снимается с флейворов вместе с моделями, зависимостью и записью в `ALL_FEATURES` (решение владельца 2026-08-16).

---

## 4. Проверка

Сборка noLegal без зависимости и моделей, запись в `ALL_FEATURES` переведена в `removed`, уменьшение
размера поставки замерено числом.

### 4.1 Repro-запись (до / после)

**До.** Дефект наблюдался чтением кода, а не прогоном: `PaddleOcrEngine.postprocess` (строки 148-158)
логировал формы трёх выходных тензоров и возвращал `emptyList()`; разбора выхода в функции не было.
Следствие в рантайме - `OfflineOcrEngineProvider.recognizeTextBlocksWithFallback` получал пустой список и
писал `Timber.w("Selected OCR engine returned no text blocks, falling back to default OCR")`, то есть выбор
PaddleOCR в настройках не менял результат ни в одном сценарии. Оба факта перепроверены 2026-08-16 и
зафиксированы в разделе «Проверка постановки».

**REPRO: not reproducible on demand** - воспроизведение на устройстве требовало флейвора `noLegal` на
arm64 с нативной привязкой `.so`, которая сама по себе ненадёжна на API 36 (S0923, Layer 2 не сделан), и
результатом всё равно был бы тихий откат на Tesseract, снаружи неотличимый от штатной работы. Прямое
доказательство дефекта - исходный код функции, приведённый в §0 verbatim.

**После.** Наблюдение исчезло вместе со своим субъектом: движок, его модели, вендоренные `.so`, его записи
в наборе доставки и его выбор в настройках удалены. Пути, где дефект проявлялся, больше не существуют:
`grep` по `app_v2/src` и `wear/src` даёт ноль упоминаний `PaddleOcrEngine`, `rowOcrEngineType`,
`rowPaddleOcrModel`, `CAP_OCR_ENGINE_SELECTION` и `NO_LEGAL_OCR`. Компиляция затронутых флейворов после
удаления - `a.ps1 fk` exit 0, `a.ps1 fkn` exit 0, `a.ps1 nd` exit 0 (фаза 03).

### 4.2 Замер поставки

Вычтено из `noLegal`: 10 МБ вендоренных `.so` под `app_v2/src/noLegal/jniLibs/arm64-v8a` плюс JNI-обёртка
`com.baidu.paddle.lite`. Модели PaddleOCR доставлялись по требованию, а не в пакете, поэтому из размера APK
они не вычитаются - вместо этого исчезает их скачивание: две записи выбыли из набора `OCR_ENGINES`, и его
размер теперь считается по оставшимся файлам Tesseract.

---

### Quiz decisions (2026-08-16)

- Развилка «дописать или снять» → снять PaddleOCR с поставки: движок ни разу не выдал результата,
  нативная привязка `.so` на arm64/API 36 и так ненадёжна (S0923), а вся текущая работа по точности OCR
  нацелена на Tesseract. Уменьшение размера поставки - бонус; возврат второго движка - отдельным тикетом
  при необходимости.

---

## Last Audit

**Date:** 2026-08-21
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 23 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Removal complete (2026-08-17 audit facts unchanged: engine classes gone, `.so` gone, zero live
references, inventory records `removed`, 11/11 steps consistent, stored-value compatibility
preserved by design). All 7 WARNs of the 2026-08-17 run fixed 2026-08-21:

1. `ext_ocr_engines_desc` EN/RU/UK now name Tesseract only (remaining 10 locales - pre-release
   bulk round, Rule 30); `tools:text` in `item_extension.xml` matches.
2. `paddlePayloadMissing` collapsed at all three `RecognitionBackend` call sites; the partial-
   payload comment now describes Tesseract's own member set.
3. `NoLegalDiagnosticsCollectors.paddleStatus()` and its row removed.
4. Four stale KDocs reworded (`RecognitionBackend`, `TextRecognitionFacade`,
   `TextRecognitionSettingsStore`, `ImageOcrManager`).
5. Orphaned `<!-- OCR Engine and PaddleOCR Models (S0288) -->` comments deleted in all three
   locales (XML parse-verified).
6. `NoLegalBundledDeliverableSetsModule` Set B comment corrected.
7. `SearchableLanguagePickerDialog` dropped `@AndroidEntryPoint` with a reason comment.

Evidence this run: `fk` exit 0, `fkn` exit 0, `fr` exit 0, `check_strings_localized -KeyPrefix
ext_ocr_engines` exit 0, greps: zero `paddlePayloadMissing`/`paddleStatus`/PaddleOCR-in-strings.

### Manual / on-device

- [ ] Open Settings → Other → OCR on a `noLegal` build and confirm no engine or model row is offered and
      the remaining font rows still respond (unchanged from 2026-08-17 - no device attached).
