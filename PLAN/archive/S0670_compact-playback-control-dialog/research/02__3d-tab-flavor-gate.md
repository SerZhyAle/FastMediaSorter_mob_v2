# Research 02 - Флавор-гейт вкладки 3D

**Spec:** S0670
**Item:** §6.2
**Status:** Resolved
**Date:** 2026-06-24

## Вопрос

Каким сигналом гейтить вкладку 3D, чтобы она показывалась ровно на «VR и выше» (vr+noLegal) и скрывалась на standard/lite/photos/legacy?

## Находки

### Текущий гейт (непригоден)

`PlaybackControlDialogFragment.activeSections` добавляет вкладку STEREO при условии `supportsVrPlayer || isStereoContent`. Это даёт ложный показ на standard при детекте стерео-контента и не соответствует решению владельца.

### Ловушка `supportsVrPlayer`

`MediaCapabilities.supportsVrPlayer = BuildConfig.SUPPORT_VR_PLAYER`. Значения по флаворам в `app_v2/build.gradle.kts`:

- standard - false
- lite - false
- photos - false
- legacy - false
- vr - **false** (S0241: vr временно идёт по общему пути плеера, как standard)
- noLegal - **true**

Итог: `supportsVrPlayer` истинен только на `noLegal`. Использовать его для гейта 3D нельзя - на `vr` вкладка пропала бы, что противоречит «VR и выше».

### Корректный сигнал: доступность VR-медиа-секции

`VrMediaSectionContract.isAvailable`:

- реализация `VrMediaSectionContractImpl` (isAvailable = true) лежит в `src/vr/java`;
- заглушка `NoOpVrMediaSectionContract` (isAvailable = false) лежит в `src/vrStub/java`.

Разводка source set в `app_v2/build.gradle.kts`:

- standard, lite, photos, legacy → `src/vrStub/java` (NoOp, false)
- vr → дефолтный `src/vr/*` + `src/vrOnly/java` (real, true)
- noLegal → явно добавляет `src/vr/java` (real, true)

Значит `isAvailable` истинен ровно на `vr`+`noLegal`, ложен на остальных - это и есть «VR и выше».

## Решение для tech-фазы

Гейтить вкладку 3D по флавор-абстракции «VR-медиа доступно», истинной на vr+noLegal:

- вариант A: пробросить `VrMediaSectionContract` в диалог через Hilt EntryPoint (диалог уже использует EntryPoint для `MediaCapabilities`);
- вариант B: добавить новое поле в `MediaCapabilities` (например `supportsVrMediaControls`), истинное в общем для vr+noLegal модуле `MediaCapabilitiesModule` (тот, что в `src/vr/java`, шарится обоими), ложное в остальных флаворных модулях.

Любой вариант соблюдает Rule 14/15 (никаких `BuildConfig.IS_*`/`SUPPORT_*` в `src/main`). Ветку `isStereoContent` убрать.

## Источники

- `app_v2/build.gradle.kts` (buildConfigField SUPPORT_VR_PLAYER; sourceSets vr/vrStub/vrOnly)
- `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrMediaSectionContract.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrMediaSectionContractImpl.kt`
- `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpVrMediaSectionContract.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` (activeSections)
