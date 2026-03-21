# Задача 1: Исправление виджета ResourceLaunch

**Статус:** Черновик
**Дата:** 2026-03-21
**Файлы:**
- `widget/ResourceLaunchWidgetProvider.kt`
- `widget/ResourceLaunchWidgetConfigActivity.kt`
- `res/xml/widget_resource_launch_info.xml` (или аналог — уточнить)
- `res/layout/widget_resource_launch.xml`

---

## Проблема

Текущий виджет ResourceLaunch:
- Может растягиваться на несколько ячеек (нет фиксации 1×1)
- Показывает только текст — нет иконки

---

## Требования

### Размер
Ровно **1×1 ячейка**. Указывается в `res/xml/widget_*_info.xml`:
```xml
android:minWidth="40dp"
android:minHeight="40dp"
android:maxResizeWidth="40dp"   <!-- запретить растяжение -->
android:maxResizeHeight="40dp"
android:resizeMode="none"
```
Значение `40dp` — стандартный минимум одной ячейки на Android.

### Иконка ресурса
Виджет должен показывать иконку над названием. Логика выбора иконки по `path` ресурса:

| Условие | Иконка |
|---------|--------|
| `path == "virtual://all_audio"` | иконка "Вся музыка" (ноты / музыкальная нота) |
| `path == "virtual://all_video"` | иконка "Всё видео" (видеокамера / плёнка) |
| `path == "virtual://all_images"` | иконка "Все изображения" (фото / горы) |
| `path == "virtual://all_docs"` | иконка "Все документы" (документ) |
| `path == "virtual://recent"` | иконка "Недавние" (часы) |
| `type == LOCAL` (обычная папка) | иконка папки |
| `type == SMB / SFTP / FTP` | иконка сети / сервера |
| `type == CLOUD` | иконка облака |

Виртуальные пути — константы `LocalMediaScanner.VIRTUAL_PATH_*`. Функция `VirtualPathUtils.isVirtualPath(path)` уже есть.

### Название
- Отображается под иконкой
- Обрезается по ширине с `ellipsis` если не влезает
- Одна строка максимум

### Общий вид
Типичный вид 1×1 виджета-ярлыка Android:
```
[ иконка 36-40dp ]
[ название, 1 строка ]
```

---

## Технические детали

- Виджет использует `RemoteViews` — иконку можно задать через `setImageViewResource(R.id.widget_icon, R.drawable.ic_...)`
- Сохранённые данные в `SharedPreferences("widget_prefs")`: `resource_id_$id`, `resource_name_$id`
- Нужно также сохранить `resource_path_$id` и `resource_type_$id` для определения иконки
- Конфигурационный экран `ResourceLaunchWidgetConfigActivity` нужно обновить, чтобы сохранял path и type

---

## Что НЕ меняется
- Логика клика (открывает `BrowseActivity` для ресурса) — остаётся прежней
- Конфигурационный флоу (Activity выбора ресурса) — остаётся, только добавить сохранение path/type
