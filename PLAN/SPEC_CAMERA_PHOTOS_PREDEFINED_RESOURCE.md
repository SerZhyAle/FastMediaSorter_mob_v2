# Задание для разработчика: Предопределённый ресурс «Фото с камеры» (Camera Photos)

**Статус**: Готово к реализации
**Флейвор**: только `standard`, `legacy`, `photos` (где `BuildConfig.SUPPORT_IMAGES == true`)

---

## 1. Суть задачи и проблематика

В приложении существуют автоматические виртуальные ресурсы (Recent Media, All Music и т.д.). В данный момент ресурс **Camera** создаётся как обычный физический путь (`/storage/emulated/0/DCIM/Camera`), что вызывает ряд проблем:
1. **Нет кнопки создания** в диалоге добавления виртуальных ресурсов.
2. **Путь редактируем** — пользователь может случайно изменить путь в настройках, так как редактор не блокирует физические пути.
3. **Путь задан хардкодом** — это ненадёжно, на некоторых устройствах Android папка камеры находится в другом месте или называется иначе.
4. При удалении ресурса ломается виджет встроенной камеры (`CameraPhotosWidgetProvider`).

**Цель:** Сделать ресурс «Camera» полноценным виртуальным ресурсом первого класса (`virtual://camera_photos`), который будет динамически находить реальную папку камеры через MediaStore и иметь заблокированные для редактирования системные поля настроек.

---

## 2. Что должно быть сделано (Требования)

1. **Новый виртуальный путь:** `virtual://camera_photos`.
2. **Параметры ресурса:** Имя берётся из ресурсов (`R.string.virtual_camera_photos`), режим отображения `GRID`, сортировка `DATE_DESC`.
3. **Поддерживаемые медиа:** `IMAGE`, `GIF` (а также `VIDEO`, если `settings.supportVideos == true`).
4. **Поиск папки:** Динамический поиск папки "Camera" через запрос к `MediaStore` (к таблице `Images.Media`).
5. **UI (Добавление ресурса):** В диалог добавления папок (`dialog_folder_selection.xml`) должна быть добавлена новая кнопка "Camera Photos".
6. **Миграция старых пользователей:** Для существующих пользователей физический путь `/storage/emulated/0/DCIM/Camera` в таблице ресурсов БД должен быть программно заменён на `virtual://camera_photos` при старте приложения.

---

## 3. Задействованные объекты и детали реализации

### 3.1. Константы и утилиты
* **`LocalMediaScanner.kt`**:
  * Добавить: `const val VIRTUAL_PATH_CAMERA_PHOTOS = "virtual://camera_photos"`.
  * Удалить старую хардкоженную константу `CAMERA_FOLDER_PATH`.
* **`VirtualPathUtils.kt`**:
  * Добавить `VIRTUAL_PATH_CAMERA_PHOTOS` во множество `ALL_VIRTUAL_PATHS`. Это автоматически заблокирует редактирование пути и типов медиа в `ResourceEditorFragment` (что и требуется).

### 3.2. Слой данных (Media Store)
* **`MediaStoreRepository.kt` (интерфейс)**:
  * Добавить `suspend fun findCameraFolderPath(): String?`.
* **`MediaStoreRepositoryImpl.kt`**:
  * Реализовать `findCameraFolderPath()`. Сделать запрос с условием `MediaStore.Images.Media.BUCKET_DISPLAY_NAME = "Camera"`, отсортировать по количеству файлов/дате (чтобы приоритизировать основную камеру), вернуть извлеченный путь папки из `DATA` или `RELATIVE_PATH` первой записи.
  * В качестве fallback (если запрос ничего не дал) возвращать классический путь `"/storage/emulated/0/DCIM/Camera"`.
* **`LocalMediaScanner.kt` (`scanFolder()`)**:
  * Добавить `when` ветку для `VIRTUAL_PATH_CAMERA_PHOTOS`. Внутри вызвать `mediaStoreRepository.findCameraFolderPath()`, и если путь получен, то просканировать его через стандартные функции сканера (передав найденный физический путь).

### 3.3. Бизнес-логика (Use Cases)
* **`ProvisionDefaultResourcesUseCase.kt`**:
  * Найти старый блок создания физического ресурса Camera. Заменить его блок на генерацию виртуального (использовать новый `VIRTUAL_PATH_CAMERA_PHOTOS`, `displayMode = DisplayMode.GRID`, `sortMode = SortMode.DATE_DESC`).
* **`ScanLocalFoldersUseCase.kt`**:
  * Добавить аналогичный блок создания для `VIRTUAL_PATH_CAMERA_PHOTOS`, если его ещё нет, при условии `BuildConfig.SUPPORT_IMAGES`.
* **`MigrateCameraResourceUseCase.kt` (НОВЫЙ ФАЙЛ)**:
  * Создать новый UseCase (`domain/usecase/MigrateCameraResourceUseCase.kt`).
  * **Алгоритм**: получить все ресурсы из БД. Если найден ресурс с `path == "/storage/emulated/0/DCIM/Camera"` — обновить его `path` на `VIRTUAL_PATH_CAMERA_PHOTOS` и `sortMode` на `DATE_DESC`, сохранив в БД. Залогировать успешную миграцию в *Timber*.

### 3.4. UI (View Models и экраны)
* **`MainViewModel.kt`**:
  * Заинжектить `MigrateCameraResourceUseCase`.
  * В блоке инициализации (`init {}`) добавить вызов `migrateCameraResourceUseCase()` **строго после** вызова `provisionDefaultResourcesUseCase()`.
  * В методе `openCameraPhotos()` изменить сравнение пути ресурса с удалённой константы на `LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS`.
* **`AddResourceViewModel.kt`**:
  * В методе `buildVirtualResource()` добавить ветку для `VIRTUAL_PATH_CAMERA_PHOTOS`. Вернуть конфигурацию (Triple/соответствующий объект): Имя, типы медиа (зависят от настроек `supportImages`/`Gifs`/`Videos`), `ResourceProfile.PHOTO_STORAGE`. Установить `displayMode = DisplayMode.GRID` и `sortMode = SortMode.DATE_DESC`.
* **`AddResourceActivity.kt`**:
  * Добавить айдишник новой кнопки `R.id.btnVirtualCameraPhotos` и её путь в маппинг кнопок виртуальных папок.
  * Если `!BuildConfig.SUPPORT_IMAGES`, скрыть `btnVirtualCameraPhotos` (`isVisible = false`).
* **`dialog_folder_selection.xml`**:
  * Добавить новую кнопку `btnVirtualCameraPhotos` (стиль `OutlinedButton`, `app:iconSize="@dimen/icon_size_menu"`) в раздел "Special Virtual Folders". Расположить эстетично рядом с другими кнопками.

### 3.5. Виджет и иконки
* **`ResourceLaunchWidgetProvider.kt`**:
  * В логике определения иконки добавить `R.drawable.ic_resource_local` (или `ic_camera` если уже есть в ресурсах для локальных папок) в маппинг для `VIRTUAL_PATH_CAMERA_PHOTOS`.

### 3.6. Строки локализации
(Строку `resource_camera` **НЕ удалять**, она используется в других местах)
* В `res/values/strings.xml`: `<string name="virtual_camera_photos">Camera Photos</string>`
* В `res/values-ru/strings.xml`: `<string name="virtual_camera_photos">Фото с камеры</string>`
* В `res/values-uk/strings.xml`: `<string name="virtual_camera_photos">Фото з камери</string>`

---

## 4. Порядок выполнения (Чек-лист)

- [ ] **1. Рефакторинг констант путей**: В `LocalMediaScanner` добавить `VIRTUAL_PATH_CAMERA_PHOTOS`, удалить старую физическую константу. В `VirtualPathUtils` добавить путь в `ALL_VIRTUAL_PATHS`.
- [ ] **2. Поиск реальной папки камеры**: Написать метод `findCameraFolderPath()` в `MediaStoreRepository` и реализовать его в `MediaStoreRepositoryImpl` (с fallback'ом на стандартный DCIM/Camera).
- [ ] **3. Реализация сканирования**: В `LocalMediaScanner.scanFolder()` добавить блок для `VIRTUAL_PATH_CAMERA_PHOTOS`, который выполняет сканирование по физическому пути, полученному из `MediaStoreRepository`.
- [ ] **4. Обновление создания по умолчанию**: В `ProvisionDefaultResourcesUseCase` и `ScanLocalFoldersUseCase` заменить создание хардкодного пути на генерацию нового виртуального ресурса.
- [ ] **5. Разработка UseCase миграции**: Создать и реализовать `MigrateCameraResourceUseCase`.
- [ ] **6. Подключение логики во ViewModels**: В `MainViewModel` добавить вызов миграции в процесс инициализации; обновить поиск ресурса в `openCameraPhotos()`. В `AddResourceViewModel` добавить ветку сборки виртуального ресурса.
- [ ] **7. Добавление строк локализации**: Прописать `virtual_camera_photos` в словари (EN, RU, UK).
- [ ] **8. Обновление UI добавления ресурса**: Вставить кнопку в `dialog_folder_selection.xml` и добавить её обработку / сокрытие по флейворам в `AddResourceActivity`.
- [ ] **9. Обновление иконки виджета**: Настроить иконку для нового виртуального пути в `ResourceLaunchWidgetProvider`.
- [ ] **10. Написание Unit-тестов**:
    - [ ] Обновить `ProvisionDefaultResourcesUseCaseTest` (гарантировать создание ресурса с `GRID` и `DATE_DESC`).
    - [ ] Создать `MigrateCameraResourceUseCaseTest` (для проверки процесса миграции старого пути).
- [ ] **11. Changelog**: Задокументировать все изменения в `dev/CHANGELOG.md` через скрипт `add_to_dev_log.ps1` (согласно правилу №6 AGENTS.md).
- [ ] **12. Features docs**: Добавить информацию о новом виртуальном ресурсе Camera Photos в `docs/FEATURES.md`, `FEATURES_RU.md`, `FEATURES_UK.md` (согласно правилу №7).
- [ ] **13. Проверка линтера**: Исключить / исправить Warning'и в затронутых файлах.

---

## 5. План тестирования

1. **Новая установка:** При сбросе кэша/чистой установке ресурс "Camera Photos" создался корректно. Поля настроек "Путь" и "Типы файлов" в редакторе скрыты (read-only).
2. **Сканирование:** При сканировании находятся фото с устройства.
3. **Обновление БД:** Виртуальный мигратор работает — старый ресурс с путём `/storage/emulated/0/DCIM/Camera` превратился в виртуальный (`virtual://camera_photos`), сохраняя свою позицию и ID. Возвращены корректные параметры Grid & Date Desc.
4. **Виджет:** При нажатии на виджет встроенной камеры, ресурс открывается без сообщения "Resource not found".
5. **Диалог добавления:** Кнопка "Camera Photos" доступна в разделе "Special Virtual Folders". При нажатии в список ресурсов добавляется правильно сконфигурированный ресурс.
