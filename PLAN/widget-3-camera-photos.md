# Задача 3: Новый виджет — Camera Photos

**Статус:** Черновик
**Дата:** 2026-03-21
**Флейворы:** `standard`, `photos`, `legacy` (где `BuildConfig.SUPPORT_IMAGES == true`)

---

## Суть

Виджет открывает Browse для папки камеры в режиме сетки. Цель — стать заменой стандартной галереи для фото с камеры.

---

## Концепция «Камера как ресурс»

Виджет не хардкодит путь — он ссылается на **предопределённый ресурс "Камера"**, который создаётся при первом запуске приложения (аналогично "Вся музыка").

Путь к папке камеры на Android: `/storage/emulated/0/DCIM/Camera`
(В коде уже используется `Environment.DIRECTORY_DCIM` = `DCIM`, а папка `Camera` внутри него — стандарт для всех Android-производителей.)

### Поведение при нажатии
1. Найти в БД ресурс, связанный с папкой камеры (по `path == "/storage/emulated/0/DCIM/Camera"` или по специальному virtual path — см. ниже)
2. Если ресурс не найден — показать Toast «Ресурс "Камера" не найден» и выйти
3. Если найден — открыть `BrowseActivity` для этого ресурса в **режиме Grid** (принудительно)

---

## Предопределённый ресурс "Камера"

Нужно добавить ресурс "Камера" в `ProvisionDefaultResourcesUseCase`, по аналогии с "Вся музыка":

```kotlin
// Вариант A: обычный локальный ресурс
name = context.getString(R.string.resource_camera)
path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Camera"
type = ResourceType.LOCAL
profile = ResourceProfile.PHOTO_STORAGE
supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF)
```

**Альтернатива — Вариант B:** ввести виртуальный путь `virtual://camera`, аналогично `virtual://all_images`. Тогда `LocalMediaScanner` при встрече этого пути делает реальный запрос к DCIM/Camera. Это чище, но больше изменений.

**Предпочтительно: Вариант A** — просто создать локальный ресурс с реальным path. Проще, минимум изменений.

### Идентификация ресурса виджетом
Виджет находит ресурс по `path` (DCIM/Camera). Нужно сохранить `resource_id` ресурса-камеры при provisioning (или искать динамически по path).

---

## Принудительный Grid-режим

`BrowseActivity` нужно запустить с флагом принудительного Grid:
```kotlin
BrowseActivity.createIntent(context, resourceId).apply {
    putExtra(EXTRA_FORCE_GRID_MODE, true)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
```
Если такого extra ещё нет — добавить при реализации.

---

## Внешний вид виджета

- Размер: **1×1 ячейка**
- Иконка: камера (`ic_camera` или аналог)
- Подпись: "Camera" / "Камера"

---

## Новые файлы
- `widget/CameraPhotosWidgetProvider.kt`
- `res/xml/widget_camera_photos_info.xml`
- `res/layout/widget_camera_photos.xml`

---

## Открытые вопросы

- Вариант A или B для пути камеры? (рекомендован A)
- Как BrowseActivity принимает режим отображения — уточнить при реализации
- Что если `/DCIM/Camera` не существует (некоторые устройства используют другой путь)? Предложение: открыть `/DCIM` как fallback, или показать Toast с предложением вручную настроить ресурс.
