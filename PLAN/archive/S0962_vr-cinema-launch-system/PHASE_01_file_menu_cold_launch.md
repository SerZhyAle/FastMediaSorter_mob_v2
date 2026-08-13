# S0962 PHASE_01 - меню видеофайла + холодный FILE_URI-вход

## Цель

Пункт «Открыть в VR-кинотеатре» в ⋮-меню видеофайла Browse, гейтируемый VR-доступностью, открывающий immersive напрямую на файле без обычного плеера.

## Операции

> **Дизайн уточнён при реализации (2026-07-06):** чтобы не менять сигнатуры god-методов `showFor` и конструктора `BrowseManagerInitializer` (смена сигнатуры воскрешает их забейслайненные LongParameterList/CyclomaticComplexMethod - S0826), хелпер инжектится напрямую в `BrowseFileOverflowMenuManager` и сам берёт `LifecycleOwner` из `@ActivityContext`. `BrowseManagerInitializer` и `BrowseActivity` не трогаются. Итог - 2 файла.

### 1. Новый хелпер запуска (business logic вне UI)

Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseVrCinemaLaunchManager.kt` (новый, `@ActivityScoped`).

- Inject: `@ActivityContext Context` (= host Activity, `LifecycleOwner`), `XrDetectionFacade`, `StartVrPlaybackUseCase`.
- `@Volatile private var latestState: XrDetectionState = NONE`.
- `init { (context as? LifecycleOwner)?.let { owner -> owner.lifecycleScope.launch { owner.repeatOnLifecycle(STARTED) { detectionFacade.state().collect { latestState = it } } } } }` - самонаблюдение, без внешнего `bind()`.
- `val isAvailable: Boolean get() = latestState == XrDetectionState.AVAILABLE_ENABLED`.
- `fun launch(file: MediaFile)` (owner берётся из `context`):
  - `Timber.d("S0962: ...")` probe (пока спека BlockNeedUserTest); info/warn-строки БЕЗ `S0962:` (ticket-log gate).
  - `request = StartVrPlaybackRequest(FILE_URI, file.toLaunchUriString(), VIDEO, source = VrLaunchPoint.BROWSE_TILE, snapshot = null)`.
  - `when (startVrPlaybackUseCase(request, returnTarget = null))`: `Started` -> no-op; `Unavailable`/`Failed` -> `Toast` R.string.vr_cinema_launch_unavailable.

### 2. Точка вставки пункта меню + инъекция

Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt`.

- В конструктор добавить `private val vrCinemaLaunchManager: BrowseVrCinemaLaunchManager` (2 параметра итого - порога нет).
- Внутри `showFor` (тело, БЕЗ смены сигнатуры) после блока «Open» добавить 2-условный гейт (>2 условий дало бы ComplexCondition):
  `if (file.type == MediaType.VIDEO && vrCinemaLaunchManager.isAvailable) items += MenuItem(getString(R.string.action_open_in_vr_cinema)) { vrCinemaLaunchManager.launch(file) }`.

### 3. Обвязка вызова

Не требуется: `BrowseFileOverflowMenuManager` уже инжектится в Browse; хелпер приходит с ним по DI. `BrowseManagerInitializer`/`BrowseActivity` не изменяются.

### 4. Строки (EN/RU/UK, через set-android-string.ps1 -Action add)

- `action_open_in_vr_cinema`: EN «Open in VR Cinema» / RU «Открыть в VR-кинотеатре» / UK «Відкрити в VR-кінотеатрі».
- `vr_cinema_launch_unavailable`: EN «VR Cinema is unavailable right now» / RU «VR-кинотеатр сейчас недоступен» / UK «VR-кінотеатр зараз недоступний».

## Флейвор-корректность

- Инъекция `XrDetectionFacade`+`StartVrPlaybackUseCase` в `src/main` -> на `standard`/`lite`/`photos`/`legacy` резолвится в No-Op (facade -> NONE -> `isAvailable=false` -> пункт скрыт). Флейвор-гардов в `src/main` нет (Rule 14).

## Валидация

- `.\a.ps1 fkn` (компиляция noLegal, где VR включён) - ожидается BUILD SUCCESSFUL.
- `.\a.ps1 fc` (стандартный: код+ресурсы, проверяет No-Op путь и строки) - BUILD SUCCESSFUL.
- detekt по затронутым файлам через `post-change.ps1 -ScopeToFile` - без новых findings.
- On-device (Quest 3, `noLegal`): ⋮ на видеофайле -> «Открыть в VR-кинотеатре» -> immersive на файле; выключить VR-3D -> пункт исчезает. (BlockNeedUserTest - probe `S0962`.)

## Probe (CLAUDE.md §2)

- `Timber.d("S0962: ...")` один на входе `BrowseVrCinemaLaunchManager.launch` - существует, пока спека `BlockNeedUserTest`; удалить при выходе из блока.
