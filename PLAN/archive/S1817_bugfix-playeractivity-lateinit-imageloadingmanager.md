# Стратегическая спецификация: S1817 - PlayerActivity dies on every open (imageLoadingManager read before assignment)

**Ticket:** S1817
**Status:** Archived
**Priority:** 95
**Date:** 2026-08-19
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Release blocker - найдено 2026-08-19 при подготовке pre-release прогона
**Tactical spec:** inline (compact spec)

---

## 1. Goal

Вернуть возможность открыть плеер. Сейчас `PlayerActivity` падает при каждом создании: экранная половина инициализации читает `imageLoadingManager` раньше, чем привязанная к binding половина его создаёт.

---

## 2. Symptom

- Устройство `RFCR110NBQJ`, сборка `2.60.8191.752-NoLegal-DEBUG`, 2026-08-19.
- Любое открытие `PlayerActivity` даёт `FATAL EXCEPTION`, воспроизведено дважды подряд, кнопка Restart на debug-экране краша приводит к тому же самому.

```
java.lang.RuntimeException: Unable to start activity ComponentInfo{com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.player.PlayerActivity}:
kotlin.UninitializedPropertyAccessException: lateinit property imageLoadingManager has not been initialized
  at PlayerActivity.getImageLoadingManager$app_v2(PlayerActivity.kt:153)
  at PlayerManagerInitializer.initAudioAndMediaServices(PlayerManagerInitializer.kt:798)
  at PlayerManagerInitializer.constructScreenLevelOnce(PlayerManagerInitializer.kt:95)
  at PlayerManagerInitializer.initialize(PlayerManagerInitializer.kt:79)
  at PlayerActivity.initializeManagers(PlayerActivity.kt:609)
  at PlayerActivity.onCreate(PlayerActivity.kt:561)
```

- Главный экран при этом здоров: `am start -f 0x10008000 -n .../ui.main.MainActivity` поднимает `MainActivity` с нулём фатальных строк в logcat. Умер именно плеер.

## 3. Root cause (прочитано по коду, не выведено из стека)

- `PlayerManagerInitializer.initialize()` (79) выполняет сначала `constructScreenLevelOnce()`, затем `constructBindingBoundManagers()`.
- `constructScreenLevelOnce()` вызывает `initAudioAndMediaServices()` (762), где строка 798 передаёт `activity.imageLoadingManager` в конструктор `PlayerMediaLoaderManager`.
- Единственное присваивание `activity.imageLoadingManager` во всём `app_v2/src` - строка 481, внутри `initImageLoading()`.
- Единственный вызов `initImageLoading()` - строка 107, внутри `constructBindingBoundManagers()`, то есть заведомо позже.

Значит падение детерминированное и не зависит от flavor: до строки 798 flavor-специфичная инициализация Cast уже отработала.

## 4. Why the gates missed it

Ошибка порядка выполнения, а не типов. `a.ps1 fk`/`fc` компилируют и молчат, потому что `lateinit` по построению откладывает проверку на рантайм. Ни один статический гейт репозитория такой порядок не моделирует.

## 5. Fix shape (архитектура отвечает сама)

KDoc над `initialize()` задаёт правило разделения: экранная половина - менеджеры **без ссылок на view**, привязанная половина - те, кто захватывает binding. По этому же правилу `PlayerMediaLoaderManager` привязан к binding, потому что принимает `binding = activity.activityBinding`. Значит он классифицирован неверно и должен переехать в привязанную половину.

- Перенести создание `activity.mediaLoaderManager` (794) из `initAudioAndMediaServices()` в привязанную половину, после `initImageLoading()` и `initBindingBoundControlsAndOcr()`.
- Но не делать его перестраиваемым: `PlayerMediaLoaderManager` несёт seam `rebind()` (S1549), и путь ре-инфлейта в `PlayerActivity` уже вызывает его сразу после `constructBindingBoundManagers()`. Значит менеджер остаётся «раз на экран», а новое место требует защиты `if (activity.isMediaLoaderManagerInitialized) return`, иначе ре-инфлейт построит второй экземпляр и осиротит подключение к аудио-сервису.
- Единственное прямое чтение `activity.mediaLoaderManager` вне лямбд - строка 929 в `initUiCoordinators()`, который в привязанной половине идёт после `initBindingBoundMediaServices()`, поэтому порядок сохраняется. Чтения на 386/391 и 885/891 лежат внутри колбэков и выполняются позже.
- Перед правкой проверить, что к моменту переноса уже существуют `videoPlayerManager`, `exoPlayerControlsManager`, `loadingIndicatorHandler`, `mediaFilesCacheManager`, `audioServiceController`.

## 6. Открытые вопросы / Research items

- **6.1 Соседняя мисклассификация.** `activity.nowPlayingManager` остаётся в экранной половине. Он несёт тот же seam `rebind()`, и путь ре-инфлейта вызывает `nowPlayingManager?.rebind(activityBinding)` в `PlayerActivity` до `constructBindingBoundManagers()`, то есть захваченный binding перенаводится, а не протухает. Перенос в перестраиваемую половину, наоборот, поднял бы второй экземпляр на каждый ре-инфлейт. Status: Resolved.
- **6.2 Защита от повтора.** Механический гейт не заводим. Чтобы поймать «чтение lateinit до присваивания» между двумя половинами, нужен межпроцедурный анализ потока данных, которого нет ни в detekt, ни в lint-правилах репозитория; grep-гейт на такое даёт ложные срабатывания на каждой лямбде. Инвариант закреплён иначе: защитой `isMediaLoaderManagerInitialized` и KDoc на `initMediaLoaderOnce()`, который называет обе половины контракта. Второй случай того же дефекта переводит решение в гейт по правилу «повторяющаяся находка -> механический гейт». Status: Resolved.
- **6.3 Область поражения.** Других синхронных чтений привязанных полей из экранной половины нет. Проверены обе функции, которые до правки не успевали выполниться: `initScreenLevelUiCoordinators()` и `initPrefetchManager()` не читают `imageLoadingManager`, `exoPlayerControlsManager` и `mediaLoaderManager`. Единственная ссылка на `exoPlayerControlsManager` из объекта, созданного в экранной половине, лежит в `PlayerVrLaunchManager.bind()` внутри блока `repeatOnLifecycle(STARTED).collect`, то есть выполняется заведомо позже обеих половин. Status: Resolved.

## 7. Acceptance

- `PlayerActivity` открывается на устройстве для изображения, аудио, видео и текста без фатальных строк.
- Пересборка debug для телефона и повторная проверка на `RFCR110NBQJ`.
- Экранная и привязанная половины остаются согласованы с KDoc над `initialize()`.

## 8. Implementation State

- `PlayerManagerInitializer.kt`: конструирование `PlayerMediaLoaderManager` вынесено из `initAudioAndMediaServices()` в новый `initMediaLoaderOnce()` с защитой `if (activity.isMediaLoaderManagerInitialized) return`.
- Вызов добавлен в `constructBindingBoundManagers()` между `initBindingBoundControlsAndOcr()` и `initBindingBoundMediaServices()`: к этому моменту `imageLoadingManager` (создан в `initImageLoading()`) и `exoPlayerControlsManager` (создан в `initBindingBoundControlsAndOcr()`) уже присвоены, а `initUiCoordinators()` со своим прямым чтением идёт следом.
- Остальные аргументы конструктора проверены на готовность: `videoPlayerManager` - ленивый геттер, `loadingIndicatorHandler` - поле с инициализатором, `mediaFilesCacheManager` - `@Inject lateinit` от Hilt, `audioServiceController` - создан в экранной половине.
- Путь ре-инфлейта не тронут: `PlayerActivity` по-прежнему вызывает `mediaLoaderManager.rebind(..)` сразу после `constructBindingBoundManagers()`, а защита не даёт построить второй экземпляр.

## 9. Проверка на устройстве (2026-08-19)

Сборка `2.60.8191.851-NoLegal-DEBUG`, устройство `RFCR110NBQJ`, установка поверх существующей (данные сохранены), версия подтверждена водяным знаком на экране источников.

- Картинка: `CAP_20260628_013337_2.jpg (127/245)` открыта в плеере, тулбар и панели «Копировать в..» / «Переместить в..» отрисованы.
- Видео: `Screen_Recording_20260510_153846_FastMediaSorter.mp4 (188/245)` открыто, кадр отрисован, транспортные кнопки на месте.
- Аудио: трек «Падал тёплый снег» играет, обложка альбома загружена, виниловый индикатор виден. Это самая показательная проверка: обложку грузит `imageLoadingManager.loadAudioCoverArt` из колбэка `onAudioServiceReady` того самого `PlayerMediaLoaderManager`, конструирование которого переносилось.
- Текст: `wd.xml (28/245)` открыт в просмотрщике текста с подсветкой синтаксиса, видны поиск и переключатель языка - то есть тулбар текстового варианта, а не общий.
- Логи: захват 316 004 байт (`temp/scratch/adb_log_20260819_190434.log`) содержит 69 упоминаний `PlayerActivity` и ноль строк `FATAL EXCEPTION` / `UninitializedPropertyAccessException`. Отдельная проверка окна в 5 000 строк - тот же результат.
- `topResumedActivity` фиксировал `PlayerActivity` как активное окно в момент проверки, чего до правки не случалось ни разу.

Каждый пункт прочитан со скриншота напрямую, а не принят со слов драйвера устройства.
