# RESEARCH.md — результаты R-01..R-13

**Источник тикет:** `S0244` `vr-preliminary-research`.
**Эпик:** `S0240` §10.0.
**Заполнено:** 2026-05-18 (через `/spec-all S0244` → параллельные research-агенты).

Каждая запись ниже — research-задача из `S0240 §10.0`. Формат отдачи:

> **Вопрос:** ...
> **Источник:** ... (код / git history / web URL)
> **Варианты:**
> - **A.** ... — pros / cons
> - **B.** ... — pros / cons
> - (опционально **C.**)
> **Best practice (рекомендация):** ... — почему этот вариант
> **Открытые риски:** ...

Если по задаче ровно один ответ без вариантов — «Вопрос → Источник → Ответ → Открытые риски».

---

## Группа A — внутри кодовой базы

Источник анализа: HEAD на ветке `DEBUG-v004`. Релевантные коммиты:

- `abc5c291` (2026-05-18 01:39) — удаление всего `app_v2/src/vr/` (Kotlin + cpp + res + AndroidManifest).
- `e7c20d95` (2026-05-18 01:18) — удаление `btn3dVrCmd` из `app_v2/src/main/res/layout/activity_player_unified.xml`.
- `c1456a85` — stub `CMakeLists.txt` по пути удалённого `vr/cpp/`.

Чтобы прочитать состояние до удаления, везде ниже используется `abc5c291~1` (родитель удаляющего коммита).

### R-01. Что было в `app_v2/src/vr/` до удаления (S0241): строковые ресурсы, layout, ключи настроек

> **Вопрос:** Какие переиспользуемые строки, ресурсы и preference-ключи лежали в `src/vr/` и могут пригодиться на Stage 0 нового переписывания?
>
> **Источник:**
> - `git show abc5c291~1:app_v2/src/vr/res/values/strings.xml` (en, базовые), `…/values-ru/strings.xml`, `…/values-uk/strings.xml`.
> - `git show abc5c291~1:app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRecentDestinationsPrefs.kt` (единственные VR-only SharedPreferences).
> - `git show abc5c291~1:app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrToggleButtonManager.kt` (ссылки на drawable/строки в `main`).
> - Drawable / строковые ресурсы, оставшиеся в `app_v2/src/main/res/` после S0241 (НЕ были удалены): `drawable/ic_vr_3d`, `drawable/ic_vr_exit`, строки `vr_toggle_enter_description`, `vr_toggle_exit_description`, `keybinding_label_vr_toggle_immersive`.

**Реестр того, что выжило в `src/main/res/` после S0241 (готово к использованию):**

- Drawables (в `main`):
  - `@drawable/ic_vr_3d` — иконка «войти в VR» (используется `VrToggleButtonManager`).
  - `@drawable/ic_vr_exit` — иконка «выйти из VR».
- Строки EN/RU/UK (`values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`):
  - `vr_toggle_enter_description` = "Immersive view" / «Иммерсивный режим».
  - `vr_toggle_exit_description` = "Exit immersive" / «Выйти из иммерсивного».
  - `keybinding_label_vr_toggle_immersive` = "Toggle Immersive".

**Реестр того, что было в `src/vr/res/` и удалено вместе с flavor source-set:**

- Локализованный override `app_name` = `FastMediaSorter VR` (EN/RU/UK).
- Welcome-page strings: `welcome_title_1` (override), `welcome_vr_title`, `welcome_vr_description`, `welcome_vr_feature_stereo`, `welcome_vr_feature_headset`, `welcome_vr_feature_formats`.
- Phone-fallback screen: `vr_phone_fallback_title`, `vr_phone_fallback_message`, `vr_phone_fallback_close`, `vr_phone_fallback_open_standard`.
- Mipmaps `ic_launcher` / `ic_launcher_adaptive_back` / `ic_launcher_adaptive_fore` для всех 5 dpi-вариантов.

**Preference-ключи / SharedPreferences VR-флавора:**

- `VrRecentDestinationsPrefs` (файл `vr/helpers/VrRecentDestinationsPrefs.kt`):
  - `PREFS_NAME = "vr_recent_destinations"`.
  - `KEY_LIST = "paths"` — JSON-массив, MRU-список из 10 элементов.
  - Хранил недавние destination-папки для VR-овой панели «Copy/Move».
- Бывший `PlaybackControlPreferences.KEY_VR_RENDERING_MODE` (ссылка осталась в `VrPlayerActivity.renderingModeListener`) — ключ лежит в `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt`. Проверить отдельно: ключ мог быть зачищен S0241 Phase 03, либо ещё жив в виде legacy-значения.

> **Открытые риски:**
> - Локализация UK/RU welcome-страницы и phone-fallback экрана исчезла из дерева — если новый стек переиспользует эти экраны, тексты нужно перенести из git history обратно в `src/main/res/values-{ru,uk}/`.
> - Иконки `ic_vr_3d` / `ic_vr_exit` остались в `src/main/res/drawable/`, но могут не соответствовать актуальному визуальному стилю v2.6.
> - `PREFS_NAME = "vr_recent_destinations"` — пользователи со старого VR-флавора имеют непустой SharedPreferences-файл; новый код должен мочь его прочитать.

### R-02. Структура экрана Settings и точка вставки группы «VR»

> **Вопрос:** Куда именно подключается новая группа настроек «VR» (Stage 0): отдельная вкладка, секция в существующей, или подэкран?
>
> **Источник:**
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt:38..148` — host, `ViewPager2` + `TabLayoutMediator`, 4 фиксированных вкладки.
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsPagerAdapter.kt:11..24` — статический список: `GeneralSettingsFragment`, `MediaSettingsFragment`, `PlaybackSettingsFragment`, `OperationsSettingsFragment`.
> - `app_v2/src/main/res/layout/activity_settings.xml` — layout host'а (`TabLayout` + `ViewPager2`).
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/MediaCategoryPagerAdapter.kt:17..51` — образец **флавор-фильтрованного** pager-адаптера (подвкладки добавляются динамически по `BuildConfig.SUPPORT_*`).
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt:34..42, 49..256` — образец «секции внутри фрагмента» с per-section SharedPreferences expanded-state.
> - Тайтлы вкладок: `R.string.settings_tab_general` / `settings_tab_media` / `settings_tab_playback` / `settings_tab_operations` (`SettingsActivity.kt:142..145`).
>
> **Иерархия классов:** Settings НЕ использует AndroidX `PreferenceFragmentCompat` / `PreferenceScreen` XML — это полностью кастомный экран на `Fragment` + ViewBinding. Файлов `res/xml/*preferences*.xml` в проекте нет. Любая логика «preference key ↔ widget» написана руками в `*SettingsFragment.kt` и читает/пишет через `SettingsViewModel` / `SharedPreferences`.
>
> **Варианты:**
> - **A.** Новая корневая вкладка `VrSettingsFragment` (пятая в `SettingsPagerAdapter`).
>   - + Самая видимая точка входа, симметрично с General/Media/Playback/Operations.
>   - + Скрывается по флавору так же, как в `MediaCategoryPagerAdapter` (паттерн уже есть).
>   - − `SettingsPagerAdapter` сейчас статичен — нужно его переписать в стиле `MediaCategoryPagerAdapter`.
>   - − Поломает `EXTRA_INITIAL_TAB` индекс «3 = Operations», если VR воткнётся в середину — придётся ставить вкладку в конец.
> - **B.** Раскрывающаяся секция «VR» внутри `PlaybackSettingsFragment` (новая `section_vr_expanded`).
>   - + Изменения локализованы одним файлом + одним layout.
>   - + Не нужно править `EXTRA_INITIAL_TAB`-контракт.
>   - − VR не «полегче» Playback — это полноценный отдельный pipeline.
>   - − В Stage 1+ VR-настроек станет много (RenderingMode, EyeBuffer, FoveatedRendering, HandTracking-toggle, ControllerLayout).
> - **C.** Кнопка «Открыть VR-настройки» внутри `GeneralSettingsFragment` → `startActivity(VrSettingsActivity)`.
>   - + Полная изоляция, новый Activity = новый файл.
>   - − Ломает паттерн «Settings = одна Activity с табами».
>   - − Дополнительная Activity нагружает `AndroidManifest`, фокусную модель, back-stack.
>
> **Best practice (рекомендация):** **A** — пятая вкладка `VrSettingsFragment` через рефакторинг `SettingsPagerAdapter` по образцу `MediaCategoryPagerAdapter`. Причины:
> - Уже есть рабочий шаблон флавор-гейтинга.
> - Симметрия с другими топ-левел доменами.
> - `EXTRA_INITIAL_TAB` контракт чинится добавлением вкладки **в конец** (`position = 4`).
> - Гейт `BuildConfig.SUPPORT_VR_PLAYER` (см. R-05) уже доступен в каждом flavor.
>
> **Открытые риски:**
> - Все 5 текущих flavor сейчас имеют `SUPPORT_VR_PLAYER = false` — пока новый рантайм не подключён, в UI вкладка нигде не покажется.
> - В `SettingsKeyboardNavigationManager` (D-pad / keyboard navigation) переход между вкладками идёт по индексам; нужно проверить keymap (`/ui-clarify` гейт — правило 17 CLAUDE.md).
> - Добавление `settings_tab_vr` обязывает заполнить EN/RU/UK + прогнать `scripts/check_strings_localized.ps1`.

### R-03. Командная панель плоского плеера — точка вставки кнопки «Immerse»

> **Вопрос:** Куда подключается новая ImageButton «войти в VR» на flat-плеере (Stage 1), как управляется её visibility и callback?
>
> **Источник:**
> - `app_v2/src/main/res/layout/activity_player_unified.xml` — host-layout плеера; верхняя command-панель определена с строк 8..256. Корневой `LinearLayout android:id="@+id/topCommandPanel" android:orientation="horizontal"`.
> - **Историческое местоположение кнопки (до S0241):** строки `97..98` в коммите `abc5c291~1`, между `btnEditCmd` и `btnSaveFrameCmd`. Полное определение восстановимо через `git show e7c20d95 -- app_v2/src/main/res/layout/activity_player_unified.xml`:
>   ```xml
>   <ImageButton android:id="@+id/btn3dVrCmd"
>       android:layout_width="@dimen/player_cmd_button_size"
>       android:layout_height="@dimen/player_cmd_button_size"
>       android:background="?attr/selectableItemBackgroundBorderless"
>       android:contentDescription="@string/vr_toggle_enter_description"
>       android:src="@drawable/ic_vr_3d"
>       android:visibility="gone"
>       app:tint="@color/selector_player_button_tint"
>       android:scaleType="centerInside"
>       android:padding="@dimen/player_button_padding" />
>   ```
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt:34..35` — константа `VR_BUTTON_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)` (S0238).
> - `CommandPanelController.kt:53..99` — `CommandPanelCallback` интерфейс (33+ хэндлера).
> - `CommandPanelController.kt:139..256` — `setupCommandPanelControls()`: точка вставки.
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` — адаптивный planner (portrait/landscape, overflow-menu).
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` — конкретная реализация callback'а.
>
> **Шаблон визуальной кнопки:** все 30+ кнопок в `topCommandPanel` — `ImageButton`, размер `@dimen/player_cmd_button_size`, фон `?attr/selectableItemBackgroundBorderless`, tint `@color/selector_player_button_tint`, scaleType `centerInside`, padding `@dimen/player_button_padding`, видимость по умолчанию `gone`.
>
> **Варианты:**
> - **A.** Восстановить именно `btn3dVrCmd` на том же месте, переиспользуя `ic_vr_3d` + `vr_toggle_enter_description`.
>   - + Минимальный diff: 1 строка XML + 1 строка в `setupCommandPanelControls()` + 1 запись в `CommandPanelCallback`.
>   - + Drawable, строка и `VR_BUTTON_MEDIA_TYPES` уже на месте.
>   - − Если новый стек захочет отдельный icon/label, всё равно придётся править.
> - **B.** Новая кнопка с новым id (`btnImmerseCmd`) и новым drawable.
>   - + Чистый старт без привязки к историческим артефактам.
>   - − Дублирование иконок/строк; `ic_vr_3d` остаётся unused и подметётся lint'ом.
>
> **Best practice (рекомендация):** **A** — восстановить `btn3dVrCmd` на старом месте. Изоляция от старого рантайма уже сделана. Иконка + строки EN/RU/UK уже локализованы.
>
> **Открытые риски:**
> - `CommandPanelLayoutPlanner` имеет жёсткую логику приоритетов в overflow — для portrait кнопка должна быть явно добавлена в priority-list. Правило 12 CLAUDE.md (layout-land) — проверить `res/layout-land/activity_player_unified.xml`.
> - `PlaybackControlPreferences.KEY_VR_RENDERING_MODE` ещё может содержать SharedPreferences listener'ы у пользователей со старого VR — Stage 1 не должен случайно их триггерить.
> - Кнопка не должна показываться, когда `BuildConfig.SUPPORT_VR_PLAYER == false`.

### R-04. Передача состояния ExoPlayer между Activity (flat → VR-host → flat)

> **Вопрос:** Какой паттерн использовать для сохранения позиции, playWhenReady и индекса медиа при переходе flat-плеер → VR-host и обратно?
>
> **Источник:**
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt:1340..1408` — `companion object` с фабриками intent'ов. **Позиция в миллисекундах через intent НЕ передаётся** — восстанавливается из `PlaybackPositionRepository`.
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt:10..16` — read-only фасад: `getResumePosition(mediaId: String): Long` + `saveResumePosition(mediaId, positionMs: Long)`.
> - `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PlaybackPositionRepository.kt` + `data/repository/PlaybackPositionRepositoryImpl.kt` — Room-backed persistence (`mediaId → positionMs`).
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionRestorer.kt:27` — единая точка чтения позиции при старте.
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PositionSaveLoop.kt:25` — периодическая запись (15s interval).
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt:55..280` — единственный `MediaSessionService` в проекте; используется **только для аудио в фоне**.
> - **Историческая практика VR (до S0241):** `VrTaskTransition.kt` использовал `Intent + flags FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP` для возврата, **без** копирования позиции в extras — рассчитывал на `PlaybackPositionRepository` как single source of truth.
>
> **Что реально есть сейчас (после S0241):**
> - ExoPlayer создаётся **per-Activity** в `VideoPlayerManager`.
> - Позиция persistится через `PlaybackPositionRepository` каждые 15s или при паузе/onStop.
> - Audio имеет `MediaSession` через `AudioPlaybackService`, но **video — нет**.
> - Никакого long-lived singleton ExoPlayer / shared `MediaController` для видео в проекте нет.
>
> **Варианты:**
> - **A.** Shared singleton `ExoPlayer` в `Service` / `@Singleton` Hilt-Manager — flat и VR-host подключаются к одной инстанции через `PlayerView.setPlayer(sharedPlayer)`.
>   - + Самый дешёвый handoff: ноль перерисовки, миллисекундное переключение.
>   - − Сервис требует foreground-notification для видео >= Android 14 (новая permission `FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
>   - − ExoPlayer не любит, когда `Surface` меняется на лету.
>   - − Проект не имеет такой инфраструктуры — это новая абстракция уровня `VideoPlaybackService extends MediaSessionService`.
> - **B.** `Bundle` extras + создание с нуля в новой Activity, восстановление позиции через `PlaybackPositionRepository.getResumePosition()`.
>   - + Нулевая новая инфраструктура.
>   - + Полностью укладывается в существующий контракт `PlayerActivity.createIntent`.
>   - − Видимая задержка (200–800 ms) на reinitialize ExoPlayer.
>   - − Для seamless «mid-playback swap без паузы» — не подходит.
> - **C.** Media3 `MediaSession` + `MediaController` reconnect (extension AudioPlaybackService на видео).
>   - + Канонический Media3-pattern.
>   - + Позиция, `playWhenReady`, `currentMediaItemIndex` синхронизируются автоматически.
>   - − Большой архитектурный сдвиг: видео сейчас Activity-bound, переход на сервис-bound потребует переписать `VideoPlayerManager` + `PlayerViewModel` (650+ LOC).
>   - − Foreground-сервис для **каждого** запуска плеера — оверхед для image-only / GIF.
>
> **Best practice (рекомендация):** **B на Stage 0/1**, с потенциальной миграцией на **C** в отдельном эпике после.
> - На Stage 0/1 цель — простой round-trip flat→VR→flat.
> - Достаточно добавить:
>   - `EXTRA_INITIAL_POSITION_MS: Long` — приоритет над DB-значением.
>   - `EXTRA_INITIAL_PLAY_WHEN_READY: Boolean` — текущее `playWhenReady`.
> - Singleton-ExoPlayer (A) отвергается: новая ownership-модель ради экономии 500 ms на переходе.
>
> **Открытые риски:**
> - `PlaybackPositionRestorer` читает позицию ТОЛЬКО по `mediaId/path` ключу. Если файл — стрим (SMB/SFTP/cloud) с нестабильным path'ом, ключ может ехать.
> - `PositionSaveLoop` имеет 15s interval — при resume в VR пользователь может оказаться на позиции, которая на 15s назад. Решается принудительным `saveResumePosition` в момент `onUserSwitchedToVr()` перед `startActivity`.
> - DTS audio (`ENABLE_DTS_DECODER`) — пересоздание ExoPlayer пересоздаёт DTS decoder; на Quest CPU может тормозить.
> - В VR-flavor включён `SUPPORT_CAST = false` — если будет переход standard → VR с активным Cast'ом, нужна явная обработка.

### R-05. Что выжило в git-истории: VR `AndroidManifest.xml`, CMake, `productFlavors.vr`

> **Вопрос:** Чистый, переиспользуемый snapshot manifest-фрагмента (Quest + Android XR intent categories + feature decls), `CMakeLists.txt` для OpenXR loader, и минимального flavor-блока — до удаления S0241.
>
> **Источник:**
> - `git show abc5c291~1:app_v2/src/vr/AndroidManifest.xml` (коммит-родитель S0241-deletion).
> - `git show abc5c291~1:app_v2/src/vr/cpp/CMakeLists.txt`.
> - `git show abc5c291~1:app_v2/build.gradle.kts` — flavor блок `create("vr") { … }` строки 291..344; `create("vrUnlicensed")` строки 352..397; `sourceSets.getByName("vrUnlicensed"/"noLegal"/"vr")` строки 408..435.
> - HEAD сейчас: `app_v2/build.gradle.kts` всё ещё содержит `create("vr")` и `create("vrUnlicensed")` с тем же `externalNativeBuild`. **Flavor-блок жив, удалена только `src/vr/` подпапка.**
> - HEAD сейчас: `app_v2/src/vr/cpp/CMakeLists.txt` существует (восстановлен `c1456a85` как stub без `ENABLE_OPENXR`-блока).
>
> **Концентрат manifest-фрагмента:**
>
> Из `git show abc5c291~1:app_v2/src/vr/AndroidManifest.xml`:
>
> - **Application-уровень meta-data:**
>   - `<meta-data android:name="com.oculus.supportedDevices" android:value="quest|quest2|quest3|questpro|quest3s" />` — обязательно на HorizonOS v67+; без него broker возвращает null cursor и `xrGetOpenGLESGraphicsRequirementsKHR` ломается с `XR_ERROR_FUNCTION_UNSUPPORTED (-7)`.
>   - `<property android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE" android:value="XR_ACTIVITY_START_MODE_HOME_SPACE" />` — Android XR SDK (Google).
> - **VR-Activity (`VrPlayerActivity`-эквивалент):**
>   - `android:taskAffinity="${applicationId}.vr"` — обязательная изоляция от 2D-task.
>   - `android:launchMode="singleTask"`, `android:exported="false"`, `android:resizeableActivity="true"`, расширенный `configChanges`.
>   - intent-filter: `MAIN` + `DEFAULT` + `com.oculus.intent.category.VR`.
>   - meta-data: `<meta-data android:name="com.oculus.vr.focusaware" android:value="true" />`.
> - **MainActivity override (через `tools:replace="android:configChanges"`):**
>   - `<layout android:defaultWidth="1920dp" android:defaultHeight="1080dp" ... />`.
>   - intent-filter дополнительно: `com.oculus.intent.category.2D` + `LAUNCHER`.
> - **VrPhoneFallbackActivity:** `android:taskAffinity=""` (пустая) — для phone-fallback.
> - **Browse/Player/Settings/Welcome activity overrides:** `android:resizeableActivity="true"` + расширенный `configChanges` через `tools:replace` для Android XR Shell.
> - **uses-feature (все `required="false"`):**
>   - `android.hardware.vr.headtracking` `android:version="1"`.
>   - `android.hardware.xr.immersive` (Google Play / Android XR).
>   - `android.software.xr.api.spatial` (Android XR SDK).
>   - `android.hardware.camera2` (для passthrough).
>   - `oculus.software.handtracking`.
> - **uses-permission:**
>   - `com.oculus.permission.HAND_TRACKING` — обязательна на HorizonOS v62+.
>   - `horizonos.permission.HEADSET_CAMERA` — Meta passthrough-камера (Horizon OS v74+, dangerous).
>
> **Концентрат CMake (`vr/cpp/CMakeLists.txt`):**
>
> - `cmake_minimum_required(VERSION 3.22.1)`, `project(openxr_native LANGUAGES CXX)`, `CMAKE_CXX_STANDARD 17`.
> - `option(ENABLE_OPENXR "..." OFF)` — main switch.
> - Если `ENABLE_OPENXR=OFF` → ранний `return()` после reference'a на CMAKE-вары (чтобы AGP не ругался «manually-specified variables were not used»).
> - Если ON: `find_package(OpenXR REQUIRED CONFIG)` через Prefab из AAR `org.khronos.openxr:openxr_loader_for_android`.
> - `add_library(openxr_native SHARED OpenXrNative.cpp OpenXrFrame.cpp OpenXrHandTracking.cpp OpenXrInput.cpp OpenXrLifecycle.cpp OpenXrSwapchain.cpp OpenXrLog.cpp OpenXrRayDraw.cpp)`.
> - `target_compile_definitions(openxr_native PRIVATE XR_USE_PLATFORM_ANDROID XR_USE_GRAPHICS_API_OPENGL_ES)`.
> - `target_link_libraries(openxr_native OpenXR::openxr_loader android log EGL GLESv3 jnigraphics)`.
> - `target_link_options(openxr_native PRIVATE "-Wl,-z,max-page-size=16384")` — обязательно для 16 KB page-size compliance (Android 15+ since Nov 2025, Google Play requirement).
>
> **Концентрат flavor-блока:**
>
> ```kotlin
> create("vr") {
>     dimension = "version"
>     versionNameSuffix = "-VR"
>     ndk { abiFilters += listOf("arm64-v8a") }
>     externalNativeBuild {
>         cmake {
>             targets += listOf("openxr_native")
>             abiFilters += listOf("arm64-v8a")
>             cppFlags += listOf("-std=c++17", "-Wall", "-Werror")
>             arguments += listOf(
>                 "-DANDROID_STL=c++_shared",
>                 "-DANDROID_PLATFORM=android-26",
>                 "-DENABLE_OPENXR=ON",
>                 "-DFMS_BUILD_REVISION=3"
>             )
>         }
>     }
>     buildConfigField("boolean", "SUPPORT_VIDEO", "true")
>     buildConfigField("boolean", "SUPPORT_AUDIO", "true")
>     buildConfigField("boolean", "SUPPORT_IMAGES", "true")
>     buildConfigField("boolean", "SUPPORT_CLOUD", "true")
>     buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
>     buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")     // S0241: detached.
>     buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "false")
>     buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.ui.player.PlayerActivity\"")
>     buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")
>     buildConfigField("boolean", "SUPPORT_CAST", "false")            // Horizon OS без GMS Cast.
>     // ... остальные SUPPORT_* поля по образцу standard
> }
> ```
>
> **Готовый минимальный стартер для нового стека (Stage 0):**
>
> 1. **Восстановить `src/vr/AndroidManifest.xml`** скопировав из `abc5c291~1`. Удалить блок `<activity android:name="com.sza.fastmediasorter.vr.VrPhoneFallbackActivity">` (классы под него удалены).
> 2. **Восстановить `src/vr/cpp/CMakeLists.txt`** из `abc5c291~1`. Файлы `OpenXr*.cpp/.h` — либо восстановить, либо переписать; Stage 0 может временно оставить `ENABLE_OPENXR=OFF`.
> 3. **flavor-блок `create("vr")` остался жив** — менять не нужно до Stage 1.
> 4. **`SUPPORT_VR_PLAYER` flag** — оставить `false` до готовности нового рантайма; перевернуть в Stage 1.
> 5. **`PLAYER_ACTIVITY_CLASS`** — оставить указывающим на flat `PlayerActivity` до готовности новой `VrPlayerActivity`.
>
> **Открытые риски:**
> - Меняется ли путь `com.oculus.intent.category.VR` / `com.oculus.vr.focusaware` в HorizonOS v68+? Не проверено в группе A; перед Stage 1 — запросить актуальную доку Meta (см. R-07).
> - OpenXR loader AAR (`org.khronos.openxr:openxr_loader_for_android`) — текущая версия в `gradle/libs.versions.toml` неизвестна; сверить совместимость с 16 KB page-size requirement и с HorizonOS v74+ (см. R-09).
> - `applicationIdSuffix = ".debug"` (debug buildType) — комбинация для buildType + flavor может ломать Dropbox/OAuth registration; история S0232 говорит «vr не имеет suffix'а».
> - `vrUnlicensed` flavor и `noLegal` flavor оба монтируют `src/vr/` — после восстановления `src/vr/` обе сборки начнут компилироваться; нужно гарантировать зелёные сборки.

---

## Группа B — внешние источники

### R-06. Android XR — `AndroidManifest.xml` и intent-categories

> **Вопрос:** Какие `<uses-feature>`, `<intent-filter>` и `<property>` нужны, чтобы приложение под Android XR корректно регистрировалось как immersive и попадало в XR-лаунчер?
>
> **Источник:**
> - https://developer.android.com/develop/xr/openxr/get-started
> - https://developer.android.com/develop/xr/package-and-distribute
> - https://developer.android.com/develop/xr/jetpack-xr-sdk/build-immersive
>
> **Ответ:** Канонический набор для OpenXR-приложения на Android XR:
>
> - `<uses-feature android:name="android.software.xr.api.openxr" android:required="true|false" />` — основной маркер «использует OpenXR runtime». Альтернатива для Jetpack XR SDK: `android.software.xr.api.spatial`.
> - `<uses-feature android:name="android.hardware.xr.input.controller" android:required="false" />`
> - `<uses-feature android:name="android.hardware.xr.input.hand_tracking" android:required="false" />`
> - `<uses-feature android:name="android.hardware.xr.input.eye_tracking" android:required="false" />`
> - `<uses-native-library android:name="libopenxr.google.so" android:required="false" />` — Google-runtime OpenXR на Android XR.
> - `<property android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE" android:value="XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED" />` — обязательно для immersive-входа.
> - `<property android:name="android.window.PROPERTY_XR_BOUNDARY_TYPE_RECOMMENDED" android:value="XR_BOUNDARY_TYPE_LARGE" />`.
> - intent-filter активити — стандартный `MAIN` + `LAUNCHER`. **Отдельной XR-категории не существует**; фильтрация по `xr.api.openxr` / `xr.api.spatial`.
> - Имя `android.hardware.vr.headtracking` — это **Quest-специфический** маркер; на Android XR не обязателен.
>
> **Best practice (рекомендация):** для FastMediaSorter (single-APK для phone+XR, см. R-10):
>
> - `android.software.xr.api.openxr` оставить с `required="false"` — иначе Play отфильтрует приложение на телефонах.
> - Все input-фичи — `required="false"`.
> - `XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED` ставить только в XR-flavor source set (`src/vr/AndroidManifest.xml`).
> - Permissions для extensions добавлять только когда соответствующий `XR_ANDROID_*` extension реально используется.
>
> **Открытые риски:**
> - Спецификация Android XR на стадии Developer Preview 2 (май 2025) — breaking changes ожидаются.
> - `libopenxr.google.so` — имя дано в текущем превью; может смениться.
> - Поведение фильтра Play при `android.software.xr.api.openxr=true` на Pixel-телефоне с XR-режимом официально не задокументировано.

### R-07. Meta Quest — манифест и оптимальные настройки для Quest 3

> **Вопрос:** Какие манифестные настройки обязательны для VR-приложения на Quest 3 и какие meta-data рекомендуются для перфоманса?
>
> **Источник:**
> - https://developers.meta.com/horizon/documentation/native/android/mobile-native-manifest/
> - https://developers.meta.com/horizon/resources/publish-mobile-manifest/
> - https://developers.meta.com/horizon/documentation/native/android/mobile-openxr/
>
> **Ответ:** Обязательный набор:
>
> - `<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" android:version="1" />` — **mandatory** для всех VR-приложений на Horizon OS.
> - `<meta-data android:name="com.oculus.supportedDevices" android:value="quest2|questpro|quest3|quest3s" />` — текущий полный список (Quest 1 устарел).
> - В launching activity: `<category android:name="com.oculus.intent.category.VR" />` дополнительно к `MAIN` + `LAUNCHER`.
> - `android:screenOrientation="landscape"` и `android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"`.
> - `android:excludeFromRecents="true"` для launching activity.
> - `android:debuggable="false"` (для release).
> - `installLocation="auto"`.
> - `minSdkVersion=29`, `targetSdkVersion` 32..36.
>
> **Refresh-rate и perf hints (рантайм, не манифест):**
> - Прямой meta-data «default refresh rate» в манифесте **не существует**.
> - Quest 3 supported: 72 / 80 / 90 / 120 Hz (120 Hz — experimental, требует Headset Settings → Developer → 120 Hz).
> - Foveation: `XR_FB_foveation` + `XR_FB_foveation_configuration`.
> - SpaceWarp: `XR_FB_space_warp`.
> - Performance settings: `XR_EXT_performance_settings`.
>
> **Immersive vs panel:** маркер immersive — категория `com.oculus.intent.category.VR` + создание OpenXR-инстанса. Без неё Quest показывает приложение в panel-окне.
>
> **Best practice (рекомендация):**
> - Поскольку minSdk проекта = 26 (`compileSdk 35`), для flavor'a Quest придётся поднять minSdk до 29 — расхождение между phone и Quest source-set'ами; через `minSdk = 29` в `vr`-flavor `defaultConfig`.
> - `supportedDevices` зафиксировать как `quest3|quest3s` (Quest 2/Pro в плане не значатся; не блокировать их добавление позже).
> - Refresh rate запрашивать 90 Hz по умолчанию (sweet spot Quest 3), 120 Hz — опционально через настройку приложения.
>
> **Открытые риски:**
> - Переход на Spatial SDK (вместо чистого OpenXR) потребует targetSdkVersion 34+ и новый набор meta-data — расхождение с phone-flavor увеличится.
> - При publishing на Horizon Store отсутствие `quest3` в `supportedDevices` блокирует ревью (см. R-11).

### R-08. Android XR — эмулятор

> **Вопрос:** Доступен ли эмулятор Android XR через AVD Manager, какие ограничения vs физическое устройство?
>
> **Источник:**
> - https://developer.android.com/develop/xr/jetpack-xr-sdk/get-studio
> - https://developer.android.com/develop/xr/jetpack-xr-sdk/studio-tools
> - https://android-developers.googleblog.com/2025/05/updates-to-android-xr-sdk-developer-preview.html
>
> **Ответ:**
> - Эмулятор доступен **только в Canary-сборке Android Studio**.
> - Системные образы: Android XR на базе Android 14 (API 34). `arm64-v8a` (Apple Silicon / ARM) и `x86_64` (Intel/AMD).
> - AVD Manager → New → категория «XR» → Headset device → загрузить XR system image.
>
> **Ограничения эмулятора:**
> - **OpenXR пока не поддерживается в эмуляторе** — приложения с OpenXR runtime могут падать при запуске. Тестируется только **Jetpack XR SDK** (spatial panels, environments).
> - Нет реального head tracking — поза симулируется через mouse-look в окне эмулятора.
> - Hand tracking / eye tracking — недоступны.
> - Контроллеры — отсутствуют физически; ввод эмулируется клавиатурой/мышью.
> - Passthrough — недоступен.
> - OpenXR composition layers — не рендерятся.
> - Производительность не репрезентативна.
>
> **Best practice (рекомендация):**
> - Эмулятор — **только** smoke-тест Jetpack-XR-UI (spatial panels, environment switching, базовая навигация).
> - Все OpenXR-related тесты — только на физическом Quest 3.
> - Когда Samsung Galaxy XR выйдет — добавить в test matrix; до этого Quest 3 — единственная VR-target-платформа.
>
> **Открытые риски:**
> - Поддержка OpenXR в эмуляторе обещана «в будущих превью» — точной даты нет.
> - Canary-канал Android Studio периодически ломает другие проекты; параллельная установка stable + Canary обязательна.

### R-09. OpenXR loader — лицензия и распространение

> **Вопрос:** Лицензия Khronos OpenXR loader для Android, можно ли ребандлить в APK; нужен ли Meta-специфичный loader; оправдан ли split `vr` / `vrUnlicensed`?
>
> **Источник:**
> - https://github.com/KhronosGroup/OpenXR-SDK
> - https://www.khronos.org/openxr/
> - https://mvnrepository.com/artifact/org.khronos.openxr/openxr_loader_for_android
> - https://developers.meta.com/horizon/documentation/native/android/mobile-openxr/
> - https://registry.khronos.org/OpenXR/specs/1.0/loader.html
>
> **Ответ:**
> - Khronos OpenXR-SDK — **Apache 2.0** + `COPYING.adoc` для headers (MIT-совместимый patent grant). Apache 2.0 разрешает ребандлинг без отчислений и без публикации исходников приложения. Требование — сохранить copyright notice.
> - AAR `org.khronos.openxr:openxr_loader_for_android` — Maven Central, Apache 2.0.
> - **Meta Quest использует штатный Khronos loader** — прямая цитата из доки Meta: «Meta supports the Khronos OpenXR Android Loader. No proprietary loader is needed.» Минимальная версия для Quest — **1.0.34**.
> - Gradle:
>   ```
>   android { buildFeatures { prefab true } }
>   dependencies { implementation 'org.khronos.openxr:openxr_loader_for_android:1.0.34' }
>   ```
> - Meta-специфичные extensions для Quest 3 (`XR_FB_*`, `XR_META_*`) поставляются как часть Horizon OS runtime, а не в APK — приложение запрашивает через `xrEnumerateInstanceExtensionProperties` и активирует при создании инстанса.
> - Android XR (Google) поставляет другой набор `XR_ANDROID_*` extensions (`XR_ANDROID_depth_texture`, `XR_ANDROID_eye_tracking`, `XR_ANDROID_face_tracking`, и т.д.) + стандартные `XR_KHR_*` / `XR_EXT_*`. Часть Meta-extensions Android XR **не поддерживает**; обратное тоже верно.
>
> **Варианты по split'у flavor'ов:**
> - **A.** Один общий `vr` source set, без разделения licensed/unlicensed.
>   - **pros:** проще CI, общий код OpenXR.
>   - **cons:** не разделяется поведение для каналов distribution (если требуется отдельная подпись для App Lab / закрытого канала vs Store).
> - **B.** Сохранить `vr` / `vrUnlicensed` (текущее состояние).
>   - **pros:** позволяет иметь debug-flavor без Meta App ID для side-load.
>   - **cons:** дублирование кода / манифестов.
>
> **Best practice (рекомендация):** **A** — единый `vr` source set. Khronos loader покрывает оба runtime (Horizon, Android XR); вендорные extensions — feature detection рантайм. Если нужна side-load сборка без Meta App ID — это конфигурация **buildType** (debug variant), а не **flavor**. Лицензионных оснований для split нет.
>
> **Открытые риски:**
> - Если проект перейдёт на проприетарные Meta XR SDK (не OpenXR — например, Meta Voice SDK), появится привязка к Meta-only коду — тогда split станет обоснован.
> - Версия Khronos loader должна быть pin'нута и подниматься синхронно с проверкой минимальной версии Horizon OS.

### R-10. Google Play — distribution model для Android XR

> **Вопрос:** Делает ли `required="true"` для XR-фич приложение невидимым на телефонах? Возможна ли single-APK стратегия для phone + Android XR?
>
> **Источник:**
> - https://developer.android.com/develop/xr/package-and-distribute
> - https://developer.android.com/guide/topics/manifest/uses-feature-element
> - https://android-developers.googleblog.com/2025/10/5-things-you-need-to-know-about.html
>
> **Ответ:**
> - **Да**, `required="true"` для XR-фичи приводит к фильтрации Google Play на устройствах без неё — phones, tablets, Wear, Auto, TV. Это **жёсткий фильтр**.
> - Google прямо рекомендует: для single-APK стратегии (phone + XR) ставить **`required="false"`** для всех XR-фич.
>
> **Варианты distribution:**
> - **A.** Single-APK / Single-track (mobile release track).
>   - **pros:** один артефакт, одно store listing, общее App Signing key, существующая phone-аудитория автоматически видит XR-версию на Android XR-устройстве. Google называет это рекомендуемым подходом.
>   - **cons:** размер APK раздут XR-зависимостями для phone-пользователей (mitigation — App Bundle + dynamic feature module).
> - **B.** Single-package, два APK на двух release-track'ах (mobile + Android XR dedicated).
>   - **pros:** XR-APK не тащит phone-зависимости.
>   - **cons:** двойная сопровождаемость, два процесса ревью.
> - **C.** Два отдельных store listing (два package name).
>   - **pros:** полная независимость.
>   - **cons:** разрывает rating / install base.
>
> **Best practice (рекомендация):** **Вариант A** для FastMediaSorter. Архитектурно:
> - В корневом манифесте `app_v2/src/main/AndroidManifest.xml` оставить только phone-фичи.
> - В `app_v2/src/vr/AndroidManifest.xml` объявить XR-specific `<uses-feature>` через manifest-merger — все с `android:required="false"`.
> - Если приоритет — минимизировать APK для phones, выделить VR-стек в **dynamic feature module**.
> - Заполнить Android XR store listing с XR-specific screenshots (см. R-13).
>
> **Открытые риски:**
> - Если на Android XR появится требование явного `xr.api.openxr=true` для попадания в XR-tab Play Store, потребуется dedicated track. Прямого подтверждения пока нет.
> - Asset pack limit для XR — до 30 GB (vs 4 GB phone). Если медиа-кэш растёт, это аргумент в пользу dedicated XR APK.

### R-11. Meta Horizon Store — процесс публикации

> **Вопрос:** Как опубликовать VR-приложение в Meta Horizon Store: каналы релиза, подпись, инструменты, время ревью?
>
> **Источник:**
> - https://developers.meta.com/horizon/resources/publish-submit/
> - https://developers.meta.com/horizon/resources/publish-release-channels/
> - https://developers.meta.com/horizon/documentation/native/android/mobile-application-signing-quest/
> - https://developers.meta.com/horizon/blog/app-submission-review-process-guide/
> - https://developers.meta.com/horizon/documentation/unity/ts-mqdh/
>
> **Каналы релиза (по умолчанию каждое приложение получает 4):**
> - **ALPHA** — invite-only, по умолчанию 200 пользователей (расширение до 2500).
> - **BETA** — invite-only, 200/2500.
> - **RC** — invite-only, 200/2500.
> - **Production (Store)** — публичный канал; требует полного review.
>
> Note: «App Lab» как отдельный merchandising-канал объединён со Store в 2024 — теперь это publication в Production с пониженным visibility.
>
> **Подпись APK:**
> - Обязательна **v2** (APK Signature Scheme v2). v1/v3 не требуются.
> - Формат keystore — стандартный Android (JKS или PKCS12).
> - **Meta не имеет аналога Play App Signing** — разработчик хранит свой ключ; потеря = невозможность обновлять.
> - SDK: `minSdkVersion ≥ 23` (рекомендуется 29 для Quest 3); `targetSdkVersion` 32..36.
> - В манифесте: `debuggable=false`, `installLocation=auto`, `excludeFromRecents=true` на launching activity.
>
> **Инструменты загрузки:**
> - **Developer Dashboard** (web) — стандартный путь.
> - **Meta Quest Developer Hub (MQDH)** — desktop tool.
> - **ovr-platform-util CLI** — для CI-пайплайнов: `ovr-platform-util upload-quest-build --app_id=<ID> --apk=<file>.apk --channel=<channel>`. Командлайн через MQDH installer.
>
> **Время ревью:**
> - SLA Meta официально **не публикует**; рекомендуется подавать **не менее чем за 2 недели до релиза**.
> - Verification of developer organization (один раз на аккаунт) — ~48 часов.
> - App Lab-стиль ревью (исторически): 4..6 недель. Для Store — сопоставимо или дольше.
> - Beta/RC/Alpha — внутренний lightweight review (часы..сутки).
>
> **Best practice (рекомендация):**
> - Локальная разработка/CI → **ALPHA** через `ovr-platform-util`.
> - Расширенное тестирование с владельцем → **BETA**.
> - Дата-locked сборка перед публикацией → **RC**.
> - Финальный production submit за 3+ недели до публичного релиза.
> - Сохранить keystore в защищённом backup — потеря = re-listing нового app entry.
>
> **Открытые риски:**
> - Без публичного SLA планирование релизов привязано к буферу 2..6 недель.
> - Если приложение содержит cloud-OAuth flows — Meta может потребовать дополнительный review поведения после login (документация требует device-code flow для media apps без клавиатуры; см. R-12).

### R-12. Privacy policy — хостинг и Google OAuth caveat

> **Вопрос:** Как захостить privacy policy так, чтобы это удовлетворило Google Play и Meta Horizon Store?
>
> **Источник:**
> - https://developers.meta.com/horizon/policy/privacy-policy/
> - https://support.google.com/googleplay/android-developer/answer/10787469
> - https://www.termsfeed.com/blog/invalid-privacy-policy-url-google/
> - https://developers.meta.com/horizon/documentation/android-apps/authentication/
>
> **Требования обеих платформ:**
> - HTTPS обязателен (Meta: «signed by a trusted website CA»). Самоподписанные сертификаты отклоняются.
> - URL публично доступен, без авторизации, не за пейволлом.
> - Содержание описывает категории данных, цели, способы удаления.
> - Google Play: обязательна Data safety form; **privacy policy URL обязателен даже для «no data collected»** приложений.
> - Meta: разрешена организационная (а не per-app) privacy policy, если она покрывает данное приложение поимённо.
> - «No data collected» формулировка допустима обеими сторонами при условии, что фактическое поведение соответствует и Data safety form в Play синхронна.
>
> **Варианты хостинга:**
> - **A.** GitHub Pages (`username.github.io/repo` или custom domain).
>   - **pros:** бесплатно, HTTPS из коробки (Let's Encrypt), version control, custom domain.
>   - **cons:** требует публичный репозиторий (или GitHub Pro $4/мес).
> - **B.** Cloudflare Pages / Netlify / Vercel — статический хост.
>   - **pros:** HTTPS из коробки, custom domain, edge CDN, бесплатный тир покрывает; репо может быть приватным.
>   - **cons:** ещё один SaaS-аккаунт.
> - **C.** `raw.githubusercontent.com/<owner>/<repo>/<branch>/docs/PRIVACY.md` — прямая ссылка на raw-файл.
>   - **pros:** ноль инфраструктуры.
>   - **cons:** raw URL отдаёт `text/plain`, **не `text/html`** — Google Play стабильно отклоняет такие URL'ы. **Не рекомендуется**.
>
> **Best practice (рекомендация):** **Вариант A** (GitHub Pages). Конкретно:
> - Завести репо `fastmediasorter-legal` (public) → `gh-pages` или `main` branch → `index.html` + `privacy.html` + `terms.html`.
> - URL вида `https://<owner>.github.io/fastmediasorter-legal/privacy.html`.
> - В Google Play Console и Meta Developer Dashboard прописать этот URL.
> - Текст продублировать в EN/RU/UK (`docs/PRIVACY_EN.md`, `PRIVACY_RU.md`, `PRIVACY_UK.md`).
>
> **Google OAuth caveat (под-вопрос R-12):**
> - **Quest (Horizon OS)** — **нет** нативной Google аккаунт-инфраструктуры, **нет** Google Play Services. `GoogleSignIn` SDK / `Credential Manager API` — **не работают**. Единственный путь — web OAuth 2.0 flow с redirect URI custom scheme:
>   - Использовать библиотеку **AppAuth-Android** (`net.openid:appauth:0.11.x`).
>   - Redirect URI зарегистрировать в Google Cloud Console как «Android client» с custom URI scheme (`com.sza.fastmediasorter:/oauth2redirect`).
>   - На Horizon OS системный браузер (Meta Browser) выполняет authorization endpoint flow.
>   - Token storage — `EncryptedSharedPreferences`.
>   - Для media-apps без клавиатуры Meta рекомендует RFC 8628 **device code flow**.
> - **Android XR** — нативный Google аккаунт **есть** (это всё ещё Android с GMS). `Credential Manager API` / `GoogleSignIn` работают. AppAuth-flow тоже валиден — единая кодовая база возможна.
>
> **Best practice (под-вопрос):** Использовать **AppAuth для обоих** target'ов как unified path; **не зашивать GoogleSignIn SDK** в `vr`-flavor. Это устраняет необходимость в Google Play Services на Quest и упрощает Hilt-биндинг.
>
> **Открытые риски:**
> - Если Meta заблокирует system Browser intent для compliance — AppAuth поломается; mitigation — device code flow.
> - GitHub Pages downtime редок, но возможен; Cloudflare Pages как backup.
> - Если приложение начнёт собирать analytics / crash reporting — privacy policy и Data safety form необходимо обновить.

### R-13. Store assets pipeline — capture VR в качестве 1920×1080+

> **Вопрос:** Как захватить screenshots и promo video из immersive VR-сессии для submission в Meta Horizon Store и Google Play (Android XR)?
>
> **Источник:**
> - https://developers.meta.com/horizon/documentation/native/android/mobile-testing-capture/
> - https://developers.meta.com/horizon/documentation/unity/ts-mqdh/
> - https://developers.meta.com/horizon/resources/asset-guidelines/
> - https://developers.meta.com/horizon/resources/video-capture-mr-vr/
> - https://360rumors.com/quest-3-3d-videos/
> - https://blog.jamie.holdings/2022/07/01/meta-oculus-quest-2-adb-commands/
>
> **Требования assets (Meta Horizon Store):**
> - Screenshot: **2560 × 1440 px (16:9)**, 24-bit PNG, 5 штук.
> - Promo video: 1080p..2K, 30..120 сек, MP4 H.264 / AAC.
> - Trailer cover: 2560 × 1440 px, PNG.
> - Hero cover: 3000 × 900 px (10:3).
> - Cover square: 1440 × 1440 px (1:1).
> - Icon: 512 × 512 px (1:1).
>
> **Pipeline для Quest 3:**
>
> **Метод 1. Built-in sharing menu** (быстро, низкое качество).
> - Home button → Share → Take Photo / Record Video.
> - Дефолт: монокулярная картинка, **1024×1024 (square)**.
> - Изменить на **1920×1080 landscape** через Settings → Camera → Aspect Ratio = Landscape (sticky).
> - Не подходит для финальных assets.
>
> **Метод 2. Meta Quest Developer Hub (MQDH)** — рекомендуется для продакшн.
> - Connect headset → Device Actions → Capture (Ctrl+Shift+S) или Record (Ctrl+Shift+R).
> - Cast 2.0 (default) поддерживает Full-Res / Widescreen 16:9 / Cinematic 4K.
> - Screenshot/record прямо из cast-окна на PC.
> - Выходной формат — монокулярный 1920×1080 / 2560×1440 без post-processing.
> - **Лучший вариант** для Meta Horizon Store submission.
>
> **Метод 3. ADB + property override** (для специфических разрешений / стерео).
> ```
> adb shell setprop debug.oculus.capture.width 2560
> adb shell setprop debug.oculus.capture.height 1440
> adb shell setprop debug.oculus.capture.bitrate 40000000
> adb shell setprop debug.oculus.capture.fps 30
> adb shell setprop debug.oculus.foveation.level 0
> adb shell setprop debug.oculus.screenCaptureEye 2   # 0=left, 1=right, 2=both
> ```
> После reboot пропсы сбрасываются.
>
> **Метод 4. Стерео-капчер** (для 360° / 180° immersive trailer).
> - `debug.oculus.screenCaptureEye 2` → side-by-side stereo 3840×1920.
> - 360° preview (опциональный asset для Store PDP): 6×1 cubestrip или 2:1 equirectangular, max 2.3 MB.
>
> **`adb shell screencap` ограничения:**
> - `screencap -p /sdcard/x.png` на Quest **возвращает только UI-layer**, не OpenXR composition. Для VR scene — методы 1..3.
>
> **Pipeline для Android XR:**
> - **Эмулятор:** обычный `adb exec-out screencap -p` работает для Jetpack-XR-UI (spatial panels), но **не для OpenXR scene**.
> - **Физическое устройство (Samsung Galaxy XR / Pixel XR когда выйдут):** ожидается аналог MQDH-cast — Android Studio Device Mirroring уже поддерживает XR-устройства в Canary.
> - Временное решение: до релиза реального XR-железа Google Play assets для XR можно делать **с того же Quest 3 + MQDH** (контент one-eye 2560×1440 идентичен для обоих сторов).
>
> **Best practice (рекомендация):**
> - **Screenshots (5 шт, 2560×1440):** MQDH Cast 2.0 → Cinematic 4K → crop в редакторе. Соблюдать safe area (top 20%, bottom 30% свободны).
> - **Promo video (30..120s, 1080p):** MQDH в Widescreen 16:9 → монтаж в DaVinci Resolve / Premiere → H.264.
> - **Hero cover (3000×900):** композитный rendered shot или extract-frame из gameplay.
> - **Те же assets** переиспользовать на Google Play XR-track.
> - Хранить assets в `temp/store_assets/quest/` и `temp/store_assets/android_xr/`.
>
> **Открытые риски:**
> - Цвета на Quest 3 OLED отличаются от phone screen — assets могут выглядеть тускло на Store preview thumbnail.
> - Если контент включает music playback — promo video должно содержать аудио-демо.
> - debug.oculus props сбрасываются на reboot — нужно либо script, либо разовый capture-day workflow.

---

## Сводка best practice (для быстрого reference)

| R-id | Best practice | Открытый риск |
|------|---------------|----------------|
| R-01 | Переиспользовать `ic_vr_3d` + строки `vr_toggle_*` из `src/main/res/`. Восстановить welcome / fallback тексты из git history при необходимости. | Старые иконки могут не подходить под визуальный стиль v2.6 — сверять с design system. |
| R-02 | Новая вкладка `VrSettingsFragment` в `SettingsPagerAdapter` (5-я позиция); рефактор адаптера по образцу `MediaCategoryPagerAdapter`. | Все 5 текущих flavor имеют `SUPPORT_VR_PLAYER=false` — вкладка нигде не покажется до Stage 1. |
| R-03 | Восстановить `btn3dVrCmd` на старом месте в `activity_player_unified.xml`. | Проверить `res/layout-land/` counterpart; `CommandPanelLayoutPlanner` priority-list. |
| R-04 | Stage 0/1: `Bundle` extras + `PlaybackPositionRepository` (вариант B). MediaSession (C) — отдельный эпик. | DTS decoder и Cast — нужны явные обработки при switch'е. |
| R-05 | Восстановить manifest + CMake из `abc5c291~1`; flavor-блок `vr` жив. `ENABLE_OPENXR=OFF` до Stage 1. | Версия OpenXR loader в `libs.versions.toml` неизвестна; 16 KB page-size требование. |
| R-06 | Android XR: `xr.api.openxr` `required=false`; `XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED` только в `src/vr/AndroidManifest.xml`. | Developer Preview 2 — breaking changes ожидаются. |
| R-07 | Quest: `vr.headtracking required=true`, `supportedDevices="quest3\|quest3s"`, minSdk=29 в `vr` flavor. Refresh rate request 90 Hz default. | Отсутствие `quest3` в `supportedDevices` блокирует review. |
| R-08 | Эмулятор XR — только Jetpack-UI smoke-test. OpenXR — только на железе (Quest 3). | OpenXR в эмуляторе обещан «в будущих превью». |
| R-09 | Единый `vr` source set; AppAuth — Apache 2.0; Khronos loader 1.0.34+; `vrUnlicensed` — переоформить как buildType debug. | Если перейдём на Meta XR SDK (не OpenXR), split вернётся как обоснованный. |
| R-10 | Single-APK, single-track; все XR `<uses-feature required="false">`; dynamic feature module для VR-стека опционально. | Возможное требование dedicated XR-track в будущем не задокументировано. |
| R-11 | ALPHA через `ovr-platform-util` для CI; production submit за 3+ недели до релиза. | Публичного SLA нет; буфер 2..6 недель. |
| R-12 | GitHub Pages для privacy policy (HTTPS+CI). **AppAuth-Android** для Google OAuth на обоих target'ах. | Если Meta заблокирует system Browser intent — fallback на device code flow. |
| R-13 | MQDH Cast 2.0 → Cinematic 4K → crop 2560×1440. Те же assets для Quest и Android XR. | debug.oculus props сбрасываются на reboot. |

---

## Сводный list открытых рисков (для эпика S0240)

- **Android XR** пока в Developer Preview 2 — API меняется (R-06, R-08).
- **OpenXR в эмуляторе** Android XR не работает — VR-стек тестируется только на Quest 3 до выхода Samsung Galaxy XR (R-08).
- **Quest требует `minSdk = 29`** против phone `minSdk = 26` — расхождение flavor'ов (R-07).
- **Meta SLA на ревью** публично не опубликован, буфер 2..6 недель перед релизом (R-11).
- **Google OAuth на Quest** требует AppAuth-flow вместо GoogleSignIn SDK — отдельная архитектурная задача (R-12).
- **Capture stereo-видео на Quest** требует ручной debug.oculus props после каждой перезагрузки (R-13).
- **`vrUnlicensed` flavor** — лицензионных оснований для split нет; оформить как buildType debug, не отдельный flavor (R-09).
- **`SUPPORT_VR_PLAYER=false` в `vr` flavor** — текущее состояние после S0241; флип в Stage 1 после готовности рантайма (R-05).
