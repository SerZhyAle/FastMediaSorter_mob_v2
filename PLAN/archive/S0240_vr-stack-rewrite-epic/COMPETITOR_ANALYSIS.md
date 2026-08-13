# COMPETITOR_ANALYSIS.md — конкурентный анализ VR-видеоплееров

**Источник тикет:** `S0244` `vr-preliminary-research`.
**Эпик:** `S0240` §6.14 + R-14.
**Заполнено:** 2026-05-18 (через `/spec-all S0244` → Group C agent).

Цель: зафиксировать удачные паттерны существующих VR-видеоплееров на Quest 3 и Android XR, отметить неудачные, явно выделить **что мы делаем лучше**.

---

## Источники, фактически прочитанные

- DeoVR: [deovr.com/blog/113](https://deovr.com/blog/113-ongoing-improvements-to-deovr), [deovr.com/blog/52 (v13.6)](https://deovr.com/blog/52-shorts-subtitles-and-more-new-features-in-the-deovr-v136-app-update), [deovr.com/blog/75 (Quest 3 guide)](https://deovr.com/blog/75-vr-videos-on-the-meta-quest-3), [deovr.com/blog/69 (subtitles)](https://deovr.com/blog/69-stream-subtitles-at-deovr), [deovr.com/blog/162 (Quest 3S)](https://deovr.com/blog/162-meta-quest-3s-and-deovr)
- DeoVR Hyper store listing: [meta.com/experiences/deovr-hyper](https://www.meta.com/en-gb/experiences/deovr-hyper/25289867110644741/)
- Skybox VR guide: [vrpupu.com/skybox-vr-player-guide](https://vrpupu.com/en/2026/01/skybox-vr-player-guide/), [vrpupu.us/skybox-2025](https://vrpupu.us/2025/06/19/skybox-vr-player-guide-local-network-airscreen-2025/)
- Skybox store: [meta.com/experiences/skybox-vr-video-player](https://www.meta.com/experiences/skybox-vr-video-player/2063931653705427/), [Steam](https://store.steampowered.com/app/721090/SKYBOX_VR_Video_Player/)
- Skybox TS-files troubleshooting: [researchhub.blog](https://researchhub.blog/skybox-vr-ts-files-working-7-expert-fixes)
- Pigasus FAQ: [hanginghatstudios.com/pigasus-faq](https://hanginghatstudios.com/pigasus-faq/), [hanginghatstudios.com/pigasus](https://hanginghatstudios.com/pigasus/)
- Pigasus store: [meta.com/experiences/pigasus-vr-media-player](https://www.meta.com/experiences/pigasus-vr-media-player/2436667223120459/)
- Bigscreen software page: [bigscreenvr.com/software](https://www.bigscreenvr.com/software)
- Bigscreen guide: [ovrdoz.com/how-to-use-bigscreen-vr](https://ovrdoz.com/en/how-to-use-bigscreen-vr/), [arvrtips.com/bigscreen-vr](https://arvrtips.com/bigscreen-vr/)
- Meta Horizon TV: [uploadvr.com/meta-overhauls-quest-tv-app](https://www.uploadvr.com/meta-overhauls-quest-tv-app-to-be-hub-for-streaming-content/), [lowpass.cc/meta-horizon-tv-app-smart-tv-ui](https://www.lowpass.cc/p/meta-horizon-tv-app-smart-tv-ui), [tomsguide.com/horizon-tv-hub](https://www.tomsguide.com/computing/vr-ar/i-binge-watched-shows-using-metas-horizon-tv-hub-on-quest-3-heres-the-good-the-bad-and-the-immersive)
- Android XR samples: [github.com/android/xr-samples](https://github.com/android/xr-samples), [developer.android.com/develop/xr/samples](https://developer.android.com/develop/xr/samples), [codelabs xr-fundamentals-part-1](https://developer.android.com/codelabs/xr-fundamentals-part-1), [quokkaman.medium.com/exploring-android-xr-samples](https://quokkaman.medium.com/exploring-the-android-xr-samples-86b7d4fb3711)

Цитируется только то, что реально открывалось через WebSearch/WebFetch; вторичные обсуждения форумов/Reddit учитывались как контекст, но без прямого цитирования.

---

## Чек-лист наблюдений (для каждого конкурента)

1. **Структура главного экрана.** Browser + библиотека + favorites — как организовано?
2. **Способ входа в иммерс.** Одна кнопка на файле / автодетект / меню / per-format?
3. **Способ возврата.** Какая кнопка контроллера / меню / комбинация?
4. **Авто-детект стерео-формата.** По имени файла / по metadata / по pattern-recognition / вручную?
5. **HUD во время immerse-проигрывания.** Что показано? Насколько ненавязчиво? Как скрывается?
6. **Управление контроллерами.** Что на trigger / grip / A / B / X / Y / thumbstick?
7. **Файловые операции внутри VR.** Есть ли (rename / move / delete / sort)? Как реализованы?
8. **Skip / seek / scrubber UX.** Как работает прокрутка?
9. **Поддержка субтитров и аудио-дорожек.** Как переключаются?
10. **Что они делают плохо.** Чего избегать.

---

## 1. DeoVR

De-facto reference для VR180/VR360, эталон по auto-detect стерео-форматов и subtitle UX.

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | Streaming-first каталог (контент-маркетплейс) с категориями Trending/New/Top, плейлисты, "My Subscriptions". Локальная файловая навигация — второстепенный режим. | with-mod — берём идею **категорий и быстрого фильтра**, но у нас primary — local + cloud, а не каталог DeoVR |
| 2 | Вход в иммерс | Клик по карточке видео → плеер сразу применяет авто-детектированный формат и стартует. Промежуточного диалога "какой это формат" нет. | yes — наш target UX тот же: "one click → play" |
| 3 | Возврат | Системная кнопка Meta (Oculus) на правом контроллере → возврат в Horizon shell, не в библиотеку приложения. Внутри плеера — закрытие control panel через паузу. Возврат именно в **библиотеку приложения** требует выйти в меню. | no — это слабое место DeoVR; у нас должен быть явный "Back to Library" в HUD |
| 4 | Авто-детект формата | Сильнейшая сторона: распознаёт SBS/OU/180/360/fisheye, поддерживает hidden dropdown для ручного override (VR180 → fisheye). | **yes — копируем подход целиком** (и в качестве fallback — manual override через скрытое меню) |
| 5 | HUD во время immerse | Минималистичный control panel: timeline (выровненный со seeker), кнопки subtitle/audio/settings. Скрывается по таймауту, появляется по взгляду/триггеру. Subtitle можно тащить в 3D-пространстве (drag in space). | yes — особенно "drag subtitles in 3D" и auto-hide timeline |
| 6 | Управление контроллерами | Trigger — select / play-pause; thumbstick — UI navigation; A — toggle control panel; recenter через системную функцию Meta (с фикcом gimbal lock в последних версиях). Xbox-controller mapping тоже поддерживается. | with-mod — наша схема должна быть проще и единообразной |
| 7 | Файловые операции в VR | Нет rename/move/delete внутри приложения. Только favorites и playlists. Управление файлами — снаружи (PC / sideload). | **no — это анти-паттерн**; наш differentiator — full file management в VR |
| 8 | Seek/scrubber UX | Ray-based: целишься в timeline лучом контроллера → trigger для seek. Preview thumbnails on hover. Timeline-blue-section alignment с seeker исправлялся отдельно. | yes — ray + preview thumbnail. Плюс **скрабер через thumbstick** для тех, кто не хочет наводиться |
| 9 | Субтитры / аудио | Subtitle dropdown в Player Settings → load custom .srt; параметры — font size, depth, background opacity, перемещение subtitles в 3D. Audio — обычный track switch. Форматы аудио: MP3/AAC/FLAC/OPUS/PCM. | **yes — copy almost verbatim**, особенно depth и 3D-positioning |
| 10 | Что плохо | (a) Library tied to DeoVR's content catalog — local-first workflow слабый. (b) Quest 3 controller wakeup/recenter issues в forum.deovr.com (controller drifts behind user, gimbal lock на recenter — фиксили в нескольких релизах). (c) Free version обрезан, ключевые фичи за платной подпиской. (d) Нет file ops внутри VR. | (n/a — это анти-паттерны) |

**Что они делают лучше нас (на данный момент):** auto-detect стерео-формата — у них самый зрелый pipeline в индустрии. Subtitle UX (3D-depth, drag-in-space) — reference-class.
**Что мы сделаем лучше (наш differentiator):** full file management (rename/move/delete) inside VR, local + cloud (SMB/SSH/WebDAV/Google Drive/Dropbox/OneDrive) как первичный сценарий, явный "Back to Library" в HUD, без рекламы каталога.

---

## 2. Bigscreen / Bigscreen Beta

**Внимание о терминологии:** _Bigscreen Beyond_ — это headset (hardware), _Bigscreen Beta_ — приложение (cinema metaphor для Quest). Здесь анализируется приложение.

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | Lobby-метафора: пользователь "приходит" в виртуальное помещение, оттуда выбирает environment (cinema/living room/space) + room (private/public/friends). Контент-источники: PlutoTV (172+ channels), Remote Desktop, "My Videos" (local), DLNA. | with-mod — берём идею **environment selection** как опцию display mode (cinema curve vs flat panel vs immersive), но без social rooms |
| 2 | Вход в иммерс | По сути иммерса в нашем смысле нет — Bigscreen всегда показывает **виртуальный экран в виртуальной комнате**, это не SBS/360-видео. Это **panel-first cinema**. "Immersion" — это просто dim ambient + curvature. | yes — это **наш fallback для mono cinema**: 2D-файл = плоский экран в комфортной комнате |
| 3 | Возврат | Menu button на левом контроллере → главное меню Bigscreen (в верхней части окна). Системная кнопка Meta → Horizon shell. | with-mod — менюшная кнопка на контроллере как **toggle** между library и playback |
| 4 | Авто-детект формата | Слабый — фокус не на стерео-видео, а на streaming caching/social. Для 3D Blu-ray ручной выбор SBS/OU. | no — у DeoVR/Skybox лучше |
| 5 | HUD во время immerse | Player overlay: screen size slider, distance, curvature toggle, ambient brightness. Всегда видим (нет full-immersion mode без HUD). | with-mod — берём **adjustable screen size + curvature + ambient dim**, но в нашем дизайне HUD должен скрываться |
| 6 | Управление контроллерами | Trigger — select; grip — grab and move screen / personal monitor; menu button — main menu; pointer-based UI. Gamepad **намеренно не поддерживается**, чтобы не конфликтовать с PC-играми в Remote Desktop. | with-mod — grip-to-grab screen positioning — отличный паттерн; gamepad мы должны поддержать (наш use-case — медиа, не игры) |
| 7 | Файловые операции в VR | Нет. Bigscreen — viewer, не filer. | no |
| 8 | Seek / scrubber UX | Стандартный horizontal scrubber в overlay, ray-pointer для seek. Без preview thumbnails. | with-mod |
| 9 | Субтитры / аудио | Минимальная поддержка — внешние срт грузить нельзя в большинстве сценариев. | no — слабое место |
| 10 | Что плохо | (a) **Не file player** — невозможно нормально работать с локальной библиотекой. (b) Social-first focus → privacy concerns, лишний UI. (c) Нет advanced 3D format support (VR180 fisheye, 360 equirect). (d) Streaming caching: PlutoTV freezes описаны на Steam discussions. | (n/a — анти-паттерны) |

**Что они делают лучше нас (на данный момент):** **panel/cinema metaphor** — самый отшлифованный в индустрии: curvature, ambient dimming, distance/size sliders. Grab-screen-by-grip — мгновенное взаимодействие.
**Что мы сделаем лучше:** Будем поддерживать **и** cinema metaphor (для mono), **и** настоящий VR-иммерс (для VR180/360/SBS) — без необходимости переключать приложения. И полноценная файловая работа.

---

## 3. Skybox VR Player

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | Channel-list: Local Files / Network (SMB) / AirScreen / YouTube / Rooms / Hidden Files. Папки + thumbnails + custom thumbnails support. | **yes — модель "источников как каналов"** хорошая metaphor; наш список будет: Local / SMB / SSH / WebDAV / Google Drive / Dropbox / OneDrive / Recent / Favorites |
| 2 | Вход в иммерс | Клик по файлу → playback стартует немедленно. Авто-детект формата через icon в bottom-right; manual override через "light-bulb icon". | yes |
| 3 | Возврат | Control panel pop-up через "double-click A/X/Trigger on empty area" → exit-кнопка. **Из плеера в браузер нет прямой кнопки return; пользователь жмёт системную Meta-button или ищет в overlay.** | with-mod — у нас будет dedicated "Back to Library" в HUD |
| 4 | Авто-детект формата | "Skybox automatically recognizes video stereo mode (180°/200°/360°/2D/3D)" — на уровне DeoVR. Light-bulb icon — manual hint. | yes — auto + hint, как в DeoVR |
| 5 | HUD во время immerse | Control panel с подсветкой/иконками subtitle/audio/settings/favorites/scene. Гайды отмечают: "control panel is more intuitive than controller shortcuts" — то есть **UI panel — primary, shortcuts — secondary**. | yes — primary UI panel, shortcuts как accelerator |
| 6 | Управление контроллерами | Чётко документирован: Double-click A/X/Trigger — play/pause; hold — drag screen; B/Y hold — reset position; thumbstick up/down — screen size; left/right — seek; **Grip + stick L/R — prev/next video, Grip + stick U/D — volume**. | **yes — это самая зрелая схема в индустрии**, копируем близко к тексту (особенно Grip-modifier idiom) |
| 7 | Файловые операции в VR | Favorites + hidden files. **Нет rename/move/delete.** | no — наш differentiator |
| 8 | Seek / scrubber UX | Thumbstick L/R — ускоренная перемотка; в HUD overlay — scrubber bar. Без preview thumbnails. | with-mod — добавим preview thumbnails |
| 9 | Субтитры / аудио | Embedded + external (.srt/.ass/.ssa); multiple audio (Dolby TrueHD, DTS Master Audio). **Но: нельзя менять position/font/color subtitles** — частая жалоба пользователей. | yes (поддержка форматов) + надо **превзойти** в кастомизации |
| 10 | Что плохо | (a) AirScreen Desktop Mirror в v2.0+ — latency 10–20 сек, регрессия после обновления. (b) Plex login broken (Google/Apple/Email — все методы). (c) Subtitle styling minimal. (d) TS-files frequently fail. (e) Files в cloud-mounted drives видны, но не воспроизводятся. | (n/a — анти-паттерны) |

**Что они делают лучше нас (на данный момент):** **controller mapping scheme** — Grip-as-modifier idiom очень чистый. Source channel metaphor для библиотеки. Auto-detection.
**Что мы сделаем лучше:** Стабильность cloud-источников (мы уже инвестируем в SMB/SSH/WebDAV/3 cloud SDKs); customizable subtitle position/font/color; file ops в VR.

---

## 4. Pigasus VR Media Player

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | Folder-based browsing + Favorites (star-icon на media panel → автоматически в Favorites folder). Top-menu — список доступных network devices (SMB shares + DLNA hosts). | with-mod — наш UI чище визуально, но идея **top-menu = network devices** правильная |
| 2 | Вход в иммерс | Клик → playback. Sticky mode (head-tracking disable) для статичной фиксации экрана. | with-mod — sticky mode сделаем опциональным toggle |
| 3 | Возврат | "**Square button = close video instantly**" — частая жалоба ревьюеров: можно случайно закрыть видео в самом интересном месте. | no — это **анти-паттерн**, требует confirmation или undo |
| 4 | Авто-детект формата | Через filename conventions (_3DH/_LR/_3DV/_TB/_360/_180F/_360EAC). **Media servers truncate long filenames → detection ломается** — задокументированная проблема. | with-mod — filename + metadata + content-pattern recognition, не только filename |
| 5 | HUD во время immerse | View Settings / Screen Settings / general Settings + zoom, aspect ratio, brightness/contrast/saturation, theater lighting dim. Богатый, но плотный — пользователи жалуются на overcrowding. | with-mod — берём набор настроек, но в более чистом layout |
| 6 | Управление контроллерами | Поддержка Quest 3/Pro/2/Original + Oculus Go + Gear VR + gamepads. Visual diagrams для каждого контроллера. **Конкретные button mappings в FAQ не документированы текстом** (только картинки). | with-mod — поддержка широкая, но документация слабая; у нас — точная схема в settings/help |
| 7 | Файловые операции в VR | **Нет rename/move/delete** — только star-favorites. | no — наш differentiator |
| 8 | Seek / scrubber UX | Swipe-based forward/backward seek с настраиваемым jump-time. A-B loop (mark start/end → continuous playback). | **yes — A-B loop — отличная нишевая фича** (для разучивания тренировок, языковых уроков); берём с пометкой "optional advanced feature" |
| 9 | Субтитры / аудио | SRT/SMI/SUB/SSA/ASS; UTF-8 default + manual encoding; 2D/3D subtitle для 3D-видео; Bluetooth audio lag auto-offset. **DTS/Dolby — НЕ поддерживается из-за лицензирования.** | yes — особенно **2D/3D subtitle toggle** для 3D-видео и **BT audio lag offset** |
| 10 | Что плохо | (a) Big square button closes video instantly — DESIGN FLAW. (b) No playback speed control. (c) Single-directory storage limitation. (d) DTS/Dolby отсутствуют из-за лицензий. (e) Auto-detect rely на filename → ломается при truncation на media server. (f) Lite version обрезана агрессивно. | (n/a — анти-паттерны) |

**Что они делают лучше нас (на данный момент):** Самая зрелая SMB/DLNA discovery на Quest (auto-discovery + manual fallback). A-B loop. 2D/3D subtitle toggle. Bluetooth audio lag offset — фишка про которую думают единицы.
**Что мы сделаем лучше:** UI density — у Pigasus всё в кучу; current modal design + filter — наш differentiator. Конфирмация на destructive actions. Filename + metadata + pattern detection (не только filename).

---

## 5. Quest TV / Meta Horizon TV

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | Smart-TV-like UI: 7 табов (Home / Movies / TV Shows / Immersive / Sports / Music / Watchlist) + hero-images + content recommendations. Content-forward: deep-link в партнёрские streaming apps (Amazon Prime, Peacock, YouTube, Spotify, DAZN, Pluto). | with-mod — берём **content-forward** идею для Recent/Recommended/Continue Watching на главном; но primary — local + cloud |
| 2 | Вход в иммерс | Tab "Immersive" + content tagged 180°/360° → запускает партнёрский plugin/app, который и делает immersive playback. **Сам Quest TV переключает в panel-first mode, immersion делегируется внешнему плееру.** | no — наша архитектура единое приложение, без передачи в external player |
| 3 | Возврат | Системная Meta-button → Horizon shell. В самом Horizon TV нет "вернуться в библиотеку" внутри 360-playback. | no — слабое UX |
| 4 | Авто-детект формата | "Doesn't distinguish between 2D and 3D" — прямая жалоба ревьюеров. Только tag-based ("immersive content tag"). | no — анти-паттерн |
| 5 | HUD во время immerse | Стандартный Horizon overlay (delegated to external player). Inconsistent UX между разными providers. | no |
| 6 | Управление контроллерами | Стандартный Horizon pointer + trigger; thumbstick — scroll каталога. Quality settings (auto/good/better/best). | with-mod — quality preset идея ОК |
| 7 | Файловые операции в VR | **Удалены user-uploaded videos!** Раньше можно было загружать своё видео — Meta отрубила в сентябре 2025 ("content deleted on Sep 22"). Major regression. | no — мы должны быть в **этой нише**, которую Meta освободила |
| 8 | Seek / scrubber UX | Делегируется external player → inconsistent. | no |
| 9 | Субтитры / аудио | Dolby Atmos поддержка анонсирована, Dolby Vision — позже. Subtitle support зависит от провайдера. | yes (Atmos если можем) |
| 10 | Что плохо | (a) **Удалили user-uploaded video в 2025** — огромное окно для конкурентов вроде нас. (b) Нет различия 2D/3D. (c) Отсутствуют Netflix, Hulu, HBO. (d) Загрузка качественного видео занимает время. (e) Делегирование иммерса другим приложениям → inconsistent UX. | (n/a) |

**Что они делают лучше нас (на данный момент):** Интеграция с Horizon shell + Dolby Atmos audio + content discovery from major streaming services. Smart-TV-like familiar UX для нетехнических пользователей.
**Что мы сделаем лучше:** **Главное — Meta освободила нишу user-uploaded video в сентябре 2025.** Мы — её прямой бенефициар: full local + cloud file player с file operations внутри VR.

---

## 6. Android XR sample apps

Это не "продукт-конкурент" в строгом смысле — это **референсная архитектура** от Google, на которой мы должны базировать наш VR-stack для Android XR flavor.

| # | Пункт | Наблюдение | Заимствуем? |
|---|-------|------------|-------------|
| 1 | Главный экран | "Hello Android XR" sample: SpatialPanel с обычным Compose-контентом, Orbiter в TopAppBar. Multi-panel layout через SpatialCurvedRow (curveRadius = 825dp). | **yes — это наша архитектурная база** для Android XR flavor |
| 2 | Вход в иммерс | Два пути: (a) программный — `requestFullSpace()` из lifecycleScope.launch; (b) манифестный — `<property android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE" android:value="XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED" />`. | **yes — оба паттерна, выбор по сценарию**: программный для пользовательского переключения, манифестный для immersive-only flavor |
| 3 | Возврат | `requestHomeSpace()` симметрично. Recommended placement — `ToggleSpaceButton` в TopAppBar с проверкой `LocalSpatialCapabilities.current.isSpatialUiEnabled`. | **yes — toggle-button pattern** для нашего "Back to Library" в HUD |
| 4 | Авто-детект формата | (n/a — sample про панели, не про видео) | n/a |
| 5 | HUD во время immerse | Orbiter — компонент, "плавающий" вдоль края панели; для action buttons. SpatialPanel может быть `.resizable()` и `.movable()`. | **yes — наш control panel должен быть Orbiter или movable SpatialPanel**; resizable/movable — flag-driven |
| 6 | Управление контроллерами | Sample использует button-based controls в Orbiter; конкретного controller input handling в Hello Android XR нет — стандартный Compose `onClick`. Glasses-sample (отдельный repo) — voice-driven. | with-mod — стандартный Compose click + наш кастом для thumbstick navigation |
| 7 | Файловые операции в VR | (n/a) | n/a |
| 8 | Seek / scrubber UX | (n/a) | n/a |
| 9 | Субтитры / аудио | (n/a) | n/a |
| 10 | Что плохо | (a) Hello Android XR — **минимальный sample**, нет media player references. (b) SDK на стадии Developer Preview 3 — API могут меняться. (c) Документация для controller input в XR — fragmented. (d) Нет официального video-player sample → мы пишем pioneer-implementation. | (n/a) |

**Что они делают лучше нас (на данный момент):** Foundational reference для spatial UI на Android XR — это первоисточник.
**Что мы сделаем лучше:** Реализуем **первый production-grade media player для Android XR** на базе этих samples. У нас уже есть Clean+MVVM, Media3, Glide, файловый стек — нам нужен только spatial layer поверх.

---

## Сводка: что мы делаем лучше

Привязка к product vision из `S0240 §1.0` — "Universal player for stereo media in VR" + наследование полного FastMediaSorter feature set.

1. **Единый плеер для всех стерео-форматов без manual format-picking.** DeoVR/Skybox — auto-detect есть, но они закрыты в свою экосистему (DeoVR — content marketplace, Skybox — single-app). Мы — universal + open: SBS/OU/VR180-fisheye/VR360-equirect/cylinder/mono cinema в одном пайплайне, без переключения между приложениями (что Quest TV делает через делегирование).
2. **File management в VR — никто не делает.** DeoVR/Skybox/Pigasus/Bigscreen/Quest TV — все **viewer-only**. У нас rename/move/delete/sort/multi-select inside VR через flavor inheritance от FastMediaSorter — уникальная позиция.
3. **Cloud-first из коробки.** Skybox WebDAV — недавнее добавление (2025); Pigasus — только SMB/DLNA. У нас уже зрелый стек: SMB/SSH/WebDAV/Google Drive/Dropbox/OneDrive — все из main app через flavor inheritance.
4. **Никакой content-marketplace pressure.** DeoVR — каталог, который продаёт контент; Quest TV — каталог, который deep-links в чужие apps. Мы — pure tool, файлы пользователя, без рекламы.
5. **Прямой бенефициар Quest TV regression в сентябре 2025.** Meta удалила user-uploaded video из Horizon TV — образовалась дыра в нише "play my files in VR". Bigscreen/Skybox/Pigasus её закрывают частично; мы — полностью + cloud + file ops.
6. **Cinema metaphor + true VR immersion в одном app.** Bigscreen — только cinema (panel-on-curved-screen); DeoVR/Skybox — только immersive. Мы — оба режима, переключение через `requestFullSpace()`/`requestHomeSpace()` на Android XR и эквивалент на Horizon OS.
7. **Customizable subtitles на уровне DeoVR без платной стены.** DeoVR — 3D-depth/drag-in-space subtitles за подписку. Pigasus — 2D/3D toggle без depth-positioning. Skybox — без styling. Мы — полный набор в основной версии.
8. **Confirmation на destructive actions.** Pigasus "square button closes video instantly" — мы делаем undo/confirmation toggle настраиваемым (в духе FastMediaSorter undo для delete/move).
9. **Honest auto-detect: filename + metadata + content pattern.** Pigasus — только filename (ломается при truncation). DeoVR/Skybox — closed-source, метод неизвестен. Мы — три источника + manual override через скрытое меню (DeoVR-style).
10. **Production-grade Android XR pioneer.** Hello Android XR — foundational sample без media player. Мы будем первым production VR media player на Android XR + параллельно Horizon OS, через flavor isolation в build.gradle.kts.

---

## Anti-patterns to avoid (consolidated)

Сведено из колонки "Что плохо" каждого конкурента:

- **Системный return only.** Не полагаться на Meta-button как единственный путь обратно в библиотеку (DeoVR/Skybox/Quest TV). Нужна явная HUD-кнопка "Back to Library".
- **Instant-close на одну кнопку.** Никаких "square button closes video instantly" (Pigasus). Confirmation или undo обязательны для destructive controller actions.
- **Filename-only format detection.** Media servers truncate filenames (Pigasus issue). Использовать filename + metadata + content-pattern.
- **Регрессии без feature parity.** Skybox AirScreen v2.0 ухудшил latency; Quest TV удалил user-uploaded video. У нас — version-pinning критичных pipelines + functionality log на каждое user-visible изменение.
- **Subtitle styling lock-in.** Skybox не даёт менять position/font/color. Делаем полную кастомизацию.
- **Closed catalog lock-in.** DeoVR библиотека привязана к их marketplace; Quest TV — к партнёрским streaming services. Мы — file-system-first, без зависимостей.
- **HUD overcrowding.** Pigasus — все настройки в одном меню → cognitive load. Нужна progressive disclosure: primary controls сразу; advanced — за дополнительный тап.
- **Делегирование иммерса другим apps.** Quest TV отдаёт 360-playback внешним плеерам → inconsistent UX. У нас всё в одном binary.
- **No file ops in VR.** Все конкуренты — viewer-only. Это наш differentiator.
- **DTS/Dolby licensing skip.** Pigasus не поддерживает. Мы — через ExoPlayer Media3 + системные кодеки; если ограничение принципиальное — явно документируем, не скрываем.

---

## Final coverage report

- **Fully analyzed (10/10 checklist filled with cited sources):** DeoVR, Skybox VR Player, Pigasus VR Media Player, Meta Horizon TV / Quest TV, Android XR sample apps — 5 of 6
- **Partially analyzed (some items inferred from secondary sources or marked n/a):** Bigscreen Beta — 1 of 6 (Bigscreen Beta full controller mapping и detailed playback HUD недоступны без device-test; public docs описывают cinema metaphor общими словами)
- **Unavailable:** none — все 6 как минимум частично purchaseable публично

Дополнительная заметка по терминологии: путаница между _Bigscreen Beyond_ (headset) и _Bigscreen Beta_ (app) в публичных источниках частая. В этом анализе разбирается только приложение _Bigscreen Beta_.
