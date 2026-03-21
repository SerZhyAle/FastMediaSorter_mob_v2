# Задача 2: Новый виджет — Random Play Music

**Статус:** Черновик
**Дата:** 2026-03-21
**Флейворы:** только `standard` и `legacy` (где `BuildConfig.SUPPORT_AUDIO == true`)

---

## Суть

Одним тапом запустить воспроизведение всей музыки в случайном порядке. Это не ярлык на Browse — это прямой запуск плеера с немедленным воспроизведением.

---

## Поведение при нажатии

1. Найти в БД ресурс с `path == "virtual://all_audio"` (`LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO`)
2. Если такого ресурса нет (пользователь удалил) — показать Toast «Ресурс "Вся музыка" не найден» и выйти
3. Если ресурс есть — запустить `PlayerActivity` с этим ресурсом:
   - Режим воспроизведения: **Shuffle**, принудительно при старте
   - Воспроизведение начинается **немедленно** (autoplay)
4. Дальнейшее поведение — стандартное поведение приложения:
   - Пользователь ходит вперёд/назад по списку треков в плеере
   - Фоновое воспроизведение через `AudioPlaybackService` — согласно настройке пользователя (нового поведения не вводим)
   - При закрытии плеера — оказывается в `BrowseActivity` для ресурса "Вся музыка" (стандартное поведение PlayerActivity)

**Важно:** никакого нового поведения в PlayerActivity не добавляем. Только специфичный запуск: конкретный ресурс + shuffle + autoplay.

---

## Внешний вид

- Размер: **1×1 ячейка**
- Иконка: отражает «музыка + перемешивание» — предположительно иконка shuffle с нотой, или просто иконка shuffle. Уточнить с дизайном.
- Подпись: строка из ресурсов (EN: "Random Music", RU: "Случайная музыка")

---

## Реализация

### Новые файлы
- `widget/RandomMusicWidgetProvider.kt` — провайдер виджета
- `res/xml/widget_random_music_info.xml` — метаданные виджета (1×1, без конфигурации)
- `res/layout/widget_random_music.xml` — лэйаут (иконка + подпись)

### Регистрация в AndroidManifest
```xml
<receiver android:name=".widget.RandomMusicWidgetProvider" ...>
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/widget_random_music_info"/>
</receiver>
```
Обернуть в `if (BuildConfig.SUPPORT_AUDIO)` — через product flavor или BuildConfig check в коде.

### Запуск PlayerActivity
Нужно изучить существующий механизм запуска PlayerActivity с ресурсом и добавить флаг shuffle+autoplay. Предположительно через Intent extra:
```kotlin
PlayerActivity.createIntent(context, resourceId).apply {
    putExtra(EXTRA_SHUFFLE_ON_START, true)
    putExtra(EXTRA_AUTOPLAY, true)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
```
Точные ключи Intent — уточнить по существующему коду PlayerActivity.

### Поиск ресурса в БД
Виджет-провайдер работает в BroadcastReceiver, без ViewModel. Нужно обращаться к БД напрямую через Room или через coroutine в `goAsync()`:
```kotlin
override fun onUpdate(...) {
    val scope = CoroutineScope(Dispatchers.IO)
    val result = goAsync()
    scope.launch {
        // Room query: найти ресурс по path = VIRTUAL_PATH_ALL_AUDIO
        result.finish()
    }
}
```

---

## Открытые вопросы

- Точные Intent extras для запуска PlayerActivity с shuffle+autoplay — изучить при реализации
- Иконка: использовать существующий `ic_shuffle` или создать отдельную для виджета?
