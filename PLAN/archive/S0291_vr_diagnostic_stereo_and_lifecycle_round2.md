# Стратегическая спецификация: S0291 - VR Test Immersive: round 2 after owner retest

**Ticket:** S0291
**Status:** Archived
**Priority:** 85
**Date:** 2026-05-22
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-05-22 по итогам ручного VR-ретеста
**Tactical spec:** `PLAN/S0291_vr_diagnostic_stereo_and_lifecycle_round2/`
**Tactical plan:** `PLAN/S0291_vr_diagnostic_stereo_and_lifecycle_round2/INDEX.md`
**Implemented date:** 2026-05-22
**Related note:** dead Colosseum sample note (absorbed into this ticket)

> **Scope:** STRATEGIC. Фиксирует подтверждённо рабочую базу, дефекты round 2 и критерии следующего цикла работ. Без имён классов, путей реализации и низкоуровневых шейдерных деталей.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - `spec`
- **Goal / expected outcome:** Provided by user - превратить устный VR-ретест в понятную спецификацию следующей волны работ
- **Local anchor:** Provided by user - on-device ретест кнопки `Test Immersive` и полный круг по текущему диагностическому плейлисту
- **Scope boundaries / forbidden areas:** Provided by user - не ломать уже хорошие 360 mono, 180 mono, лучи контроллеров, перетаскивание HUD и рабочий flat stereo reference; сосредоточиться на стерео, sample quality, provisioning и repeat-launch crash
- **Done / success signal:** Provided by user - должно быть ясно, что уже считается рабочей базой, что именно сломано и что нужно чинить дальше без повторного разбора голосовых заметок
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions while translating spoken feedback into spec form
- **UI decisions / delegation:** Provided by user - текущий HUD, лучи и жест перетаскивания остаются без редизайна; новая UI-перестройка не запрашивалась

---

## 1. Проблема

После ручного VR-ретеста стало ясно, что диагностический immersive-режим больше нельзя считать «целиком сломанным». Часть поведения владелец прямо подтвердил как хорошую и полезную, а часть остаётся дефектной или вводящей в заблуждение. Пока эти две группы не разведены в явную спецификацию, дальнейшие правки рискуют ломать уже рабочий путь или лечить не код, а плохой тестовый контент.

Текущий плейлист проходит полный круг и возвращается к `diagnostic_360_mono.jpg`, поэтому набор наблюдений уже достаточно стабилен для отдельного тикета следующего раунда.

### 1.1 Повторный вход в immersive ломается

Выход из immersive по кнопке контроллера возвращает в окно настроек ожидаемо, но повторный запуск `Test Immersive` в том же процессе приводит к падению. Это блокирует быстрые циклы ручной проверки и делает любую дальнейшую QA-работу одноразовой.

### 1.2 Стерео-корректность не подтверждена

- `diagnostic_180_stereo_tb.jpg` и `diagnostic_180_stereo_sbs.jpg` воспринимаются как вероятно перепутанные по глазам или как минимум лишённые ожидаемого объёма.
- `video_180_stereo_tb.mp4` и `video_180_stereo_sbs.mp4` дают тот же субъективный эффект: проекция выглядит уместной, звук есть, но глубина не ощущается как правильная.
- `diagnostic_360_stereo_tb.jpg` и `diagnostic_360_stereo_sbs.jpg` дают двоякое впечатление: объём как будто есть, но истина по left/right не доказана.
- `video_360_stereo_sbs.mp4` выглядит явно сломанным: вместо ожидаемого 360 stereo видео владелец видит split-картинку с белой полосой и ощущение статичного кадра.

### 1.3 Метод верификации left/right сейчас не работает

Владелец не видит никаких букв `L` и `R`, на которые рассчитывал текущий диагностический набор. Значит, существующий способ доказать маршрутизацию глаз либо не генерируется, либо плохо читается, либо подменяется другим контентом.

### 1.4 Часть sample-набора неверна или нечестно названа

- `moraine_lake_flat_mono.jpg` фактически выглядит как совмещённая stereo-картинка, хотя должен быть обычным flat mono reference.
- `moraine_lake_flat_sbs.jpg` по содержимому фактически совпадает с низкокачественным sample про кабана и уже не соответствует своему имени.
- `colosseum_flat_mono.jpg` показывает не Колизей, а речку или берег; само имя унаследовано от старой неудачной загрузки и теперь мешает диагностике.

### 1.5 Часть видео-слотов не выполняет диагностическую роль

- `video_360_mono.mp4` и `video_180_mono.mp4` воспринимаются как статическая картинка, а не как полезное motion video.
- `video_360_stereo_tb.mp4` распознаётся как видео, но sample слишком слаб по качеству и желательно заменить его вариантом со звуком.
- `big_buck_bunny_flat_mono.mp4` слишком низкого качества для опорного flat mono sample, даже если сам путь воспроизведения уже работает правильно.

### 1.6 Есть отдельная визуальная проблема качества

На `colosseum_flat_mono.jpg` при движении головой ощущается «рябь» по изображению. Причина не установлена: это может быть источник, фильтрация, aliasing или другой дефект render-quality.

### 1.7 Round 9 verification revealed two new lifecycle symptoms

После round 9 (сохранение `DiagnosticXrActivity` живой через task move) повторный запуск immersive больше не падает, но возникли два новых дефекта, ломающих owner-acceptable приёмку:

- **Выход возвращает Settings в чёрное XR-окружение.** До запуска приложения пользователь видит домашнее окружение Quest (passthrough через камеры). После выхода из immersive по кнопке контроллера панель Settings снова показывается, но вокруг неё чёрная XR-сцена вместо обычного passthrough. Регрессия по сравнению с состоянием «до запуска».
- **Повторный запуск immersive показывает HUD как одноцветный прямоугольник без текста.** Контент сферы/квада восстанавливается, но HUD на second launch выглядит как ровная серая плитка вместо ожидаемого filename + projection/layout баннера. Регрессия по сравнению с first launch.

Эти два симптома непосредственно блокируют owner-checklist (§14, пункт 1) и должны быть закрыты следующим раундом тактической работы.

> **Supersession note (2026-05-30):** оба симптома наблюдались на round-9 коде, где `DiagnosticXrActivity` оставалась живой через `moveTaskToBack(true)`. Этот путь снят: `S0295` (`vr-generic-immerse-playback-contract`, Verified 2026-05-25) переработал выход в `finish()` + Home/`PendingIntent` panel-host handoff, поэтому повторный вход теперь создаёт свежую Activity. HUD-симптом закрыт устойчивым фиксом (§6.9, Step 06.1). Passthrough-симптом (§6.8) частично вне lane S0291 — см. §6.8 статус.

---

## 2. Цели

1. Зафиксировать owner-confirmed baseline как обязательный `do not break`.
2. Сделать повторный вход в immersive из окна настроек стабильным в рамках одного процесса.
3. Восстановить доверие к stereo-диагностике для 180 и 360 photo/video samples.
4. Починить или заменить misprovisioned и misleading samples, чтобы имя слота соответствовало наблюдаемому контенту.
5. Поднять качество слабых video references до уровня, пригодного для честной ручной проверки.
6. Устранить или корректно локализовать отдельный дефект `video_360_stereo_sbs.mp4`.
7. Исследовать и по возможности уменьшить «рябь» на flat image references без регрессии текущего приемлемого опыта.
8. Сохранить deterministic QA-loop: один и тот же плейлист, один и тот же порядок, понятный результат после полного круга.
9. Восстановить корректное возвращение home passthrough окружения после выхода из immersive и гарантировать, что HUD заново показывает имя файла на повторном запуске в той же сессии.

**Non-goals:**

- Полный редизайн VR-плеера или переписывание всей XR-архитектуры.
- Новая пользовательская фича вне внутреннего диагностического режима.
- Изменение уже подтверждённо хорошего HUD и controller-ray поведения без отдельной причины.
- Широкий форматный R&D вне тех дефектов, которые выявлены этим ретестом.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- `diagnostic_360_mono.jpg` по качеству устраивает и должен остаться эталоном для текущей 360 mono ветки.
- Два луча от рук во время работы контроллеров идут правильно и должны считаться закреплённым baseline.
- Нижняя кнопка контроллера, которой можно схватить HUD и унести его в сторону, работает хорошо и должна сохраниться.
- `diagnostic_180_mono.jpg` отображается корректно и не требует переосмысления.
- `big_buck_bunny_flat_tb.mp4` показывает убедительное 3D, звук и устраивающий размер экрана; этот путь нельзя испортить.
- `big_buck_bunny_flat_sbs.mp4` сейчас повторяет тот же хороший flat stereo reference и тоже должен остаться рабочим.

### 3.2 Жёсткие ограничения

- **Flavor:** работа остаётся в VR-специфичном контуре и следует правилам `vr` / `noLegal` isolation.
- **Wear OS:** не затрагивается.
- **Производительность:** нельзя ухудшить текущий owner-acceptable опыт на Quest 3 ради локального улучшения одного слота.
- **Детерминизм:** диагностический плейлист должен оставаться воспроизводимым на чистой машине и чистом устройстве.
- **Честность sample-набора:** имя файла, его содержимое и его диагностическая роль не должны противоречить друг другу.
- **Лицензирование sample-источников:** использовать только стабильные и допустимые публичные источники или детерминированные bundled/generated replacements.
- **Validation:** итоговая приёмка требует ручной Quest 3 проверки с повторными входами в immersive, полным кругом плейлиста и поочерёдным закрытием глаз на stereo diagnostics.
- **UI contract:** текущие HUD-interaction и controller rays не меняются по поведению, если отдельная спека этого не потребует.

---

## 4. Контекст текущей архитектуры

Внутренний VR diagnostic mode состоит из четырёх логических частей: подготовка sample-набора, определение projection/layout для каждого слота, immersive render path для фото и видео, и слой интерактивного HUD с контроллерами. Последний ручной ретест показывает, что HUD и базовая input-механика в основном вышли в рабочее состояние, а основные остаточные проблемы сосредоточены на стыке lifecycle, stereo-interpretation и качества самих test assets.

Поэтому следующая волна работ должна различать три гипотезы отдельно: сломан render path, сломан provisioning sample-ов, или sample сам по себе плохой и не подходит как truth source. Смешивать эти причины в один общий «VR всё ещё плохой» тикет больше нельзя.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы

- **Baseline lock.** Сначала превратить подтверждённо хорошие наблюдения из §3.1 в явный регрессионный чек-лист для всех следующих фаз.
- **Lifecycle hardening.** Затем снять блокирующий дефект повторного входа в immersive, чтобы QA снова стала многократной, а не одноразовой.
- **Stereo truth path.** После этого вернуть способ однозначно проверить left/right routing на диагностических stereo slots, а уже потом менять общие stereo-решения.
- **Sample library correction.** Отдельным потоком привести sample-набор в честное состояние: wrong asset, wrong name, fake motion video, low-quality reference.
- **Render-quality investigation.** «Рябь» и прочие quality issues разбирать только после того, как baseline, lifecycle и stereo-truth снова надёжны.

### 5.2 Потоки данных и событий

- **Provisioning flow:** deterministic sources или bundled assets → локальный diagnostic cache → on-device playlist.
- **Interpretation flow:** filename markers и явные diagnostic hints → projection/layout choice → image/video render path.
- **Lifecycle flow:** enter immersive → inspect samples → exit to settings → re-enter in same process without stale state.
- **Verification flow:** synthetic stereo truth sample → one-eye owner check → real-depth sample → acceptance or correction.

### 5.3 Точки расширяемости

- Sample-набор должен позволять заменять отдельные источники без переписывания всей диагностической сцены.
- Stereo-truth verification должна переиспользоваться в будущих VR-тикетах, а не быть разовой импровизацией этого раунда.
- Если для отдельных слотов понадобятся исключения, они должны быть явными и трассируемыми, а не скрытыми в молчаливых эвристиках.

---

## 6. Открытые вопросы / Research items

1. **180 stereo truth**
   - **Вопрос:** действительно ли 180 TB/SBS samples перепутаны по глазам, или проблема в слабом источнике и невыразительной глубине?
   - **Нужно выяснить:** независимый truth-sample или другой однозначный left/right check.
   - **Ответ:** текущий renderer следует OpenXR primary stereo convention: view index `0` это левый глаз, view index `1` это правый глаз. Новые 180 stereo samples построены из одного Vuze XR SBS source, затем TB выводится из SBS без глобального swap. Оставшийся субъективный вопрос по объёму проверяется только owner one-eye check по крупным `L/R` labels; общий eye-order не нужно менять без такого подтверждения.
   - **Статус:** Answered, implemented, awaiting owner Quest 3 verification

2. **360 SBS breakage**
   - **Вопрос:** `video_360_stereo_sbs.mp4` сломан из-за layout interpretation, frozen video path или отдельного 360-SBS render bug?
   - **Нужно выяснить:** на каком этапе ветка перестаёт вести себя как настоящее видео.
   - **Ответ:** основной дефект был в interpretation path: filename parsing мог поймать generic `_stereo` раньше `_sbs` и отправить SBS slot через TB layout. Текущий parser проверяет SBS/TB markers до generic stereo marker. `ffprobe` подтверждает, что `video_360_stereo_sbs.mp4` является motion video `8640x2160`, `8.000s`, H.264 + AAC, а MD5 кадров на `t=0/3/7` различаются.
   - **On-device 2026-06-04 (Quest 3, лог):** interpretation-fix корректен, но сам сэмпл невоспроизводим на устройстве. Аппаратный AVC-декодер Quest 3 не инициализируется на `8640×2160` H.264 (профиль High, level 6.0): `ERROR_CODE_DECODER_INIT_FAILED` (4001) на стадии инициализации декодера, `IllegalArgumentException` в нативной конфигурации, предупреждение «Unknown AVC level: 60». Критерий §11.6 недостижим с этим сэмплом — нужна замена на воспроизводимый эквивалент того же формата (меньшее разрешение или HEVC/AV1). Наблюдаемость отказа доказана через S0322; краш отчёта об ошибке (S0341) блокирует device-test.
   - **Статус:** Reopened 2026-06-04 — interpretation исправлен, но сэмпл превышает аппаратный декодер устройства

3. **Invisible L/R markers**
   - **Вопрос:** почему владелец не видит `L/R` markers?
   - **Нужно выяснить:** они не генерируются, слишком малы, плохо размещены или замещаются real-content sample-ами.
   - **Ответ:** старый набор не давал надёжного видимого truth-cue на фактических real-content replacements. Provisioning теперь использует `drawtext` с найденным системным fontfile (`C:\Windows\Fonts\ariblk.ttf`) и крупными центральными `L/R` labels для stereo image/video outputs.
   - **Статус:** Answered and fixed, awaiting visual confirmation

4. **Moraine sample mismatch**
   - **Вопрос:** почему `moraine_lake_flat_mono.jpg` выглядит как stereo-combined asset?
   - **Нужно выяснить:** stale local cache, wrong overwrite order или неверный исходный файл.
   - **Ответ:** подтверждён stale/provisioning confusion: локальный canonical cache уже очищался, но на Quest в `/sdcard/Pictures/FastMediaSorterVrTest` оставался legacy `colosseum_flat_mono.jpg`, совпадающий по hash с новым `lakeside_flat_mono.jpg`, и мог путать ручную проверку. Runtime playlist больше не ссылается на Colosseum slot. Скрипт теперь удаляет canonical и known legacy remote names перед `adb push`.
   - **On-device 2026-06-04 (Quest 3, лог):** новый симптом на тех же слотах — `moraine_lake_flat_mono.jpg` и `moraine_lake_flat_sbs.jpg` не декодируются в diagnostic-пути: `BitmapFactory` бросает `IllegalArgumentException: Problem decoding into existing bitmap`, баннер «Failed to decode image». Это другой режим отказа, чем исходный «wrong content» — теперь сбой именно декодирования (вероятно переиспользуемая bitmap-буфер несовместимого размера/конфига в flat-image пути либо повреждённый provisioning-вывод). Остальные 360/180 картинки декодируются нормально.
   - **Статус:** Reopened 2026-06-04 — сбой декодирования flat-image на moraine-слотах

5. **Better 360 stereo video with audio**
   - **Вопрос:** какой публичный 360 stereo motion sample со звуком и приемлемым качеством можно использовать как новый reference?
   - **Нужно выяснить:** стабильный источник, лицензию и минимально достаточное качество.
   - **Ответ:** для S0291 выбран стабильный публичный Bino `rolling-marbles-360-tb.mp4` как 360 3D reference. У source нет полезной штатной audio path, поэтому provisioning добавляет мягкий diagnostic tone, чтобы проверять video+audio pipeline вместе. Более эстетичный high-quality 360 stereo sample с native audio остаётся future refinement, а не blocking item этой спеки.
   - **Статус:** Answered for S0291

6. **Flat-image shimmer**
   - **Вопрос:** чем вызвана «рябь» на flat mono reference при поворотах головы?
   - **Нужно выяснить:** проблема источника, фильтрации, sampling quality или другого render-quality слоя.
   - **Ответ:** наиболее вероятная причина для high-detail flat image при head motion - texture minification aliasing/moire на слабой filtering chain. Static image upload теперь генерирует mipmaps, включает `GL_LINEAR_MIPMAP_LINEAR` minification и включает conservative anisotropic filtering when supported.
   - **Статус:** Answered and mitigated, awaiting owner visual check

7. **Repeat-launch crash path**
   - **Вопрос:** какой именно lifecycle branch остаётся грязным после выхода из immersive по контроллеру?
   - **Нужно выяснить:** какой остаточный state переживает exit и ломает следующий launch.
   - **Ответ:** найденный риск - stale native initialized state и Java/native lifecycle race при повторном входе после exit. Runtime теперь проверяет native initialized state до init, force-cleans stale non-running state, синхронно останавливает render thread и освобождает ExoPlayer/surface до native shutdown.
   - **Статус:** Answered for crash path; round 9 owner verification revealed two new related symptoms — see items 8 and 9 below.

8. **Polite OpenXR exit handshake**
   - **Вопрос:** какой шаг OpenXR exit-handshake пропущен в текущем выходе из сессии, из-за чего Quest compositor не возвращает home passthrough окружение после выхода?
   - **Нужно выяснить:** что должно вызываться вместо/в дополнение к простому выходу из frame loop и немедленному уничтожению сессии, чтобы рантайм Quest корректно пометил приложение как «больше не в immersive» и восстановил passthrough вокруг Settings панели.
   - **Промежуточный анализ (2026-05-30):** текущий native teardown вызывает `xrDestroySession` напрямую из running-состояния, без graceful handshake (`xrRequestExitSession` / прокачки `pollEvents` до `XR_SESSION_STATE_STOPPING` → `xrEndSession` → `EXITING`). Сцена жёстко `XR_ENVIRONMENT_BLEND_MODE_OPAQUE`, слой passthrough не композитится (расширение `XR_FB_passthrough` не включено при создании инстанса). Сам exit-путь Activity принадлежит `S0295` (Verified). `xr_session.cpp` = 1526 LOC (>1500) — добавление handshake на месте запрещено Strict Rule 2 без предварительной extraction.
   - **Статус:** Open — deferred за пределы S0291. **Owner 2026-05-30: diagnostic-first** — тикет-преемник НЕ заводится, пока Quest 3 не подтвердит, воспроизводится ли §11.15 на текущем S0295-`finish()`-коде (возможно, корректного exit уже достаточно и явный handshake/passthrough-слой не нужен). Развилка реализации (чистый session-end handshake vs `XR_FB_passthrough` слой) решается после наблюдения, не раньше.

9. **HUD persistence across re-entry**
   - **Вопрос:** где именно в текущей логике re-entry теряется HUD-байт-буфер, из-за чего повторный запуск в той же сессии показывает HUD как одноцветный прямоугольник без текста?
   - **Нужно выяснить:** какая queue-точка должна срабатывать при возобновлении native-сессии (помимо первичного `onCreate`-пути) для image-элементов плейлиста, чтобы баннер с именем файла и projection/layout заново попадал в новую native HUD-текстуру.
   - **Ответ (2026-05-30):** найденная точка — `onRenderThreadSessionReady`. Она ре-queue'ила HUD только для video-веток (`isVideoFilename`); для image-элементов баннер ставился лишь одноразово в `onCreate`-пути (`decodeBundledAsset` / `decodeImageToActivityBytes`). При recreation native-сессии HUD-текстура пересоздаётся как 1×1 placeholder, а pending-HUD-байты предыдущей сессии очищаются, поэтому image-элемент оставался серым placeholder. Фикс: `onRenderThreadSessionReady` теперь безусловно ре-queue'ит баннер текущего элемента для всех типов медиа (идемпотентно). Замечание о расхождении: round-9 симптом наблюдался на снятом `moveTaskToBack`-коде; на текущем `S0295`-`finish()`-пути повторный вход создаёт свежую Activity (`onCreate` ре-queue'ит баннер), так что симптом мог уже не воспроизводиться — фикс делает путь устойчивым к обоим сценариям re-entry.
   - **Статус:** Answered and hardened (Step 06.1), awaiting owner Quest 3 round-10 confirmation (§11.16).

---

## 7. Риски

- **Stereo overcorrection.** Вероятность: средняя. Последствие: глобальный stereo-fix может сломать текущий хороший flat stereo reference. Митигация: не менять общий eye-order без явного truth-check.
- **Bad-source confusion.** Вероятность: высокая. Последствие: команда будет чинить renderer, хотя проблема в самом sample. Митигация: явно разделять source problem и render problem в тактике.
- **Lifecycle partial fix.** Вероятность: средняя. Последствие: один путь повторного входа починится, а другой останется падать. Митигация: проверять несколько exit/re-enter сценариев, а не один happy path.
- **Quality tuning regression.** Вероятность: средняя. Последствие: локальное улучшение flat image вызовет fps regression или испортит working baseline. Митигация: quality work только после baseline lock и с обязательным owner retest.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это внутренний диагностический VR-контур, а не новая публичная capability.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Owner-confirmed good behavior becomes a locked regression baseline**

- **Решение:** всё, что владелец прямо назвал хорошим, фиксируется как `do not break` до начала следующих правок.
- **Альтернативы:** продолжать считать весь режим одинаково подозрительным.
- **Почему:** теперь у нас есть достаточно точное разделение «это уже хорошо» и «это ещё плохо».

**ADR-2: Eye-order нельзя чинить по субъективному ощущению на случайном real footage**

- **Решение:** сначала вернуть или заменить понятный truth path для `L/R` проверки, потом решать судьбу общих stereo-веток.
- **Альтернативы:** вносить глобальный stereo-fix по одному ощущению «объём странный».
- **Почему:** сам sample может быть слабым, перепутанным или плохо читаемым.

**ADR-3: Sample quality и sample provenance входят в scope этой спеки**

- **Решение:** misnamed, stale, fake-motion и low-quality diagnostic samples исправляются внутри этого тикета, а не выносятся как «не кодовая мелочь».
- **Альтернативы:** лечить только renderer, оставляя плохой набор тестов.
- **Почему:** плохие samples создают ложные баги и удорожают каждую следующую проверку.

**ADR-4: Repeat-launch stability является входным условием дальнейшего VR-QA**

- **Решение:** дефект повторного запуска рассматривается как blocking prerequisite, а не как фоновая cleanup-задача.
- **Альтернативы:** проверять всё остальное только через полный перезапуск приложения.
- **Почему:** владельцу нужен быстрый повторяемый цикл проверки в одной сессии.

---

## 10. Связи с другими спеками

- **S0322** (bugfix-vr-diagnostic-video-playback) - наблюдаемость отказа `video_360_stereo_sbs.mp4` доказала аппаратный потолок AVC-декодера Quest 3 на `8640×2160`; ветка «замена сэмпла» (§6.2) ведётся здесь. S0322 в `BlockByOtherTask` до этой замены.
- **S0341** (bugfix-debug-notification-theme-crash) - краш отладочного уведомления под non-Material темой VR-диагностики роняет приложение на ERROR-путях и блокирует on-device проверку S0291; должен быть закрыт до следующего device-теста.
- **S0290** - широкий VR quality umbrella, из которого этот тикет забирает owner round 2 findings и превращает их в отдельный следующий фронт работ.
- **S0249** - исходный internal diagnostic immersive path, на котором основан текущий тестовый режим.
- **Dead Colosseum sample note** - absorbed here; проблема dead/misleading colosseum sample отдельно больше не нужна.

---

## 11. Критерии готовности (strategic-level)

1. Повторный запуск `Test Immersive` после выхода из immersive в том же процессе проходит без падения.
2. `diagnostic_360_mono.jpg` остаётся по качеству не хуже текущей owner-approved базы.
3. Лучи контроллеров продолжают идти правильно от обеих рук.
4. HUD по-прежнему можно схватить нижней кнопкой контроллера и перенести в другое место.
5. `diagnostic_180_mono.jpg` продолжает отображаться корректно.
6. `video_360_stereo_sbs.mp4` больше не выглядит как split static frame с белой полосой и воспринимается как настоящее moving 360 stereo video.
7. 180 stereo photo и video references либо дают убедительный объём, либо заменены на такие, которые дают.
8. Left/right verification становится понятной владельцу: он видит однозначный diagnostic cue для каждого глаза на соответствующих stereo samples.
9. `moraine_lake_flat_mono.jpg`, lake stereo variants и `colosseum_flat_mono.jpg` больше не противоречат своим именам и назначению.
10. Слоты, обозначенные как видео, действительно являются полезными motion references для QA, а не статическими заглушками.
11. `video_360_stereo_tb.mp4` заменён или улучшен до owner-acceptable качества; наличие звука считается предпочтительным целевым исходом.
12. `big_buck_bunny_flat_mono.mp4` больше не выбивается вниз по качеству относительно остального набора.
13. «Рябь» на flat image reference либо заметно снижена, либо её причина документирована вместе с owner-acceptable решением.
14. После полного круга текущий диагностический плейлист снова возвращается к `diagnostic_360_mono.jpg`, и ни один locked baseline из §3.1 не регрессирует.
15. После выхода из immersive по кнопке контроллера панель Settings возвращается в HorizonOS на фоне домашнего passthrough-окружения, а не в чёрной XR-сцене.
16. Повторный запуск Test Immersive в той же сессии сразу показывает HUD с именем файла и projection/layout, а не одноцветный прямоугольник без текста.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация выполнена. Следующий шаг: owner Quest 3 verification, затем `/spec-check S0291`.

Фактическая декомпозиция фаз:

- Lifecycle re-entry stabilization
- Stereo truth cues and eye-order verification
- 360 SBS video defect isolation
- Sample library correction and honest naming
- Weak video replacement and quality uplift
- Flat-image shimmer investigation
- Regression verify against locked baselines

## 13. Implementation notes

- Lifecycle re-entry now checks native initialized state separately from running state and force-cleans stale non-running native state before creating a new diagnostic XR session.
- Diagnostic sample provisioning now clears canonical generated outputs, creates readable `L/R` text labels, replaces weak/static motion references with Bino rolling-marbles clips, upgrades flat mono video to the official Blender 1080p trailer, renames the misleading Colosseum slot to `lakeside_flat_mono.jpg`, and removes stale remote canonical/legacy files before `adb push`.
- Static image texture upload now uses mipmap generation, trilinear minification, and conservative anisotropic filtering when supported to reduce flat-image shimmer.
- Validation completed: `setup_test_vr.ps1` exited 0 with `adb` absent and regenerated all 19 canonical samples; noLegal debug build `2.60.5221.845` completed successfully; device provisioning on Quest 3 exited 0 and final `/sdcard/Pictures/FastMediaSorterVrTest` + `/sdcard/Movies/FastMediaSorterVrTest` contain exactly 19 canonical files with matching local/device MD5 hashes.

## 14. Owner verification checklist

1. Exit immersive by controller button, return to settings, then launch `Test Immersive` again in the same app process.
2. Complete the full diagnostic playlist loop and confirm it returns to `diagnostic_360_mono.jpg`.
3. On stereo image/video slots, close one eye at a time and confirm the visible `L/R` cue matches expectation.
4. Re-check locked baselines: 360 mono quality, 180 mono display, controller rays, HUD grab/move, and flat stereo Big Buck Bunny references.
5. Watch flat image references while moving head left/right and report whether shimmer is reduced or still objectionable.

## Last Audit

**2026-05-30 (`/spec-all` resume, review-mode after drift)** — drift-check verdict `DRIFT` (4 commits + inline `S0291:` markers), no prior `## Last Audit`. Findings:

- Original phases 01–05 (rounds 1–9) implemented and committed; all phase checkboxes `[x]`.
- The 2026-05-23 `/spec-update` reopened the spec `BlockNeedUserTest → Tactical`, removed 22 `.kt` debug tags, and added round-10 work (§1.7, Goal #9, §6.8, §6.9, §11.15/§11.16) — but the tactical Phase 06 to carry it existed only as Proposed P-1/P-2.
- **Stale probes cleaned:** two C++ `LOGD("S0291: …")` probes (`diagnostic_xr_runtime.cpp` initSession entry, `xr_session.cpp` static-texture-filtering entry) survived the 2026-05-23 reopen while status was `Tactical` — removed. The `// S0291:` explanatory comment at `xr_session.cpp` 180°-mirror code de-identified to plain English.
- **§1.7 drift:** both round-9 symptoms were observed on the `moveTaskToBack`-alive-Activity exit, which `S0295` (Verified 2026-05-25) replaced with `finish()` + panel-host handoff. Current re-entry creates a fresh Activity. The symptoms must be re-confirmed on the current build.
- **HUD-blank (§6.9 / §11.16):** root cause located — `onRenderThreadSessionReady` re-queued the HUD only for video items. Hardened to re-queue the current item's banner for all media types (Step 06.1, Kotlin-only, idempotent). Round-10 device probe `S0291: session-ready HUD re-queue` inserted. noLegal debug build gate applied.
- **Passthrough-on-exit (§6.8 / §11.15):** NOT closed inside S0291. Native teardown calls `xrDestroySession` from running state with no graceful OpenXR handshake; scene is hard `OPAQUE` with no passthrough layer. Three blockers: exit path owned by `S0295` (Verified); `xr_session.cpp` 1526 LOC > 1500 (Strict Rule 2 blocks in-place handshake without extraction); open design fork (clean session-end vs explicit `XR_FB_passthrough`). Re-routed to a successor ticket + owner decision + Quest 3 observation.
- **Residual gap:** §11.15 (home passthrough after exit) remains open. §11.16 (HUD banner on re-entry) implemented, awaiting owner Quest 3 confirmation.
- **Owner decision 2026-05-30 (diagnostic-first):** no DEFECT A successor ticket is allocated yet. Quest 3 round-10 must first confirm whether §11.15 still reproduces on the current S0295 `finish()` exit path; only then is the clean-handshake-vs-`XR_FB_passthrough` design fork decided and a successor ticket opened.

## Revision History

- **2026-05-22** - created by android-rd-specialist from owner on-device feedback after manual VR retest
- **2026-05-22** - by `/spec-update` (`GPT-5`, focus: structure, completeness, consistency)
  - Applied: 1. Proposed (DISCUSS): 0.
  - Reframed the strategic spec around locked baselines, owner round 2 defects, absorbed sample-note follow-ups, and observable next-step acceptance criteria.
- **2026-05-22** - implemented by `/spec-dev`; moved to owner Quest 3 verification with temporary `S0291:` probes.
- **2026-05-22 22:53 owner round 8 (post-verify deltas, applied while still BlockNeedUserTest)**
  - HUD quad height reduced 1.5x (`0.169m -> 0.113m`), width preserved at `0.3m`. Source: `app_v2/src/vr/cpp/xr_hud_world.cpp::xr_hud_init`.
  - Zoom value (`g.zoom`) now resets to `1.0f` on every `xr_session_set_render_config` invocation so each slide starts at default framing. Source: `app_v2/src/vr/cpp/xr_session.cpp::xr_session_set_render_config`.
  - Added Activity-side re-entry diagnostic probes (`S0291:` on `onCreate`, `proceedWithInitialization`, `surfaceCreated`) so the next logcat captures the full path of a repeat immersive launch and pinpoints where it stalls or short-circuits. Source: `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`.
  - APK: `2.60.5222.259` (noLegalDebug), installed on Quest 3 `2G0YC5ZG5608DL`.
- **2026-05-22 23:14 owner round 9 (re-entry root cause + fix)**
  - Owner retest with v259 logcat (`logs/current.log`, lines 3970..4181) confirmed second immersive launch fully initializes (onCreate, surfaceCreated, render thread starts, OpenXR session created, swapchain allocated, frame loop running) BUT the OpenXR session never leaves `XR_SESSION_STATE_IDLE` (line 4124 + 4128). Owner sees "вечный полёт" - the runtime renders empty frames but never transitions to VISIBLE/FOCUSED, so no content shows.
  - Smoking gun: session 1 emits `nativeOnActivityReady: DiagnosticXrActivity` (logcat line 988) which transitions the session through IDLE -> READY -> ... -> FOCUSED. Session 2 produces NO such event. Quest OpenXR loader stops tracking the `ActivityLifecycleCallbacks` after the first Activity is destroyed, so the second Activity instance is invisible to the loader even though our native bridge correctly replaces the JNI globalref.
  - Fix: keep the `singleTask` `DiagnosticXrActivity` instance alive across immersive exit/enter cycles. Replaced `finish()` in `onRenderThreadExit` with `moveTaskToBack(true)` (fallback to `finish()` when the OS refuses, e.g. last activity in task). On re-entry the existing intent flag `FLAG_ACTIVITY_NEW_TASK` + manifest `launchMode="singleTask"` brings the same Activity instance back to the foreground; `onResume` then drives `maybeStartRenderThread("onResume")` which restarts the render thread against the still-valid native runtime. The Quest loader keeps its Activity binding intact because the Activity object is never destroyed.
  - Source: `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` (`returnToPreviousTaskOrFinish`, `onResume`, `maybeStartRenderThread`).
  - APK: `2.60.5222.322` (noLegalDebug), installed on Quest 3 `2G0YC5ZG5608DL`.
- **2026-05-23** - by `/spec-update` (`claude-sonnet-4.5`, focus: language, structure, verifiability, consistency, completeness, style)
  - Re-opened from BlockNeedUserTest → Tactical; debug tags removed: 22 across `DiagnosticXrActivity.kt` (7), `DiagnosticXrRenderThread.kt` (14), `NativeDiagnosticXrRuntime.kt` (1).
  - Applied: 4. Proposed (DISCUSS): 2.
  - Added §1.7 capturing owner round 9 verification deltas (Settings panel returns to black XR void instead of home passthrough; second immersive launch shows HUD as flat one-colour rectangle without text).
  - Added §2 Goal #9 covering passthrough restoration and HUD persistence across re-entry.
  - Updated §6.7 status to reflect partial closure and added §6.8 (polite OpenXR exit handshake) and §6.9 (HUD persistence across re-entry) as Open research items.
  - Added §11 acceptance criteria #15 and #16 covering passthrough return and HUD-on-re-entry observable outcomes.
- **2026-06-04** - by `/spec-update` (`claude-opus-4.8`, focus: completeness, consistency)
  - Re-opened from BlockNeedUserTest → Tactical after on-device log (Quest 3, noLegalDebug 2.60.6032.327); debug tags removed: 1 (`DiagnosticXrActivity.kt` session-ready HUD re-queue probe).
  - Applied: §6.2 reopened — `video_360_stereo_sbs.mp4` (`8640×2160` H.264) exceeds the Quest 3 hardware AVC decoder (`ERROR_CODE_DECODER_INIT_FAILED` 4001 at decoder init); §11.6 unreachable with this sample. §6.4 reopened — `moraine_lake_flat_mono/sbs.jpg` fail to decode (`Problem decoding into existing bitmap`). §10 linked to S0322 (sample replacement) and S0341 (debug-notification crash blocking device test).
  - Applied: 4. Proposed (DISCUSS): 1 (P-3).

## Proposed Structural Changes

### Proposal P-1 - Add tactical phase 06 for lifecycle round 10 (proposed 2026-05-23 by claude-sonnet-4.5)

**Status:** Applied 2026-05-30 (`/spec-all`) — `PHASE_06__lifecycle-round10-exit-and-hud-rebind.md` created (Step 06.1 done, Step 06.2 deferred).
**Affected:** §12 Phase decomposition; tactical INDEX; new file `PHASE_06__lifecycle-round10-exit-and-hud-rebind.md`
**Rationale:** §1.7 introduces two concrete defects (black XR void after exit, blank HUD rectangle on second launch) that map to §6.8 and §6.9 Open research items and §11.15/§11.16 acceptance criteria. They cannot be closed without code changes and therefore need their own tactical phase.
**Suggested edit:**
> §12 current list ends with "Regression verify against locked baselines" → append "Lifecycle round 10: polite OpenXR exit handshake and HUD re-queue on session re-entry".

### Proposal P-2 - Reopen tactical INDEX from Done to In Progress for round 10 (proposed 2026-05-23 by claude-sonnet-4.5)

**Status:** Applied 2026-05-30 (`/spec-all`) — INDEX set to `In Progress`, `Phases: 5 / 6 done`, Phase 06 row + Change Log entry added.
**Affected:** `PLAN/S0291_vr_diagnostic_stereo_and_lifecycle_round2/INDEX.md` (`Status:` field, `Phases:` counter, Phase Overview table, Change Log)
**Rationale:** Strategic status is now Tactical with two new Open research items and two new acceptance criteria. The tactical INDEX still reads `Status: Done`, `Phases: 5 / 5 done`. After P-1 lands, INDEX must add Phase 06 row, drop `Status:` to `In Progress`, bump `Phases:` to `5 / 6 done`, and append a Change Log entry. Per `/spec-update` rules edits to the tactical INDEX from this skill are DISCUSS-only.
**Suggested edit:**
> INDEX `Status: Done` → `Status: In Progress`; `Phases: 5 / 5 done` → `Phases: 5 / 6 done`; new row `| 06 | lifecycle-round10-exit-and-hud-rebind | 01 | ⬜ Not started | 0/N | PHASE_06__lifecycle-round10-exit-and-hud-rebind.md |`; Change Log entry `2026-05-23 - reopened for round 10 after owner verification deltas; see strategic §1.7`.

### Proposal P-3 - Add tactical phase for round-11 reopen: device-playable 360 SBS sample + flat-image decode fix (proposed 2026-06-04 by claude-opus-4.8)

**Status:** Proposed
**Affected:** §12 Phase decomposition; tactical INDEX; new phase file
**Rationale:** On-device log 2026-06-04 reopened §6.2 and §6.4. Two concrete defects need code/provisioning changes and therefore a dedicated tactical phase: (1) `video_360_stereo_sbs.mp4` at `8640×2160` H.264 is undecodable on the Quest 3 hardware AVC decoder — provisioning must supply a device-playable 360 SBS reference (lower resolution or HEVC/AV1) so §11.6 becomes reachable; (2) `moraine_lake_flat_mono/sbs.jpg` fail to decode (`Problem decoding into existing bitmap`) — the flat-image decode path or the provisioning output must be fixed. Device verification of this phase is itself blocked by S0341 (debug-notification crash on ERROR paths).
**Suggested edit:**
> §12 phase list → append "Round 11: device-playable 360 SBS sample replacement and flat-image decode fix (blocked on S0341 for device test)".
