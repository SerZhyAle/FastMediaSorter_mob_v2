# VR Problem List — Quest 3 Session 2026-04-26

Log: `logs/fastmediasorter_20260426_050014.log` · Session: 05:00–05:14 · Version: 2.65.7260.457-VR-DEBUG

---

## Контекст сессии

Пользователь тестировал VR-просмотр видео на Meta Quest 3. Открывались файлы:
- `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` (локальный, 7K VR180 fisheye SBS)
- `Boersensaal_Hamburg_stereo_360_8K_25s.webm` (локальный, 360° SBS)
- `Ess-Na-Crub_Waterfall_360_mono_10s.webm` (локальный, 360° mono)
- `The.Fall.Guy.2024.2160p.UHD.BDREMUX.HEVC-Нечипорук.mkv` (локальный, 2D)
- `VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4` (SMB, 3dv = OU TAB стерео)
- `Transcendence(2014)3D-halfOU(Ash61)Dub.IPTV.mkv` (SMB, half-OU)

---

## P1 — Критические проблемы (неверная картинка или полный отказ)

### P1-1: VR180 Fisheye отображается неверно — видна полусфера вместо стерео
**Симптом:** 18VR-файл (7168×3584, `3dh` суффикс) детектируется как `VR180_FISHEYE_SBS` и рендерится через слой `EQUIRECT_2`. Пользователь видит полусферу 180° без правильного стерео-разделения.  
**Причина:** `VR180_FISHEYE_SBS` — это fisheye-проекция (круговые линзы), а `EQUIRECT_2` — equirectangular слой OpenXR. Это разные форматы. Для fisheye нужна предварительная обработка (undistort / remapping) перед подачей на equirect-слой, либо специальный слой. Такой обработки в `VrStereoRenderer` нет — `StereoVideoProcessor: buildGlEffect → no effect for MONO` выдаётся вместо fisheye-шейдера.  
**Лог:** `[849] VrPlayerActivity: renderVrFrame #1 layer=EQUIRECT_2 stereo=VR180_FISHEYE_SBS`; `[748] StereoVideoProcessor: buildGlEffect → no effect for MONO`  
**Итог:** Fisheye-контент физически не может отображаться корректно без remapping-шейдера.

---

### P1-2: OU/TAB стерео (`3dv`) не поддерживается — автоматический откат в плоское кино
**Симптом:** `VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4` детектируется как `OU` и тут же:  
`DefaultVrLayerFactory: unsupported stereo=OU renderMode=CINEMA, falling back to cinema quad`  
Файл открывается в режиме `QUAD_CINEMA` — плоский экран, без стерео.  
**Причина:** `DefaultVrLayerFactory` не имеет реализации для Over-Under (TAB) стерео в OpenXR рендерере. SBS поддерживается через UV offset в шейдере, OU — нет.  
**Лог:** `[10101] W DefaultVrLayerFactory: unsupported stereo=OU renderMode=CINEMA, falling back to cinema quad`  
**Затронуто:** Все файлы с суффиксами `3dv`, `OU`, `TAB`, `halfOU`.

---

### P1-3: Лучи контроллеров и рук не отображаются в VR-пространстве
**Симптом:** Пользователь не видит лучей от контроллеров и рук. Нет возможности "прицелиться" в элементы управления в VR-пространстве.  
**Причина:** В OpenXR native-слое инициализируется hand tracking (`initHandTracking: ready left=1 right=1 aim=1`) и aim-указатель (`pointer=0x1e5` в input callback), но визуализация лучей (ray line rendering) не реализована. В нативном рендерере нет кода отрисовки controller ray / hand aim ray. Все пользовательские взаимодействия в текущей реализации — это 2D-тач по Android overlay поверх VR-картинки, а не ray-cast в 3D.  
**Лог:** `[816] OpenXrNative: initHandTracking: ready left=1 right=1 aim=1`; все UserAction-события = `TOUCH: action=DOWN/UP` (2D тач), нет ray-hit событий.

---

### P1-4: OpenControls не открывает полноценный видеоконтрол — только минимальный HUD-стрип
**Симптом:** Кнопка X контроллера (type=3 → `OpenControls`) нажималась 4 раза подряд. Каждый раз происходит только `VrHudRenderer: setVisible(true)` + `VrHudRenderer: first HUD bitmap upload succeeded (1024x256)`. Никакого диалога VideoControl, никакой панели управления воспроизведением не появляется.  
**Причина:** `OpenControls` команда в `VrPlayerActivity` вызывает только `VrHudRenderer.setVisible(true)`, который показывает тонкую полоску 1024×256 пикселей (4:1). Это не полноценный HUD с перемоткой, треком и другими контролами. `VideoControlDialog` (панельного плеера) в иммерсиве не вызывается.  
**Лог:** `[6816-6829] VrInput[xr]: type=3 → OpenControls` × 4; `VrHudRenderer: first HUD bitmap upload succeeded (1024x256)` × 4.  
**Ожидалось:** Полноценная HUD-панель с паузой, перемоткой, звуком, информацией о треке.

---

### P1-5: Видеоконтрол из иммерсива недоступен — сообщение в UI не объяснено в логах
**Симптом:** Пользователь видит сообщение "видеоконтроль из иммерсив недоступен" в приложении.  
**Причина:** В `VrPlayerActivity` нет маршрута к полноценному VideoControlDialog. При нажатии X (OpenControls) показывается только HUD-стрип. Для получения полного контрола нужно выйти из иммерсива — это и фиксирует UI в виде тоста/текста.  
**Лог:** косвенно — `[6816-6829]` OpenControls × 4 без открытия диалога.

---

## P2 — Серьёзные проблемы (неверное поведение)

### P2-1: Детекция стереоформата для `Boersensaal_Hamburg_stereo_360_8K_25s.webm` неверна
**Симптом:** Файл в папке `360_sbs` с именем `stereo_360` детектируется как `EQUIRECT_360_MONO`. Через секунду режим самопроизвольно меняется на `EQUIRECT_360_SBS` по метаданным контейнера.  
**Причина:** `StereoDetector` разбирает имя файла по суффиксам. Слово `stereo` без явного `sbs`/`3dh`/`lr` суффикса не даёт сигнал SBS. Детектор видит `360_8K_25s` → mono. Только после `onTracksChanged` (метаданные webm) режим корректируется.  
**Лог:** `[1720] StereoDetector: filename match → EQUIRECT_360_MONO`; `[1946] VideoPlayerManager: onTracksChanged → detected stereo=EQUIRECT_360_MONO` (остаётся!); `[1805] VrPlayerActivity: stereoMode → EQUIRECT_360_SBS` (изменился через другой путь).  
**Итог:** Добавить `stereo` как синоним SBS в детектор. Также возможна рассинхронизация: onTracksChanged даёт MONO, а stereoMode становится SBS через другой путь — два источника конфликтуют.

---

### P2-2: Утечка стереорежима между файлами при навигации в иммерсиве
**Симптом:** После просмотра `Boersensaal` (EQUIRECT_360_SBS) следующий файл `Ess-Na-Crub_Waterfall_360_mono_10s.webm` получает `requested=EQUIRECT_360_SBS` вместо `AUTO/UNKNOWN`.  
**Причина:** При `nextFile()` предыдущий стерео-режим не сбрасывается до маршрутизации. `PlayerStereoModeCoordinator` записывает `UNKNOWN` для нового файла, но `requested` в момент вызова `resolvePlaybackRoute` берётся из состояния предыдущего файла.  
**Лог:** `[1967] route decision file=Ess-Na-Crub...webm type=VIDEO requested=EQUIRECT_360_SBS effective=EQUIRECT_360_SBS` — при имени файла содержащим `360_mono`.  
**Итог:** Новый файл воспроизводится в неверном стерео режиме.

---

### P2-3: STANDARD_PANEL_FALLBACK при навигации к 2D-файлу разрушает XR-сессию
**Симптом:** При нажатии PreviousFile в иммерсиве (первый файл → переход к последнему — "Приключения Паддингтона 2.mkv" = 2D фильм): приложение полностью уничтожает XR-сессию и запускает стандартный PlayerActivity.  
**Причина:** `resolvePlaybackRoute` для MONO файла возвращает `STANDARD_PANEL_FALLBACK`. Логика: если файл не VR → запустить стандартный плеер. Но пользователь ожидает Cinema-режим (плоский экран в VR), а не выход из иммерсива.  
**Лог:** `[979] VrPlayerActivity: launching standard PlayerActivity fallback`; `[980] W VrPlayerActivity: forceStopVrPlayback reason=standard-player-fallback:stereo-mode`  
**Ожидалось:** 2D-контент открывается в QUAD_CINEMA режиме прямо внутри VR, без разрушения XR-сессии.

---

### P2-4: TogglePausePlay срабатывает 3 раза подряд за 2 секунды
**Симптом:** Одно нажатие trigger → три команды `TogglePausePlay` (05:10:15, 05:10:16, 05:10:17). Видео переходит: пауза → воспроизведение → пауза → воспроизведение.  
**Причина:** Отсутствие дебаунса на вход type=0. Тригге джойстика при нажатии и удержании генерирует несколько событий.  
**Лог:** `[3327,3402,3471] handling VR command TogglePausePlay` × 3 подряд.

---

### P2-5: SMB воспроизведение завершается ошибкой (errorCode=2000, watchdog timeout)
**Симптом:** При открытии `Transcendence(2014)3D-halfOU.mkv` через SMB:  
1. `SmbDataSource.open: watchdog timeout after 12000ms — invalidating ExoPlayer pooled connection`  
2. `VideoPlayerManager: Playback error — errorCode=2000` (дважды подряд)  
3. `Error reading from network at position 0, size 2048` (трижды)  
**Причина:** SMB соединение зависает или пул соединений содержит протухшее подключение. После invalidation повторные попытки чтения с позиции 0 (retry) также падают.  
**Лог:** `[7773] E SmbDataSource.open: watchdog timeout after 12000ms`; `[7799,9425] E VideoPlayerManager: Playback error — errorCode=2000`.

---

### P2-6: Хаптик-фидбэк контроллеров не работает
**Симптом:** При каждом событии, которое должно давать вибрацию: `nativeTriggerHaptic: xrApplyHapticFeedback returned -16`.  
**Причина:** Код ошибки -16 в OpenXR = `XR_ERROR_FEATURE_UNSUPPORTED` или некорректный handle хаптик-актуатора. Возможно, action для haptic не создан или action path неверный.  
**Лог:** `[1807] D OpenXrNative: nativeTriggerHaptic: xrApplyHapticFeedback returned -16`.

---

## P3 — Средние проблемы (деградация UX)

### P3-1: INTERACTION_PROFILE_CHANGED (type=52) не обрабатывается
**Симптом:** При каждой XR-сессии: `pollEvents: unhandled event type=52`.  
**Причина:** Событие смены профиля взаимодействия (переключение контроллер ↔ руки) не обрабатывается в `pollEvents`. Если пользователь убирает контроллеры и переходит на hand tracking, приложение не перестраивает input bindings.  
**Лог:** `[1028,2193,3583,4316,5045] D OpenXrNative: pollEvents: unhandled event type=52`.

---

### P3-2: VrHudRenderer race condition при выходе из иммерсива
**Симптом:** При Exit/ToggleImmersiveMode: `VrHudRenderer: createHudSwapchain(1024 x 256) returned false` (3 раза).  
**Причина:** `VrHudRenderer.setVisible(true)` вызывается после `OpenXrSessionManager.release()`. К этому моменту XR-сессия уже destroyed и создание swapchain невозможно. Race condition между Kotlin-кодом и native XR.  
**Лог:** `[3603] W VrHudRenderer: createHudSwapchain(1024 x 256) returned false`; аналогично `[4328], [5052]`.

---

### P3-3: Touch Pro Controller warning при каждой XR-сессии (6 раз)
**Симптом:** `setupActionSet: suggest /interaction_profiles/oculus/touch_pro_controller failed: -22` — при каждой инициализации XR.  
**Причина:** Приложение пытается зарегистрировать bindings для Touch Pro Controller, которого нет на Quest 3. Сам код помечает это как `non-fatal if profile unsupported`, но warning появляется при каждой сессии.  
**Лог:** `[814,1864,2912,4205,4933,10311] W setupActionSet: suggest /interaction_profiles/oculus/touch_pro_controller failed: -22`.  
**Решение:** Убрать регистрацию Touch Pro профиля или подавить warning (уже есть пометка non-fatal, достаточно понизить до D-уровня).

---

### P3-4: OpenFileOps нажимается 3 раза за 5 секунд без результата
**Симптом:** type=2 (`OpenFileOps`) срабатывает 3 раза подряд (05:11:04, 05:11:06, 05:11:09) — пользователь не понимает что происходит и жмёт снова.  
**Причина:** Нет визуального фидбэка что команда принята, и нет дебаунса. `VrHudRenderer` показывается/скрывается, но OpeFileOps-диалог (если он вызывается) не отображается в VR-пространстве.  
**Лог:** `[5709-5723] VrInput[xr]: type=2 → OpenFileOps` × 3.

---

### P3-5: Первый запуск XR-сессии ждёт 1093ms (холодный старт)
**Симптом:** `BaseActivity.setupViews[VrPlayerActivity]: START (waited 1093ms for first frame)`.  
**Причина:** Инициализация OpenXR + создание EGL context + SwapChain + GL ресурсов делается последовательно. Последующие запуски быстрее (144-201ms).  
**Лог:** `[614] D VrPlayerActivity: setupViews START (waited 1093ms)`.

---

### P3-6: Режим `stereoMode=MONO` устанавливается несколько раз до завершения маршрутизации
**Симптом:** `StereoVideoProcessor: buildGlEffect → no effect for MONO` + `applyConfiguredVideoEffects — no effects, pipeline already clean, skipping` — 3-5 раз подряд до того как stereoMode становится известен.  
**Причина:** Несколько асинхронных триггеров (`stereo-mode`, `player-state`, `onResume`, `route-decision`) вызывают `resolvePlaybackRoute` и `applyStereoEffect` с промежуточным состоянием `MONO/UNKNOWN`. Pipeline сбрасывается несколько раз впустую.  
**Лог:** `[697,713,748] StereoVideoProcessor: buildGlEffect → no effect for MONO` до определения режима.

---

## P4 — Функциональные пробелы (отсутствующий функционал)

### P4-1: Нет индикатора текущего стерео-режима в HUD — нет кнопки ручного переключения
**Симптом:** Пользователь видит неверную картинку (MONO вместо SBS, или fisheye как equirect), но нет способа переключить формат прямо в иммерсиве.  
**Причина:** VrHudRenderer показывает только 1024×256 strip. Нет кнопки "Format" или UI для `StereoMode` в HUD.

---

### P4-2: 2D-файлы в иммерсиве требуют выхода из VR — нет Cinema-режима внутри сессии
**Симптом:** `STANDARD_PANEL_FALLBACK` при любом MONO-контенте — приложение уходит во flatscreen PlayerActivity. Нет Cinema mode (QUAD_CINEMA) без выхода из VR.  
**Причина:** `resolvePlaybackRoute` возвращает `STANDARD_PANEL_FALLBACK` для `plain-2d-content`. Нет опции "показать в VR на виртуальном экране".  
**Частично реализовано:** `QUAD_CINEMA` layer тип существует (The Fall Guy открылся через `forceImmersiveThisLaunch` или `user-forced-immersive`). Нужна кнопка "Force Cinema" для 2D в браузере.

---

### P4-3: Нет перемотки / управления позицией из VR-иммерсива
**Симптом:** В VR-иммерсиве доступны: TogglePause, VolumeStep+/-, NextFile, PreviousFile, Exit, ToggleImmersiveMode, OpenFileOps, ShowCheatsheet, OpenControls. Нет перемотки (seek), нет выбора дорожки, нет субтитров.  
**Причина:** Нет соответствующих VrCommand типов или они не реализованы в `VrPlayerActivity`.

---

### P4-4: Slideshow auto-advance в иммерсиве не учитывает стерео-режим следующего файла
**Симптом:** `NEXT triggered by: Playback ended (slideshow)` → следующий файл получает `requested=EQUIRECT_360_SBS` (из предыдущего файла) вместо AUTO — проблема P2-2 возникает и при автоматическом slideshow.

---

## Дополнительные наблюдения

| # | Наблюдение | Строки лога |
|---|-----------|-------------|
| O1 | `Microgestures=0` — микрожесты рук не включены (расширение есть, но disabled) | [784] |
| O2 | `HandAim(META=0 FB=1)` — используется FB_hand_aim, а не META_hand_aim | [783] |
| O3 | `REFERENCE_SPACE_CHANGE_PENDING` (type=40) — unhandled при каждой сессии | [994-997] |
| O4 | `JobCancellationException` при уничтожении VrPlayerActivity с активным корутином | [1134-1137] |
| O5 | При VRHush SMB: `Closed SMB data source (totalRead=48 bytes)` — файл прочитан лишь 48 байт, потом соединение закрыто; воспроизведение прервалось | [10319] |
| O6 | `SmbDataSource: Pooled connection failed, retrying with fresh` — пул хранит мёртвые соединения | [10204] |

---

## Итог

Из 14 зафиксированных проблем:
- **P1** (критические, неверная картинка): 5 проблем — fisheye рендеринг, OU не поддерживается, нет лучей, нет полного HUD
- **P2** (серьёзные, неверное поведение): 6 проблем — детекция стерео, утечка режима, FALLBACK, дебаунс, SMB, хаптик
- **P3** (средние, деградация UX): 6 проблем — profile events, race condition HUD, Touch Pro warning, дебаунс
- **P4** (отсутствующий функционал): 4 пробела — индикатор формата, cinema mode, перемотка в VR, slideshow

Наиболее критичные для немедленного исправления: **P1-1** (fisheye), **P1-2** (OU), **P1-4** (пустой OpenControls), **P2-3** (FALLBACK разрушает сессию), **P2-4** (дебаунс trigger).
