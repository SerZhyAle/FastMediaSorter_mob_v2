# APK Install from Browse — noLegal Research

**Direction:** APK install flow из Browse (S0156 §5.1 Столп E)
**Epic:** S0156
**Date:** 2026-05-13

---

## Что найдено

### Текущее состояние в приложении

При нажатии на `.apk` файл в Browse открывается `BottomSheetDialog` через `BrowseBinaryFileHandler.showBinaryFileMenu`. Доступные действия: Share, Open With, Copy, Move, Rename, Delete. Отдельной кнопки «Установить» нет ни в одном flavor.

«Open With» вызывает `BrowseBinaryFileHandler.openWithDefaultApp`:
- Строится `Uri.parse(mediaFile.path)` — это raw `file://` URI.
- Запускается `Intent(Intent.ACTION_VIEW)` с MIME `application/vnd.android.package-archive`.
- **Критический баг:** передача `file://` URI в другое приложение через `startActivity` бросает `FileUriExposedException` на API 24+. Функция «Open With» для `.apk` сломана на всех поддерживаемых версиях (minSdk 26 = API 24+). `FLAG_GRANT_READ_URI_PERMISSION` на `file://` URI не работает — флаг действует только для `content://`.

`BinaryFileTypeDetector` корректно определяет `"apk"` → `MediaType.BINARY_EXECUTABLE`. MIME `application/vnd.android.package-archive` уже возвращается правильно.

### Android API для установки APK

- `android.Manifest.permission.REQUEST_INSTALL_PACKAGES` — protection level `signature|appop`; на API 26+ пользователь выдаёт per-app через Settings → «Install unknown apps».
- `PackageManager.canRequestPackageInstalls()` — API 26+; runtime-проверка текущего состояния разрешения.
- `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` — API 26+; deeplink в per-app Settings с `Uri.parse("package:${packageName}")` как data.
- `Intent(Intent.ACTION_INSTALL_PACKAGE)` + `EXTRA_RETURN_RESULT = true` — запускает системный PackageInstaller UI с явным диалогом подтверждения. Единственный правильный путь.
- `EXTRA_NOT_UNKNOWN_SOURCE = false` (значение по умолчанию) — системный установщик показывает предупреждение «неизвестный источник». Для noLegal оставляем значение по умолчанию — правильно.
- `ActivityResultContracts.StartActivityForResult` для получения `RESULT_OK` / `RESULT_CANCELED` / `RESULT_FIRST_USER` из установщика.

### Существующая инфраструктура FileProvider

`app_v2/src/main/res/xml/file_provider_paths.xml`:
- `<external-path name="external_storage" path="." />` — покрывает всё внешнее хранилище.
- `<cache-path name="cache" path="." />` — покрывает internal cache.
- Authority: `${applicationId}.fileprovider`. Для noLegal: `com.sza.fastmediasorter.nolegal.fileprovider`.
- FileProvider полностью готов; никаких дополнительных path entries не требуется.

Примеры корректного использования в кодовой базе: `BrowseShareOperationsHelper.kt:59`, `BrowseCameraCaptureManager.kt:129`, `PlayerShareManager.kt:33` — все строят `content://` URI через `FileProvider.getUriForFile` с authority `"${activity.packageName}.fileprovider"`.

### `@BindsOptionalOf` паттерн для noLegal DI

`BrowsePassthroughCaptureProvider` — прямой прецедент:
- `src/main/java/.../di/BrowsePassthroughOptionalModule.kt` объявляет `@BindsOptionalOf`.
- `src/noLegal/java/.../di/NoLegalLinkDownloadModule.kt` предоставляет конкретную реализацию.
- `BrowseManagerInitializer` принимает `java.util.Optional<BrowsePassthroughCaptureProvider>`.

Этот паттерн применим для `BrowseApkInstallHandler` без изменений архитектуры.

### Разрешения в манифесте

`REQUEST_INSTALL_PACKAGES` отсутствует в `src/main/AndroidManifest.xml` и в `src/noLegal/AndroidManifest.xml`. Добавляется исключительно в noLegal manifest.

### Почему это только noLegal (store policy)

Google Play Policy «Device and Network Abuse» + «Permissions»:
- `REQUEST_INSTALL_PACKAGES` помечен как high-risk permission; приложения с этим разрешением проходят ручной review.
- File manager без явного MDM/enterprise/store контекста с функцией установки произвольных APK отклоняется при review.
- FastMediaSorter не является app store, MDM-приложением или enterprise installer — кейс не проходит Play review.
- noLegal никогда не публикуется в Play Store; ограничение полностью снимается compile-time изоляцией в `src/noLegal/`.

---

## Просто и быстро

**1. `REQUEST_INSTALL_PACKAGES` в `src/noLegal/AndroidManifest.xml`**

Лицензия: стандартная Android permission, без redistribution-ограничений.
Объём: одна строка XML.

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

**2. `BrowseApkInstallHandler` в `src/noLegal/java/.../ui/browse/managers/`**

Лицензия: собственный код проекта, Apache-2.0.
Объём: ~120–150 строк.

Инкапсулирует полный flow:
- `canRequestPackageInstalls()` → если `false`, rationale dialog → `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- `ActivityResultLauncher` для возврата из Settings → после успеха повторно предлагает установку.
- `FileProvider.getUriForFile()` → `content://` URI.
- `Intent(Intent.ACTION_INSTALL_PACKAGE)` + `EXTRA_RETURN_RESULT = true`.
- `ActivityResultLauncher` для получения результата установки → Toast с исходом.

**3. `@BindsOptionalOf` DI wiring (зеркало `BrowsePassthroughOptionalModule`)**

Добавить в `src/main/java/.../di/`:
- `BrowseApkInstallOptionalModule` с `@BindsOptionalOf fun optionalApkInstallHandler(): BrowseApkInstallHandler`.

Добавить в `src/noLegal/java/.../di/`:
- Hilt `@Module` предоставляет конкретный `BrowseApkInstallHandler`.

Объём: ~30 строк на два модуля.

**4. Дополнительная кнопка «Установить» в `BrowseBinaryFileHandler.showBinaryFileMenu`**

Если `extension == "apk"` AND `apkInstallHandler.isPresent` → показать кнопку «Установить» в bottom sheet поверх существующих пунктов.

**5. Два новых launcher в `BrowseLauncherManager`**

Класс уже содержит все `ActivityResultLauncher`-регистрации Browse. Добавить:
- launcher для `ACTION_INSTALL_PACKAGE` → callback в `BrowseLauncherCallbacks`.
- launcher для `ACTION_MANAGE_UNKNOWN_APP_SOURCES` → callback в `BrowseLauncherCallbacks`.

---

## Сложно но возможно

**Split APK install (`.apks` / `.xapk` bundle)**

`PackageInstaller` session API: несколько APK splits пишутся в `PackageInstaller.Session`, затем `session.commit()`. На API 31+ `SessionParams.setRequireUserAction(USER_ACTION_REQUIRED)` (явно, не `NOT_REQUIRED`) показывает системный диалог — это единственно допустимый вариант. Требует отдельного coroutine-based pipeline, progress tracking, cleanup при ошибке. Значительно сложнее однофайлового `ACTION_INSTALL_PACKAGE`.

- Type blocker: complexity (не store-policy, не лицензия).
- Предварительное условие: должен существовать базовый однофайловый APK install (пункт выше).

**Установка APK из сетевого ресурса (SMB / FTP / SFTP)**

Промежуточный шаг: скачать APK во внутренний cache → установить из cache. `UnifiedFileOperationHandler` уже умеет копировать файлы из сетевых источников. Нужен lifecycle-aware progress display (паттерн `BrowseLoadingManager`), cleanup после завершения установки (успех или отказ), корректная обработка ошибок при загрузке.

- Type blocker: complexity.

---

## Фантастика, но хочется

**Тихая установка без диалога**

Требует либо device owner (`DevicePolicyManager`), либо root + `pm install`, либо Shizuku ADB shell. На системном уровне `PackageInstaller.SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)` (API 31+) — технически возможно только при device owner или shell UID. **ЗАПРЕЩЕНО** в noLegal: нарушает security-rule S0156 §3.2 (явный запрет silent install и hidden behavior).

**Установка с downgrade / signature override**

Требует `adb install --allow-downgrade` или root. Системный PackageInstaller всегда проверяет signature mismatch и version downgrade — пользователю показывается ошибка установщика. Без root не обходится.

---

## Блокеры

- **`store-policy`:** `REQUEST_INSTALL_PACKAGES` в market APK → Google Play high-risk permission review → отклонение для file manager context. Изоляция в `src/noLegal/` полностью снимает блокер: noLegal не публикуется.
- **`security-risk`:** Silent install через `PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED` — запрещено. Любой install path обязан проходить через системный UI с явным подтверждением пользователя.
- **`bug-current`:** `BrowseBinaryFileHandler.openWithDefaultApp` использует `Uri.parse(mediaFile.path)` — это `file://` URI, бросает `FileUriExposedException` на API 24+. APK install handler исправляет это для `.apk`-ветки; общий баг требует отдельного fix.
- **`ux-constraint`:** нельзя автоматически начинать установку сразу после возврата из `ACTION_MANAGE_UNKNOWN_APP_SOURCES` без дополнительного тапа пользователя — Android не гарантирует `RESULT_OK` из этого Intent; нужна явная повторная попытка (кнопка в snackbar или повторный tap на файл).

---

## Потенциальные follow-up спеки

1. **noLegal APK Install Handler** — `BrowseApkInstallHandler` в `src/noLegal/`, `REQUEST_INSTALL_PACKAGES` в noLegal manifest, `@BindsOptionalOf` DI wiring, кнопка «Установить» в binary file bottom sheet, два `ActivityResultLauncher` в `BrowseLauncherManager`, rationale dialog + Settings deeplink, `FileProvider` URI для `ACTION_INSTALL_PACKAGE`. Отдельный slug: `Sxxxx_nolegal-apk-install.md`.

2. **Fix `BrowseBinaryFileHandler.openWithDefaultApp` — `file://` → `content://`** — заменить `Uri.parse(mediaFile.path)` на `FileProvider.getUriForFile()` для локальных путей, оставить raw URI только для сетевых схем (smb://, ftp://, sftp://, http://). Это отдельный баг не специфичный для noLegal; может быть отдельной public-flavor fix-спекой.

3. **noLegal APK Cache-and-Install из сетевых ресурсов** — download-to-cache step перед install, progress UI (паттерн `BrowseLoadingManager`), cleanup после успешной / неуспешной установки. Зависит от спеки №1.
