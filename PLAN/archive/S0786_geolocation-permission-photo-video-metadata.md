# Спецификация: S0786 - Опциональное разрешение геолокации для метаданных фото-видео

**Ticket:** S0786
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29 (во время S0766)

**Текст:**

Добавить в наше активити получения прав при инсталляции и из окна настроек, и во все алгоритмы работы с правами, право на получение геолокации - объяснить, что это для фото-видео. Если пользователь не хочет - не требуем, просто не вставляем эту информацию в метаданные.

---

## 1. Проблема

S0766 уже реализовал сам геотег: manifest объявляет `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, `CameraLocationProvider` даёт координаты, а капча-путь встраивает их в EXIF при включённом opt-in-флаге и наличии разрешения (без разрешения - тихо пропускает). Но LOCATION не зарегистрировано в permission-framework приложения, поэтому не появляется ни в онбординге, ни в Settings -> Permissions. Пользователь может выдать его только через системные настройки, из-за чего geotag фактически недостижим обычным путём.

## 2. Цели

1. LOCATION появляется в онбординге получения прав и в Settings -> Permissions с понятным описанием, что это для геометки снимаемых фото/видео.
2. Пользователь может выдать/отклонить его из UI приложения; отказ ничего не ломает - снимки просто без координат (поведение S0766).

**Non-goals:**

- Сам механизм геотега и встраивание в EXIF (сделано в S0766).
- Фоновая геолокация (`ACCESS_BACKGROUND_LOCATION`) - не нужна, координаты берутся только при открытой камере.

## 3. Ограничения

- **Flavor:** permission-registry общий; entry без flavor-gate (как `camera`).
- **API level:** minSdk 23 (LocationManager, S0766).
- **Локализация:** EN/RU/UK - добавлены `perm_group_location`, `perm_title_location`, `perm_desc_location`.
- **Play policy:** разрешение уже объявлено в manifest (S0766) - S0786 не вводит новых обязательств Data Safety, только делает его грантимым из UI.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0766 (геотег-механизм + manifest-разрешения).

## 4. Критерии готовности

1. `access_fine_location` присутствует в `getEntries()`, `getWelcomeEntries()` и в группе `PermissionGroup.LOCATION`.
2. Entry `optional = true` (отказ допустим).
3. Строки rationale EN/RU/UK на месте, объясняют «для фото/видео».
4. Проект компилируется; юнит-тесты registry зелёные.

## Реализация (2026-07-01, Simple-путь)

- `PermissionGroup`: добавлено значение `LOCATION` (между `CAMERA` и `SYSTEM`).
- `PermissionRegistryRepositoryImpl`: добавлен `PermissionEntry(id="access_fine_location", manifestName=ACCESS_FINE_LOCATION, group=LOCATION, optional=true, minSdk=23)`; ветка `LOCATION -> R.string.perm_group_location` в `getGroups()`. Fine-location даёт точную геометку; капча-путь принимает и coarse, так что coarse-only грант тоже работает.
- Строки `perm_group_location` / `perm_title_location` / `perm_desc_location` (EN/RU/UK); описание подчёркивает опциональность и «нет координат при отказе».
- Тест `PermissionRegistryRepositoryImplTest`: +кейс на регистрацию LOCATION в registry/onboarding/groups - зелёный.
- Компиляция `compileStandardDebugKotlin` + `processStandardDebugResources` - BUILD SUCCESSFUL; тест registry - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** первый запуск (онбординг прав) показывает пункт «Местоположение» с описанием про геометку фото/видео; тот же пункт есть в Settings -> Permissions; выдача разрешения + включённый geotag -> снятое в приложении фото содержит GPS в EXIF; отказ -> снимок без координат, приложение не требует разрешения повторно.
