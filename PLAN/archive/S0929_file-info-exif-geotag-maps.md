# S0929 - EXIF в диалоге «Информация о файле»: время съёмки и геометка-ссылка

**Ticket:** S0929
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-04
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-04

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

Диалог "Информация о файле": из EXIF показывать дату/время съёмки и координаты (геометку). Координаты должны выглядеть как ссылка (нажимаемые) - при нажатии запускается приложение карт по умолчанию (Google Maps) для координат. Если такого приложения нет - открывается браузер по умолчанию.

---

## Goal

В диалоге «Информация о файле» для изображений надёжно показывать из EXIF дату/время съёмки и GPS-координаты. Координаты - кликабельная ссылка: тап открывает приложение карт по умолчанию (через `geo:`-intent), при отсутствии карт - браузер по умолчанию (https-ссылка на карту), иначе - Toast «нет приложения карт».

Проблема: инфраструктура частично есть, но для **локальных** фото (MediaStore пишет `exif*=null`) дата съёмки в диалоге не извлекается вовсе, а кликабельная геометка (`tvGpsLocation`) лежит в видео-секции и для изображений не видна. Портрет и ландшафт рассинхронизированы (в ландшафте `tvGpsLocation` вне секции -> потенциальный дубль GPS).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- **UI-решение:** геометка и дата съёмки живут в существующей EXIF-секции диалога `dialog_file_info` (портрет+ландшафт). Кликабельная геометка = `tvExifGPS`, стиль-ссылка через `?attr/colorPrimary`. Видео-GPS остаётся отдельным `tvGpsLocation`, гейт по типу VIDEO.
- **Data-решение:** дата съёмки и GPS для локальных фото добираются в асинхронном пути `MediaMetadataHelper.getDetailedInfo`, fallback к уже заполненным `MediaFile.exif*` (сетевой путь).

---

## Фазы

### Фаза 1 - EXIF: дата съёмки в детальных метаданных

1. В `core/util/MediaMetadataHelper.kt` добавить поле `dateTimeTaken: Long?` в `DetailedMediaInfo`.
2. В `extractImageInfo(..)` читать `ExifInterface.TAG_DATETIME_ORIGINAL` с fallback на `TAG_DATETIME`, парсить в millis приватным хелпером (`SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)`), вернуть в `DetailedMediaInfo`.
3. **Verification:** `.\a.ps1 fk` -> BUILD SUCCESSFUL; grep `dateTimeTaken` присутствует в поле и в присваивании.

### Фаза 2 - Запуск карт с fallback (launch manager)

1. В `ui/dialog/helpers/FileInfoLaunchManager.kt` добавить `openLocationInMaps(latitude: Double, longitude: Double)`:
   - `geo:<lat>,<lng>?q=<lat>,<lng>` через `ACTION_VIEW` (только приложения карт);
   - при `ActivityNotFoundException` - `https://www.google.com/maps/search/?api=1&query=<lat>,<lng>` (браузер);
   - при повторной неудаче - Toast `no_maps_app_available`.
2. Приватный `tryStart(intent): Boolean` инкапсулирует `startActivity` + перехват `ActivityNotFoundException`.
3. **Verification:** `.\a.ps1 fk` -> BUILD SUCCESSFUL; grep `openLocationInMaps`, `geo:`, `maps/search`.

### Фаза 3 - Диалог: показ и кликабельность

1. В `ui/dialog/FileInfoDialog.kt`:
   - приватный `bindGpsLink(view, lat, lng)` - `isClickable/isFocusable=true`, снять `textIsSelectable`, `setOnClickListener { launchManager.openLocationInMaps(lat, lng) }`.
   - приватный `showExifGpsLink(lat, lng)` - текст `exif_gps_label` + `formatGPS`, visible, `bindGpsLink`.
   - `displayExifInfo()`: для GPS вызывать `showExifGpsLink(..)` вместо простого текста.
   - `updateDetailedInfo(details)`: для `IMAGE`/`GIF` - если `mediaFile.exifDateTime == null && details.dateTimeTaken != null`, показать `tvExifDateTime` через `formatDate`; если `details.latitude/longitude != null`, вызвать `showExifGpsLink(..)`.
   - Блок `tvGpsLocation` гейтить на `MediaType.VIDEO`, клик - через `bindGpsLink`/`launchManager.openLocationInMaps` (убрать inline-`Intent`).
   - Удалить неиспользуемые импорты (`ActivityNotFoundException`, `Toast`, `Intent`-fully-qualified) после переноса логики.
2. XML `res/layout/dialog_file_info.xml` **и** `res/layout-land/dialog_file_info.xml`: у `tvExifGPS` добавить `android:textColor="?attr/colorPrimary"` (аффорданс ссылки).
3. **Verification:** `.\a.ps1 fc` -> BUILD SUCCESSFUL.

### Фаза 4 - Debug-теги, сборка, блок на устройство

1. Вставить `Timber.d("S0929: ..")` на входах изменённых потоков (тап-геометка; async-заполнение EXIF для изображения).
2. `.\a.ps1 d` -> BUILD SUCCESSFUL (валидирует код + теги).
3. Статус -> `BlockNeedUserTest` (нужна проверка на устройстве: тап открывает карты, fallback в браузер, дата/GPS для локального фото).

---

## Критерии готовности

1. Для локального фото с EXIF диалог показывает дату съёмки и GPS даже при `rememberFileList=off` и сортировке не по дате.
2. GPS-координаты выглядят как ссылка (`colorPrimary`) и реагируют на тап/D-pad.
3. Тап по координатам открывает приложение карт по умолчанию; при его отсутствии - браузер; при отсутствии обоих - Toast.
4. Нет дубля GPS-строки в ландшафте.
5. Сетевой (SMB) путь показа EXIF не сломан.

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: в информации о файле - дата съёмки и кликабельная геометка (открытие карты).

- **EN:** File info now shows the photo's capture date and a tappable GPS location that opens your default maps app.
- **RU:** В информации о файле теперь видны дата съёмки и кликабельная геометка, открывающая приложение карт по умолчанию.
- **UK:** В інформації про файл тепер видно дату зйомки та клікабельну геомітку, що відкриває типовий застосунок карт.
