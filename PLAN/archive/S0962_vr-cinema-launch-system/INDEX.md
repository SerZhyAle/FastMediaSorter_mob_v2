# S0962 - VR-кинотеатр, Столп 1: тактический план

**Ticket:** S0962
**Strategic spec:** `PLAN/S0962_vr-cinema-launch-system.md`
**Parent epic:** S0773
**Status:** Tactical

## Объём итерации (решение владельца 2026-07-06)

Только сквозной путь, не зависящий от Столпа 2 (immersive-браузер, S0963):

- Пункт «Открыть в VR-кинотеатре» в контекстном меню (⋮) видеофайла в Browse.
- Гейт видимости пункта: VR скомпилирован в билд И мастер-тумблер VR-3D включён И устройство XR-способно.
- Холодный вход в immersive напрямую на выбранном видеофайле (`FILE_URI`), без открытого обычного плеера.

Отложено на другие столпы/тикеты:

- Плитка программы «VR-кинотеатр» + диалог выбора ресурса -> едут со Столпом 2 (нужен браузер как назначение).
- Пункт «Открыть в VR-кинотеатре» для ресурса -> Столп 2 (S0963).
- HUD-дорожки -> Столп 3 (S0964).
- Паритет гейтов на флейворе `vr` (сейчас `SUPPORT_VR_PLAYER=false` на `vr`) -> отдельный follow-up унификации гейтов (наследник закрытого S0241). Столп 1 работает на `noLegal` (VR-плеер включён).

## Переиспользуемая база (research подтвердил)

- Транспорт: `core/xr/VrLaunchContract.kt` (`StartVrPlaybackRequest`, `VrLaunchPoint.BROWSE_TILE` - точка «из браузера», ноль call-site'ов).
- Use-case: `core/xr/StartVrPlaybackUseCase.kt`; `invoke(request, returnTarget=null)` сам стартует immersive-Activity (`startActivity` + `FLAG_ACTIVITY_NEW_TASK`), preflight по `XrDetectionFacade`.
- Гейт-фасад: `core/xr/XrDetectionFacade.kt` -> `XrDetectionState` (`NONE` / `AVAILABLE_DISABLED_BY_USER` / `AVAILABLE_ENABLED`).
- Биндинги есть в обоих флейворах: реальные в `src/vr/.../di/XrModule.kt`, No-Op в `src/vrStub/.../di/NoOpXrModule.kt` (`NoOpXrDetectionFacade` -> NONE, `NoOpStartVrPlaybackUseCase` -> Unavailable). Инъекция в `src/main` компилится на всех вариантах без флейвор-гардов.
- Меню видеофайла: `ui/browse/helpers/BrowseFileOverflowMenuManager.kt#showFor`, вызывается из `ui/browse/managers/BrowseManagerInitializer.kt#showPerFileOverflowMenu`.
- Прецедент гейта+запуска: `ui/player/helpers/PlayerVrLaunchManager.kt` (warm-вход, требует открытый файл).

## Фазы

- PHASE_01 - меню видеофайла + холодный FILE_URI-вход (единственная фаза итерации).

## Критерии готовности (тактический уровень)

1. На `noLegal` при включённом VR-3D на XR-устройстве в меню (⋮) видеофайла виден пункт «Открыть в VR-кинотеатре».
2. Пункт скрыт, когда VR-3D выключен, устройство не XR, либо билд без VR (`standard`/`lite`/`photos`/`legacy` - No-Op фасад -> NONE).
3. Нажатие открывает immersive напрямую на этом видеофайле, обычный плеер не открывается; выход возвращает в Browse.
4. Non-local (сеть/облако) видео: пункт может отображаться, но запуск даёт короткий toast «недоступно» (use-case возвращает InvalidUri) - без краха.
5. Обычный плеер, диагностическая кнопка и остальные пункты меню не затронуты.
6. Компиляция зелёная на `noLegal` (fkn) и на стандартном (fc); detekt-гейт по затронутым файлам чистый.
</content>
