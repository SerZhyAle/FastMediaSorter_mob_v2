# Спецификация: Анимация пустой обложки аудио (Audio Empty-State Animation)

## 1. Описание задачи
Добавить новую настройку в раздел **Настройки -> Медиа -> Аудио**, позволяющую пользователю выбирать тип анимации на экране плеера при отсутствии встроенной обложки у аудиофайла и при отсутствии обложек в папке (или при отключенном поиске онлайн).

Настройка называется: **"Эквалайзер в отсутствии картинки"**.

### Доступные режимы (Dropdown / AutoCompleteTextView):
1. **"фон экрана плеера черный"** (`NONE` / `BLACK_BACKGROUND`) - поведение по умолчанию.
2. **"иконка ноты + 2-3 тонких кольца/ореола, медленно пульсирующих"** (`AVD_PULSE`) - пульсация через AnimatedVectorDrawable или `ViewPropertyAnimator`.
3. **"дышащие столбики (9)"** (`CANVAS_BARS`) - кастомный `View` с 9 анимированными столбиками (синусоида/рандом в стиле эквалайзера).
4. **"GIF/WebP loop"** (`GIF_LOOP`) - зацикленное воспроизведение предустановленного GIF-файла (`Video_je_spreman_.gif`).

---

## 2. Изменения в слое данных (Data Layer)

### 2.1. `SettingsManager.kt` и `AppSettings.kt`
- В `AppSettings` добавить новое поле: `val audioEmptyStateMode: String = "NONE"`.
- В `SettingsManager`:
  - Добавить ключ: `private val AUDIO_EMPTY_STATE_MODE = stringPreferencesKey("audio_empty_state_mode")`
  - Добавить чтение: `audioEmptyStateMode = preferences[AUDIO_EMPTY_STATE_MODE] ?: "NONE"`
  - Добавить метод сохранения: `suspend fun setAudioEmptyStateMode(value: String)`.

---

## 3. Изменения в слое UI (Настройки)

### 3.1. Ресурсы (`strings.xml`, `arrays.xml`)
- Добавить строку заголовка: `<string name="audio_empty_state_label">Эквалайзер в отсутствии картинки</string>`
- Добавить массив `audio_empty_state_entries` для отображения в UI:
  - "фон экрана плеера черный"
  - "иконка ноты + 2-3 тонких кольца/ореола, медленно пульсирующих"
  - "\"дышащие\" столбики (9)"
  - "GIF/WebP loop"
- Добавить массив `audio_empty_state_values` ключей: `NONE`, `AVD_PULSE`, `CANVAS_BARS`, `GIF_LOOP`.

### 3.2. Макет `fragment_settings_audio.xml`
Добавить новый блок (LinearLayout) с:
- `TextView` для заголовка (`@string/audio_empty_state_label`).
- `TextInputLayout` с вложенным `AutoCompleteTextView` (Material Exposed Dropdown Menu) для выбора режима.

### 3.3. Логика `SettingsViewModel` и `SettingsFragment` (Аудио)
- Прокинуть `audioEmptyStateMode` из `SettingsManager` до UI.
- Настроить адаптер для `AutoCompleteTextView` используя ресурсы массивов.
- Сохранять выбранное значение при смене пункта через вызов метода во ViewModel.

---

## 4. Изменения в экране плеера (Player Layer)

### 4.1. Подготовка ресурсов (Активы)
- Файл `C:\GIT\FastMediaSorter_mob_v2\temp\assets\Video_je_spreman_.gif` должен быть перемещен в `app_v2/src/main/res/raw/` (или `drawable/`) с именем `raw_audio_empty_state.gif` (в нижнем регистре без спецсимволов).
- Создать кастомную `View` для режима `CANVAS_BARS` (например, `AudioBreathingBarsView.kt`), рисующую 9 столбиков через `onDraw`.
- Создать логику/вектор (или использовать `ViewPropertyAnimator`) для режима `AVD_PULSE`.

### 4.2. Макет `activity_player_unified.xml` (вариант для аудио)
- Добавить контейнер/вьюхи поверх или контейнер-заменитель `audioCoverArtView` в области `mediaContentArea`.
- При необходимости добавить:
  1. `AudioBreathingBarsView` (по умолчанию `gone`).
  2. `ImageView` для GIF (по умолчанию `gone`).
  3. Существующий `audioCoverArtView` будет использоваться для `NONE` и для `AVD_PULSE`.

### 4.3. Менеджер отображения (`ImageLoadingManager.kt` или аналог)
- Метод `loadAudioCoverArt()` должен учитывать настройку `audioEmptyStateMode` в ветке фоллбэка.
  - Если режим `NONE`: отображать `ic_music_note` статично, прятать остальные вьюхи анимации.
  - Если режим `AVD_PULSE`: отображать `ic_music_note` и запускать бесконечный `animate().scaleX(...).scaleY(...)` или стартовать AnimatedVectorDrawable. Остальные прятать.
  - Если режим `CANVAS_BARS`: прятать `ic_music_note`, показывать `AudioBreathingBarsView`, вызывать метод старта анимации (внутри `AudioBreathingBarsView`).
  - Если режим `GIF_LOOP`: прятать `ic_music_note` и Canvas-bars, показывать `ImageView` для GIF и загружать `raw_audio_empty_state.gif` через Glide.

### 4.4. Жизненный цикл анимации (Синхронизация с ExoPlayer)
- Анимация не должна проигрываться вечно (расход батареи).
- Слушать `Player.Listener.onIsPlayingChanged(isPlaying: Boolean)`.
- Если `isPlaying == true`, запускать выбранную анимацию (возобновлять Glide, продолжать `ValueAnimator` у Canvas, продолжать масштабирование ноты).
- Если `isPlaying == false` (пауза, стоп, буферизация), ставить анимацию на паузу.

---

## 5. Порядок реализации (Чеклист разработчика)
1. Перенести `Video_je_spreman_.gif` в ресурсы `res/raw`.
2. Обновить `AppSettings.kt` и `SettingsManager.kt`.
3. Добавить UI-элементы в `fragment_settings_audio.xml` и ресурсы строк/массивов.
4. Связать настройки в соответствующем фрагменте/activity настроек.
5. Реализовать `AudioBreathingBarsView` (Canvas, ObjectAnimator/ValueAnimator на onDraw).
6. Внедрить новые View в макет плеера (опционально - в `ViewStub` для производительности, так как нужны не всегда).
7. Добавить логику переключения в менеджер загрузки обложек.
8. Добавить наблюдение за `isPlaying` из лобби ExoPlayer для паузы/старта анимаций. 
9. Проверить отсутствие утечек памяти и чрезмерной утечки CPU при длительной работе эквалайзера.
