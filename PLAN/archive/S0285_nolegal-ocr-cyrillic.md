# Стратегическая спецификация: S0285 — OCR кириллицы: research двух осей (STANDARD legally-safe vs noLegal sideload-only)

**Ticket:** S0285
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-21
**Tier:** 4 — Strategic
**Epic:** S0156
**Roadmap entry:** Ad-hoc — запрос 2026-05-21, выделен из S0156 §6.6 (приоритетный example "улучшение кириллического OCR")
**Tactical plan:** [`PLAN/S0285_nolegal-ocr-cyrillic/INDEX.md`](S0285_nolegal-ocr-cyrillic/INDEX.md)

> **Scope:** STRATEGIC + RESEARCH. Карта возможных OCR-движков и моделей для кириллицы с разбором по двум осям: что юридически и технически допустимо в публичных сборках STANDARD/VR, и что доступно только в личной sideload-only сборке noLegal. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Текущий OCR-stack в STANDARD состоит из двух движков: ML Kit `text-recognition` (Apache 2, базовая Latin-script модель, кириллица распознаётся частично и с заметным падением качества) и Tesseract4Android (Apache 2 wrapper над Apache 2 Tesseract, стандартные LSTM-модели `rus.traineddata` / `ukr.traineddata`). На чистом печатном тексте этого хватает, на реальных фото с шумом, перспективой, рукописными элементами или плотной вёрсткой документов качество кириллицы заметно отстаёт от того, что сегодня дают современные open-source решения класса PaddleOCR, EasyOCR, dbnet+CRNN ONNX-портов и LLM-vision моделей.

При этом «зажатость рамками легального поля» имеет более широкий смысл, чем строгая redistribution-license. На STANDARD одновременно действуют четыре практических ограничения: (1) лицензия должна быть совместима с публичным маркетом (Apache 2, MIT, BSD приемлемы; GPL/AGPL — нет в нынешней изоляционной модели); (2) APK size budget не позволяет тащить 50–200 МБ моделей; (3) cold start и runtime memory должны оставаться приемлемыми для широкого круга устройств; (4) Google Play политика на dynamic code loading запрещает скачивание исполняемых моделей с серверов разработчика, даже если они и попадают только в одну сборку (signing key один на весь портфель). Эти четыре границы вместе и формируют ту самую "легальную рамку", упомянутую в формулировке задачи.

Нужна отдельная research-спека, которая не внедряет конкретный движок, а проходит по всему landscape OCR-решений для кириллицы и для каждого кандидата отвечает на два независимых вопроса: можно ли его поднять в STANDARD без нарушения четырёх ограничений выше, и если нет — попадает ли он в `noLegal` sideload-only канал и почему именно. Результатом должен быть chassis-документ из umbrella research S0156, дополненный конкретными рекомендациями по follow-up implementation спекам.

---

## 2. Цели

1. Составить карту всех значимых OCR-движков и моделей с поддержкой кириллицы по состоянию на момент исследования: open-source библиотеки, ONNX/TFLite порты предобученных моделей, on-device LLM с vision capabilities, paid SDK с прозрачной лицензией, облачные OCR API.
2. Для каждого кандидата зафиксировать ось A — пригодность к STANDARD: лицензия, redistribution terms, APK size impact, runtime cost, Google Play policy compliance (включая dynamic code loading), реальное качество кириллицы на типичных пользовательских сценариях.
3. Для каждого кандидата зафиксировать ось B — пригодность к `noLegal`: blocker type (license / store-policy / heavy-runtime / size budget / external binary / privacy / paid terms), реальная ценность относительно текущего baseline, sideload-only distribution implications.
4. Выявить минимум один store-safe upgrade path для STANDARD (даже если это всего лишь подключение ML Kit Cyrillic-script модуля или замена `rus.traineddata` на более свежую LSTM-модель), либо явно зафиксировать, что upgrade-coridor отсутствует.
5. Выявить ранжированный список `noLegal` кандидатов с конкретными blocker-причинами для каждого; список упорядочен по убыванию ожидаемой ценности.
6. Сформировать список follow-up implementation спецификаций (по правилам S0156 §F: slug содержит `nolegal`, header содержит `**Epic:** S0156`) — отдельная спека на каждый самостоятельный кластер (например, "ML Kit Cyrillic module" для STANDARD, "ONNX PaddleOCR sideload" для noLegal, "Cloud Vision opt-in fallback" если решено).

**Non-goals:**

- Внедрение любого нового OCR-движка в рамках этой спеки.
- Замена существующих ML Kit / Tesseract движков ради самой замены (см. S0156 ADR-7 / правило замены из вопроса 4).
- Обучение собственных моделей кириллицы или построение OCR-инфраструктуры в облаке проекта.
- Сравнительные benchmarks с публикацией: результаты ресёрча предназначены для внутренних follow-up спек, не для маркетинговых сравнений.
- Любые работы, требующие обхода чужих лицензионных соглашений, paid-tier ограничений или DRM-подобных защит моделей.
- Изменения существующего OCR-pipeline в STANDARD до открытия отдельной implementation спеки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исследование должно явно различать "что можно сегодня дотянуть в STANDARD" и "что доступно только в noLegal". Это две независимые оси, не одна шкала.
2. Если хотя бы один store-safe upgrade в STANDARD реально доступен — он должен быть выделен как первый кандидат на follow-up implementation, без задержки в общем audit-document.
3. Для `noLegal`-ветки приоритет — реальное расширение возможностей пользователя, а не "ещё один движок ради разнообразия". Каждое предложение должно показать конкретный сценарий, на котором новый движок даёт качественно другой результат.
4. Облачные OCR API (Google Cloud Vision, Yandex Vision OCR, OpenAI Vision, Anthropic Vision) рассматриваются как отдельная категория с собственными privacy/cost/region implications — не смешиваются с on-device решениями.
5. Исследование должно учесть, что Google Play политика на dynamic code loading применяется к signing key владельца целиком, не к отдельной сборке. Скачивание исполняемых моделей с серверов разработчика — риск для всего портфолио (см. S0156 ADR-6).
6. Если найдено решение, которое технически проходит ось A, но требует существенной перестройки текущего OCR-pipeline, — это допустимо вынести в follow-up спеку, не пытаясь "впихнуть" в текущую архитектуру в рамках research.
7. Результаты исследования должны интегрироваться в общий output-документ S0156 как отдельный самостоятельный раздел, а не дублировать существующую umbrella-структуру.

### 3.2 Жёсткие ограничения

- **Flavor:** для оси A — `standard` (включая VR, который базируется на standard); для оси B — `noLegal`. Никакие новые `BuildConfig.IS_NO_LEGAL_FLAVOR` гейты в `src/main/` не вводятся — flavor-isolation идёт через `src/<flavor>/java/` source-sets (CLAUDE.md Rule 15). При появлении implementation спек обе оси обязаны следовать `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **Лицензионная граница (ось A):** в STANDARD допускаются Apache 2, MIT, BSD, ISC, lib-MPL 2.0 (когда применима как library-clause). LGPL — отдельное исследование совместимости с проприетарным wrapping в Android APK, по умолчанию исключается. GPL/AGPL/SSPL — недопустимы для оси A.
- **Лицензионная граница (ось B):** GPL/AGPL допустимы, если изоляция compile-time sourceSet'ом (S0156 ADR-5). Closed-source paid SDK — допустимы при наличии прозрачных redistribution terms; "evaluation only" SDK не допускаются даже в sideload.
- **APK size budget (ось A):** ориентир — рост APK не более чем на 15 МБ относительно текущего baseline для always-shipped содержимого APK. Большие модели не обязаны ехать в APK: предпочтительный corridor для OCR/translation assets — explicit user-requested `download-after-install`, если скачивается data-only payload и runtime-движок уже находится в APK (см. ниже про dynamic code loading).
- **Dynamic code loading:** скачивание исполняемого кода или native libraries после установки приложения через серверы разработчика — недопустимо для signing key проекта. Скачивание моделей-данных (`.tflite`, `.onnx`, `.traineddata`, `.bin` без исполняемого кода) — допустимо при условии, что runtime-движок уже находится в APK и обрабатывает модель как passive data. Граница "data vs code" фиксируется на этапе follow-up спек, не в этом ресёрче.
- **Runtime budget:** новые движки не должны увеличивать cold start активности OCR более чем на 1.5 секунды на baseline-устройстве проекта (Quest 3 baseline, mid-range Android phone) для already-installed assets. Для quality-first OCR-pack, который пользователь явно запросил и дождался загрузки, допустим более медленный first-run / heavy-document path, если UX честно показывает progress и не маскирует ожидание под "быстрый" режим. Memory peak во время OCR-сессии — не более чем 2× от текущего peak.
- **Privacy:** облачные OCR-движки требуют явного user opt-in, локального шифрования credentials и понятного disclosure в settings. Никакие облачные движки не активируются автоматически без выбора пользователя.
- **Communication policy:** новые user-visible strings (выбор движка, ошибки, прогресс-индикаторы, opt-in диалоги) обязаны проходить через `docs/COMMUNICATION_POLICY.md` и его зеркала RU/UK; тон-checklist §6 — обязательный gate.
- **Localization:** EN/RU/UK parity для всех новых строк. UK-перевод обязателен независимо от того, к какой оси относится новый движок.

### 3.3 Owner inputs (Approval gate)

Каждое поле ниже содержит конкретное значение, чтобы спека могла перейти Draft → Approved. Состав полей определён характером спеки: `flavor-aware`, `perf-critical`, `ui-facing` (вероятная настройка выбора движка), `comm-policy-applies` (новые error/opt-in строки). Проверка: `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id S0285`.

- **Flavor scope:** ось A — `standard` (с автоматическим распространением на `vr`, так как VR = standard + VR-возможности по S0156 §3.1.8); ось B — `noLegal` (compile-time sourceSet isolation, S0156 ADR-5). Flavor `lite`, `photos`, `legacy` — вне scope: lite не включает OCR-функционал; photos — упрощённая photo-only сборка, OCR-upgrade не приоритет; legacy — minSdk 23 baseline, не получает экспериментальные движки.
- **Performance budget:** cold start OCR-сессии не более чем +1.5 сек к текущему baseline для already-installed assets; memory peak не более чем 2× от текущего peak; APK size impact для STANDARD-оси — до +15 МБ только для bundled payload. Для user-requested OCR data-packs жёсткий MB-cap не задаётся сам по себе, но follow-up spec обязана обосновать размер, показать UX загрузки и объяснить, почему по умолчанию скачивается именно quality-first пакет, а не "fast" вариант.
- **UI placement contract:** если research даст рекомендацию ввести selector "OCR engine" в Settings → Translation & OCR, размещение и порядок controls согласуются на этапе follow-up спеки через `/ui-clarify`. На уровне strategic spec — обязательство, что любая новая user-visible настройка пройдёт `/ui-clarify` gate до implementation.
- **Accessibility:** новые controls обязаны поддерживать TalkBack (contentDescription), keyboard/D-pad focus traversal (CLAUDE.md Rule 17 — три input modes), нецветовое отличие active/inactive состояний. Конкретные требования финализируются на implementation этапе.
- **Communication policy:** все user-visible strings (selector labels, opt-in диалоги для облачных движков, error states при недоступности модели, progress indicators при загрузке моделей-данных) обязаны пройти tone checklist §6 `docs/COMMUNICATION_POLICY.md` до commit.
- **Validation level:** для каждой follow-up implementation спеки — `assembleStandardDebug` + `assembleNoLegalDebug` (по применимости оси) обязательны; manual device test обязателен (BlockNeedUserTest перед Verified).
- **Owner sign-off:** 2026-05-21 — owner подтвердил scope (research-only, две оси, follow-ups отдельными спеками); подтвердил, что облачные движки рассматриваются как отдельная категория с opt-in моделью. Доп. owner guidance: для OCR/translation-pack download-after-install считается логичным основным corridor'ом; bundled "тащить всё в APK" нежелательно; при явном запросе OCR quality важнее speed, и default-download должен быть quality-first package, а не "fast" preset.
- **Related tickets:** S0156 (epic, umbrella research для noLegal capability surface); потенциальные follow-up implementation спеки — id присваиваются при их открытии, не сейчас.

---

## 4. Контекст текущей архитектуры

OCR в проекте реализован как часть viewer-pipeline для изображений и PDF. На уровне ролей: viewer-слой получает bitmap или page-render, передаёт его в OCR-роль, та возвращает структурированный текст с координатами, дальше overlay-слой рисует AR-перевод поверх изображения. Текущая реализация поддерживает два движка одновременно: ML Kit как primary path для скорости и Tesseract как fallback для лучшего качества кириллицы и offline-режима.

ML Kit text-recognition 16.0.1 — Apache 2 библиотека от Google, GMS dependency, обновляется автоматически через Play Services. Поддержка скриптов в базовой модели — Latin с partial Cyrillic. Существуют отдельные модули `text-recognition-chinese`, `text-recognition-japanese`, `text-recognition-korean`, `text-recognition-devanagari`, но **отдельного store-safe Cyrillic-модуля Google не выпустил**. Это первый структурный gap: то, что во всех других script-семействах Google закрыл отдельным модулем, в кириллице остаётся "best effort" внутри Latin-модуля.

Tesseract4Android 4.8.0 — Apache 2 wrapper над Tesseract 4 (LSTM-движок). LSTM-модели `rus.traineddata` и `ukr.traineddata` распространяются Google под Apache 2 на `tessdata_best` и `tessdata_fast` репозиториях. Качество на чистом печатном тексте приличное, на реальных фото — заметно проигрывает современным DL-моделям. Native libraries (`libtesseract.so`, `libleptonica.so`, `libpng.so`, `libjpeg.so`) уже в APK, 16 КБ page alignment настроен для Android 15+.

Архитектурно текущий OCR-pipeline допускает добавление дополнительных engine-стратегий через ту же роль, что используют ML Kit и Tesseract сегодня — это инфраструктурно похоже на extraction-strategy registry из S0116/S0117. Это значит, что follow-up implementation спеки смогут добавлять новые движки add-pattern'ом, а не replace-pattern'ом (S0156 ADR-7).

S0156 §6.6 уже зафиксировал, что улучшение кириллического OCR — priority-example для noLegal-research. Эта спека реализует тот research для конкретного кластера, оставляя более широкий audit OCR/translation/AI capabilities на других follow-up спеках под S0156.

---

## 5. Предлагаемый подход

Эта спека не вводит новую runtime-архитектуру — она вводит структуру research-документа, который пройдёт по landscape OCR-кандидатов и для каждого даст явный verdict по обеим осям. Архитектура pipeline не меняется на этом этапе; меняется capability matrix, на которую опираются follow-up implementation спеки.

### 5.1 Основные столпы / модули

**Столп A — Категоризация OCR-кандидатов.**

- Каждый кандидат относится ровно к одной primary категории: (1) GMS/Apache 2 модульный OCR от Google и аналогов; (2) Tesseract-семейство (engine + альтернативные модели); (3) Deep-learning ONNX/TFLite порты предобученных моделей (PaddleOCR, EasyOCR, dbnet+CRNN); (4) On-device LLM с vision capabilities (Gemini Nano, Phi-vision, llama.cpp с vision-моделями); (5) Closed-source SDK (ABBYY Mobile OCR, ReadIRIS, Anyline); (6) Облачные OCR API (Google Cloud Vision, Yandex Vision, OpenAI Vision, Anthropic Vision, Azure Computer Vision); (7) Sidecar-binary решения (упакованный Python interpreter с PaddleOCR, native CLI с tesseract-current).
- Категория сама по себе не определяет judgement — она задаёт типичный профиль ограничений, который потом разбирается индивидуально по обеим осям.

**Столп B — Анализ оси A (STANDARD pathway).**

- Для каждого кандидата фиксируется: точная лицензия, redistribution terms, требования Google Play (включая dynamic code loading risk), APK size impact, runtime cost на baseline-устройствах, реальное качество кириллицы на типичных пользовательских сценариях (печатный текст, фото документа, фото вывески, рукопись, плотная вёрстка), доступность UK-language model отдельно от RU-language model.
- Результат для каждого кандидата по оси A: `Accept` (можно поднять в STANDARD), `Conditional` (можно при доп. условиях — opt-in, dynamic data download без кода, отдельный download-after-install module), `Reject` (по конкретной причине из списка ограничений §3.2).

**Столп C — Анализ оси B (noLegal pathway).**

- Для каждого кандидата, который не прошёл ось A или прошёл условно, дополнительно оценивается ось B: какой именно blocker не пускает его в STANDARD, насколько blocker устраним в sideload-only канале, как кандидат соотносится с другими `noLegal`-кандидатами по ценности, не дублирует ли он функционал, который другие noLegal-движки дают дешевле.
- Результат для каждого кандидата по оси B: `Strong fit` (ясный value-add для noLegal без существенных negatives), `Acceptable` (value-add есть, но требует существенной integration работы), `Weak fit` (value-add не оправдывает integration cost даже в sideload), `Reject` (за пределами допустимости даже для личной sideload-only сборки).

**Столп D — Качественная оценка кириллицы.**

- Качество кириллицы оценивается не абстрактно, а на пяти типовых сценариях: (1) чистый печатный текст на белом фоне (baseline); (2) фото бумажного документа с естественным освещением и небольшой перспективой; (3) фото вывески / уличного указателя с фоновым шумом; (4) рукописный текст (где применимо); (5) плотная многоколоночная вёрстка PDF-страницы.
- Для каждого кандидата записывается ожидаемая performance characteristic по этим пяти сценариям, основанная на публичных benchmark'ах, GitHub-issues, академических работах и пользовательских отчётах. Собственный benchmarking — non-goal этого ресёрча (см. §2).

**Столп E — Output artifact и интеграция с S0156.**

- Результат — отдельный документ в `PLAN/S0156_nolegal-capability-surface-audit/`, соответствующий шаблону Столп F из S0156: разделы "Что найдено", "Просто и быстро", "Сложно но возможно", "Фантастика, но хочется", "Блокеры", "Потенциальные follow-up спеки".
- Из этого документа рождаются follow-up implementation спеки по правилам S0156: slug содержит `nolegal` (для оси B), для оси A — обычное имя без `nolegal`-prefix (это уже не `noLegal`-only); header каждой follow-up спеки содержит `**Epic:** S0156` независимо от оси, потому что эпиком остаётся общий S0156 capability research, а не эта S0285.
- Если ось A даёт хотя бы один реальный store-safe upgrade, он выделяется как первый кандидат на implementation и записывается в output-документ как `Просто и быстро` или `Сложно но возможно` (по сложности интеграции).

### 5.2 Потоки данных и событий

OCR-кандидат → категоризация по Столпу A → анализ оси A по Столпу B → если не прошёл или прошёл условно — анализ оси B по Столпу C → оценка качества кириллицы по Столпу D → запись verdict'а в output-документ по Столпу E → опционально открытие follow-up implementation спеки.

### 5.3 Точки расширяемости

- Категория "Облачные OCR API" должна допускать расширение без переписывания audit-структуры: если в течение research-периода появится новый Vision-API провайдер, он встраивается в существующую категорию с тем же шаблоном анализа.
- Категория "On-device LLM с vision" сейчас находится в раннем этапе зрелости (Gemini Nano начал появляться на узком наборе устройств в 2024–2025); audit обязан зафиксировать "что доступно сегодня" и явно отметить, какие кандидаты потребуют переоценки через 6–12 месяцев.
- Output-документ S0156 поддерживает iterative accumulation (S0156 §F): новый research-pass дописывает находки в существующий документ, не создаёт параллельный.
- Введение нового OCR engine в runtime — extends pattern (S0156 ADR-7): новые движки регистрируются в существующей engine-registry роли, не форкают pipeline.

---

## 6. Открытые вопросы / Research items

1. **Существует ли отдельный store-safe ML Kit модуль для кириллицы**
   - **Вопрос:** Google выпустил `text-recognition-chinese`, `-japanese`, `-korean`, `-devanagari` как отдельные Apache 2 модули. Существует ли аналогичный отдельный `text-recognition-cyrillic` или эквивалентный store-safe upgrade в семействе ML Kit?
   - **Нужно выяснить:** актуальный maven listing ML Kit, GMS announcements за 2024–2026, наличие в `com.google.mlkit:*` или `com.google.android.gms:vision-*` Cyrillic-specific package'а.
   - **Статус:** Open

2. **Доступна ли свежая Tesseract LSTM-модель кириллицы со значительно лучшим качеством**
   - **Вопрос:** есть ли в `tessdata_best` или сторонних публичных репозиториях обновлённая LSTM-модель `rus`/`ukr` (Tesseract 5.x training run) с заметным приростом качества относительно дефолтных моделей Tesseract 4.x, которые сейчас в проекте?
   - **Нужно выяснить:** актуальные tessdata-репозитории, community-trained модели на GitHub под Apache 2 / без redistribution ограничений, совместимость с Tesseract4Android 4.8.0 (engine ABI).
   - **Статус:** Open

3. **Какой PaddleOCR / EasyOCR ONNX-порт реально работоспособен в Android**
   - **Вопрос:** существуют ли стабильные ONNX/TFLite-порты PaddleOCR (Apache 2) или EasyOCR (Apache 2) Cyrillic-моделей с разумным runtime cost на mid-range Android устройстве? Размер моделей? Inference latency?
   - **Нужно выяснить:** GitHub-репозитории портов, размеры моделей (`det`, `rec`, `cls`), benchmarks на ARM-CPU и NNAPI, наличие community-issues про кириллицу.
   - **Статус:** Open

4. **Применим ли on-device LLM (Gemini Nano, Phi-vision) для OCR кириллицы**
   - **Вопрос:** Gemini Nano появился на узком наборе устройств с 2024. Поддерживает ли он vision input для OCR кириллицы? Какие лицензионные и distribution implications для использования через AICore API? Применимы ли альтернативные on-device модели (Phi-3.5-vision, MLLM от MediaTek)?
   - **Нужно выяснить:** AICore API documentation, требования к устройствам, лицензионные условия использования, доступность Cyrillic-script tasks.
   - **Статус:** Open

5. **Какие закрытые SDK с прозрачными redistribution terms существуют для Android OCR кириллицы**
   - **Вопрос:** ABBYY Mobile OCR Engine, Anyline OCR SDK, ReadIRIS Mobile — кто из них публикует прозрачные redistribution-условия (а не "evaluation only"), сколько это стоит, применимо ли для sideload-only сборки владельца?
   - **Нужно выяснить:** актуальные pricing tier'ы, eval vs production terms, наличие per-device или per-volume лицензирования, размер интеграционного SDK.
   - **Статус:** Open

6. **Опт-ин облачные OCR API: какие провайдеры и в каких регионах**
   - **Вопрос:** Google Cloud Vision, Yandex Vision OCR, OpenAI Vision, Anthropic Vision, Azure Computer Vision — кто из них даёт лучшее качество кириллицы на типичных сценариях, какие cost tier'ы, какие privacy implications, какие регионы доступны без VPN, какие требуют billing-account verification?
   - **Нужно выяснить:** public benchmarks, free-tier limits, GDPR/local-data residency policies, наличие batch API для PDF-страниц.
   - **Статус:** Open

7. **Sidecar-binary решения: насколько практичен termux-style runtime для OCR**
   - **Вопрос:** допустимо ли исследовать sidecar-binary OCR-runner (упакованный PaddleOCR + Python runtime, или native CLI с tesseract-current) как часть `noLegal`-сборки? Каковы implications для startup latency, memory, security, distribution size?
   - **Нужно выяснить:** existing Android termux-derived runtime'ы, размер минимального Python runtime с PaddleOCR на ARM64, IPC-механизмы (file-based, socket-based) для интеграции с основным приложением.
   - **Статус:** Open

8. **Cyrillic vs Latin+Cyrillic mixed text — какой движок справляется лучше**
   - **Вопрос:** реальные документы и фото часто содержат смешанный латинский + кириллический текст (URL внутри русскоязычного абзаца, английские термины в украинском тексте, аббревиатуры). Какой из кандидатов даёт стабильный результат на mixed-script content без auto-switch ошибок?
   - **Нужно выяснить:** behavior каждого кандидата на mixed-script, наличие explicit language hint API, корректность detection границ между скриптами.
   - **Статус:** Open

9. **Украинская кириллица: отдельные ли требуются модели**
   - **Вопрос:** украинская кириллица имеет специфические буквы (`ї`, `є`, `ґ`, `і`), которые в чисто-русских моделях могут распознаваться хуже. Какие кандидаты имеют отдельную UK-модель, а какие используют общую Cyrillic-модель? Где это критично?
   - **Нужно выяснить:** наличие отдельных `ukr.traineddata` vs `rus.traineddata` у каждого кандидата, реальное качество UK-specific символов в Cyrillic-only моделях.
   - **Статус:** Open

10. **Dynamic code loading vs dynamic data loading — где проходит граница для Google Play**
    - **Вопрос:** скачивание `.tflite` / `.onnx` / `.traineddata` модели после установки — это data download (допустимо) или code download (запрещено политикой)? Где проходит юридическая и техническая граница для signing key проекта?
    - **Нужно выяснить:** актуальная редакция Google Play developer policy на dynamic code loading, прецеденты (Hugging Face apps, OCR apps в Google Play, ML-приложения с downloadable models), позиция Play Developer Support.
    - **Статус:** Open

11. **Стоп-критерий research-итераций**
    - **Вопрос:** в какой момент считать research завершённым: фиксированное количество разобранных кандидатов, покрытие всех 7 категорий из Столпа A, или явный owner-decision по S0156 §6.10?
    - **Owner decision:** наследуется правило из S0156 §6.10 — research завершается явным owner-decision, что покрытие уже достаточно для перехода к decomposition.
    - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Research не найдёт ни одного store-safe upgrade для STANDARD | Средняя | Ось A окажется пустой, ожидание owner'а не оправдается | Явно зафиксировать negative result с обоснованием для каждого rejected-кандидата; ввести ML Kit Cyrillic-script проверку как baseline-attempt |
| Качественная оценка кириллицы будет поверхностной из-за non-goal на собственный benchmarking | Средняя | Verdict'ы по Столпу D окажутся ненадёжны | Опираться на множественные источники (GitHub issues, academic papers, community reports); при невозможности — явно отмечать "verdict требует device-test на implementation этапе" |
| On-device LLM landscape быстро эволюционирует, fixing snapshot устареет за месяцы | Высокая | Decision matrix окажется неактуальной к моменту implementation | В output-документе S0156 явно отметить кандидаты "snapshot YYYY-MM" и требовать переоценку при открытии follow-up |
| Облачные OCR API могут радикально изменить pricing или regional availability | Средняя | Cost estimate в verdict'ах устареет | Не делать exact pricing коммитментом; фиксировать "free tier exists / paid tier" качественно |
| Dynamic code loading boundary будет понята слишком оптимистично | Средняя | Implementation спека добавит механизм, который потом блокирует Play release | Закрыть вопрос 10 до перехода к decomposition; при сомнениях — defer mechanism на noLegal |
| Sidecar-binary OCR-runner создаст maintenance burden, несопоставимый с value-add | Средняя | noLegal обрастёт хрупкой зависимостью | Применять `weak fit` verdict в Столпе C для всех решений с sidecar runtime, если не доказан существенный quality gap |
| Output-документ S0156 будет расти бесконечно без явной точки остановки | Средняя | Research затянется на месяцы без декомпозиции | Применять правило S0156 §6.10: stop по owner-decision; не пытаться "закрыть все 7 категорий полностью" |
| UK-language coverage окажется хуже RU у большинства кандидатов | Средняя | UK-пользователи получат меньший прирост качества | Явно отмечать UK-specific verdict для каждого кандидата; UK не подменяется RU-моделью без явной верификации |
| ML Kit пометит Cyrillic OCR как deprecated до завершения research | Низкая | Часть analysis окажется привязанной к умирающему API | Мониторить ML Kit announcements; при deprecation — переориентироваться на on-device alternatives |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` на этом этапе: это research-only ticket, не пользовательская фича.

Будущие follow-up implementation спеки оси A могут добавить новый OCR-движок в STANDARD — тогда обновление `docs/FEATURES*.md` будет частью той конкретной implementation спеки, не этой.

Будущие follow-up implementation спеки оси B (noLegal) по правилу S0156 §3.1.9 не появляются в публичных `docs/FEATURES*.md` — они идут в локальный `docs/FEATURES_noLegal.md` (gitignored).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Две независимые оси анализа, а не одна шкала "приемлемости"**

- **Решение:** каждый OCR-кандидат проходит независимый анализ по оси A (STANDARD) и по оси B (noLegal). Verdict по одной оси не предопределяет verdict по другой.
- **Альтернативы:** единая шкала "от store-safe до noLegal-only" с одним финальным verdict'ом.
- **Почему:** STANDARD-blocker и noLegal-fitness — это разные вопросы. Кандидат, который не прошёл STANDARD по APK-size, может быть отличным fit'ом для noLegal. Кандидат, который не прошёл STANDARD по лицензии, может быть менее интересен для noLegal, чем кандидат с другим licence-blocker. Единая шкала эту разницу размывает.

**ADR-2: Облачные OCR API — отдельная категория с opt-in моделью**

- **Решение:** облачные провайдеры (Cloud Vision, Yandex Vision, OpenAI/Anthropic Vision, Azure) рассматриваются как отдельная категория, не смешиваясь с on-device движками. Активация — всегда explicit user opt-in.
- **Альтернативы:** относить облачные движки к той же категории, что on-device.
- **Почему:** privacy/cost/region profile у облачных движков радикально отличается. Смешивание с on-device создаёт ложное чувство эквивалентности и затрудняет анализ.

**ADR-3: Snapshot-фиксация для быстро меняющихся технологий**

- **Решение:** для on-device LLM и облачных API verdict'ы фиксируются с явным "snapshot YYYY-MM" маркером и требуют переоценки при открытии follow-up implementation спеки.
- **Альтернативы:** фиксировать verdict'ы без time-marker как стабильную истину.
- **Почему:** Gemini Nano, on-device vision models, cloud OCR API меняются на масштабе месяцев. Фиксация без time-marker создаёт ложное чувство стабильности.

**ADR-4: Add-pattern для будущих OCR-движков, не replace**

- **Решение:** даже если research найдёт превосходящего кандидата, он добавляется к существующим ML Kit + Tesseract движкам через add-pattern (S0156 ADR-7), а не заменяет их.
- **Альтернативы:** замена существующего pipeline.
- **Почему:** правило замены из S0156 §6 вопрос 4 — не менять существующее ради самой замены. ML Kit даёт скорость, Tesseract — offline-режим и приличное baseline-качество. Новый движок добавляется как дополнительная стратегия, выбираемая или автоматически (по сценарию), или пользователем.

**ADR-5: Граница "data download" vs "code download" фиксируется на этапе follow-up, не в этом research**

- **Решение:** research зафиксирует, что граница существует, но конкретная её юридическая интерпретация (через open question 10) определяется в follow-up implementation спеке для каждого конкретного механизма download-after-install.
- **Альтернативы:** попытаться зафиксировать абсолютную границу прямо в этом research.
- **Почему:** Google Play policy interpretation эволюционирует, прецеденты у других ML-приложений растут. Привязка к конкретному моменту времени в research-спеке создаст риск устаревания.

---

## 10. Связи с другими спеками

- **S0156** (epic) — umbrella research для всего `noLegal` capability surface; эта S0285 — один из самостоятельных кластеров S0156 (OCR/translation branch из §6.6), пишется как отдельная research-спека с собственным output-документом, интегрируемым в `PLAN/S0156_nolegal-capability-surface-audit/`.
- **S0117** (Archived) — baseline для `noLegal` flavor isolation pattern (compile-time sourceSet, fixed dependency version, no dynamic loading); applicable как architectural precedent для оси B implementation спек.
- **S0116** — generic URL/media downloader; не пересекается напрямую, но даёт паттерн extraction-strategy registry, на который опираются ADR-4 и ADR-7 этой спеки.

Эта спека не блокируется и не блокирует никаких других tickets на момент создания. Зависимости появятся при открытии follow-up implementation спек.

---

## 11. Критерии готовности (strategic-level)

1. Output-документ создан в `PLAN/S0156_nolegal-capability-surface-audit/` и соответствует шаблону Столп F из S0156 (разделы "Что найдено", "Просто и быстро", "Сложно но возможно", "Фантастика, но хочется", "Блокеры", "Потенциальные follow-up спеки").
2. По крайней мере одна из 7 категорий Столпа A разобрана с конкретными кандидатами (или явным negative result).
3. Открытые вопросы §6 1–10 либо закрыты, либо явно перенесены в follow-up implementation спеку с указанием конкретного открытого пункта.
4. Для каждого разобранного кандидата зафиксирован verdict по оси A (`Accept` / `Conditional` / `Reject` + причина) и при необходимости по оси B (`Strong fit` / `Acceptable` / `Weak fit` / `Reject` + blocker type).
5. Найден и явно отмечен хотя бы один store-safe upgrade-кандидат для STANDARD, либо явно зафиксировано отсутствие такого upgrade-пути с обоснованием.
6. Список ранжированных noLegal-кандидатов сформирован с указанием blocker type для каждого.
7. Список потенциальных follow-up implementation спек составлен (без присвоения id) с указанием оси (A или B) и кластера.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0285` — создаст `PLAN/S0285_nolegal-ocr-cyrillic/` с фазами для прохождения research по 7 категориям Столпа A и интеграции output'а с S0156 audit-document.

---

## Last Audit

**Date:** 2026-05-22
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

### Manual / on-device

- Никаких runtime-проверок не требуется — research-only ticket без code changes. Follow-up implementation спеки (`tesseract-cyrillic-model-swap-evaluation`, `cloud-vision-ocr-opt-in`, `nolegal-paddleocr-paddlelite-bundle`, `nolegal-vlm-ocr-lab`) откроют свои отдельные verification loops по правилу S0156 §F.

### Audit summary

- Output artifact `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` создан (573 строки), все 7 категорий разобраны с verdict'ами по обеим осям, Snapshot-секция закрыта.
- §11 критерии 1–7: PASS (output exists, 7/7 категорий, research items 1–10 закрыты, verdicts axis A+B, минимум один store-safe upgrade выделен, ranked noLegal list собран, follow-up спеки перечислены).
- §8 FEATURES: EXEMPT ("Без изменений").
- Tactical INDEX: 7/7 phases ✅ Done; все Step.* статусы `[x] done`; `TODO(phase-XX)` = 0; `_(populated in Phase…)_` placeholders = 0.
- Debug-tag invariant: PASS (0 `Timber.d("S0285:` тегов в `.kt`, status не `BlockNeedUserTest`).
- Dev changelog: 33 записи по `S0285` / `ocr-cyrillic`.
- Catalog sync: EXEMPT (no `.kt` mutations).
