# Стратегическая спецификация: S0183 — APK Install from Browse (noLegal)

**Ticket:** S0183
**Status:** BlockNeedUserTest
**Implemented date:** 2026-05-14
**Priority:** 50
**Date:** 2026-05-13
**Tier:** 3
**Epic:** S0156
**Tactical plan:** `PLAN/S0183_nolegal-apk-install/INDEX.md`
**Roadmap entry:** S0156 §5.1 Столп E — «APK install flow из Browse»
**Research:** `PLAN/S0156_nolegal-capability-surface-audit/apk-install.md`

---

## 1. Проблема

В noLegal-сборке нет возможности установить `.apk`-файл прямо из Browse. Кнопка «Open With» в bottom sheet технически сломана (передаёт `file://` URI, что бросает `FileUriExposedException` на API 24+). Пользователь вынужден открывать файловый менеджер ОС для установки APK.

---

## 2. Цели

1. Добавить кнопку «Установить» в bottom sheet при клике на `.apk` — только в noLegal flavor.
2. Реализовать корректный install flow через системный `PackageInstaller` UI с явным диалогом подтверждения.
3. Обработать permission rationale (`REQUEST_INSTALL_PACKAGES`) и deeplink в Settings.
4. Обработать все исходы установки: успех, отказ пользователя, ошибка.

**Non-goals:**
- Silent install любого вида.
- Split APK / `.xapk` / `.apks` bundle.
- Установка APK из сетевых ресурсов (SMB/FTP/SFTP).
- Fix `openWithDefaultApp` для не-APK файлов (отдельный баг).
- Root / Shizuku / ADB install paths.

---

## 3. Ограничения

- **Flavor:** исключительно `noLegal`. Compile-time изоляция через `src/noLegal/java/`.
- **Manifest:** `REQUEST_INSTALL_PACKAGES` только в `src/noLegal/AndroidManifest.xml`.
- **Silent install:** `PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED` запрещён.
- **API:** `PackageManager.canRequestPackageInstalls()` + `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` — API 26+, совпадает с noLegal minSdk.
- **Strings:** EN/RU/UK parity, ключи с префиксом `s0183_`.
- **Communication policy:** все user-visible строки проверяются против `docs/COMMUNICATION_POLICY.md` §2 и §6.

---

## 4. Архитектура

- `BrowseApkInstallHandler` — abstract class в `src/main/`. Контракт: `registerLaunchers`, `showInstallMenu`.
- `BrowseApkInstallHandlerImpl` — конкретная реализация в `src/noLegal/`. `@Inject constructor(@ApplicationContext context: Context)`.
- `BrowseApkInstallOptionalModule` — `@BindsOptionalOf` в `src/main/di/`.
- `BrowseApkInstallModule` — `@Binds` в `src/noLegal/di/`.
- `BrowseActivity` инжектирует `Optional<BrowseApkInstallHandler>`, передаёт в `BrowseManagerInitializer`.
- `BrowseManagerInitializer` вызывает `registerLaunchers(activity)` при инициализации, передаёт handler в `BrowseBinaryFileHandler`.
- `BrowseBinaryFileHandler` показывает кнопку «Установить» если `handler != null && extension == "apk"`.

**Наследованные ADR из S0156:**
- ADR-3: один `noLegal`, compile-time изоляция.
- ADR-4: personal sideload не отменяет security review.
- ADR-5: `src/noLegal/java/` sourceSet, не runtime flag.

---

## 5. Влияние на пользователя

Без изменений в `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.
Новая возможность фиксируется в `docs/FEATURES_noLegal.md` + `_RU` + `_UK`.

---

## 6. Открытые вопросы

Нет.

---

## Revision History

- **2026-05-13** — initial strategic spec; immediately advanced to Tactical per research document `apk-install.md`.
