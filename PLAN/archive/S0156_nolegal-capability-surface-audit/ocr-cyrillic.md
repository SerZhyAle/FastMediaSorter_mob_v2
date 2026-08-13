# Research: OCR кириллицы

**Направление:** S0156 Столп E (OCR/translation branch из §6.6)
**Дата первого прохода:** 2026-05-21
**Статус:** First pass complete — see "Snapshot и статус" at end of document
**Source spec:** S0285

## Таксономия и оси анализа

Каждый OCR-кандидат относится ровно к одной из семи категорий ниже, с типичным blocker-профилем. Verdict выдаётся независимо по двум осям: ось A (STANDARD store-safe applicability) и ось B (noLegal sideload-only applicability).

- **GMS/Apache 2 модульный OCR (ML Kit family)** — Apache 2 от Google, доступен через `com.google.mlkit:*`, GMS dependency, обновляется через Play Services. Типичный blocker: ограниченный set поддерживаемых scripts; для кириллицы — отсутствие отдельного модуля.
- **Tesseract-семейство** — Apache 2 engine + Apache 2 LSTM модели от Google. Уже в проекте через `cz.adaptech:tesseract4android:4.8.0`. Типичный blocker: качество на real-world фото без preprocessing уступает современным DL-движкам.
- **Deep-learning ONNX/TFLite порты** — PaddleOCR, EasyOCR, dbnet+CRNN. Лицензия чаще Apache 2 / MIT, runtime — ONNX Runtime или TensorFlow Lite. Типичный blocker: не столько "размер сам по себе", сколько наличие clean Android runtime + legal install-on-demand corridor для quality-first model packs.
- **On-device LLM с vision** — Gemini Nano (через AICore), Phi-3.5-vision, llama.cpp с vision-моделями. Типичный blocker: device-support matrix (узкий), Google Play policy stance на AICore-style execution, snapshot-эволюция за месяцы.
- **Closed-source SDK** — ABBYY Mobile OCR, Anyline OCR SDK, ReadIRIS Mobile, Tencent OCR, Huawei HMS OCR. Типичный blocker: per-device / per-volume лицензирование (стоимость, прозрачность terms), evaluation-only режимы.
- **Облачные OCR API** — Google Cloud Vision, Yandex Vision OCR, OpenAI Vision, Anthropic Vision, Azure Computer Vision Read. Типичный blocker: privacy implications (image content покидает device), cost per-image, regional availability, opt-in friction.
- **Sidecar-binary решения** — termux-style runtime с embedded Python+PaddleOCR; native CLI (tesseract-current, Paddle-Lite demo). Типичный blocker: Google Play policy на dynamic code execution; SELinux constraints на subprocess execution; IPC complexity.

### Verdict legend

- **Ось A (STANDARD):** `Accept` — можно поднять без дополнительных условий; `Conditional` — можно при доп. условиях (opt-in, dynamic data download без code, отдельный download-after-install module); `Reject` — невозможно без нарушения strategic §3.2 ограничений.
- **Ось B (noLegal):** `Strong fit` — ясный value-add без существенных negatives; `Acceptable` — value-add есть, но требует существенной integration работы; `Weak fit` — value-add не оправдывает integration cost; `Reject` — за пределами допустимости даже для personal sideload.

### Owner guidance after first pass

- **Install-on-demand preferred:** bundled "тащить всё в APK" не рассматривается как желаемый default path для тяжёлых OCR/translation assets. Если legal/policy boundary позволяет data-only download, именно этот corridor должен считаться основным для axis A `Conditional` кандидатов.
- **Quality-first default:** если пользователь явно решил включить OCR и дождался загрузки пакета, предпочтительным default является лучший качественный пакет, а не "fast" preset. Быстрые/урезанные модели имеют смысл только как fallback, а не как главный recommendation path.
- **Patience is acceptable:** для heavy OCR tasks вроде большого PDF owner допускает более долгий first-run и processing time, если UX честно показывает, что происходит. Поэтому bundled APK size cap и latency caps нельзя читать как абсолютный запрет на качественные install-on-demand models.

## Пять сценариев оценки качества кириллицы (Столп D)

Качество каждого кандидата оценивается не абстрактно, а на пяти типовых пользовательских сценариях. Источники оценки — публичные benchmarks, GitHub issues, academic papers, community reports; собственный benchmarking — non-goal этого ресёрча. Per-scenario verdict: `strong` / `acceptable` / `weak` / `not applicable` / `verdict requires device-test on implementation`.

1. **Чистый печатный текст на белом фоне** — baseline floor; цифровой текст из PDF-страницы или скан high-DPI печатной страницы без шума и перспективы. На этом сценарии должен быть успешен даже Tesseract default.
2. **Фото бумажного документа с естественным освещением и небольшой перспективой** — реальный пользовательский сценарий «сфотографировал документ телефоном»; стрессует lighting tolerance, slight perspective correction, размытие краёв. RU и UK треки отслеживаются отдельно (для UK критичны буквы `ї`, `є`, `ґ`, `і`).
3. **Фото вывески / уличного указателя с фоновым шумом** — outdoor lighting, perspective, окружающие отвлекающие элементы; стрессует detection accuracy на фрагментированном тексте.
4. **Рукописный текст** — где применимо; большинство классических engine'ов (Tesseract, ML Kit base) не справляются; современные DL-движки и LLM могут.
5. **Плотная многоколоночная вёрстка PDF-страницы** — multi-column layout, embedded таблицы, footnotes; стрессует layout analysis. RU и UK треки отдельно (украинская кириллица в дeнсных научных PDF — отдельный набор failure modes).

## Анализ по категориям

### 1. GMS/Apache 2 модульный OCR (ML Kit family)

**Кандидат:** ML Kit Text Recognition v2 (bundled `com.google.mlkit:text-recognition:16.0.1` / unbundled `com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`)

- **Статус артефакта:** отдельный `com.google.mlkit:text-recognition-cyrillic` не опубликован; в официальном Android guide перечислены только Latin, Chinese, Devanagari, Japanese, Korean артефакты, без отдельного Cyrillic package.
- **Что Google документирует публично:** overview для Text Recognition v2 по состоянию на 2026-05 говорит про распознавание Chinese / Devanagari / Japanese / Korean / Latin scripts; при этом страница supported languages показывает, что часть языков в `Cyrl` размечена как `Cyrillic script model`, то есть кириллица поддерживается внутри общего recognizer-а, но не как самостоятельный publishable module.
- **Min SDK / distribution:** Android guide требует API 23+; bundled-вариант даёт статическое включение модели в APK, unbundled-вариант грузит модель через Google Play Services ModuleInstallClient / install-time dependency metadata.
- **Размер / runtime из официальной доки:** около `4 MB` app-size increase per bundled script per architecture либо около `260 KB` на script для Play-Services варианта; инициализация у unbundled может ждать download-before-first-use.
- **Release snapshot:** ML Kit release notes фиксируют `com.google.mlkit:text-recognition:16.0.1` и script-specific siblings на `2024-08-07`; после этого Google не добавил отдельный Cyrillic artifact в публичный список зависимостей.
- **Sources:** https://developers.google.com/ml-kit/vision/text-recognition/v2/android ; https://developers.google.com/ml-kit/vision/text-recognition/v2/languages ; https://developers.google.com/ml-kit/release-notes

**Ось A:** Reject
Reason: `not available as standalone` upgrade path. Проект уже использует base ML Kit recognizer; публичного store-safe шага вида "подключить `com.google.mlkit:text-recognition-cyrillic`" нет, а повторное подключение того же Latin/Cyrillic base recognizer не даёт нового Cyrillic-specific quality corridor.

**Ось B:** n/a (already covered by axis A)
Reason: отсутствие отдельного Cyrillic module не создаёт noLegal-specific value-add; это тот же ML Kit stack, уже допустимый для STANDARD.

**Качество на сценариях Столпа D:**

- `clean print` — `acceptable`: сам продукт позиционируется для data-entry / receipts / business cards, то есть для структурированного печатного текста; однако Google не публикует отдельный Cyrillic benchmark.
- `paper photo (RU)` — `verdict requires device-test on implementation`: official docs говорят, что качество входного изображения критично, но RU-specific paper/photo benchmark не дан.
- `paper photo (UK)` — `verdict requires device-test on implementation`: та же причина, плюс UK-specific glyph set (`ї`, `є`, `ґ`, `і`) не вынесен в отдельную оценку Google.
- `signage` — `acceptable`: street-sign use case у ML Kit документирован, но не в разрезе Cyrillic-only accuracy.
- `handwriting` — `not applicable`: для рукописного текста Google выделяет отдельный Digital Ink stack; Text Recognition v2 не описан как handwriting-first API.
- `dense PDF (RU)` — `verdict requires device-test on implementation`: Google документирует block/line/element structure, но не публикует многоколоночный Cyrillic PDF benchmark.
- `dense PDF (UK)` — `verdict requires device-test on implementation`: тот же structural limit без UK-specific published evaluation.
- **Citations:** https://developers.google.com/ml-kit/vision/text-recognition/v2 ; https://developers.google.com/ml-kit/vision/text-recognition/v2/android ; https://developers.google.com/ml-kit/vision/text-recognition/v2/languages ; https://developers.google.com/ml-kit/vision/digital-ink-recognition/base-models

**Бюджеты:**

- **APK delta:** для самого проекта дополнительный Cyrillic-specific delta = `0 MB`, потому что базовый `com.google.mlkit:text-recognition:16.0.1` уже подключён; hypothetical switch на Play-Services unbundled path выглядел бы как около `+260 KB`, bundled Latin path — около `+4 MB` per script per architecture, но отдельного Cyrillic script artifact не существует.
- **Cold start delta:** `~0.0 s` для already-bundled current baseline; `0 s in APK / wait-for-download before first use` для unbundled Play Services варианта по официальной документации.
- **Memory peak estimate:** `~1.0x current ML Kit peak`, потому что речь не о новом движке, а о том же already-integrated recognizer-е; если менять bundled/unbundled mode, нужно device-test only.
- **Budget verdict:** по числам ML Kit family проходит strategic caps, но это не material upgrade candidate, поэтому axis A остаётся `Reject` не из-за бюджета, а из-за отсутствия standalone Cyrillic path.

### 2. Tesseract-семейство

**Кандидат:** official Tesseract LSTM data sets (`tessdata_fast`, `tessdata`, `tessdata_best`) for `rus.traineddata` and `ukr.traineddata`

- **Engine / ABI reality:** вопрос из strategic §6.2 про "engine v4.x" для `cz.adaptech:tesseract4android:4.8.0` на практике устарел. README upstream wrapper указывает Tesseract OCR `5.5.1`, но одновременно требует `A v4.0.0 trained data file(s)`; это согласуется с official `tessdoc`, где прямо сказано, что language-model `traineddata` from the `4.0.0` family can be used with Tesseract `5.x.x`.
- **Official file sets:** `tessdata_best` = most accurate / slowest / LSTM-only; `tessdata_fast` = fastest / least accurate / LSTM-only; `tessdata` = legacy + integerized LSTM, faster than `tessdata_best`, slightly less accurate than `tessdata_best`.
- **Latest official Cyrillic file dates found in current upstream history:** `tessdata_best/rus.traineddata` and `tessdata_best/ukr.traineddata` trace to `2017-09-14`; `tessdata_fast/rus.traineddata` and `tessdata_fast/ukr.traineddata` also trace to `2017-09-14`; `tessdata/rus.traineddata` shows `2018-05-10`; `tessdata/ukr.traineddata` shows `2018-03-22`. Иными словами, официального "fresh Tesseract 5.x Cyrillic retrain" для RU/UK по состоянию на 2026-05 не найдено.
- **Current artifact sizes:** `tessdata_best/rus.traineddata` ~`15.3 MB`, `tessdata_best/ukr.traineddata` ~`10.9 MB`; `tessdata/rus.traineddata` ~`19.9 MB`, `tessdata/ukr.traineddata` ~`12.4 MB`. `tessdata_fast` остаётся самым лёгким operational choice, но официальная документация описывает его как least accurate из трёх наборов.
- **Community-trained path:** официальный `tessdata_contrib` под Apache 2 существует, но в его текущем содержимом нет `rus`/`ukr`; документированного Apache-2 / public-domain community model с явным, воспроизводимо описанным приростом качества над official `rus.traineddata` / `ukr.traineddata` в первом проходе не найдено.
- **Sources:** https://github.com/adaptech-cz/Tesseract4Android ; https://github.com/tesseract-ocr/tessdoc/blob/main/Data-Files.md ; https://github.com/tesseract-ocr/tessdata ; https://github.com/tesseract-ocr/tessdata_best ; https://github.com/tesseract-ocr/tessdata_fast ; https://github.com/tesseract-ocr/tessdata_contrib

**Ось A:** Conditional
Reason: store-safe path внутри семейства есть, и после owner guidance его самый логичный вид — explicit user-requested download of `tessdata_best` data packs поверх уже shipped runtime. Bundled `tessdata_best` for both `rus.traineddata` + `ukr.traineddata` exceeds the STANDARD APK `+15 MB` cap, но это больше не главный product corridor.

**Ось B:** Acceptable
Reason: noLegal может позволить более тяжёлый `tessdata_best` bundle или расширенный набор RU+UK models без жёсткого +15 MB store cap, но value-add остаётся эволюционным, а не скачкообразным: это улучшение внутри старого Tesseract family, не новый class of OCR quality.

**Качество на сценариях Столпа D:**

- `clean print` — `strong`: Tesseract остаётся документ-ориентированным OCR engine, а official docs прямо описывают его как OCR engine для printed text; именно этот сценарий для него baseline.
- `paper photo (RU)` — `acceptable`: Tesseract может работать, но official docs отдельно предупреждают, что для хорошего результата часто нужно улучшать качество input image; natural-photo robustness не является сильной стороной.
- `paper photo (UK)` — `acceptable`: для `ukr.traineddata` есть отдельная официальная модель, но published first-pass evidence о заметном преимуществе над RU model на UK glyphs не найдено; итог всё равно требует implementation device-test.
- `signage` — `weak`: scene-text paper с Tesseract показывает usable result only in constrained use cases and reports the task as significantly more challenging than scanned documents.
- `handwriting` — `weak`: даже в research с дополнительным обучением handwriting accuracy у Tesseract остаётся ограниченной; без отдельного retraining stock `rus`/`ukr` models не выглядят realistic handwriting path.
- `dense PDF (RU)` — `acceptable`: на чистом rendered-PDF bitmap Tesseract обычно силён как line recognizer, но multi-column ordering / layout segmentation остаются серой зоной без локального теста.
- `dense PDF (UK)` — `acceptable`: отдельная `ukr.traineddata` модель уменьшает риск glyph-loss, но доказанного public benchmark для dense Ukrainian scholarly PDFs не найдено.
- **Citations:** https://github.com/tesseract-ocr/tesseract ; https://github.com/tesseract-ocr/tessdoc/blob/main/Data-Files.md ; https://arxiv.org/abs/2004.08079 ; https://arxiv.org/abs/1003.5886

**Бюджеты:**

- **APK delta:** текущий `tessdata_fast` baseline для RU+UK = примерно `7.69 MB` (`rus` ~`3.86 MB` + `ukr` ~`3.83 MB`). Переход на `tessdata_best` для RU+UK = примерно `26.16 MB` (`rus` ~`15.30 MB` + `ukr` ~`10.86 MB`), то есть `+18.47 MB` к нынешнему fast-baseline и уже выше strategic STANDARD cap `+15 MB`. Переход на `tessdata` RU+UK = примерно `32.33 MB`, то есть ещё тяжелее.
- **Cold start delta:** `estimate +0.4..1.2 s` для `tessdata_best` относительно текущего `tessdata_fast` baseline, потому что official docs прямо характеризуют `best` как slowest set, а объём модели растёт примерно в `3.4x`; точное значение требует device-test на Quest 3 и mid-range phone.
- **Memory peak estimate:** `estimate 1.3x..1.8x current Tesseract peak` для `tessdata_best` RU+UK relative to `tessdata_fast`; при полном `tessdata` set можно ожидать ещё выше из-за более крупных integerized files + legacy payload.
- **Budget verdict:** bundled `tessdata_best` RU+UK для STANDARD не проходит APK budget, но при owner-preferred install-on-demand corridor это уже не делает candidate плохим само по себе. Главный follow-up вопрос смещается с "влезает ли в APK" на "есть ли clean UX и policy-safe data-pack download path".

**Резюме фазы 02:** лучший store-safe candidate после phase 02 — axis A `Conditional` Tesseract install-on-demand path для quality-first `tessdata_best`, тогда как ML Kit дал axis A negative result из-за отсутствия standalone Cyrillic module. Внутри Tesseract family единственный осмысленный follow-up — прямое сравнение current `rus.traineddata` / `ukr.traineddata` baseline против quality-first `tessdata_best` на проектных RU/UK сценариях, а не поиск "fresh 5.x retrain", которого upstream не показывает. Для axis B здесь нет radically new OCR class — следующие value-add кандидаты надо искать уже в категориях 3..7.

### 3. Deep-learning ONNX/TFLite порты

**Кандидат A:** official PaddleOCR PP-OCRv5 mobile stack for Cyrillic via Paddle-Lite Android deployment

- **What exists officially:** PaddleOCR documents Android/mobile deployment through Paddle-Lite and `deploy/android_demo`; the official mobile path is optimized Paddle/Paddle-Lite `.nb` inference, not a published TFLite package. So the "TFLite" part of this category is currently community/conversion territory; the official Android-ready path is Paddle-Lite.
- **Cyrillic model availability:** official multilingual docs publish `cyrillic_PP-OCRv5_mobile_rec` with average accuracy `80.27`, explicitly covering Russian, Belarusian, Ukrainian and a broader Cyrillic set; there is also `eslav_PP-OCRv5_mobile_rec` (`81.6`) for East Slavic only.
- **det / rec / cls sizes:** shared `PP-OCRv5_mobile_det` = `4.7 MB`; `cyrillic_PP-OCRv5_mobile_rec` = `7.7 MB`; text-line orientation classifier `PP-LCNet_x0_25_textline_ori` = `0.96 MB`. **Total mobile bundle:** about `13.36 MB` for `det + rec + cls` with the general Cyrillic recognizer. If East-Slavic-only `eslav_PP-OCRv5_mobile_rec` is used instead (`14 MB`), total rises to about `19.66 MB`.
- **Latency evidence found publicly:** official docs publish per-model CPU timings for the recognition module and full-pipeline CPU timings for PP-OCRv5 mobile, but those numbers are not Android ARM64/NNAPI-specific. Official Android deployment docs confirm mobile execution through Paddle-Lite and mention Android NNAPI support at the runtime level; a published Cyrillic-specific ARM64/NNAPI benchmark was not found in the first pass.
- **License:** Apache 2 for PaddleOCR and Paddle-Lite; official docs and repos expose Android deployment and model download links under the same open stack.
- **Cyrillic-vs-Latin signal:** official PP-OCRv5 multilingual table shows `cyrillic_PP-OCRv5_mobile_rec` at `80.27` versus `latin_PP-OCRv5_mobile_rec` at `84.7`, so Cyrillic is supported as a first-class model family but still trails Latin on the vendor's own metrics.
- **Sources:** https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.en.md ; https://www.paddleocr.ai/main/en/version3.x/module_usage/text_recognition.html ; https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/lite/readme.md ; https://github.com/PaddlePaddle/Paddle-Lite ; https://www.paddleocr.ai/v3.0.0/en/version3.x/module_usage/text_detection.html

**Ось A:** Conditional
Reason: `cyrillic_PP-OCRv5_mobile_rec` keeps `det + rec + cls` just under the STANDARD `+15 MB` cap on paper, but the official Android deployment path is Paddle-Lite-specific and not a drop-in TFLite artifact; latency and memory still need device validation, and the East-Slavic-only recognizer (`eslav`) already exceeds the APK budget when combined with `det + cls`.

**Ось B:** Strong fit
Reason: this is the first candidate with an explicitly published Cyrillic model family, a realistic Android deployment story, Apache-2.0 licensing, and a qualitatively newer OCR architecture than Tesseract.

**Кандидат B:** EasyOCR `cyrillic_g2` with upstream `craft` / `dbnet18` detectors and community Android export path

- **What exists upstream:** EasyOCR ships a built-in `cyrillic_g2` recognition model and two detector families (`craft` and `dbnet18`), but the official distribution format is PyTorch `.pth` / release ZIP assets, not ONNX or TFLite. Any Android deployment therefore depends on community export/conversion rather than an official mobile artifact.
- **Cyrillic model availability:** upstream `easyocr/config.py` contains `cyrillic_lang_list` including `ru` and `uk`, and release `v1.6.1` explicitly introduced `cyrillic_g2` as the new default Cyrillic recognizer.
- **det / rec sizes:** `cyrillic_g2.zip` = about `13.49 MB`; default `craft_mlt_25k.zip` detector = about `73.67 MB`; `dbnet18` detector asset `pretrained_ic15_res18.zip` = about `49.55 MB`. **Total practical bundle:** about `63.04 MB` with `dbnet18 + cyrillic_g2`, or about `87.16 MB` with `craft + cyrillic_g2`.
- **Android/runtime reality:** official docs and releases target Python / PyTorch. ONNX export exists in community repos, but the upstream project does not publish a supported Android runtime or stable ONNX/TFLite release channel for Cyrillic.
- **License:** Apache 2.
- **Sources:** https://github.com/JaidedAI/EasyOCR/blob/master/easyocr/config.py ; https://github.com/JaidedAI/EasyOCR/releases ; https://github.com/JaidedAI/EasyOCR

**Ось A:** Reject
Reason: even the lighter practical detector bundle is `> +50 MB`, far above the STANDARD `+15 MB` APK cap, and there is no official Android-ready artifact from upstream.

**Ось B:** Weak fit
Reason: Cyrillic support is real, but the integration story is materially worse than PaddleOCR: heavier detector payloads, PyTorch-first packaging, and no maintained Android deployment contract.

**Кандидат C:** community Cyrillic TrOCR handwriting model (`cyrillic-trocr/trocr-handwritten-cyrillic`)

- **What exists:** Hugging Face hosts a MIT-licensed TrOCR fine-tune for Church Slavonic / Russian / Ukrainian handwriting, built on `kazars24/trocr-base-handwritten-ru`.
- **Model size / shape:** the repository reports `333,921,792` parameters in safetensors and about `1.34 GB` total storage footprint for the model repo; this is before any Android packaging overhead. For comparison, Microsoft's official `Phi-3.5-vision-instruct-onnx` `cpu_and_mobile` export is still about `3.22 GB`, so transformer OCR remains multi-gigabyte territory even after mobile-oriented conversion.
- **Use-case fit:** this candidate is handwriting-specialized and not positioned as a compact general OCR stack for mixed print/photo/signage workloads.
- **Android/runtime reality:** no official Android app package or TFLite artifact was found; deployment would require a custom ONNX Runtime / transformers-style stack.
- **License:** MIT.
- **Sources:** https://huggingface.co/cyrillic-trocr/trocr-handwritten-cyrillic ; https://huggingface.co/microsoft/trocr-small-handwritten ; https://huggingface.co/microsoft/Phi-3.5-vision-instruct-onnx/tree/main/cpu_and_mobile

**Ось A:** Reject
Reason: multi-hundred-megabyte to gigabyte-scale transformer OCR is outside the STANDARD size and runtime budgets by a wide margin.

**Ось B:** Reject
Reason: the model is interesting as a handwriting research artifact, but as a production sideload OCR engine it is too specialized and too heavy relative to PaddleOCR-level alternatives.

**Качество на сценариях Столпа D:**

- **PaddleOCR PP-OCRv5 mobile**
- `clean print` — `strong`: official PP-OCRv5 multilingual tables position the mobile recognizer as a production OCR stack for printed text.
- `paper photo (RU)` — `acceptable`: detector + recognizer split is designed for camera captures, but the first-pass source set did not include a published RU-specific mobile photo benchmark.
- `paper photo (UK)` — `acceptable`: Ukrainian is explicitly inside the official Cyrillic model family, but UK-specific glyph behavior still requires implementation device-test.
- `signage` — `acceptable`: official text-detection materials target scene text and in-the-wild images.
- `handwriting` — `weak`: the cited PP-OCRv5 mobile stack is text detection + printed-text recognition, not handwriting-first OCR.
- `dense PDF (RU)` — `acceptable`: strong recognizer + explicit detector is a better fit than Tesseract for dense rendered pages, but layout ordering still needs local validation.
- `dense PDF (UK)` — `acceptable`: same reasoning with Ukrainian coverage included in the vendor model family.
- **Citations:** https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.en.md ; https://www.paddleocr.ai/v3.0.0/en/version3.x/module_usage/text_detection.html ; https://www.paddleocr.ai/main/en/version3.x/module_usage/text_recognition.html

- **EasyOCR `cyrillic_g2`**
- `clean print` — `acceptable`: EasyOCR is a general OCR stack with broad script coverage, but no vendor-published Cyrillic benchmark pack was found for the current model.
- `paper photo (RU)` — `acceptable`: detector-based scene OCR design makes it more plausible than stock Tesseract on photos, but evidence in the reviewed upstream sources remains indirect.
- `paper photo (UK)` — `acceptable`: upstream language list includes `uk`, but no dedicated Ukrainian quality evidence was found.
- `signage` — `acceptable`: `craft` / `dbnet18` are scene-text detectors by design, which should help against cluttered backgrounds.
- `handwriting` — `weak`: the reviewed upstream materials do not position `cyrillic_g2` as a handwriting-specialized model.
- `dense PDF (RU)` — `weak`: without an explicit layout/document pipeline, dense multi-column ordering remains a likely weak point.
- `dense PDF (UK)` — `weak`: same limitation for Ukrainian pages.
- **Citations:** https://github.com/JaidedAI/EasyOCR/blob/master/easyocr/config.py ; https://github.com/JaidedAI/EasyOCR/releases

- **Cyrillic TrOCR handwriting**
- `clean print` — `weak`: the model card describes handwriting fine-tuning, not printed OCR optimization.
- `paper photo (RU)` — `weak`: handwriting specialization does not automatically solve camera-photo detection and cropping.
- `paper photo (UK)` — `weak`: same limitation; no public mobile photo benchmark found.
- `signage` — `weak`: not a scene-text detector stack.
- `handwriting` — `strong`: this is the one scenario the candidate is explicitly optimized for.
- `dense PDF (RU)` — `weak`: document-layout OCR is outside its advertised scope.
- `dense PDF (UK)` — `weak`: same limitation for Ukrainian PDFs.
- **Citations:** https://huggingface.co/cyrillic-trocr/trocr-handwritten-cyrillic ; https://huggingface.co/docs/transformers/en/model_doc/trocr

**Бюджеты:**

- **PaddleOCR PP-OCRv5 mobile:** `det + rec + cls` with the general Cyrillic recognizer is about `13.36 MB`, which is just under the STANDARD `+15 MB` bundled cap; East-Slavic-only `eslav` raises the bundle to about `19.66 MB`, but under owner guidance that size is still plausible if shipped as explicit data-pack download rather than APK payload. Cold start estimate for the fully bundled path is `+0.6..1.4 s`; memory estimate `1.4x..1.9x current Tesseract peak`. **Budget verdict:** axis A remains `Conditional`, now mainly because Android/Paddle-Lite validation is incomplete and install-on-demand UX/policy details are still open.
- **EasyOCR `cyrillic_g2`:** practical bundle `+63.04..87.16 MB` depending on detector choice, with cold start likely `> +1.5 s` and memory well above the 2x current peak budget on many devices. **Budget verdict:** axis A `Reject` not just for size, but because the candidate still lacks a clean official Android artifact and would ask the project to solve both packaging and runtime from scratch.
- **Cyrillic TrOCR handwriting:** practical model footprint starts around `+1.34 GB` and mobile-oriented transformer exports in the same family remain multi-gigabyte. Cold start and memory are outside the project's mobile budgets by orders of magnitude. **Budget verdict:** axis A `Reject` for `APK size cap` and runtime budget.

### 4. On-device LLM с vision

**Кандидат A:** Gemini Nano via AICore and ML Kit GenAI APIs

- `_snapshot: 2026-05_`
- **What exists officially:** Android now exposes Gemini Nano both through AICore-facing paths and through ML Kit GenAI APIs. Public ML Kit docs list `Image description` and `Prompt` with multimodal prompts among the supported features, while the older Google AI Edge SDK page is already marked deprecated in favour of ML Kit Prompt API.
- **Distribution mechanics:** AICore is a system-level module. Google documents that AICore manages model distribution and updates itself; model downloads go through Private Compute Services, not through the app package.
- **Device support reality:** the experimental AI Edge SDK page still lists Pixel 9 series for experimentation and text modality only there, while ML Kit Prompt API blog guidance says current best performance is on the Pixel 10 series. Quest-class support was not found in the reviewed public docs.
- **OCR reality:** Google documents image description and multimodal prompting, but does not document Gemini Nano as a deterministic Cyrillic OCR API. That makes it a possible "read the text in this image" experiment, not a stable OCR replacement.
- **License / terms:** proprietary Google system service plus ML Kit GenAI additional terms, not Apache/MIT.
- **Sources:** https://developer.android.com/ai/gemini-nano/ai-edge-sdk ; https://developer.android.com/ai/gemini-nano/ml-kit-genai ; https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences

**Ось A:** Reject
Reason: APK size is attractive because AICore carries the model, but the current public device matrix is narrow and Google does not position Gemini Nano as a supported Cyrillic OCR stack.

**Ось B:** Weak fit
Reason: sideload-only distribution does not solve the main blocker here, which is limited device availability plus non-OCR-first behavior.

**Кандидат B:** `microsoft/Phi-3.5-vision-instruct` and its ONNX mobile-oriented variants

- `_snapshot: 2026-05_`
- **What exists:** Microsoft's public model card positions Phi-3.5 Vision as a multilingual multimodal model with `4.2B` parameters. Hugging Face also hosts an ONNX export line that explicitly includes a `cpu_and_mobile` folder.
- **Model size:** the base Hugging Face model repo occupies about `8.29 GB`; the ONNX `cpu_and_mobile` subtree alone is about `3.22 GB`. This is vastly beyond the project's APK and memory budgets.
- **Mobile/runtime story:** Microsoft and silicon partners publicly discuss mobile optimization for the Phi-3.5 family, but the available public artifacts are still heavyweight model packages, not a turnkey Android OCR feature.
- **OCR reality:** the model can answer image-text prompts and scores well on TextVQA-like benchmarks, but it is a general VLM, not a structured OCR engine with documented RU/UK extraction guarantees.
- **License:** MIT.
- **Sources:** https://huggingface.co/microsoft/Phi-3.5-vision-instruct ; https://huggingface.co/microsoft/Phi-3.5-vision-instruct-onnx/tree/main/cpu_and_mobile ; https://www.mediatek.com/tek-talk-blogs/mediatek-dimensity-chipsets-now-optimized-for-microsoft-phi-3.5

**Ось A:** Reject
Reason: multi-gigabyte model payload makes this impossible for STANDARD.

**Ось B:** Weak fit
Reason: technically interesting, but the payload and integration cost are still too large relative to the user value of "better OCR".

**Качество на сценариях Столпа D:**

- **Gemini Nano via AICore**
- `clean print` — `verdict requires device-test on implementation`: multimodal prompt support exists, but public docs do not publish OCR accuracy for Cyrillic text extraction.
- `paper photo (RU)` — `verdict requires device-test on implementation`: no RU camera-photo OCR benchmark was found.
- `paper photo (UK)` — `verdict requires device-test on implementation`: same gap for Ukrainian.
- `signage` — `verdict requires device-test on implementation`: image description capability suggests possible usefulness, but not a documented OCR SLA.
- `handwriting` — `weak`: public docs do not present Gemini Nano as a handwriting OCR model.
- `dense PDF (RU)` — `weak`: long-page structured extraction is outside the reviewed productized use cases.
- `dense PDF (UK)` — `weak`: same limitation.
- **Citations:** https://developer.android.com/ai/gemini-nano/ml-kit-genai ; https://developer.android.com/ai/gemini-nano/ai-edge-sdk

- **Phi-3.5 Vision**
- `clean print` — `acceptable`: public benchmark tables show solid document-intelligence and TextVQA behavior for general text-in-image tasks.
- `paper photo (RU)` — `verdict requires device-test on implementation`: multilingual VLM ability exists, but RU-specific OCR benchmarks were not found in the reviewed model card.
- `paper photo (UK)` — `verdict requires device-test on implementation`: same for Ukrainian.
- `signage` — `acceptable`: a general VLM can likely read sign-like text, but structured OCR output remains non-deterministic.
- `handwriting` — `acceptable`: broader multimodal reasoning gives it a better ceiling than classic OCR on some handwriting samples, but again without Cyrillic handwriting guarantees.
- `dense PDF (RU)` — `verdict requires device-test on implementation`: reasoning is strong, but multi-column OCR extraction consistency is unproven in the reviewed sources.
- `dense PDF (UK)` — `verdict requires device-test on implementation`: same limitation for Ukrainian PDFs.
- **Citations:** https://huggingface.co/microsoft/Phi-3.5-vision-instruct ; https://huggingface.co/docs/transformers/en/model_doc/trocr

**Бюджеты:**

- **Gemini Nano via AICore:** app-side APK delta is effectively `0 MB` because model distribution is managed by AICore; however first-use latency can include model/config fetch managed by the system service, and supported-device coverage is still narrow. Memory is mostly shifted to the system-managed foundation-model stack rather than the app heap. **Budget verdict:** budget alone would be attractive, but axis A stays `Reject` because the blocker is capability/documentation breadth, not APK size.
- **Phi-3.5 Vision:** `+3.22 GB` for the mobile-oriented ONNX subtree, or `+8.29 GB` for the base repo, with cold start and memory demands far outside the project's mobile corridor. **Budget verdict:** axis A `Reject` for `APK size cap` and runtime budget.

**Резюме фазы 03:** ведущий candidate для axis B после фазы 03 — `PaddleOCR PP-OCRv5 mobile`, потому что это единственный on-device DL путь с внятной Cyrillic model family и реалистичным Android story. Gemini Nano today выглядит как интересный re-evaluation target на горизонте `6..12` месяцев, если device coverage и OCR-oriented docs расширятся; Phi-3.5 Vision пригоден скорее как lab reference, чем как мобильный OCR roadmap. Ни один on-device LLM/VLM candidate не проходит axis A в текущем срезе, а из pure-DL путей axis A остаётся `Conditional` corridor у PaddleOCR, где главный вопрос уже не "влезает ли в APK", а "есть ли clean install-on-demand path и рабочий Paddle-Lite integration".

### 5. Closed-source SDK

**Кандидат A:** ABBYY FineReader Engine / OCR SDK family

- **Redistribution:** public mobile redistribution tier is not published on the reviewed public pages; the official path is `Contact Sales` / demo-driven enterprise contracting.
- **Cyrillic supported:** yes. ABBYY states OCR support for `200+` languages including European languages using the Cyrillic alphabet; ICR is listed for `120+` languages.
- **License / cost order:** proprietary, enterprise-style commercial licensing; public pages do not expose self-serve pricing, which puts it in the `enterprise-only / quote-based` bucket.
- **SDK download size:** not publicly disclosed on the reviewed public pages.
- **Personal sideload legality:** cannot be assumed from public sources; legal inclusion would depend on a signed commercial agreement, not on an openly published permissive redistribution grant.
- **Sources:** https://www.abbyy.com/ocr-sdk/features/ocr/

**Ось A:** Reject
Reason: technically capable, but redistribution terms and pricing transparency do not satisfy the project's publicly shippable store-safe bar.

**Ось B:** Reject
Reason: no public evidence was found that a personal sideload-only build may legally ship the SDK without a negotiated commercial license.

**Кандидат B:** Anyline Mobile OCR / ID Scanning SDK

- **Redistribution:** public site describes annual licenses tailored per customer and asks prospects to get an individual quote; redistribution is therefore commercial-contract based, not openly licensed.
- **Cyrillic supported:** yes, but in a constrained sense. The public FAQ says the mobile SDK supports recognition of alphanumeric Latin-based, Cyrillic and Arabic characters.
- **Product fit:** Anyline is positioned around mobile data capture, IDs, MRZ, VIN and similar workflows rather than broad "OCR any page/photo/PDF" scenarios.
- **SDK download size:** not publicly disclosed on the reviewed public pages.
- **Integration/runtime notes:** Anyline states the SDK runs with a native C++ core, supports Android/iOS and common wrappers, and works offline.
- **License / cost order:** proprietary annual license; practical pricing is quote-based rather than public-list.
- **Personal sideload legality:** plausible only under a paid contract; no public free redistribution grant was found.
- **Sources:** https://anyline.com/products/scan-id ; https://anyline.com/technology

**Ось A:** Reject
Reason: closed commercial redistribution plus use-case narrowness make it a poor STANDARD fit even before cost.

**Ось B:** Weak fit
Reason: if the owner is willing to pay and sign terms, the SDK is technically usable on mobile, but it is better aligned with structured ID/data-capture than with the app's broad OCR-for-content scenarios.

**Качество на сценариях Столпа D:**

- **ABBYY OCR SDK**
- `clean print` — `strong`: ABBYY explicitly positions the engine as a high-accuracy OCR platform for document conversion and field extraction.
- `paper photo (RU)` — `strong`: enterprise OCR positioning and multilingual OCR/ICR support suggest a high floor on photographed documents.
- `paper photo (UK)` — `strong`: Cyrillic support is explicit, though the reviewed public page does not separate Ukrainian metrics.
- `signage` — `acceptable`: likely usable, but ABBYY is document-first rather than scene-text-first.
- `handwriting` — `acceptable`: ICR support exists, but the reviewed material does not claim handwriting leadership for Cyrillic specifically.
- `dense PDF (RU)` — `strong`: full-text recognition + document-conversion positioning matches this scenario well.
- `dense PDF (UK)` — `strong`: same reasoning with Cyrillic language coverage.
- **Citations:** https://www.abbyy.com/ocr-sdk/features/ocr/

- **Anyline SDK**
- `clean print` — `acceptable`: mobile capture and OCR support are real, but the product is not advertised as a general-purpose document OCR leader.
- `paper photo (RU)` — `acceptable`: on-device capture in real-world conditions is an Anyline selling point.
- `paper photo (UK)` — `acceptable`: Cyrillic character support is stated, but no Ukrainian-specific quality evidence was found.
- `signage` — `weak`: not a target workflow in the reviewed product material.
- `handwriting` — `not applicable`: the reviewed public pages do not position the SDK as handwriting OCR.
- `dense PDF (RU)` — `weak`: PDF/document archival OCR is not the visible product focus on the reviewed pages.
- `dense PDF (UK)` — `weak`: same limitation.
- **Citations:** https://anyline.com/products/scan-id ; https://anyline.com/technology

**SDK integration cost:**

- **ABBYY OCR SDK:** manual enterprise onboarding, proprietary binaries, vendor-led upgrade cadence, and likely high-quality contract support once licensed. Integration complexity for Android is materially higher than adding a Maven OSS dependency.
- **Anyline SDK:** closer to a drop-in commercial mobile SDK thanks to its Android-native packaging and wrapper support, but still quote-gated, contract-bound, and product-module oriented rather than "plug this into generic OCR everywhere".

### 6. Облачные OCR API

**Кандидат A:** Google Cloud Vision OCR

- **Cyrillic support:** explicit. Google Cloud Vision documents `ru` and `uk` as supported `Cyrl` languages and supports both `TEXT_DETECTION` and `DOCUMENT_TEXT_DETECTION`.
- **Free tier:** first `1000` units per month are free for `Text Detection` and `Document Text Detection`.
- **Paid tier:** `Text Detection` and `Document Text Detection` are priced at `$1.50 / 1000` units for tiers `1001..5,000,000`, then `$0.60 / 1000` above that.
- **Data residency:** governed by broader Google Cloud infrastructure/compliance controls; the reviewed public Vision OCR docs expose no OCR-specific per-request residency selector.
- **Regional availability:** widely available globally and generally reachable without a special vendor-specific mobile runtime.
- **Sources:** https://cloud.google.com/vision/docs/languages ; https://cloud.google.com/vision/pricing

**Ось A:** Conditional
Reason: cloud OCR is only viable as explicit opt-in because image content leaves the device, but Google gives the cleanest public OCR-language and pricing matrix among the reviewed providers.

**Ось B:** Acceptable
Reason: sideload-only distribution does not remove the privacy/cost tradeoff, yet as an optional fallback it remains operationally useful.

**Кандидат B:** Azure Vision Read / Document Intelligence

- **Cyrillic support:** explicit for print OCR. Azure says `Read` supports many languages, mixed languages on the same line, and lists Belarusian (Cyrillic) among printed-text languages; overview pages explicitly mention Russian and Cyrillic scripts for print OCR. Handwriting support in the reviewed docs is narrower and does not include Russian/Ukrainian.
- **Free tier:** the public quickstart says the free pricing tier `F0` can be used to try the service.
- **Paid tier:** paid usage is metered per image/page under Azure Vision pricing; the public OCR docs tie billing to the Vision pricing page, but the exact regional paid number was not exposed on the reviewed static Learn pages.
- **Data residency:** Document Intelligence privacy docs say analysis data and results are stored temporarily in Azure Storage in the same region; analyze responses are retained for `24 hours` unless deleted earlier.
- **Regional availability:** broad global Azure footprint; no obvious VPN-only restriction in the reviewed public docs.
- **Sources:** https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/language-support ; https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/quickstarts-sdk/client-library ; https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/document-intelligence/data-privacy-security

**Ось A:** Conditional
Reason: privacy/opt-in is still mandatory, but Azure has a strong mixed-language document story and explicit same-region processing semantics.

**Ось B:** Acceptable
Reason: useful as a fallback cloud path, especially for dense documents, but not compelling enough to outrank the best on-device candidate.

**Кандидат C:** Yandex Vision OCR

- **Cyrillic support:** explicit and unusually strong for the CIS context. Yandex publishes a `Latin-Cyrillic` language model with Russian and many other Cyrillic languages; the service page advertises `48` supported languages and separate handwriting mode for Russian and English.
- **Free tier:** no always-free public tier was found in the reviewed docs; onboarding is via billing account in `ACTIVE` or `TRIAL_ACTIVE` state.
- **Paid tier:** `Printed text recognition` = about `$0.0010827867` per image/page; `Handwriting recognition` = about `$0.0124590144`.
- **Data residency:** tied to Yandex Cloud regions/account setup; public limits docs state OCR results can be stored on the server for up to `3 days`.
- **Regional availability:** strongest explicit CIS alignment among the reviewed providers; practical availability is likely better for CIS users than many US-first AI APIs.
- **Sources:** https://yandex.cloud/en/docs/vision/concepts/ocr/supported-languages ; https://yandex.cloud/en/docs/vision/concepts/ocr ; https://yandex.cloud/en/docs/vision/pricing ; https://yandex.cloud/en/docs/vision/concepts/limits

**Ось A:** Conditional
Reason: privacy/opt-in still applies, but Yandex is a serious candidate for Cyrillic-heavy workloads and CIS reach.

**Ось B:** Acceptable
Reason: technically attractive for RU-centric OCR, though still cloud-bound and therefore subject to the same user-trust and quota concerns.

**Кандидат D:** OpenAI Vision via image-capable API models

- **Cyrillic support:** not documented as an OCR product, but image-capable models can process image inputs and can be prompted to transcribe text.
- **Free tier:** no standing general-purpose free tier for production API use was found on the current pricing pages.
- **Paid tier:** token-based rather than per-image fixed. The public pricing calculator example shows a `512 x 512` low-resolution image costing about `$0.000263` in input tokens before output tokens.
- **Data residency:** official API docs expose project-level data residency controls. Reviewed public docs list US and Europe plus several other regions, with image support in non-US regions gated behind enhanced abuse-monitoring / zero-retention approval. Default API abuse-monitoring logs are retained for up to `30 days`.
- **Regional availability:** depends on OpenAI commercial availability and billing posture; this is less CIS-friendly than Yandex Cloud in practice.
- **Sources:** https://openai.com/api/pricing/ ; https://developers.openai.com/api/docs/guides/your-data

**Ось A:** Conditional
Reason: viable only as explicit opt-in cloud OCR-like fallback; privacy, billing, and residency approvals are part of the feature surface.

**Ось B:** Acceptable
Reason: can be useful where high-quality multimodal interpretation matters more than deterministic OCR formatting, but it is still not a Cyrillic-specialized OCR service.

**Кандидат E:** Anthropic Vision via Claude API

- **Cyrillic support:** not documented as a dedicated OCR service, but the vision API accepts images and can answer image-text prompts.
- **Free tier:** no standing free production tier was found in the reviewed docs.
- **Paid tier:** token-based. Current public pricing lists `Claude Sonnet 4.6` at `$3 / MTok` input and `$15 / MTok` output; image cost therefore depends on tokenized image size rather than a flat per-page tariff.
- **Data residency:** first-party Claude API is global by default; public pricing docs note a `1.1x` multiplier for US-only inference on newer models. Separate privacy docs say Anthropic API inputs/outputs are deleted from the backend within `30 days` by default, unless a zero-retention agreement or policy/legal exception applies.
- **Regional availability:** commercial API availability is broad, but still less region-tailored to CIS than Yandex Cloud.
- **Sources:** https://platform.claude.com/docs/en/build-with-claude/vision ; https://platform.claude.com/docs/en/about-claude/pricing ; https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data

**Ось A:** Conditional
Reason: only sensible as explicit opt-in fallback and not as the default OCR path.

**Ось B:** Acceptable
Reason: technically capable as a multimodal fallback, but the OCR problem it solves overlaps heavily with other cloud providers that are more OCR-specific.

**Качество на сценариях Столпа D:**

- **Google Cloud Vision OCR**
- `clean print` — `strong`: productized OCR with dedicated `DOCUMENT_TEXT_DETECTION`.
- `paper photo (RU)` — `acceptable`: general OCR API is designed for images, though the reviewed public docs do not publish RU-specific camera-photo benchmarks.
- `paper photo (UK)` — `acceptable`: explicit `uk` language support exists.
- `signage` — `acceptable`: `TEXT_DETECTION` is designed for image text, including scene-like inputs.
- `handwriting` — `acceptable`: Google documents handwriting OCR as GA under document text detection, but no Cyrillic handwriting split was found.
- `dense PDF (RU)` — `strong`: dedicated document OCR path is explicitly part of the product.
- `dense PDF (UK)` — `strong`: same reasoning with Ukrainian language support present.
- **Citations:** https://cloud.google.com/vision/docs/languages ; https://cloud.google.com/vision/pricing

- **Azure Vision Read**
- `clean print` — `strong`: Read API is explicitly deep-learning OCR for documents and images.
- `paper photo (RU)` — `acceptable`: image OCR is supported, though the reviewed public pages do not expose RU-specific camera benchmarks.
- `paper photo (UK)` — `acceptable`: mixed-language and broad print-language support reduce risk.
- `signage` — `acceptable`: Azure distinguishes image OCR and document OCR editions.
- `handwriting` — `weak` for RU/UK: handwriting support in the reviewed GA language tables does not include Russian or Ukrainian.
- `dense PDF (RU)` — `strong`: document OCR and same-line mixed-language handling are explicit strengths.
- `dense PDF (UK)` — `strong`: same reasoning for Ukrainian print text.
- **Citations:** https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/language-support ; https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/overview-ocr

- **Yandex Vision OCR**
- `clean print` — `strong`: the service is positioned squarely as OCR for image/PDF text.
- `paper photo (RU)` — `strong`: Yandex explicitly targets image OCR in a RU-centric stack.
- `paper photo (UK)` — `acceptable`: the Latin-Cyrillic model is strong, but Ukrainian is not called out separately in the reviewed public language list.
- `signage` — `acceptable`: image OCR is supported, though not specifically sold as scene-text specialist software.
- `handwriting` — `acceptable` for RU and `weak` for UK: public docs explicitly say handwriting mode supports Russian and English.
- `dense PDF (RU)` — `strong`: PDF recognition is a named use case.
- `dense PDF (UK)` — `acceptable`: language-model coverage is broad, but the strongest document-language evidence is RU-centric.
- **Citations:** https://yandex.cloud/en/docs/vision/concepts/ocr ; https://yandex.cloud/en/docs/vision/concepts/ocr/supported-languages ; https://yandex.cloud/en/services/vision

- **OpenAI Vision**
- `clean print` — `acceptable`: image-capable models can transcribe visible text, but there is no dedicated OCR SLA in the reviewed docs.
- `paper photo (RU)` — `acceptable`: likely workable as multimodal extraction, but requires prompt design and local validation.
- `paper photo (UK)` — `acceptable`: same as RU, without vendor-published OCR metrics.
- `signage` — `acceptable`: multimodal models can reason over text-in-image well enough for fallback use.
- `handwriting` — `acceptable`: general VLM behavior may help on handwriting, but without deterministic OCR guarantees.
- `dense PDF (RU)` — `acceptable`: can extract text from rendered page images, though ordering/structure consistency must be validated.
- `dense PDF (UK)` — `acceptable`: same limitation.
- **Citations:** https://openai.com/api/pricing/ ; https://developers.openai.com/api/docs/guides/your-data

- **Anthropic Vision**
- `clean print` — `acceptable`: vision API can answer text-in-image prompts, but not as a dedicated OCR engine.
- `paper photo (RU)` — `acceptable`: plausible fallback, though not benchmarked in the reviewed docs.
- `paper photo (UK)` — `acceptable`: same limitation for Ukrainian.
- `signage` — `acceptable`: multimodal prompt path can likely read signage-level text.
- `handwriting` — `acceptable`: general VLM capability may help, but the vendor does not document Cyrillic handwriting OCR.
- `dense PDF (RU)` — `acceptable`: usable as a fallback on rendered pages, with structure consistency left to implementation testing.
- `dense PDF (UK)` — `acceptable`: same limitation.
- **Citations:** https://platform.claude.com/docs/en/build-with-claude/vision ; https://platform.claude.com/docs/en/about-claude/pricing

**Privacy/Cost:**

- **Google Cloud Vision OCR:** image content leaves the device and is processed by Google Cloud. Cost is simple and predictable for OCR: `0..1000` units free, then `$1.50 / 1000`. Opt-in friction is medium because the feature needs provider disclosure, account-backed credentials and quota/error UX.
- **Azure Vision Read:** image content leaves the device; reviewed public privacy docs emphasize same-region temporary storage and `24h` retention for analysis results in the document stack. Cost is metered per image/page with free `F0` trial entry. Opt-in friction is medium for the same reasons as Google.
- **Yandex Vision OCR:** image content leaves the device and results may remain on the server for up to `3 days` per the public limits page. Cost is very low for printed OCR (`~$0.00108` per page) but not free by default outside trial onboarding. Opt-in friction is medium, with better CIS alignment than most global providers.
- **OpenAI Vision:** image content leaves the device; default API abuse-monitoring retention is up to `30 days`, with project-level residency controls available for eligible customers. Cost is token-based rather than fixed per page; a small `512 x 512` example is around `$0.000263` input before output tokens. Opt-in friction is medium-to-high because residency/approval details and token-based billing are harder to explain clearly than flat OCR tariffs.
- **Anthropic Vision:** image content leaves the device; default backend deletion target is `30 days`, with separate zero-retention arrangements available by contract. Cost is token-based and model-dependent rather than page-flat. Opt-in friction is medium-to-high for the same reason as OpenAI plus the fact that the product is not OCR-specific.

**Communication policy note for cloud opt-in:**

Any follow-up implementation spec that adds cloud OCR MUST run all user-visible opt-in and quota/error copy through [docs/COMMUNICATION_POLICY.md](../../docs/COMMUNICATION_POLICY.md) `§2` and `§6`.

- Cloud-OCR enable confirmation dialog title/body for a named provider.
- Error toast/snackbar when cloud OCR quota or billing limit is exhausted.
- Settings entry label and supporting text for the off-by-default cloud OCR toggle.

**Резюме фазы 04:** среди closed-source SDK ни один candidate не поднялся выше axis B `Weak fit`: ABBYY упирается в enterprise-only redistribution, Anyline лучше по mobile packaging, но уже по use-case слишком узок. В cloud-категории ведущий axis A `Conditional` candidate на текущем проходе — `Google Cloud Vision OCR`, с `Azure Vision Read` и `Yandex Vision OCR` как близкими альтернативами под разный региональный профиль. Ни один commercial pathway не даёт устойчивого no-cost-to-user варианта: бесплатные квоты у облаков годятся для prototype/low-volume, а closed SDK требуют коммерческий контракт.

### 7. Sidecar-binary решения

**Кандидат A:** embedded Python runtime (`python-for-android` / BeeWare-style packaging) + PaddleOCR Python stack

- **Bundle size:** realistic mobile bundle is `>150 MB` once you add CPython runtime, native Python dependencies, Paddle runtime pieces, and OCR models; even before app glue this is an order of magnitude above the clean store-safe corridor.
- **IPC options:** file-based request/response, localhost socket / Unix domain socket, or in-process Python bridge if the entire interpreter is embedded. All three options add state-management and lifecycle complexity compared to a normal Android library.
- **Startup latency:** first OCR call would pay Python interpreter startup plus model load; practical cold start is likely multiple seconds, not the sub-`1.5 s` target.
- **Play policy stance:** Google Play explicitly calls out interpreted languages such as Python loaded at runtime as a policy-sensitive area when not packaged with the app. Even when packaged, a sidecar interpreter materially raises the dynamic-execution risk profile compared with a plain native library.
- **Sources:** https://support.google.com/googleplay/android-developer/answer/16559646?hl=en ; https://github.com/kivy/python-for-android ; https://beeware.org/project/projects/tools/briefcase/

**Ось A:** Reject
Reason: `APK size cap` failure plus a direct dynamic-execution policy risk surface.

**Ось B:** Weak fit
Reason: technically possible for a personal build, but the maintenance burden is disproportionate even before OCR quality is considered.

**Кандидат B:** native CLI sidecar (`tesseract` / Paddle-Lite demo binary) invoked via `Runtime.exec`

- **Bundle size:** for a Tesseract-class native sidecar with binary + OCR data, practical bundle lands roughly in the `25..40 MB` corridor depending on the chosen language data; Paddle-Lite CLI plus models lands in a similar or larger range.
- **Process-spawn / IPC cost:** every OCR request pays a subprocess spawn + marshaling overhead that JNI embedding avoids. This is strictly worse than the current in-process `Tesseract4Android` path when the underlying engine is the same.
- **SELinux / packaging constraint:** Android is much friendlier to executing packaged native libraries from `lib/<abi>/` than arbitrary extracted binaries in app files directories. A sidecar that relies on unpack-and-exec from writable storage raises more platform-friction and review risk.
- **Comparison vs JNI:** if a stable JNI wrapper exists, sidecar CLI adds complexity without improving OCR quality; its only rationale is "wrapper unavailable", not "better recognition".
- **Sources:** https://support.google.com/googleplay/android-developer/answer/16559646?hl=en ; https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/lite/readme.md ; https://github.com/tesseract-ocr/tesseract

**Ось A:** Reject
Reason: distribution/process-execution risk is higher than JNI embedding, while quality is not inherently better.

**Ось B:** Weak fit
Reason: feasible for experiments, but unjustified as a maintained product path unless it unlocks an otherwise unavailable engine.

**Качество на сценариях Столпа D:**

- **Embedded Python + PaddleOCR:** inherits PaddleOCR quality from category 3: `strong` on clean print, `acceptable` on paper photo/signage and dense PDF, `weak` on handwriting. **Citation:** see PaddleOCR sources in category 3.
- **Native CLI sidecar (tesseract-current):** inherits Tesseract-family quality from category 2: `strong` on clean print, `acceptable` on paper photo and dense PDF, `weak` on signage and handwriting. **Citation:** see Tesseract sources in category 2.

**Dynamic-loading boundary:**

- **Embedded Python + PaddleOCR:** under strategic `§3.2` and `S0156 ADR-6`, this candidate is the clearest red-flag in the whole matrix. If Python code or modules are downloaded after install, it is a direct policy problem; even if fully packaged, the presence of a general interpreter broadens the dynamic-execution attack surface. Public Play policy text explicitly names interpreted languages such as Python loaded at runtime as sensitive. AICore-style managed model downloads are not comparable here because AICore is a Google system service, not app-bundled arbitrary execution.
- **Native CLI sidecar:** downloading `.onnx` / `.tflite` / `.traineddata` for a prepackaged engine looks closer to data than code, but shipping an extra executable binary and invoking it from app storage changes the risk profile. Under `S0156 ADR-6`, this should be treated as a signing-key-level risk increase even if the underlying OCR engine is benign.

**Резюме фазы 05:** ни один sidecar path не даёт такого quality jump, который оправдал бы его maintenance и policy burden поверх уже найденных on-device DL вариантов. Лучший возможный verdict здесь — axis B `Weak fit`, потому что даже в sideload-мире PaddleOCR-as-library выглядит разумнее, чем OCR-as-runtime-inside-runtime. Категория 7 — самая рискованная из всех семи, и phase 06 должна явно пометить это в общей матрице.

## Кросс-осевая матрица кандидатов

| Кандидат | Категория | Ось A verdict | Ось B verdict | Primary blocker | Качество (печать/фото/вывеска/рукопись/PDF) | APK delta |
|---|---|---|---|---|---|---|
| Tesseract selective `tessdata_best` swap | Tesseract | Conditional | Acceptable | Needs clean install-on-demand data-pack UX and policy-safe delivery | `s/a/w/w/a` | `+3..18.47 MB` |
| PaddleOCR PP-OCRv5 mobile (`cyrillic`) | DL ONNX/TFLite | Conditional | Strong fit | Needs Paddle-Lite Android validation and policy-safe data-pack delivery | `s/a/a/w/a` | `+13.36 MB` |
| Google Cloud Vision OCR | Cloud API | Conditional | Acceptable | Privacy + explicit opt-in | `s/a/a/a/s` | `0 MB` |
| Azure Vision Read | Cloud API | Conditional | Acceptable | Privacy + explicit opt-in | `s/a/a/w/s` | `0 MB` |
| Yandex Vision OCR | Cloud API | Conditional | Acceptable | Privacy + cloud dependency | `s/s/a/a/s` | `0 MB` |
| OpenAI Vision | Cloud API | Conditional | Acceptable | Privacy + token billing + non-OCR-specific output | `a/a/a/a/a` | `0 MB` |
| Anthropic Vision | Cloud API | Conditional | Acceptable | Privacy + token billing + non-OCR-specific output | `a/a/a/a/a` | `0 MB` |
| ML Kit Text Recognition v2 (current family) | ML Kit | Reject | n/a (already covered by axis A) | No standalone Cyrillic module | `a/?/a/-/?` | `0 MB` |
| EasyOCR `cyrillic_g2` | DL ONNX/TFLite | Reject | Weak fit | No official Android artifact + heavy detector/runtime path | `a/a/a/w/w` | `+63.04..87.16 MB` |
| Gemini Nano via AICore | On-device LLM | Reject | Weak fit | Narrow device matrix + undocumented OCR guarantee | `?/?/?/w/w` | `0 MB` |
| Phi-3.5 Vision | On-device LLM | Reject | Weak fit | Multi-gigabyte model payload | `a/?/a/a/?` | `+3.22..8.29 GB` |
| Anyline SDK | Closed-source SDK | Reject | Weak fit | Contract-bound license + ID-centric scope | `a/a/w/-/w` | `n/a public` |
| ABBYY OCR SDK | Closed-source SDK | Reject | Reject | Enterprise redistribution opacity | `s/s/a/a/s` | `n/a public` |
| TrOCR handwritten Cyrillic | DL ONNX/TFLite | Reject | Reject | Runtime/memory scale + handwriting-only specialization | `w/w/w/s/w` | `+1.34 GB` |
| Embedded Python + PaddleOCR sidecar | Sidecar runtime | Reject | Weak fit | Dynamic-execution risk + very large bundle | `s/a/a/w/a` | `>150 MB` |
| Native CLI sidecar (`tesseract` / Paddle-Lite) | Sidecar runtime | Reject | Weak fit | IPC / SELinux / subprocess overhead | `s/a/w/w/a` | `+25..40 MB` |

## Ранжированный список noLegal-кандидатов

**Top noLegal recommendation:** `PaddleOCR PP-OCRv5 mobile` is the only candidate that reaches axis B `Strong fit`.

1. `PaddleOCR PP-OCRv5 mobile` — DL ONNX/TFLite — primary blocker for axis A: non-trivial Paddle-Lite integration plus the need for a clean user-requested data-pack flow — best chance at a real quality jump for Cyrillic without going to the cloud.
2. `Tesseract selective tessdata_best swap` — Tesseract — primary blocker for axis A: need to formalize quality-first install-on-demand data packs in a policy-safe UX — lowest-risk way to improve current offline OCR if we accept only evolutionary gains.
3. `Google Cloud Vision OCR` — Cloud API — primary blocker for axis A: privacy and opt-in requirements — strongest cloud fallback with the clearest public OCR pricing/language matrix.
4. `Azure Vision Read` — Cloud API — primary blocker for axis A: privacy and opt-in requirements — strong dense-document fallback with explicit mixed-language handling.
5. `Yandex Vision OCR` — Cloud API — primary blocker for axis A: privacy and cloud dependency — most CIS-aligned provider for Cyrillic-heavy workloads.
6. `OpenAI Vision` — Cloud API — primary blocker for axis A: privacy, token-based billing, and non-OCR-specific output — useful when multimodal interpretation matters more than rigid OCR formatting.
7. `Anthropic Vision` — Cloud API — primary blocker for axis A: privacy, token-based billing, and non-OCR-specific output — similar fallback value with less OCR-specific positioning.

## Ось A: store-safe upgrade for STANDARD

**positive:** at least three `Conditional` store-safe paths exist, but they are not equal.

The owner-preferred store-safe offline path is now explicit **install-on-demand quality-first OCR data packs**, not "ship everything in APK". In that framing, the closest-to-production path is a **Tesseract selective model-swap** follow-up: keep the current architecture, replace the current fast baseline with user-requested `tessdata_best` packs, and validate policy-safe delivery plus UX. The more ambitious offline path is **PaddleOCR PP-OCRv5 mobile**, which still needs a real Android/Paddle-Lite feasibility pass; a **cloud OCR opt-in** path remains store-safe in principle, but only behind explicit user consent, provider disclosure, credentials/quota UX, and communication-policy gating.

## Потенциальные follow-up implementation спеки

- **tesseract-cyrillic-model-swap-evaluation** — Ось A — compare current `tessdata_fast` against quality-first `tessdata_best` install-on-demand packs for RU/UK project scenarios and decide whether a store-safe offline upgrade exists. — Owner: S0156 epic
- **cloud-vision-ocr-opt-in** — Ось A — build a provider-abstracted opt-in cloud OCR fallback with disclosure, quota handling, and off-by-default settings flow. — Owner: S0156 epic
- **nolegal-paddleocr-paddlelite-bundle** — Ось B — integrate Paddle-Lite with the `cyrillic_PP-OCRv5_mobile_rec` stack and benchmark it against the current ML Kit + Tesseract baseline. — Owner: S0156 epic
- **nolegal-vlm-ocr-lab** — Ось B — isolate Gemini Nano / Phi-class multimodal OCR experiments behind a lab-only engine selector to validate whether VLMs add real value beyond classical OCR. — Owner: S0156 epic

## Закрытие strategic §6 research items

- **Q8 — Latin+Cyrillic mixed:** mixed-script behavior is no longer a theoretical blocker. Google Cloud Vision and Azure Read both publicly document mixed-language handling, while Yandex chooses a single language model per request and current on-device candidates still need local validation for mixed Latin+Cyrillic overlays. In practice, mixed-script robustness is another point in favour of cloud fallbacks and against assuming ML Kit/Tesseract parity.
- **Q9 — UK Cyrillic:** Ukrainian must be treated as a real sub-problem, not "Russian minus one locale". The reviewed stack shows explicit evidence in three places: `ukr.traineddata` exists separately from `rus.traineddata`, Google Cloud lists `uk` separately from `ru`, and PaddleOCR's Cyrillic recognizer explicitly includes Ukrainian. This means future implementation specs must keep RU and UK evaluation tracks separate, especially for glyphs `ї`, `є`, `ґ`, `і`.
- **Q10 — Dynamic-loading boundary:** current public evidence supports a nuanced reading. Downloaded `.traineddata`, `.tflite`, or `.onnx` files consumed by a prepackaged engine look closer to data than to executable code, but Google Play policy text is explicit that interpreted languages such as Python loaded at runtime are sensitive, and sidecar executables materially change the risk profile. Therefore model-download-after-install can only be treated as axis A `Conditional`, never automatically safe, while Python/CLI sidecars stay on the red side of `strategic §3.2` and `S0156 ADR-6`. Authority reviewed: https://support.google.com/googleplay/android-developer/answer/16559646?hl=en ; https://developer.android.com/ai/gemini-nano/ai-edge-sdk

- **Owner note after first pass:** after the initial research pass, owner clarified the intended product reading of `Бюджеты`: for OCR/translation, explicit user-requested package download is not considered a product smell but the preferred path; bundled APK bloat is the thing to avoid. This shifts future follow-up specs toward quality-first install-on-demand packs, not toward shaving every candidate down to the smallest always-bundled model.

## Snapshot и статус

- **Snapshot date:** 2026-05-21 (initial research pass)
- **Status:** Initial findings — iterative accumulation continues per S0156 §F rules
- **Owner stop criterion:** continues until explicit owner-decision per S0156 §6.10
